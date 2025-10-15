package io.github.chains_project.coolname.api_finder;

import io.github.chains_project.coolname.api_finder.utils.PackageMatcher;
import io.github.chains_project.coolname.api_finder.model.*;
import io.github.chains_project.coolname.api_finder.utils.PathWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sootup.callgraph.CallGraph;
import sootup.callgraph.RapidTypeAnalysisAlgorithm;
import sootup.core.inputlocation.AnalysisInputLocation;
import sootup.core.jimple.basic.Value;
import sootup.core.jimple.common.expr.AbstractInvokeExpr;
import sootup.core.jimple.common.stmt.InvokableStmt;
import sootup.core.jimple.common.stmt.JAssignStmt;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.model.Body;
import sootup.core.model.SootMethod;
import sootup.core.signatures.MethodSignature;
import sootup.java.bytecode.frontend.inputlocation.JavaClassPathAnalysisInputLocation;
import sootup.java.core.JavaSootMethod;
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
     */
    public static void process(String pathToJar, String reportPath, String packageName, Path packageMapPath) {
        // Start reading the jar with sootup. Here we use all the public methods as the entry points. That means we
        // don't plan to do anything (generate tests etc) for private methods.
        ignoredPrefixes = PackageMatcher.loadIgnoredPrefixes(packageName);
        JavaView view = createJavaView(pathToJar);
        Set<MethodSignature> entryPoints = detectEntryPoints(view, packageName);
        log.info("Found " + entryPoints.size() + " public methods as entry points.");

        AnalysisResult result = analyzeReachability(view, entryPoints, packageMapPath);

        // Write the three different output files
        PathWriter.writeAllFormats(result, reportPath, view);
        log.info("All analysis reports written successfully.");
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

            // Build reverse call graph for efficient backward traversal. Otherwise, it takes too long to run with the
            // forward graph (from public methods to third party methods).
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
        // Check all methods except the last one - they should NOT be third-party
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
        // ToDo: We could have used the callsTo method here.
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

    private static String extractPackageName(String method) {
        int methodDot = method.lastIndexOf('.');
        if (methodDot == -1) return null;
        String className = method.substring(0, methodDot);
        int classDot = className.lastIndexOf('.');
        if (classDot == -1) return null;
        return className.substring(0, classDot);
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

    /**
     * Performs backward slicing on methods to extract only the relevant statements
     * needed to reach a specific target (usually a third-party method call).
     *
     * This uses backward data-flow and control-flow analysis to identify which
     * statements in a method actually contribute to the target call.
     */
    public static class MethodSlicer {

        private static final Logger log = LoggerFactory.getLogger(MethodSlicer.class);

        /**
         * Extract method slices for all methods in a path.
         * For each method, we perform backward slicing from the call to the next method
         * in the path (or the third-party method if it's the last one).
         */
        public static List<String> extractMethodSlices(JavaView view, List<MethodSignature> path) {
            List<String> slices = new ArrayList<>();

            for (int i = 0; i < path.size(); i++) {
                MethodSignature currentMethod = path.get(i);

                // For the last method, we're interested in the call to the third-party method
                // For other methods, we're interested in the call to the next method in the path
                MethodSignature targetCall = (i < path.size() - 1) ? path.get(i + 1) : path.get(i);

                String slice = performBackwardSlice(view, currentMethod, targetCall);
                slices.add(slice);
            }

            return slices;
        }

        /**
         * Perform backward slicing on a method to extract statements relevant to reaching
         * the target method call. This identifies variables and statements that influence
         * the target call through data and control dependencies.
         */
        private static String performBackwardSlice(JavaView view, MethodSignature methodSig,
                                                   MethodSignature targetCall) {
            try {
                Optional<JavaSootMethod> methodOpt = view.getMethod(methodSig);

                if (methodOpt.isEmpty() || !methodOpt.get().hasBody()) {
                    return "// Slicing not available for: " +
                            getFilteredMethodSignature(methodSig);
                }

                SootMethod method = methodOpt.get();
                Body body = method.getBody();
                List<Stmt> stmts = body.getStmts();

                // Step 1: Find the statement that invokes the target method
                Stmt targetStmt = findTargetInvocation(stmts, targetCall);
                if (targetStmt == null) {
                    // If we can't find a specific target, just return a simplified version
                    return extractSimplifiedBody(body);
                }

                // Step 2: Perform backward slicing from the target statement
                Set<Stmt> relevantStmts = computeBackwardSlice(body, targetStmt);

                // Step 3: Format the slice as readable code
                return formatSlice(methodSig, relevantStmts, stmts);

            } catch (Exception e) {
                log.warn("Failed to slice method {}: {}", methodSig, e.getMessage());
                return "// Error during slicing: " + e.getMessage();
            }
        }

        /**
         * Find the statement in the method that invokes the target method.
         * This is our slicing criterion - the point from which we work backwards.
         */
        private static Stmt findTargetInvocation(List<Stmt> stmts, MethodSignature targetCall) {
            for (Stmt stmt : stmts) {
                // Check if this is a statement that can contain an invoke expression
                if (stmt instanceof InvokableStmt) {
                    InvokableStmt invokableStmt = (InvokableStmt) stmt;
                    // Check if this statement invokes the target method
                    if (invokableStmt.getInvokeExpr().isPresent() &&
                            invokableStmt.getInvokeExpr().get().getMethodSignature().equals(targetCall)) {
                        return stmt;
                    }
                } else if (stmt instanceof JAssignStmt) {
                    // Assignment statements can also contain invoke expressions on the right side
                    JAssignStmt assignStmt = (JAssignStmt) stmt;
                    Value rightOp = assignStmt.getRightOp();
                    if (rightOp instanceof AbstractInvokeExpr) {
                        AbstractInvokeExpr invokeExpr = (AbstractInvokeExpr) rightOp;
                        if (invokeExpr.getMethodSignature().equals(targetCall)) {
                            return stmt;
                        }
                    }
                }
            }
            return null;
        }

        /**
         * Compute the backward slice from a target statement.
         * This includes all statements that the target depends on through:
         * - Data dependencies (variables used in the target)
         * - Control dependencies (conditions that must be true to reach the target)
         */
        private static Set<Stmt> computeBackwardSlice(Body body, Stmt targetStmt) {
            Set<Stmt> slice = new LinkedHashSet<>();
            Set<Value> relevantValues = new HashSet<>();
            Queue<Stmt> worklist = new LinkedList<>();

            // Start with the target statement
            worklist.add(targetStmt);
            slice.add(targetStmt);

            // Add all values used in the target statement
            targetStmt.getUses().forEach(relevantValues::add);

            // Work backwards through the method
            List<Stmt> stmts = body.getStmts();

            while (!worklist.isEmpty()) {
                Stmt current = worklist.poll();
                int currentIndex = stmts.indexOf(current);

                // Look at all statements before the current one
                for (int i = currentIndex - 1; i >= 0; i--) {
                    Stmt predecessor = stmts.get(i);

                    // Check if this statement defines any value we care about
                    boolean isRelevant = false;

                    // Check data dependencies - does this statement define a variable we use?
                    if (predecessor instanceof JAssignStmt) {
                        JAssignStmt assign = (JAssignStmt) predecessor;
                        Value leftOp = assign.getLeftOp();

                        // If this defines a value we need, add it to the slice
                        if (relevantValues.contains(leftOp)) {
                            isRelevant = true;
                            // Now we also care about the values used in the right-hand side
                            assign.getRightOp().getUses().forEach(relevantValues::add);
                        }
                    }

                    // Check control dependencies - conditions that control whether we reach the target
                    // For simplicity, we include all conditional statements (if, switch, etc.)
                    if (predecessor.branches()) {
                        isRelevant = true;
                        // Add the values used in the condition
                        predecessor.getUses().forEach(relevantValues::add);
                    }

                    // If this statement is relevant and not already in slice, add it
                    if (isRelevant && slice.add(predecessor)) {
                        worklist.add(predecessor);
                    }
                }
            }

            return slice;
        }

        /**
         * Format the slice into readable code.
         * Presents the relevant statements in their original order.
         */
        private static String formatSlice(MethodSignature methodSig, Set<Stmt> slice, List<Stmt> allStmts) {
            StringBuilder sb = new StringBuilder();

            sb.append("// Slice for: ").append(getFilteredMethodSignature(methodSig)).append("\n");
            sb.append("// Contains ").append(slice.size()).append(" relevant statements\n\n");

            // Keep statements in their original order
            List<Stmt> orderedSlice = allStmts.stream()
                    .filter(slice::contains)
                    .collect(Collectors.toList());

            for (Stmt stmt : orderedSlice) {
                sb.append(stmt.toString()).append("\n");
            }

            return sb.toString();
        }

        /**
         * Extract a simplified version of the method body.
         * Used as a fallback when we can't perform proper slicing.
         * This just removes some noise but keeps most of the method.
         */
        private static String extractSimplifiedBody(Body body) {
            StringBuilder sb = new StringBuilder();
            sb.append("// Simplified body (full slicing not available)\n\n");

            // Just return the body with some basic filtering
            List<Stmt> stmts = body.getStmts();
            for (Stmt stmt : stmts) {
                // Skip some very basic statements that are just noise
                String stmtStr = stmt.toString();
                if (!stmtStr.contains("nop") && !stmtStr.trim().isEmpty()) {
                    sb.append(stmtStr).append("\n");
                }
            }

            return sb.toString();
        }
    }
}
