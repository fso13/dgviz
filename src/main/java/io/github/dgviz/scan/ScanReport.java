package io.github.dgviz.scan;

import java.util.List;

/**
 * Report returned to CI / MR after a scan.
 */
public record ScanReport(
        long repositoryId,
        long analysisRunId,
        String projectName,
        String status,
        int nodeCount,
        int issueCount,
        int vulnerabilityCount,
        int conflictCount,
        List<Finding> findings,
        String markdown,
        List<String> createdIssues
) {
    public record Finding(
            String type,
            String severity,
            String title,
            String description,
            String recommendation,
            String cve,
            Double cvss,
            List<String> artifacts
    ) {
    }
}
