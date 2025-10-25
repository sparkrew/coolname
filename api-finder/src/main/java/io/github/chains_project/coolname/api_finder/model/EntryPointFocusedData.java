package io.github.chains_project.coolname.api_finder.model;

import java.util.List;

/**
 * Format 2: Entry point focused data
 * This format only includes the body of the entry point method,
 * useful when you mainly care about the public API method.
 */
public record EntryPointFocusedData(
        String entryPoint,
        String entryPointBody,
        String thirdPartyMethod,
        String thirdPartyPackage,
        List<String> path
) {
}
