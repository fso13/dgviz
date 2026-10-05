package io.github.dgviz.analysis;

import io.github.dgviz.model.DependencyGraph;
import io.github.dgviz.model.DependencyNode;
import io.github.dgviz.model.DependencyOrigin;
import io.github.dgviz.model.Issue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detects artifacts that appear both as direct and transitive (or repeated direct declarations).
 */
public final class DuplicateDetector implements IssueDetector {

    @Override
    public List<Issue> detect(DependencyGraph graph) {
        Map<String, Set<DependencyOrigin>> originsByGav = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();

        for (DependencyNode node : graph.nodes()) {
            if (node.origin() == DependencyOrigin.PROJECT) {
                continue;
            }
            String gav = node.coordinate().gavKey();
            originsByGav.computeIfAbsent(gav, k -> new HashSet<>()).add(node.origin());
            counts.merge(gav, 1, Integer::sum);
        }

        List<Issue> issues = new ArrayList<>();
        for (Map.Entry<String, Set<DependencyOrigin>> entry : originsByGav.entrySet()) {
            Set<DependencyOrigin> origins = entry.getValue();
            boolean directAndTransitive = origins.contains(DependencyOrigin.DIRECT)
                    && origins.contains(DependencyOrigin.TRANSITIVE);
            boolean repeated = counts.getOrDefault(entry.getKey(), 0) > 1
                    && origins.contains(DependencyOrigin.DIRECT);

            if (directAndTransitive || repeated) {
                issues.add(Issue.duplicate(
                        entry.getKey(),
                        "Keep a single declaration; exclude transitive copy or remove redundant direct dependency."
                ));
            }
        }
        return issues;
    }
}
