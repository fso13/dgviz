package io.github.dgviz.model;

import java.util.List;
import java.util.Objects;

/**
 * A single analysis finding attached to one or more artifacts.
 */
public record Issue(
        IssueType type,
        IssueSeverity severity,
        String title,
        String description,
        String recommendation,
        List<String> artifactKeys,
        String cve,
        Double cvss,
        String fixedVersion
) {
    public Issue {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(title, "title");
        description = description == null ? "" : description;
        recommendation = recommendation == null ? "" : recommendation;
        artifactKeys = artifactKeys == null ? List.of() : List.copyOf(artifactKeys);
        cve = cve == null ? "" : cve;
        fixedVersion = fixedVersion == null ? "" : fixedVersion;
    }

    public static Issue conflict(String ga, List<String> versions, String recommendation) {
        return new Issue(
                IssueType.VERSION_CONFLICT,
                IssueSeverity.ERROR,
                "Version conflict: " + ga,
                "Multiple versions found: " + String.join(", ", versions),
                recommendation,
                versions.stream().map(v -> ga + ":" + v).toList(),
                "",
                null,
                ""
        );
    }

    public static Issue duplicate(String gav, String recommendation) {
        return new Issue(
                IssueType.DUPLICATE,
                IssueSeverity.WARNING,
                "Duplicate dependency: " + gav,
                "Artifact is declared both directly and transitively (or repeatedly).",
                recommendation,
                List.of(gav),
                "",
                null,
                ""
        );
    }

    public static Issue vulnerability(
            String gav,
            String cve,
            IssueSeverity severity,
            String description,
            Double cvss,
            String recommendation,
            String fixedVersion
    ) {
        return new Issue(
                IssueType.VULNERABILITY,
                severity,
                "Vulnerability " + cve + " in " + gav,
                description,
                recommendation,
                List.of(gav),
                cve,
                cvss,
                fixedVersion == null ? "" : fixedVersion
        );
    }

    /** @deprecated use {@link #vulnerability(String, String, IssueSeverity, String, Double, String, String)} */
    @Deprecated
    public static Issue vulnerability(
            String gav,
            String cve,
            IssueSeverity severity,
            String description,
            Double cvss,
            String recommendation
    ) {
        return vulnerability(gav, cve, severity, description, cvss, recommendation, "");
    }
}
