package io.github.dgviz.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Full result of a dependency analysis run.
 */
public record AnalysisResult(
        String projectName,
        String sourcePath,
        Instant analyzedAt,
        DependencyGraph graph,
        List<Issue> issues
) {
    public AnalysisResult {
        Objects.requireNonNull(projectName, "projectName");
        Objects.requireNonNull(sourcePath, "sourcePath");
        Objects.requireNonNull(analyzedAt, "analyzedAt");
        Objects.requireNonNull(graph, "graph");
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public boolean hasProblems() {
        return issues.stream().anyMatch(i ->
                i.severity() == IssueSeverity.ERROR || i.severity() == IssueSeverity.CRITICAL);
    }

    public long conflictCount() {
        return issues.stream().filter(i -> i.type() == IssueType.VERSION_CONFLICT).count();
    }

    public long duplicateCount() {
        return issues.stream().filter(i -> i.type() == IssueType.DUPLICATE).count();
    }

    public long vulnerabilityCount() {
        return issues.stream().filter(i -> i.type() == IssueType.VULNERABILITY).count();
    }
}
