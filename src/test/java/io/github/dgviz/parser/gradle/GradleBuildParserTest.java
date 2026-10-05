package io.github.dgviz.parser.gradle;

import io.github.dgviz.model.DependencyGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GradleBuildParserTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldParseDeclaredDependenciesAndCatalog() throws Exception {
        Files.writeString(tempDir.resolve("settings.gradle.kts"), "rootProject.name = \"demo\"");
        Files.writeString(tempDir.resolve("build.gradle.kts"), """
                dependencies {
                    implementation("com.google.guava:guava:32.1.2-jre")
                    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
                    implementation(libs.jackson.databind)
                }
                """);
        Path gradleDir = tempDir.resolve("gradle");
        Files.createDirectories(gradleDir);
        Files.writeString(gradleDir.resolve("libs.versions.toml"), """
                [versions]
                jackson = "2.17.2"
                
                [libraries]
                jackson-databind = { module = "com.fasterxml.jackson.core:jackson-databind", version.ref = "jackson" }
                """);

        DependencyGraph graph = new GradleBuildParser().parse(tempDir);

        assertThat(graph.nodes()).anyMatch(n -> n.coordinate().gaKey().equals("com.google.guava:guava"));
        assertThat(graph.nodes()).anyMatch(n ->
                n.coordinate().gavKey().equals("com.fasterxml.jackson.core:jackson-databind:2.17.2"));
    }
}
