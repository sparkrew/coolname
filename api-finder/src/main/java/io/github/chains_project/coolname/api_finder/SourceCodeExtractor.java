package io.github.chains_project.coolname.api_finder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sootup.core.signatures.MethodSignature;
import spoon.MavenLauncher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Extracts actual source code from Java files using Spoon.
 * This reads the project's .java files to get the real source code
 * instead of the Jimple IR representation.
 * <p>
 * Uses caching at two levels:
 * Model cache: The parsed Spoon model for the source root
 * Method cache: Individual method bodies that have been extracted
 */
public class SourceCodeExtractor {

    private static final Logger log = LoggerFactory.getLogger(SourceCodeExtractor.class);
    // Method cache: maps method signature string to extracted source code
    private static final Map<String, String> methodCache = new HashMap<>();
    // Type cache: maps class name to CtType for faster lookups
    private static final Map<String, CtType<?>> typeCache = new HashMap<>();
    // Model cache
    private static CtModel model;
    protected static String currentSourceRoot;

    /**
     * Initialize or retrieve the Spoon model for the given source root.
     * This is cached to avoid re-parsing the entire source tree multiple times.
     */
    private static CtModel getOrCreateModel(String sourceRootPath) {
        // Cache the model if we are using the same source root
        if (model != null && sourceRootPath.equals(currentSourceRoot)) {
            return model;
        }
        log.info("Building Spoon model from source root: {}", sourceRootPath);
        try {
            MavenLauncher launcher = new MavenLauncher(sourceRootPath,
                    MavenLauncher.SOURCE_TYPE.APP_SOURCE);
            // Configure Spoon to be more lenient
            launcher.getEnvironment().setNoClasspath(true);
            launcher.getEnvironment().setCommentEnabled(true);
            launcher.getEnvironment().disableConsistencyChecks();
            model = launcher.buildModel();
            currentSourceRoot = sourceRootPath;
            // Clear caches when we build a new model
            methodCache.clear();
            typeCache.clear();
            log.info("Spoon model built successfully with {} types", model.getAllTypes().size());
            return model;
        } catch (Exception e) {
            log.error("Error building Spoon model: {}", e.getMessage(), e);
            model = null;
            currentSourceRoot = null;
            throw new RuntimeException("Failed to build Spoon model", e);
        }
    }

    /**
     * Get the current source root path.
     */
    public static String getCurrentSourceRoot() {
        return currentSourceRoot;
    }

    /**
     * Extract a method's source code from the actual Java source file using Spoon.
     *
     * @param methodSig      The method signature to extract
     * @param sourceRootPath The root directory of the source code
     * @return The source code of the method, or null if not found
     */
    public static String extractMethodFromSource(MethodSignature methodSig, String sourceRootPath) {
        // Create a cache key from the method signature
        String cacheKey = methodSig.toString();
        // Check if we already extracted this method
        if (methodCache.containsKey(cacheKey)) {
            log.trace("Method cache hit for {}", cacheKey);
            return methodCache.get(cacheKey);
        }
        try {
            CtModel spoonModel = getOrCreateModel(sourceRootPath);
            // Get the fully qualified class name
            String className = methodSig.getDeclClassType().getFullyQualifiedName();
            String methodName = methodSig.getName();
            // Handle inner classes - Spoon uses $ for inner classes
            CtType<?> ctType = findTypeCached(spoonModel, className);
            if (ctType == null) {
                log.debug("Type not found in Spoon model: {}", className);
                // Cache the null result to avoid repeated lookups
                methodCache.put(cacheKey, null);
                return null;
            }
            // Handle special method names from bytecode
            String sourceCode = null;
            if ("<init>".equals(methodName)) {
                // <init> represents a constructor
                sourceCode = extractConstructor(ctType, methodSig);
            } else if ("<clinit>".equals(methodName)) {
                // <clinit> represents a static initializer block
                sourceCode = extractStaticInitializer(ctType);
            } else {
                // Regular method - pass methodSig for overload resolution
                sourceCode = extractRegularMethod(ctType, methodName, methodSig);
            }
            if (sourceCode == null) {
                log.debug("Method {} not found in type {}", methodName, className);
            }
            // Cache the result (even if null)
            methodCache.put(cacheKey, sourceCode);
            if (sourceCode != null) {
                log.trace("Cached method source for {}", cacheKey);
            }
            return sourceCode;
        } catch (Exception e) {
            log.warn("Error extracting source code for {}: {}", methodSig, e.getMessage());
            // Cache the null result to avoid repeated errors
            methodCache.put(cacheKey, null);
            return null;
        }
    }

    /**
     * Extract a regular method by name and parameter types.
     * Handles method overloading by matching the full signature.
     */
    private static String extractRegularMethod(CtType<?> ctType, String methodName, MethodSignature methodSig) {
        // Get all methods with the matching name
        var candidateMethods = ctType.getMethods().stream()
                .filter(m -> m.getSimpleName().equals(methodName))
                .toList();
        if (candidateMethods.isEmpty()) {
            return null;
        }
        // If there's only one method with this name, we are lucky, just return it
        if (candidateMethods.size() == 1) {
            return candidateMethods.get(0).prettyprint();
        }
        // Multiple methods with same name - not so lucky this time, need to match by parameter types,
        // why? bcoz of overloading
        int paramCount = methodSig.getParameterTypes().size();
        var paramTypes = methodSig.getParameterTypes();
        // Try to find exact match by parameter count and types
        Optional<CtMethod<?>> exactMatch = candidateMethods.stream()
                .filter(m -> m.getParameters().size() == paramCount)
                .filter(m -> parametersMatch(m, paramTypes))
                .findFirst();
        if (exactMatch.isPresent()) {
            return exactMatch.get().prettyprint();
        }
        // We are unlucky, Fall back to matching by parameter count only.
        Optional<CtMethod<?>> countMatch = candidateMethods.stream()
                .filter(m -> m.getParameters().size() == paramCount)
                .findFirst();
        if (countMatch.isPresent()) {
            log.debug("Multiple overloaded methods found for {}, matched by parameter count", methodName);
            return countMatch.get().prettyprint();
        }
        // We are really unlucky! After all this effort, we just have to return the first method.
        log.debug("Multiple overloaded methods found for {}, returning first one", methodName);
        return candidateMethods.get(0).prettyprint();
    }

    /**
     * Check if a Spoon method's parameters match the SootUp method signature's parameter types.
     * Compares type names (simple or qualified) to handle overloading.
     */
    private static boolean parametersMatch(CtMethod<?> spoonMethod,
                                           java.util.List<sootup.core.types.Type> sootParams) {
        var spoonParams = spoonMethod.getParameters();
        if (spoonParams.size() != sootParams.size()) {
            return false;
        }
        // Compare each parameter type
        for (int i = 0; i < spoonParams.size(); i++) {
            var spoonParam = spoonParams.get(i);
            var sootParam = sootParams.get(i);
            String spoonTypeName = spoonParam.getType().getQualifiedName();
            String sootTypeName = sootParam.toString();
            // Try to match by simple name or qualified name
            if (!typesMatch(spoonTypeName, sootTypeName)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if two type names match, handling both simple and qualified names.
     * For example: "String" matches "java.lang.String", "int" matches "int"
     */
    private static boolean typesMatch(String spoonTypeName, String sootTypeName) {
        // Direct match
        if (spoonTypeName.equals(sootTypeName)) {
            return true;
        }
        // Try matching simple names (last part after dot)
        String spoonSimple = spoonTypeName.contains(".") ?
                spoonTypeName.substring(spoonTypeName.lastIndexOf(".") + 1) :
                spoonTypeName;
        String sootSimple = sootTypeName.contains(".") ?
                sootTypeName.substring(sootTypeName.lastIndexOf(".") + 1) :
                sootTypeName;
        if (spoonSimple.equals(sootSimple)) {
            return true;
        }
        // Handle array types
        if (spoonTypeName.endsWith("[]") && sootTypeName.endsWith("[]")) {
            String spoonBase = spoonTypeName.substring(0, spoonTypeName.length() - 2);
            String sootBase = sootTypeName.substring(0, sootTypeName.length() - 2);
            return typesMatch(spoonBase, sootBase);
        }
        return false;
    }

    /**
     * Extract a constructor from the type.
     * If there are multiple constructors, tries to match by parameter count.
     * Otherwise returns the first constructor.
     */
    private static String extractConstructor(CtType<?> ctType, MethodSignature methodSig) {
        // Get all constructors
        var constructors = ctType.getElements(
                element -> element instanceof spoon.reflect.declaration.CtConstructor
        );
        if (constructors.isEmpty()) {
            return null;
        }
        // If there's only one constructor, return it
        if (constructors.size() == 1) {
            return constructors.get(0).prettyprint();
        }
        // Try to match by parameter count
        int paramCount = methodSig.getParameterTypes().size();
        Optional<? extends CtConstructor<?>> matchingConstructor =
                constructors.stream()
                        .map(c -> (CtConstructor<?>) c)
                        .filter(c -> c.getParameters().size() == paramCount)
                        .findFirst();
        if (matchingConstructor.isPresent()) {
            // Get the pretty-printed source code for the matching constructor, toString does not give proper source
            return matchingConstructor.get().prettyprint();
        }
        // Screw it, we give up, fall back to first constructor
        log.debug("Multiple constructors found, returning first one for {}", ctType.getQualifiedName());
        return constructors.get(0).prettyprint();
    }

    /**
     * Extract static initializer block(s) from the type.
     * Static initializers are represented as <clinit> in bytecode.
     */
    private static String extractStaticInitializer(CtType<?> ctType) {
        // Get all anonymous executable blocks (static initializers)
        var staticBlocks = ctType.getElements(
                element -> element instanceof spoon.reflect.code.CtBlock &&
                        element.getParent() instanceof CtType &&
                        !element.isImplicit()
        );
        if (staticBlocks.isEmpty()) {
            // No explicit static initializer found
            log.debug("No static initializer found for {}", ctType.getQualifiedName());
            return "";
        }
        // Combine all static blocks
        StringBuilder sb = new StringBuilder();
        sb.append(ctType.getQualifiedName()).append("\n");
        for (var block : staticBlocks) {
            sb.append(block.prettyprint()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Find a type with caching to speed up repeated lookups.
     */
    private static CtType<?> findTypeCached(CtModel spoonModel, String fullyQualifiedName) {
        // Check cache first
        if (typeCache.containsKey(fullyQualifiedName)) {
            return typeCache.get(fullyQualifiedName);
        }
        // Not in cache, do the lookup
        CtType<?> type = findType(spoonModel, fullyQualifiedName);
        // Cache the result (even if null)
        typeCache.put(fullyQualifiedName, type);
        return type;
    }

    /**
     * Find a type in the Spoon model by its fully qualified name.
     * Handles both regular classes and inner classes.
     */
    private static CtType<?> findType(CtModel spoonModel, String fullyQualifiedName) {
        // Try direct lookup first
        CtType<?> type = spoonModel.getAllTypes().stream()
                .filter(t -> t.getQualifiedName().equals(fullyQualifiedName))
                .findFirst()
                .orElse(null);
        if (type != null) {
            return type;
        }
        // Handle inner classes - replace $ with . for Spoon's format
        String spoonName = fullyQualifiedName.replace('$', '.');
        type = spoonModel.getAllTypes().stream()
                .filter(t -> t.getQualifiedName().equals(spoonName))
                .findFirst()
                .orElse(null);
        if (type != null) {
            return type;
        }
        // Try looking for the outer class and then finding the inner class, yeah, never gonna give you up.
        if (fullyQualifiedName.contains("$")) {
            String outerClassName = fullyQualifiedName.substring(0, fullyQualifiedName.indexOf("$"));
            CtType<?> outerType = findTypeCached(spoonModel, outerClassName);
            if (outerType != null) {
                String innerClassName = fullyQualifiedName.substring(fullyQualifiedName.lastIndexOf("$") + 1);
                return outerType.getNestedType(innerClassName);
            }
        }
        return null;
    }

    /**
     * Get the Spoon model for use in other classes (like MethodSlicer).
     * This allows sharing the same parsed model across different operations.
     */
    public static CtModel getModel(String sourceRootPath) {
        return getOrCreateModel(sourceRootPath);
    }

    /**
     * Clear the cached model and method cache.
     * Useful for testing or when processing multiple projects.
     */
    public static void clearCache() {
        model = null;
        currentSourceRoot = null;
        methodCache.clear();
        typeCache.clear();
        log.debug("Cleared all caches");
    }

    /**
     * Get cache statistics for monitoring/debugging.
     */
    public static String getCacheStats() {
        return String.format("Method cache: %d entries, Type cache: %d entries",
                methodCache.size(), typeCache.size());
    }
}
