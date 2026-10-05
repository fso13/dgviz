package io.github.dgviz.scan;

import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * Payload from the Gradle plugin (dependency tree and/or CycloneDX SBOM).
 */
public record ScanRequest(
        String projectName,
        String buildSystem,
        Boolean createIssues,
        DependencyTreeNode dependencyTree,
        JsonNode sbom
) {
    public record DependencyTreeNode(
            String group,
            String name,
            String version,
            String configuration,
            Boolean direct,
            List<DependencyTreeNode> dependencies
    ) {
    }
}
