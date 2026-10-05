package io.github.dgviz.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Gradle plugin: {@code id("io.github.dgviz.scan")}.
 */
public class DgvizPlugin implements Plugin<Project> {

    public static final String EXTENSION = "dgviz";
    public static final String TASK = "dgvizScan";

    @Override
    public void apply(Project project) {
        DgvizExtension extension = project.getExtensions().create(EXTENSION, DgvizExtension.class);
        project.getTasks().register(TASK, DgvizScanTask.class, task -> {
            task.setGroup("verification");
            task.setDescription("Send dependency tree + SBOM to DGViz and write scan report");
            task.getHost().convention(extension.getHost());
            task.getRepositoryId().convention(extension.getRepositoryId());
            task.getToken().convention(extension.getToken());
            task.getCreateIssues().convention(extension.getCreateIssues());
            task.getFailOnIssues().convention(extension.getFailOnIssues());
            task.getConfigurationName().convention(extension.getConfigurationName());
            task.getOutputDir().convention(project.getLayout().getBuildDirectory().dir("reports/dgviz"));
        });
    }
}
