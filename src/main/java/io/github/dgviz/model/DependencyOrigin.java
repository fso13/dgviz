package io.github.dgviz.model;

/**
 * Whether a dependency is declared directly or brought transitively.
 */
public enum DependencyOrigin {
    DIRECT,
    TRANSITIVE,
    PROJECT
}
