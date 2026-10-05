package io.github.dgviz.model;

import java.util.Objects;

/**
 * Directed edge from parent dependency to child dependency.
 */
public record DependencyEdge(
        DependencyNode from,
        DependencyNode to
) {
    public DependencyEdge {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
    }
}
