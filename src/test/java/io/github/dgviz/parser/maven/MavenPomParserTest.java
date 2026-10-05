package io.github.dgviz.parser.maven;

import io.github.dgviz.model.DependencyGraph;
import io.github.dgviz.model.DependencyOrigin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MavenPomParserTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldParseMultiModuleProject() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>parent</artifactId>
                  <version>1.0.0</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>core</module>
                  </modules>
                  <properties>
                    <guava.version>32.1.2-jre</guava.version>
                  </properties>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.google.guava</groupId>
                        <artifactId>guava</artifactId>
                        <version>${guava.version}</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);
        Path core = tempDir.resolve("core");
        Files.createDirectories(core);
        Files.writeString(core.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                    <groupId>com.example</groupId>
                    <artifactId>parent</artifactId>
                    <version>1.0.0</version>
                  </parent>
                  <artifactId>core</artifactId>
                  <dependencies>
                    <dependency>
                      <groupId>com.google.guava</groupId>
                      <artifactId>guava</artifactId>
                    </dependency>
                    <dependency>
                      <groupId>junit</groupId>
                      <artifactId>junit</artifactId>
                      <version>4.13.2</version>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
                </project>
                """);

        MavenPomParser parser = new MavenPomParser();
        assertThat(parser.supports(tempDir)).isTrue();

        DependencyGraph graph = parser.parse(tempDir);

        assertThat(graph.rootName()).isEqualTo("com.example:parent:1.0.0");
        assertThat(graph.nodes()).anyMatch(n ->
                n.coordinate().gaKey().equals("com.google.guava:guava")
                        && n.coordinate().version().equals("32.1.2-jre")
                        && n.origin() == DependencyOrigin.DIRECT);
        assertThat(graph.nodes()).anyMatch(n ->
                n.coordinate().gaKey().equals("junit:junit")
                        && n.scope().name().equals("TEST"));
    }
}
