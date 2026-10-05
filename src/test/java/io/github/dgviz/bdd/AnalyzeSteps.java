package io.github.dgviz.bdd;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.github.dgviz.analysis.AnalysisEngine;
import io.github.dgviz.model.AnalysisResult;
import io.github.dgviz.model.IssueType;
import org.assertj.core.api.Assertions;

import java.nio.file.Files;
import java.nio.file.Path;

public class AnalyzeSteps {

    private Path projectDir;
    private AnalysisResult result;

    @Given("a multi-module Maven project with conflicting guava versions")
    public void mavenProjectWithConflicts() throws Exception {
        projectDir = Files.createTempDirectory("dgviz-bdd-");
        Files.writeString(projectDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>parent</artifactId>
                  <version>1.0.0</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>a</module>
                    <module>b</module>
                  </modules>
                </project>
                """);
        writeModule("a", "31.1-jre");
        writeModule("b", "32.1.2-jre");
    }

    private void writeModule(String name, String guavaVersion) throws Exception {
        Path module = projectDir.resolve(name);
        Files.createDirectories(module);
        Files.writeString(module.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                    <groupId>com.example</groupId>
                    <artifactId>parent</artifactId>
                    <version>1.0.0</version>
                  </parent>
                  <artifactId>%s</artifactId>
                  <dependencies>
                    <dependency>
                      <groupId>com.google.guava</groupId>
                      <artifactId>guava</artifactId>
                      <version>%s</version>
                    </dependency>
                  </dependencies>
                </project>
                """.formatted(name, guavaVersion));
    }

    @When("the project is analyzed")
    public void analyzeProject() {
        result = AnalysisEngine.createDefault(0).analyze(projectDir);
    }

    @Then("the analysis should report a version conflict for {string}")
    public void shouldReportConflict(String ga) {
        Assertions.assertThat(result.issues())
                .anyMatch(i -> i.type() == IssueType.VERSION_CONFLICT && i.title().contains(ga));
    }

    @Then("the analysis report should contain at least {int} conflict")
    public void shouldContainConflicts(int count) {
        Assertions.assertThat(result.conflictCount()).isGreaterThanOrEqualTo(count);
    }
}
