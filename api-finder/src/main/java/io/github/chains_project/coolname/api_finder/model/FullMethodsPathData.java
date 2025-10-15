package io.github.chains_project.coolname.api_finder.model;

import java.util.List;

/**
 * Format 1: Complete path with all method bodies
 * This format includes the full implementation of every method in the path,
 * useful for understanding the complete code flow.
 */
public record FullMethodsPathData(
        String entryPoint,
        String thirdPartyMethod,
        String thirdPartyPackage,
        List<String> path,
        List<String> fullMethods
) {
}