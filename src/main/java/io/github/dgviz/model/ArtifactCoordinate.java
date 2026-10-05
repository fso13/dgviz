package io.github.dgviz.model;

import java.util.Objects;

/**
 * Immutable Maven/Gradle-style artifact coordinate.
 */
public record ArtifactCoordinate(
        String groupId,
        String artifactId,
        String version
) {

    public ArtifactCoordinate {
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(artifactId, "artifactId");
        version = version == null ? "" : version;
    }

    public String gaKey() {
        return groupId + ":" + artifactId;
    }

    public String gavKey() {
        return groupId + ":" + artifactId + ":" + version;
    }

    @Override
    public String toString() {
        return gavKey();
    }
}
