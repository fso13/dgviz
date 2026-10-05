package io.github.dgviz.gradle;

import org.gradle.api.provider.Property;

/**
 * Configuration block {@code dgviz { ... }}.
 */
public abstract class DgvizExtension {

    public abstract Property<String> getHost();

    public abstract Property<Long> getRepositoryId();

    public abstract Property<String> getToken();

    /** When true, DGViz may open GitLab/GitHub issues from the report. */
    public abstract Property<Boolean> getCreateIssues();

    /** Fail the Gradle task when DGViz reports vulnerabilities/conflicts. */
    public abstract Property<Boolean> getFailOnIssues();

    /** Configuration to resolve (default {@code runtimeClasspath}). */
    public abstract Property<String> getConfigurationName();

    public DgvizExtension() {
        getHost().convention("http://localhost:8080");
        getCreateIssues().convention(false);
        getFailOnIssues().convention(true);
        getConfigurationName().convention("runtimeClasspath");
    }
}
