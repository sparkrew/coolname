import os
import re
import json
import gc
from datetime import datetime
from pathlib import Path
from typing import List, Dict, Any
import torch
from transformers import AutoTokenizer, AutoModelForCausalLM

# import PyTorch/XLA, If not present fall back to CPU or CUDA
_USE_XLA = False
try:
    import torch_xla.core.xla_model as xm
    import torch_xla.utils.utils as xu
    _USE_XLA = True
except Exception:
    xm = None
    xu = None

# Helper Utilities
def safe_name(s: str) -> str:
    s = re.sub(r"[^A-Za-z0-9._\- ]+", "_", s)
    s = re.sub(r"\s+", "_", s)
    return s.strip("_")[:80] or "dependency"

def write_file(path: Path, content: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


# Model Loader
def get_device():
    """Return the best device (TPU XLA device if available, else CUDA if available, else CPU)."""
    if _USE_XLA:
        return xm.xla_device()
    if torch.cuda.is_available():
        return torch.device("cuda")
    return torch.device("cpu")

def load_model(model_name: str):
    print(f"[info] Loading model: {model_name}")

    device = get_device()
    using_xla = _USE_XLA and ("xla" in str(device).lower())
    using_cuda = torch.cuda.is_available() and not using_xla

    # Choose appropriate dtype, TPU preferr bfloat16
    if using_xla:
        model_dtype = torch.bfloat16
    elif using_cuda:
        model_dtype = torch.float16
    else:
        model_dtype = torch.float32

    tokenizer = AutoTokenizer.from_pretrained(model_name, use_fast=True)

    model = AutoModelForCausalLM.from_pretrained(
        model_name,
        torch_dtype=model_dtype,
        low_cpu_mem_usage=True,
        device_map=None,
    )

    model.to(device)

    print(f"[info] Model loaded successfully on {device} (dtype={model_dtype})")
    return model, tokenizer, device


llm_instruction = """
Your task is to generate a JUnit test that confirms reachability of the specified third party method within the target project.

You are given dependency information that describes:
1. The entry point method inside the project that eventually triggers the third party call.
2. The full method body of the entry point.
3. The fully qualified third party method that must be reached during execution.
4. The package that contains the third party method.
5. The path of intermediate calls that lead from the entry point to the third party method.
6. Either the full methods or sliced methods along that path depending on the technique being used.

Your goal is to produce a Java JUnit test that:
* Calls the entry point in a way that ensures execution flows through the provided path.
* Ensures the third party method is actually reached.
* Provides any required setup, mocks, stubs, or input values so the path executes without failure.
* Avoids unrelated logic and focuses only on reaching the third party call.

Output Requirements:
* Return only the Java code in a fenced code block labeled java i.e ```java```. Do not include explanations or commentary!!!
* The test must compile.
* Use realistic values for parameters so the call chain executes.
* If mocking is needed to force reachability, add appropriate mocks or placeholders.
"""

def test_generator(
    prompt: str,
    dependency_data: dict,
    model_name: str,
    output_root: str,
    technique: str = "",
):
    timestamp = datetime.utcnow().strftime("%Y%m%dT%H%M%SZ")

    qualified_name = dependency_data.get("thirdPartyMethod") or dependency_data.get("thirdPartyPackage") or "unknown"
    entry_point = dependency_data.get("entryPoint", "entry")
    
    safe_filename = safe_name(f"{entry_point}__{qualified_name}") + f"_{timestamp}.java"

    folder = Path(output_root)
    folder.mkdir(parents=True, exist_ok=True)
    model, tokenizer, device = load_model(model_name)

    messages = [
        {"role": "system", "content": llm_instruction},
        {"role": "user", "content": prompt},
    ]

    if hasattr(tokenizer, "apply_chat_template"):
        inputs = tokenizer.apply_chat_template(
            messages,
            add_generation_prompt=False,
            tokenize=True,
            return_dict=True,
            return_tensors="pt",
        )
    else:
        conversation_text = "\n".join([m["content"] for m in messages])
        inputs = tokenizer(conversation_text, return_tensors="pt")

    for k, v in list(inputs.items()):
        if isinstance(v, torch.Tensor):
            inputs[k] = v.to(device)

    print("[info] Generating message...")
    
    outputs = model.generate(**inputs, max_new_tokens=2048)
    if _USE_XLA:
        try:
            xm.mark_step()
        except Exception:
            pass
    print("[info] Generation complete.")

    if isinstance(outputs, torch.Tensor):
        seq = outputs[0]
    else:
        seq = getattr(outputs, "sequences", None)
        seq = seq[0] if seq is not None else outputs[0]

    start_idx = inputs["input_ids"].shape[-1] if "input_ids" in inputs else 0
    
    # Move seq to CPU for decoding
    seq_cpu = seq.cpu() if seq.device.type != "cpu" else seq
    generated_ids = seq_cpu[start_idx:].numpy().tolist()
    decoded = tokenizer.decode(generated_ids, skip_special_tokens=True).strip()

    result_text = decoded or ""

    m = re.search(r"```(?:java)?\s*(.*?)\s*```", result_text, re.DOTALL)
    java_code = (m.group(1).strip() if m else result_text).strip()

    if not java_code:
        java_code = "// [warning] model returned empty output\n" + (result_text or "// no content")

    file_path = folder / safe_filename
    file_path.write_text(java_code, encoding="utf-8")

    print(f"[done] Output written to {file_path.resolve()}")

    try:
        del model
        del tokenizer
        
        gc.collect()
        if _USE_XLA:
            try:
                xm.rendezvous("cleanup")
            except Exception:
                pass
        elif torch.cuda.is_available():
            try:
                torch.cuda.empty_cache()
                torch.cuda.ipc_collect()
            except Exception:
                pass
    except Exception:
        pass

    return {
        "folder": str(folder.resolve()),
        "java_file": str(file_path.resolve()),
    }

def generate_etheo_third_party_tests(
    project_name: str,
    model_name: str,
    examples_root: str,
    output_root: str,
):
    base = Path(examples_root) / project_name

    technique_files = {
        "entry_point": ("third_party_apis_entry_point.json", "entryPointPaths"),
        "full_methods": ("third_party_apis_full_methods.json", "fullMethodsPaths"),
        "sliced": ("third_party_apis_sliced.json", "slicedPaths"),
    }

    results = []

    for technique, (filename, json_key) in technique_files.items():
        file_path = base / filename
        if not file_path.exists():
            print(f"[skip] missing file {filename}")
            continue

        try:
            raw = file_path.read_text(encoding="utf8")
            parsed = json.loads(raw)
            deps = parsed.get(json_key, [])
        except Exception as e:
            print(f"[error] failed to load {filename}: {e}")
            continue

        if not isinstance(deps, list) or not deps:
            print(f"[skip] no data in {filename}")
            continue

        technique_output_dir = (
            Path(output_root) / project_name / technique
        )
        technique_output_dir.mkdir(parents=True, exist_ok=True)

        for idx, dep in enumerate(deps):
            try:
                dependency_data = dep
                dep_json = json.dumps(dependency_data, indent=2, ensure_ascii=False)

                prompt = (
                    f"Dependency Data\n"
                    f"{dep_json}\n\n"
                )

                out = test_generator(
                    prompt=prompt,
                    dependency_data=dependency_data,
                    model_name=model_name,
                    output_root=str(technique_output_dir),
                    technique=technique,
                )

                results.append({
                    "technique": technique,
                    "file": filename,
                    "index": idx,
                    "entryPoint": dependency_data.get("entryPoint"),
                    "thirdPartyMethod": dependency_data.get("thirdPartyMethod"),
                    "result": out,
                })

                print(f"[ok] {filename} #{idx} for technique {technique}")

            except Exception as e:
                print(f"[error] {filename} #{idx} failed: {e}")
                continue

    return results


if __name__ == "__main__":
    results = generate_etheo_third_party_tests(
        project_name="tika-data",
        model_name="codellama/CodeLlama-70b-Instruct-hf",
        examples_root="/etheo-project/examples",
        output_root="/generated_tests/tika",
    )

