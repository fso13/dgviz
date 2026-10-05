package io.github.dgviz.analysis;

import io.github.dgviz.model.ArtifactCoordinate;
import io.github.dgviz.model.DependencyGraph;
import io.github.dgviz.model.DependencyNode;
import io.github.dgviz.model.DependencyOrigin;
import io.github.dgviz.model.DependencyScope;
import io.github.dgviz.model.Issue;
import io.github.dgviz.model.IssueType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConflictDetectorTest {

    @Test
    @DisplayName("Should detect version conflict when same GA has multiple versions")
    void shouldDetectVersionConflict() {
        DependencyGraph graph = new DependencyGraph("demo");
        DependencyNode root = node("demo", "app", "1.0", DependencyOrigin.PROJECT);
        graph.addRoot(root);
        graph.addEdge(root, node("org.example", "lib", "1.0.0", DependencyOrigin.DIRECT));
        graph.addEdge(root, node("org.example", "lib", "2.0.0", DependencyOrigin.TRANSITIVE));

        List<Issue> issues = new ConflictDetector().detect(graph);

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).type()).isEqualTo(IssueType.VERSION_CONFLICT);
        assertThat(issues.get(0).title()).contains("org.example:lib");
    }

    @Test
    @DisplayName("Should not flag single-version artifacts")
    void shouldNotFlagSingleVersion() {
        DependencyGraph graph = new DependencyGraph("demo");
        DependencyNode root = node("demo", "app", "1.0", DependencyOrigin.PROJECT);
        graph.addRoot(root);
        graph.addEdge(root, node("org.example", "lib", "1.0.0", DependencyOrigin.DIRECT));

        assertThat(new ConflictDetector().detect(graph)).isEmpty();
    }

    private static DependencyNode node(String g, String a, String v, DependencyOrigin origin) {
        return new DependencyNode(new ArtifactCoordinate(g, a, v), DependencyScope.COMPILE, origin, "");
    }
}
