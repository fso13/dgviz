package io.github.dgviz.repository;

import io.github.dgviz.project.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "repository")
public class CodeRepository {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private RepositorySourceType sourceType = RepositorySourceType.PLUGIN;

    @Column(name = "vcs_url", length = 1000)
    private String vcsUrl;

    @Column(name = "gitlab_path", length = 500)
    private String gitlabPath;

    @Column(name = "remote_host", length = 500)
    private String remoteHost;

    /** Token for GitLab/GitHub issue filing (optional). */
    @Column(name = "access_token", length = 2000)
    private String accessToken;

    /** Secret for Gradle plugin → DGViz scan API. */
    @Column(name = "scan_token", length = 64, unique = true)
    private String scanToken;

    @Column(name = "create_issues_default", nullable = false)
    private boolean createIssuesDefault;

    @Column(name = "default_branch", nullable = false, length = 200)
    private String defaultBranch = "main";

    @Column(name = "local_path", length = 1000)
    private String localPath;

    @Column(name = "build_system", length = 50)
    private String buildSystem = "gradle";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public RepositorySourceType getSourceType() {
        return sourceType == null ? RepositorySourceType.PLUGIN : sourceType;
    }

    public void setSourceType(RepositorySourceType sourceType) {
        this.sourceType = sourceType == null ? RepositorySourceType.PLUGIN : sourceType;
    }

    public String getVcsUrl() {
        return vcsUrl;
    }

    public void setVcsUrl(String vcsUrl) {
        this.vcsUrl = vcsUrl;
    }

    public String getGitlabPath() {
        return gitlabPath;
    }

    public void setGitlabPath(String gitlabPath) {
        this.gitlabPath = gitlabPath;
    }

    public String getRemoteHost() {
        return remoteHost;
    }

    public void setRemoteHost(String remoteHost) {
        this.remoteHost = remoteHost;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public boolean hasAccessToken() {
        return accessToken != null && !accessToken.isBlank();
    }

    public String getScanToken() {
        return scanToken;
    }

    public void setScanToken(String scanToken) {
        this.scanToken = scanToken;
    }

    public boolean isCreateIssuesDefault() {
        return createIssuesDefault;
    }

    public void setCreateIssuesDefault(boolean createIssuesDefault) {
        this.createIssuesDefault = createIssuesDefault;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    public String getLocalPath() {
        return localPath;
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }

    public String getBuildSystem() {
        return buildSystem;
    }

    public void setBuildSystem(String buildSystem) {
        this.buildSystem = buildSystem;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
