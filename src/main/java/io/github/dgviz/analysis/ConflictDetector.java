package io.github.dgviz.analysis;

import io.github.dgviz.model.ArtifactCoordinate;
import io.github.dgviz.model.DependencyGraph;
import io.github.dgviz.model.DependencyNode;
import io.github.dgviz.model.DependencyOrigin;
import io.github.dgviz.model.Issue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Finds the same GA with different versions across the graph.
 */
public final class ConflictDetector implements IssueDetector {

    @Override
    public List<Issue> detect(DependencyGraph graph) {
        Map<String, Set<String>> versionsByGa = new LinkedHashMap<>();
        for (DependencyNode node : graph.nodes()) {
            if (node.origin() == DependencyOrigin.PROJECT) {
                continue;
            }
            ArtifactCoordinate c = node.coordinate();
            if (c.version().isBlank()) {
                continue;
            }
            versionsByGa.computeIfAbsent(c.gaKey(), k -> new LinkedHashSet<>()).add(c.version());
        }

        List<Issue> issues = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : versionsByGa.entrySet()) {
            if (entry.getValue().size() > 1) {
                List<String> versions = entry.getValue().stream().sorted().collect(Collectors.toList());
                String recommendation = "Align versions of " + entry.getKey()
                        + " via dependencyManagement / BOM / version catalog. Preferred single version: "
                        + versions.get(versions.size() - 1);
                issues.add(Issue.conflict(entry.getKey(), versions, recommendation));
            }
        }
        return issues;
    }
}
