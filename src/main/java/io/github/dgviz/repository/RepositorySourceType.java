package io.github.dgviz.repository;

public enum RepositorySourceType {
    /** Receives dependency tree / SBOM via Gradle plugin API. */
    PLUGIN,
    GITLAB,
    GITHUB,
    /** @deprecated use {@link #PLUGIN} */
    @Deprecated
    LOCAL;

    public static RepositorySourceType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return PLUGIN;
        }
        String v = raw.trim().toUpperCase();
        if ("LOCAL".equals(v)) {
            return PLUGIN;
        }
        return RepositorySourceType.valueOf(v);
    }

    public boolean isRemote() {
        return this == GITLAB || this == GITHUB;
    }
}
