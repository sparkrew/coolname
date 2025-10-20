package io.github.chains_project.coolname.api_finder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.nio.file.Path;

public class Main {

    static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        int exitCode = new CommandLine(new CLIEntryPoint()).execute(args);
        System.exit(exitCode);
    }

    @CommandLine.Command(subcommands = {Processor.class}, mixinStandardHelpOptions = true, version = "0.1")
    public static class CLIEntryPoint implements Runnable {
        @Override
        public void run() {
            CommandLine.usage(this, System.out);
        }
    }

    @CommandLine.Command(name = "process", mixinStandardHelpOptions = true, version = "0.1")
    private static class Processor implements Runnable {
        @CommandLine.Option(
                names = {"-j", "--jar-path"},
                paramLabel = "JAR-PATH",
                description = "The path to the JAR file to analyze",
                required = true
        )
        String jarPath;

        @CommandLine.Option(
                names = {"-r", "--report-file"},
                paramLabel = "REPORT-FILE",
                description = "The path to the JSON file where analysis results should be written to. If not specified,"
                        + " the report will be written to a file named third_party_apis.json",
                defaultValue = "third_party_apis.json"
        )
        String reportFile;

        @CommandLine.Option(
                names = {"-p", "--package-name"},
                paramLabel = "PACKAGE-NAME",
                description = "The package name of the project under consideration to filter them as not third-party " +
                        "APIs.",
                required = true
        )
        String packageName;

        @CommandLine.Option(
                names = {"-m", "--package-map"},
                paramLabel = "PACKAGE-MAP",
                description = "The path to the package map file. " +
                        "This file contains the mapping of package names to Maven coordinates.",
                required = true
        )
        Path packageMapPath;

        @CommandLine.Option(
                names = {"-s", "--source-code-path"},
                paramLabel = "SOURCE-CODE-PATH",
                description = "The path to the source code root directory of the project under consideration."
        )
        String sourceCodePath;

        @Override
        public void run() {
            if (sourceCodePath == null) {
                log.warn("No source code path provided, skipping source code extraction.");
                MethodExtractor.process(jarPath, reportFile, packageName, packageMapPath);
            }
            MethodExtractor.process(jarPath, reportFile, packageName, packageMapPath, sourceCodePath);
        }
    }
}
