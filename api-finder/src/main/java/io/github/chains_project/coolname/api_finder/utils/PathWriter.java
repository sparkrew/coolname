package io.github.chains_project.coolname.api_finder.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.chains_project.coolname.api_finder.MethodExtractor;
import io.github.chains_project.coolname.api_finder.model.*;;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sootup.core.model.SootMethod;
import sootup.core.signatures.MethodSignature;
import sootup.java.core.JavaSootMethod;
import sootup.java.core.views.JavaView;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles writing analysis results in three different formats:
 * 1. Full methods - complete implementation of all methods in paths
 * 2. Entry point focused - only the entry point body with path info
 * 3. Method slices - relevant code slices for reaching third-party methods
 */
public class PathWriter {

    private static final Logger log = LoggerFactory.getLogger(PathWriter.class);

    /**
     * Write all three output formats from the analysis result
     */
    public static void writeAllFormats(AnalysisResult result, String basePath, JavaView view) {
        // Generate the three output file paths based on the base path
        String fullMethodsPath = basePath.replace(".json", "_full_methods.json");
        String entryPointPath = basePath.replace(".json", "_entry_point.json");
        String slicedPath = basePath.replace(".json", "_sliced.json");

        // Write Format 1: Full methods for all methods in the path
        writeFullMethodsFormat(result, fullMethodsPath, view);

        // Write Format 2: Entry point body only
        writeEntryPointFormat(result, entryPointPath, view);

        // Write Format 3: Method slices (code slicing based on data/control flow)
        // This one is optional and more complex - we'll implement a basic version
        writeSlicedFormat(result, slicedPath, view);
    }

    /**
     * Format 1: Write paths with full method bodies for all methods
     * This gives you the complete implementation of every method in the path
     */
    private static void writeFullMethodsFormat(AnalysisResult result, String outputPath, JavaView view) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        try {
            List<FullMethodsPathData> fullMethodsPaths = new ArrayList<>();

            for (ThirdPartyPath tp : result.thirdPartyPaths()) {
                // Extract full method bodies for all methods in the path
                List<String> fullMethods = extractFullMethodBodies(view, tp.path());

                // Build the path as strings
                List<String> pathStrings = tp.path().stream()
                        .map(MethodExtractor::getFilteredMethodSignature)
                        .collect(Collectors.toList());

                String thirdPartyPackage = extractPackageName(
                        MethodExtractor.getFilteredMethodSignature(tp.thirdPartyMethod())
                );

                FullMethodsPathData data = new FullMethodsPathData(
                        MethodExtractor.getFilteredMethodSignature(tp.entryPoint()),
                        MethodExtractor.getFilteredMethodSignature(tp.thirdPartyMethod()),
                        thirdPartyPackage,
                        pathStrings,
                        fullMethods
                );

                fullMethodsPaths.add(data);
            }

            File outputFile = new File(outputPath);
            mapper.writeValue(outputFile, Map.of("fullMethodsPaths", fullMethodsPaths));
            log.info("Successfully wrote {} full methods paths to {}", fullMethodsPaths.size(),
                    outputFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to write full methods format to JSON", e);
        }
    }

    /**
     * Format 2: Write paths with only the entry point body
     * This is useful when you primarily care about the public API method
     */
    private static void writeEntryPointFormat(AnalysisResult result, String outputPath, JavaView view) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        try {
            List<EntryPointFocusedData> entryPointPaths = new ArrayList<>();

            for (ThirdPartyPath tp : result.thirdPartyPaths()) {
                // Extract only the entry point body
                String entryPointBody = extractMethodBody(view, tp.entryPoint());

                // Build the path as strings
                List<String> pathStrings = tp.path().stream()
                        .map(MethodExtractor::getFilteredMethodSignature)
                        .collect(Collectors.toList());

                String thirdPartyPackage = extractPackageName(
                        MethodExtractor.getFilteredMethodSignature(tp.thirdPartyMethod())
                );

                EntryPointFocusedData data = new EntryPointFocusedData(
                        MethodExtractor.getFilteredMethodSignature(tp.entryPoint()),
                        entryPointBody,
                        MethodExtractor.getFilteredMethodSignature(tp.thirdPartyMethod()),
                        thirdPartyPackage,
                        pathStrings
                );

                entryPointPaths.add(data);
            }

            File outputFile = new File(outputPath);
            mapper.writeValue(outputFile, Map.of("entryPointPaths", entryPointPaths));
            log.info("Successfully wrote {} entry point paths to {}", entryPointPaths.size(),
                    outputFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to write entry point format to JSON", e);
        }
    }

    /**
     * Format 3: Write paths with code slices
     * This extracts only the relevant portions of each method needed to reach the third-party call
     */
    private static void writeSlicedFormat(AnalysisResult result, String outputPath, JavaView view) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        try {
            List<SlicedMethodData> slicedPaths = new ArrayList<>();

            for (ThirdPartyPath tp : result.thirdPartyPaths()) {
                // Extract slices for each method in the path
                // This is the complex part that uses data/control flow analysis
                List<String> methodSlices = MethodExtractor.MethodSlicer.extractMethodSlices(view, tp.path());

                // Build the path as strings
                List<String> pathStrings = tp.path().stream()
                        .map(MethodExtractor::getFilteredMethodSignature)
                        .collect(Collectors.toList());

                String thirdPartyPackage = extractPackageName(
                        MethodExtractor.getFilteredMethodSignature(tp.thirdPartyMethod())
                );

                SlicedMethodData data = new SlicedMethodData(
                        MethodExtractor.getFilteredMethodSignature(tp.entryPoint()),
                        MethodExtractor.getFilteredMethodSignature(tp.thirdPartyMethod()),
                        thirdPartyPackage,
                        pathStrings,
                        methodSlices
                );

                slicedPaths.add(data);
            }

            File outputFile = new File(outputPath);
            mapper.writeValue(outputFile, Map.of("slicedPaths", slicedPaths));
            log.info("Successfully wrote {} sliced paths to {}", slicedPaths.size(),
                    outputFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to write sliced format to JSON", e);
        }
    }

    /**
     * Extract full method bodies for all methods in a path.
     * This gives you the complete implementation of each method.
     */
    private static List<String> extractFullMethodBodies(JavaView view, List<MethodSignature> path) {
        List<String> methodBodies = new ArrayList<>();

        for (MethodSignature methodSig : path) {
            String body = extractMethodBody(view, methodSig);
            methodBodies.add(body);
        }

        return methodBodies;
    }

    /**
     * Extract the body of a single method.
     * Returns the method's implementation or a placeholder if not available.
     */
    private static String extractMethodBody(JavaView view, MethodSignature methodSig) {
        try {
            Optional<JavaSootMethod> methodOpt = view.getMethod(methodSig);

            if (methodOpt.isPresent()) {
                SootMethod method = methodOpt.get();

                // Get method body if available (this is the Jimple IR representation)
                if (method.hasBody()) {
                    return method.getBody().toString();
                } else {
                    // If no body available (e.g., third-party, interface, or abstract method)
                    return "// Method body not available for: " +
                            MethodExtractor.getFilteredMethodSignature(methodSig);
                }
            } else {
                return "// Method not found in view: " +
                        MethodExtractor.getFilteredMethodSignature(methodSig);
            }
        } catch (Exception e) {
            log.warn("Failed to extract method body for {}: {}", methodSig, e.getMessage());
            return "// Error extracting method body: " + e.getMessage();
        }
    }

    /**
     * Extract the package name from a fully qualified method signature
     */
    private static String extractPackageName(String method) {
        int methodDot = method.lastIndexOf('.');
        if (methodDot == -1) return null;
        String className = method.substring(0, methodDot);
        int classDot = className.lastIndexOf('.');
        if (classDot == -1) return null;
        return className.substring(0, classDot);
    }
}
