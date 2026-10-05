package io.github.dgviz.analysis;

import io.github.dgviz.model.ArtifactCoordinate;
import io.github.dgviz.model.DependencyGraph;
import io.github.dgviz.model.DependencyNode;
import io.github.dgviz.model.DependencyOrigin;
import io.github.dgviz.model.DependencyScope;
import io.github.dgviz.model.IssueType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DuplicateDetectorTest {

    @Test
    void shouldDetectDirectAndTransitiveDuplicate() {
        DependencyGraph graph = new DependencyGraph("demo");
        DependencyNode root = new DependencyNode(
                new ArtifactCoordinate("demo", "app", "1"),
                DependencyScope.COMPILE,
                DependencyOrigin.PROJECT,
                ""
        );
        graph.addRoot(root);
        DependencyNode direct = new DependencyNode(
                new ArtifactCoordinate("org.example", "lib", "1.0"),
                DependencyScope.COMPILE,
                DependencyOrigin.DIRECT,
                ""
        );
        DependencyNode transitive = new DependencyNode(
                new ArtifactCoordinate("org.example", "lib", "1.0"),
                DependencyScope.COMPILE,
                DependencyOrigin.TRANSITIVE,
                "module-a"
        );
        graph.addEdge(root, direct);
        graph.addEdge(root, transitive);

        assertThat(new DuplicateDetector().detect(graph))
                .anyMatch(i -> i.type() == IssueType.DUPLICATE);
    }
}
