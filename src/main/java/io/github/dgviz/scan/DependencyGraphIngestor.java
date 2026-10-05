package io.github.dgviz.scan;

import tools.jackson.databind.JsonNode;
import io.github.dgviz.model.ArtifactCoordinate;
import io.github.dgviz.model.DependencyGraph;
import io.github.dgviz.model.DependencyNode;
import io.github.dgviz.model.DependencyOrigin;
import io.github.dgviz.model.DependencyScope;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds {@link DependencyGraph} from Gradle dependency tree JSON or CycloneDX SBOM.
 */
@Component
public class DependencyGraphIngestor {

    public DependencyGraph fromRequest(ScanRequest request) {
        String projectName = blankTo(request.projectName(), "project");
        DependencyGraph graph = new DependencyGraph(projectName);
        DependencyNode root = new DependencyNode(
                new ArtifactCoordinate(projectName, projectName, "0"),
                DependencyScope.COMPILE,
                DependencyOrigin.PROJECT,
                ""
        );
        graph.addRoot(root);

        boolean fromTree = false;
        if (request.dependencyTree() != null) {
            ingestTree(graph, root, request.dependencyTree(), true);
            fromTree = graph.size() > 1;
        }
        if (!fromTree && request.sbom() != null && !request.sbom().isNull()) {
            ingestCycloneDx(graph, root, request.sbom());
        }
        if (graph.size() <= 1) {
            throw new IllegalArgumentException(
                    "Пустое дерево зависимостей: передайте dependencyTree и/или CycloneDX sbom");
        }
        return graph;
    }

    private void ingestTree(
            DependencyGraph graph,
            DependencyNode parent,
            ScanRequest.DependencyTreeNode node,
            boolean isRootPayload
    ) {
        if (node == null) {
            return;
        }
        // Root payload may describe the project itself — only children matter if group/name empty
        boolean hasCoord = notBlank(node.group()) && notBlank(node.name());
        DependencyNode current = parent;
        if (hasCoord && !isRootPayload) {
            DependencyOrigin origin = Boolean.TRUE.equals(node.direct())
                    ? DependencyOrigin.DIRECT
                    : DependencyOrigin.TRANSITIVE;
            DependencyScope scope = mapScope(node.configuration());
            current = new DependencyNode(
                    new ArtifactCoordinate(node.group(), node.name(), blankTo(node.version(), "")),
                    scope,
                    origin,
                    blankTo(node.configuration(), "")
            );
            graph.addEdge(parent, current);
        } else if (hasCoord && isRootPayload) {
            // treat top-level listed deps under project root
            current = parent;
        }
        if (node.dependencies() != null) {
            for (ScanRequest.DependencyTreeNode child : node.dependencies()) {
                ingestTree(graph, current, child, false);
            }
        }
    }

    private void ingestCycloneDx(DependencyGraph graph, DependencyNode root, JsonNode sbom) {
        JsonNode components = sbom.path("components");
        if (!components.isArray()) {
            return;
        }
        Map<String, DependencyNode> byBomRef = new HashMap<>();
        for (JsonNode c : components) {
            String group = text(c, "group");
            String name = text(c, "name");
            String version = text(c, "version");
            if (name.isBlank()) {
                continue;
            }
            if (group.isBlank()) {
                String purl = text(c, "purl");
                // pkg:maven/group/name@version
                if (purl.startsWith("pkg:maven/")) {
                    String rest = purl.substring("pkg:maven/".length());
                    int at = rest.indexOf('@');
                    String coords = at > 0 ? rest.substring(0, at) : rest;
                    String[] parts = coords.split("/");
                    if (parts.length >= 2) {
                        group = parts[0];
                        name = parts[1];
                    }
                    if (at > 0 && version.isBlank()) {
                        version = rest.substring(at + 1);
                    }
                }
            }
            if (group.isBlank()) {
                continue;
            }
            DependencyNode node = new DependencyNode(
                    new ArtifactCoordinate(group, name, version),
                    DependencyScope.RUNTIME,
                    DependencyOrigin.DIRECT,
                    "sbom"
            );
            graph.addEdge(root, node);
            String bomRef = text(c, "bom-ref");
            if (!bomRef.isBlank()) {
                byBomRef.put(bomRef, node);
            }
        }
        JsonNode deps = sbom.path("dependencies");
        if (deps.isArray()) {
            for (JsonNode d : deps) {
                String ref = text(d, "ref");
                DependencyNode from = byBomRef.get(ref);
                if (from == null) {
                    continue;
                }
                JsonNode dependsOn = d.path("dependsOn");
                if (!dependsOn.isArray()) {
                    continue;
                }
                for (JsonNode depRef : dependsOn) {
                    DependencyNode to = byBomRef.get(depRef.asText(""));
                    if (to != null) {
                        graph.addEdge(from, to);
                    }
                }
            }
        }
    }

    private static DependencyScope mapScope(String configuration) {
        if (configuration == null) {
            return DependencyScope.UNKNOWN;
        }
        String c = configuration.toLowerCase();
        if (c.contains("test")) {
            return DependencyScope.TEST;
        }
        if (c.contains("runtime")) {
            return DependencyScope.RUNTIME;
        }
        if (c.contains("compile") || c.contains("implementation") || c.contains("api")) {
            return DependencyScope.COMPILE;
        }
        return DependencyScope.UNKNOWN;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? "" : v.asText("");
    }

    private static boolean notBlank(String v) {
        return v != null && !v.isBlank();
    }

    private static String blankTo(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v.trim();
    }
}
