package io.github.dgviz.analysis.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "analysis_issue")
public class StoredAnalysisIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_run_id", nullable = false)
    private AnalysisRun analysisRun;

    @Column(name = "issue_type", nullable = false, length = 50)
    private String issueType;

    @Column(nullable = false, length = 50)
    private String severity;

    @Column(nullable = false, length = 1000)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String recommendation;

    @Column(length = 64)
    private String cve;

    private Double cvss;

    @Column(name = "artifact_keys", columnDefinition = "TEXT")
    private String artifactKeys;

    @Column(name = "fixed_version", length = 200)
    private String fixedVersion;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AnalysisRun getAnalysisRun() {
        return analysisRun;
    }

    public void setAnalysisRun(AnalysisRun analysisRun) {
        this.analysisRun = analysisRun;
    }

    public String getIssueType() {
        return issueType;
    }

    public void setIssueType(String issueType) {
        this.issueType = issueType;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }

    public String getCve() {
        return cve;
    }

    public void setCve(String cve) {
        this.cve = cve;
    }

    public Double getCvss() {
        return cvss;
    }

    public void setCvss(Double cvss) {
        this.cvss = cvss;
    }

    public String getArtifactKeys() {
        return artifactKeys;
    }

    public void setArtifactKeys(String artifactKeys) {
        this.artifactKeys = artifactKeys;
    }

    public String getFixedVersion() {
        return fixedVersion;
    }

    public void setFixedVersion(String fixedVersion) {
        this.fixedVersion = fixedVersion;
    }

    /** First GAV from artifact_keys, e.g. {@code org.freemarker:freemarker:2.3.34}. */
    public String primaryGav() {
        if (artifactKeys == null || artifactKeys.isBlank()) {
            return "";
        }
        return artifactKeys.split(",")[0].trim();
    }

    /** {@code group:artifact} from primary GAV. */
    public String packageName() {
        String gav = primaryGav();
        if (gav.isBlank()) {
            return "—";
        }
        int last = gav.lastIndexOf(':');
        if (last <= 0) {
            return gav;
        }
        // group:artifact:version → drop version (last segment)
        String ga = gav.substring(0, last);
        // if only one colon, it might already be GA
        return ga.isBlank() ? gav : ga;
    }

    public String packageVersion() {
        String gav = primaryGav();
        if (gav.isBlank()) {
            return "—";
        }
        int last = gav.lastIndexOf(':');
        if (last < 0 || last == gav.length() - 1) {
            return "—";
        }
        // Ensure we have group:artifact:version (at least 2 colons)
        int first = gav.indexOf(':');
        if (first < 0 || first == last) {
            return "—";
        }
        return gav.substring(last + 1);
    }

    public String advisoryUrl() {
        if (cve == null || cve.isBlank()) {
            return null;
        }
        if (cve.startsWith("CVE-")) {
            return "https://osv.dev/vulnerability/" + cve;
        }
        if (cve.startsWith("GHSA-")) {
            return "https://osv.dev/vulnerability/" + cve;
        }
        return "https://osv.dev/vulnerability/" + cve;
    }

    public String fixedVersionDisplay() {
        return fixedVersion == null || fixedVersion.isBlank() ? "—" : fixedVersion;
    }
}
