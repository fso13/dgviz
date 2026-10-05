package io.github.dgviz.analysis.store;

import io.github.dgviz.repository.CodeRepository;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "analysis_run")
public class AnalysisRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repository_id", nullable = false)
    private CodeRepository repository;

    @Column(name = "analyzed_at", nullable = false)
    private Instant analyzedAt = Instant.now();

    @Column(name = "project_name", length = 500)
    private String projectName;

    @Column(name = "node_count", nullable = false)
    private int nodeCount;

    @Column(name = "issue_count", nullable = false)
    private int issueCount;

    @Column(name = "conflict_count", nullable = false)
    private int conflictCount;

    @Column(name = "vulnerability_count", nullable = false)
    private int vulnerabilityCount;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(length = 2000)
    private String message;

    @Column(name = "graph_json", columnDefinition = "TEXT")
    private String graphJson;

    @Column(name = "sbom_json", columnDefinition = "TEXT")
    private String sbomJson;

    @Column(name = "issues_json", columnDefinition = "TEXT")
    private String issuesJson;

    @OneToMany(mappedBy = "analysisRun", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AnalysisDependency> dependencies = new ArrayList<>();

    @OneToMany(mappedBy = "analysisRun", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StoredAnalysisIssue> issues = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CodeRepository getRepository() {
        return repository;
    }

    public void setRepository(CodeRepository repository) {
        this.repository = repository;
    }

    public Instant getAnalyzedAt() {
        return analyzedAt;
    }

    public void setAnalyzedAt(Instant analyzedAt) {
        this.analyzedAt = analyzedAt;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public int getNodeCount() {
        return nodeCount;
    }

    public void setNodeCount(int nodeCount) {
        this.nodeCount = nodeCount;
    }

    public int getIssueCount() {
        return issueCount;
    }

    public void setIssueCount(int issueCount) {
        this.issueCount = issueCount;
    }

    public int getConflictCount() {
        return conflictCount;
    }

    public void setConflictCount(int conflictCount) {
        this.conflictCount = conflictCount;
    }

    public int getVulnerabilityCount() {
        return vulnerabilityCount;
    }

    public void setVulnerabilityCount(int vulnerabilityCount) {
        this.vulnerabilityCount = vulnerabilityCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getGraphJson() {
        return graphJson;
    }

    public void setGraphJson(String graphJson) {
        this.graphJson = graphJson;
    }

    public String getSbomJson() {
        return sbomJson;
    }

    public void setSbomJson(String sbomJson) {
        this.sbomJson = sbomJson;
    }

    public String getIssuesJson() {
        return issuesJson;
    }

    public void setIssuesJson(String issuesJson) {
        this.issuesJson = issuesJson;
    }

    public List<AnalysisDependency> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<AnalysisDependency> dependencies) {
        this.dependencies = dependencies;
    }

    public List<StoredAnalysisIssue> getIssues() {
        return issues;
    }

    public void setIssues(List<StoredAnalysisIssue> issues) {
        this.issues = issues;
    }
}
