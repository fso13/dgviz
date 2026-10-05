package io.github.dgviz.model;

import java.util.Objects;

/**
 * A node in the dependency graph.
 */
public final class DependencyNode {

    private final ArtifactCoordinate coordinate;
    private final DependencyScope scope;
    private final DependencyOrigin origin;
    private final String modulePath;

    public DependencyNode(
            ArtifactCoordinate coordinate,
            DependencyScope scope,
            DependencyOrigin origin,
            String modulePath
    ) {
        this.coordinate = Objects.requireNonNull(coordinate, "coordinate");
        this.scope = scope == null ? DependencyScope.UNKNOWN : scope;
        this.origin = origin == null ? DependencyOrigin.DIRECT : origin;
        this.modulePath = modulePath == null ? "" : modulePath;
    }

    public ArtifactCoordinate coordinate() {
        return coordinate;
    }

    public DependencyScope scope() {
        return scope;
    }

    public DependencyOrigin origin() {
        return origin;
    }

    public String modulePath() {
        return modulePath;
    }

    public String id() {
        return coordinate.gavKey() + "|" + scope + "|" + modulePath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DependencyNode that)) {
            return false;
        }
        return Objects.equals(coordinate, that.coordinate)
                && scope == that.scope
                && origin == that.origin
                && Objects.equals(modulePath, that.modulePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(coordinate, scope, origin, modulePath);
    }

    @Override
    public String toString() {
        return coordinate + " [" + scope + "/" + origin + "]";
    }
}
