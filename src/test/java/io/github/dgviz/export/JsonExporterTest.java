package io.github.dgviz.export;

import io.github.dgviz.model.AnalysisResult;
import io.github.dgviz.model.ArtifactCoordinate;
import io.github.dgviz.model.DependencyGraph;
import io.github.dgviz.model.DependencyNode;
import io.github.dgviz.model.DependencyOrigin;
import io.github.dgviz.model.DependencyScope;
import io.github.dgviz.model.Issue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonExporterTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldWriteJsonReport() throws Exception {
        DependencyGraph graph = new DependencyGraph("demo");
        DependencyNode root = new DependencyNode(
                new ArtifactCoordinate("demo", "app", "1"),
                DependencyScope.COMPILE,
                DependencyOrigin.PROJECT,
                ""
        );
        graph.addRoot(root);
        AnalysisResult result = new AnalysisResult(
                "demo",
                "/tmp/demo",
                Instant.parse("2024-01-01T00:00:00Z"),
                graph,
                List.of(Issue.conflict("org.x:y", List.of("1", "2"), "align"))
        );

        Path out = tempDir.resolve("report.json");
        new JsonExporter().export(result, out);

        String json = Files.readString(out);
        assertThat(json).contains("\"projectName\" : \"demo\"");
        assertThat(json).contains("VERSION_CONFLICT");
    }
}
