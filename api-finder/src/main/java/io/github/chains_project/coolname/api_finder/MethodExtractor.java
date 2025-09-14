package io.github.chains_project.coolname.api_finder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.chains_project.coolname.api_finder.utils.PackageMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sootup.callgraph.CallGraph;
import sootup.callgraph.RapidTypeAnalysisAlgorithm;
import sootup.core.inputlocation.AnalysisInputLocation;
import sootup.core.model.SootMethod;
import sootup.core.signatures.MethodSignature;
import sootup.java.bytecode.frontend.inputlocation.JavaClassPathAnalysisInputLocation;
import sootup.java.core.views.JavaView;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class MethodExtractor {

    static final Logger log = LoggerFactory.getLogger(MethodExtractor.class);
    static Set<String> ignoredPrefixes;

    /**
     * This method processes the JAR file to extract third party API calls and their paths.
     * It initializes the call graph,and finds paths that involve third-party method calls.
     *
     * @param pathToJar      Path to the JAR file to analyze.
     * @param reportPath     Path where the analysis report will be written.
     * @param packageName    The package name of the project under consideration to filter the events.
     * @param packageMapPath Path to the package map file that contains the mapping of package names to Maven coordinates.
     */
    public static void process(String pathToJar, String reportPath, String packageName, Path packageMapPath) {
        // Start reading the jar with sootup. Here we use all the public methods as the entry points.
        ignoredPrefixes = PackageMatcher.loadIgnoredPrefixes(packageName);
        JavaView view = createJavaView(pathToJar);
        Set<MethodSignature> entryPoints = detectEntryPoints(view, packageName);
        log.info("Found " + entryPoints.size() + " public methods as entry points.");

        AnalysisResult result = analyzeReachability(view, entryPoints, packageMapPath);

        writeThirdPartyPathsToJson(result, reportPath);
        log.info("Third party paths written to: " + reportPath);
    }

    private static JavaView createJavaView(String pathToJar) {
        AnalysisInputLocation inputLocation = new JavaClassPathAnalysisInputLocation(pathToJar);
        return new JavaView(inputLocation);
    }

    private static AnalysisResult analyzeReachability(JavaView view, Set<MethodSignature> entryPoints,
                                                      Path packageMapPath) {
        List<ThirdPartyPath> thirdPartyPaths = new ArrayList<>();

        try {
            RapidTypeAnalysisAlgorithm cha = new RapidTypeAnalysisAlgorithm(view);
            CallGraph cg = cha.initialize(new ArrayList<>(entryPoints));

            // For each entry point, find all reachable methods and identify third-party calls
            for (MethodSignature entryPoint : entryPoints) {
                Set<MethodSignature> reachable = getAllReachableMethods(cg, entryPoint);

                // Find all third-party methods in reachable set
                Set<MethodSignature> thirdPartyMethods = reachable.stream()
                        .filter(ms -> MethodExtractor.isThirdPartyMethod(ms, packageMapPath))
                        .collect(Collectors.toSet());

                // For each third-party method, find direct paths from entry point
                for (MethodSignature thirdPartyMethod : thirdPartyMethods) {
                    List<List<MethodSignature>> allPaths = findPaths(cg, entryPoint, thirdPartyMethod);

                    for (List<MethodSignature> path : allPaths) {
                        // Check if this is a direct path (only the last method is third-party)
                        if (isDirectPath(path, packageMapPath)) {
                            List<String> pathStrings = path.stream()
                                    .map(MethodExtractor::getFilteredMethodSignature)
                                    .collect(Collectors.toList());

                            String thirdPartyPackage = extractPackageName(getFilteredMethodSignature(thirdPartyMethod));

                            ThirdPartyPath tpPath = new ThirdPartyPath(
                                    getFilteredMethodSignature(entryPoint),
                                    getFilteredMethodSignature(thirdPartyMethod),
                                    thirdPartyPackage,
                                    pathStrings
                            );

                            thirdPartyPaths.add(tpPath);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to initialize call graph.", e);
        }

        return new AnalysisResult(thirdPartyPaths);
    }

    /**
     * Check if a path is direct - meaning only the target method is third-party,
     * all intermediate methods are from the project itself
     */
    private static boolean isDirectPath(List<MethodSignature> path, Path packageMapPath) {
        if (path.size() <= 1) return true;

        // Check all methods except the last one - they should NOT be third-party
        for (int i = 0; i < path.size() - 1; i++) {
            if (isThirdPartyMethod(path.get(i), packageMapPath)) {
                return false;
            }
        }

        // The last method should be third-party
        return isThirdPartyMethod(path.get(path.size() - 1), packageMapPath);
    }

    private static String extractPackageName(String method) {
        int methodDot = method.lastIndexOf('.');
        if (methodDot == -1) return null;
        String className = method.substring(0, methodDot);
        int classDot = className.lastIndexOf('.');
        if (classDot == -1) return null;
        return className.substring(0, classDot);
    }

    private static String getFilteredMethodSignature(MethodSignature method) {
        String className = filterName(method.getDeclClassType().getFullyQualifiedName());
        String methodName = filterName(method.getName());
        return className + "." + methodName;
    }

    private static String filterName(String name) {
        // Replace $ followed by digit (e.g., $Array1234) with nothing
        name = name.replaceAll("\\$\\d+", "");
        // Replace $ followed by letter (e.g. Java.ArrayInitializer) with a dot
        name = name.replaceAll("\\$(?=[A-Za-z])", ".");
        return name;
    }

    // Detect entry points - all public methods
    private static Set<MethodSignature> detectEntryPoints(JavaView view, String packageName) {
        return view.getClasses()
                .filter(c -> c.getType().getPackageName().getName().startsWith(packageName))
                .flatMap(c -> c.getMethods().stream())
                .filter(SootMethod::isPublic)
                .map(SootMethod::getSignature)
                .collect(Collectors.toSet());
    }

    private static boolean isThirdPartyMethod(MethodSignature method, Path packageMapPath) {
        String packageName = method.getDeclClassType().getPackageName().getName();
        // The ignored prefixes are either loaded from a txt file or are hardcoded (for basic jdk methods). The
        // package name is also added to the ignored prefixes.
        for (String ignore : ignoredPrefixes) {
            if (packageName.startsWith(ignore)) return false;
        }
        return PackageMatcher.containsPackage(packageName, packageMapPath);
    }

    private static Set<MethodSignature> getAllReachableMethods(CallGraph cg, MethodSignature start) {
        Set<MethodSignature> reachable = new HashSet<>();
        Deque<MethodSignature> queue = new ArrayDeque<>();
        queue.add(start);
        reachable.add(start);

        while (!queue.isEmpty()) {
            MethodSignature current = queue.poll();
            for (CallGraph.Call call : cg.callsFrom(current)) {
                MethodSignature target = call.getTargetMethodSignature();
                if (reachable.add(target)) {
                    queue.add(target);
                }
            }
        }
        return reachable;
    }

    private static List<List<MethodSignature>> findPaths(CallGraph cg, MethodSignature start, MethodSignature target) {
        List<List<MethodSignature>> results = new ArrayList<>();
        Deque<List<MethodSignature>> stack = new ArrayDeque<>();
        Set<MethodSignature> visited = new HashSet<>();
        stack.push(List.of(start));

        while (!stack.isEmpty()) {
            List<MethodSignature> path = stack.pop();
            MethodSignature last = path.get(path.size() - 1);

            if (last.equals(target)) {
                results.add(new ArrayList<>(path));
                continue;
            }

            if (visited.contains(last)) continue;
            visited.add(last);

            for (CallGraph.Call call : cg.callsFrom(last)) {
                MethodSignature next = call.getTargetMethodSignature();
                if (!visited.contains(next)) {
                    List<MethodSignature> newPath = new ArrayList<>(path);
                    newPath.add(next);
                    stack.push(newPath);
                }
            }
        }
        return results;
    }

    private static void writeThirdPartyPathsToJson(AnalysisResult result, String outputPath) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        try {
            File outputFile = new File(outputPath);
            mapper.writeValue(outputFile, result);
            log.info("Successfully wrote {} third-party paths to {}", result.thirdPartyPaths().size(),
                    outputFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to write third-party paths to JSON", e);
        }
    }

    private record AnalysisResult(
            List<ThirdPartyPath> thirdPartyPaths
    ) {
    }

    public record ThirdPartyPath(
            String entryPoint,
            String thirdPartyMethod,
            String thirdPartyPackage,
            List<String> path
    ) {
    }
}
