package io.github.dgviz.analysis;

import io.github.dgviz.model.DependencyGraph;
import io.github.dgviz.model.Issue;

import java.util.List;

/**
 * Detects one class of dependency issues.
 */
public interface IssueDetector {

    List<Issue> detect(DependencyGraph graph);
}
