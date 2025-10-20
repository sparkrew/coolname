package io.github.chains_project.coolname.api_finder;

import io.github.chains_project.coolname.api_finder.model.AnalysisResult;
import io.github.chains_project.coolname.api_finder.model.ThirdPartyPath;
import io.github.chains_project.coolname.api_finder.utils.PackageMatcher;
import io.github.chains_project.coolname.api_finder.utils.PathWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sootup.callgraph.CallGraph;
import sootup.callgraph.RapidTypeAnalysisAlgorithm;
import sootup.core.inputlocation.AnalysisInputLocation;
import sootup.core.model.SootMethod;
import sootup.core.signatures.MethodSignature;
import sootup.java.bytecode.frontend.inputlocation.JavaClassPathAnalysisInputLocation;
import sootup.java.core.views.JavaView;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class MethodExtractor {

    static final Logger log = LoggerFactory.getLogger(MethodExtractor.class);
    static Set<String> ignoredPrefixes;

    /**
     * This method processes the JAR file to extract third party API calls and their paths.
     * It initializes the call graph, and finds paths that involve third-party method calls.
     *
     * @param pathToJar      Path to the JAR file to analyze.
     * @param reportPath     Path where the analysis report will be written.
     * @param packageName    The package name of the project under consideration to filter the events.
     * @param packageMapPath Path to the package map file that contains the mapping of package names to Maven coordinates.
     * @param sourceRootPath Path to the project source code root directory (optional, can be null). If provided, actual source code will be extracted instead of Jimple IR.
     */
    public static void process(String pathToJar, String reportPath, String packageName, Path packageMapPath, String sourceRootPath) {
        // Start reading the jar with sootup. Here we use all the public methods as the entry points. That means we
        // don't plan to do anything (generate tests etc) for private methods.
        // Don't want any more complications with reflections and all. What we are doing is complicated enough.
        ignoredPrefixes = PackageMatcher.loadIgnoredPrefixes(packageName);
        JavaView view = createJavaView(pathToJar);
        Set<MethodSignature> entryPoints = detectEntryPoints(view, packageName);
        log.info("Found " + entryPoints.size() + " public methods as entry points.");

        AnalysisResult result = analyzeReachability(view, entryPoints, packageMapPath);

        // Write the three different output files
        PathWriter.writeAllFormats(result, reportPath, view, sourceRootPath);
        log.info("All analysis reports written successfully.");
    }

    /**
     * Overloaded version without source root path for backward compatibility
     */
    public static void process(String pathToJar, String reportPath, String packageName, Path packageMapPath) {
        process(pathToJar, reportPath, packageName, packageMapPath, null);
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

            // Identify all third-party methods that are actually called in the codebase. We go backwards from
            // third-party methods to public methods to find all paths. This is because we expect this would be more
            // efficient than doing it the other way round, as there are usually much fewer third-party methods than
            // public methods.
            Set<MethodSignature> allThirdPartyMethods = findAllThirdPartyMethods(cg, packageMapPath);
            log.info("Found {} third-party methods in call graph", allThirdPartyMethods.size());

            // Build reverse call graph for efficient backward traversal. Otherwise, it takes painfully long time to
            // run with the forward graph (from public methods to third party methods).
            Map<MethodSignature, Set<MethodSignature>> reverseCallGraph = buildReverseCallGraph(cg);

            // For each third-party method, find all public methods that can reach it
            for (MethodSignature thirdPartyMethod : allThirdPartyMethods) {
                // Find all methods that can reach this third-party method by traversing backwards
                Set<MethodSignature> reachingMethods = findReachingMethods(
                        reverseCallGraph,
                        thirdPartyMethod,
                        entryPoints,
                        packageMapPath
                );

                // For each public method that can reach this third-party method,
                // find the shortest direct path and create a ThirdPartyPath entry.
                for (MethodSignature publicMethod : reachingMethods) {
                    if (entryPoints.contains(publicMethod)) {
                        // Here, we look for the shortest path from the public method to the third-party method.
                        // We do that because otherwise the number of paths tend to explode.
                        // Now, we have one path per source (public method) & target (third-party method) pair.
                        // This is good for our test generation goal because we generate tests for the public method
                        // in order to reach the third-party method. It is important to note that, we still collect
                        // multiple paths to reach a third party method, as long as they originate from different public
                        // methods.
                        List<MethodSignature> path = findShortestDirectPath(
                                cg,
                                publicMethod,
                                thirdPartyMethod,
                                packageMapPath
                        );

                        if (path != null && !path.isEmpty()) {
                            ThirdPartyPath tpPath = new ThirdPartyPath(
                                    publicMethod,
                                    thirdPartyMethod,
                                    path
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
        // Check all methods except the last one - they should not be third-party
        for (int i = 0; i < path.size() - 1; i++) {
            if (isThirdPartyMethod(path.get(i), packageMapPath)) {
                return false;
            }
        }
        // The last method should be third-party
        return isThirdPartyMethod(path.get(path.size() - 1), packageMapPath);
    }

    /**
     * Find all third-party methods that are actually called in the call graph
     */
    private static Set<MethodSignature> findAllThirdPartyMethods(CallGraph cg, Path packageMapPath) {
        Set<MethodSignature> thirdPartyMethods = new HashSet<>();
        // Iterate through all calls in the call graph
        for (MethodSignature method : cg.getMethodSignatures()) {
            for (CallGraph.Call call : cg.callsFrom(method)) {
                MethodSignature target = call.getTargetMethodSignature();
                if (isThirdPartyMethod(target, packageMapPath)) {
                    thirdPartyMethods.add(target);
                }
            }
        }
        return thirdPartyMethods;
    }

    /**
     * Build a reverse call graph: maps each method to all methods that call it
     */
    private static Map<MethodSignature, Set<MethodSignature>> buildReverseCallGraph(CallGraph cg) {
        Map<MethodSignature, Set<MethodSignature>> reverseGraph = new HashMap<>();
        // We could have used the callsTo method here. Just don't wanna touch it when it works.
        for (MethodSignature caller : cg.getMethodSignatures()) {
            for (CallGraph.Call call : cg.callsFrom(caller)) {
                MethodSignature callee = call.getTargetMethodSignature();
                reverseGraph.computeIfAbsent(callee, k -> new HashSet<>()).add(caller);
            }
        }
        return reverseGraph;
    }

    /**
     * Find all methods (especially public ones) that can reach the target method
     * by traversing backwards through the call graph
     */
    private static Set<MethodSignature> findReachingMethods(
            Map<MethodSignature, Set<MethodSignature>> reverseCallGraph,
            MethodSignature target,
            Set<MethodSignature> entryPoints,
            Path packageMapPath) {

        Set<MethodSignature> reachingPublicMethods = new HashSet<>();
        Set<MethodSignature> visited = new HashSet<>();
        Deque<MethodSignature> queue = new ArrayDeque<>();

        queue.add(target);
        visited.add(target);
        while (!queue.isEmpty()) {
            MethodSignature current = queue.poll();

            // Get all methods that call the current method
            Set<MethodSignature> callers = reverseCallGraph.getOrDefault(current, Collections.emptySet());

            for (MethodSignature caller : callers) {
                // Skip if it's a third-party method (we only want project methods in the path)
                if (isThirdPartyMethod(caller, packageMapPath)) {
                    continue;
                }
                // If this is a public method (entry point), add it to results
                if (entryPoints.contains(caller)) {
                    reachingPublicMethods.add(caller);
                }
                // Continue traversing backwards if not visited
                if (visited.add(caller)) {
                    queue.add(caller);
                }
            }
        }
        return reachingPublicMethods;
    }

    /**
     * Find the shortest direct path from start to target where only the target is third-party.
     * Uses BFS to find the shortest path.
     */
    private static List<MethodSignature> findShortestDirectPath(
            CallGraph cg,
            MethodSignature start,
            MethodSignature target,
            Path packageMapPath) {

        Deque<List<MethodSignature>> queue = new ArrayDeque<>();
        Set<MethodSignature> visited = new HashSet<>();
        queue.add(List.of(start));
        visited.add(start);

        while (!queue.isEmpty()) {
            List<MethodSignature> path = queue.poll();
            MethodSignature last = path.get(path.size() - 1);

            for (CallGraph.Call call : cg.callsFrom(last)) {
                MethodSignature next = call.getTargetMethodSignature();

                if (next.equals(target)) {
                    // Found the target - construct and return the complete path
                    List<MethodSignature> completePath = new ArrayList<>(path);
                    completePath.add(next);

                    // Verify this is a direct path (only target is third-party).
                    // Here, we do not consider the paths that have third party methods in between.
                    if (isDirectPath(completePath, packageMapPath)) {
                        return completePath;
                    }
                }

                // Only continue if this is not a third-party method and not visited
                if (!isThirdPartyMethod(next, packageMapPath) && visited.add(next)) {
                    List<MethodSignature> newPath = new ArrayList<>(path);
                    newPath.add(next);
                    queue.add(newPath);
                }
            }
        }
        return null;
    }

    public static String getFilteredMethodSignature(MethodSignature method) {
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
}
