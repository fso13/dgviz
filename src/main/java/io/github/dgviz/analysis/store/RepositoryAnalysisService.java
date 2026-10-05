package io.github.dgviz.analysis.store;

import io.github.dgviz.export.CycloneDxSbomExporter;
import io.github.dgviz.model.IssueType;
import io.github.dgviz.repository.CodeRepository;
import io.github.dgviz.security.AccessControlService;
import io.github.dgviz.vulnerability.Vulnerability;
import io.github.dgviz.vulnerability.VulnerabilityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class RepositoryAnalysisService {

    private static final Pattern FIXED_EVENT = Pattern.compile("\"fixed\"\\s*:\\s*\"([^\"]+)\"");

    private final AnalysisRunRepository analysisRunRepository;
    private final AnalysisDependencyRepository dependencyRepository;
    private final StoredAnalysisIssueRepository issueRepository;
    private final AccessControlService accessControl;
    private final CycloneDxSbomExporter sbomExporter;
    private final VulnerabilityRepository vulnerabilityRepository;

    public RepositoryAnalysisService(
            AnalysisRunRepository analysisRunRepository,
            AnalysisDependencyRepository dependencyRepository,
            StoredAnalysisIssueRepository issueRepository,
            AccessControlService accessControl,
            CycloneDxSbomExporter sbomExporter,
            VulnerabilityRepository vulnerabilityRepository
    ) {
        this.analysisRunRepository = analysisRunRepository;
        this.dependencyRepository = dependencyRepository;
        this.issueRepository = issueRepository;
        this.accessControl = accessControl;
        this.sbomExporter = sbomExporter;
        this.vulnerabilityRepository = vulnerabilityRepository;
    }

    @Transactional(readOnly = true)
    public List<AnalysisRun> latestRunsForAccessibleRepos() {
        List<CodeRepository> repos = accessControl.accessibleRepositories();
        List<AnalysisRun> runs = new ArrayList<>();
        for (CodeRepository repo : repos) {
            analysisRunRepository.findFirstByRepositoryIdOrderByAnalyzedAtDesc(repo.getId())
                    .ifPresent(runs::add);
        }
        return runs;
    }

    @Transactional(readOnly = true)
    public Map<Long, AnalysisRun> latestRunMap() {
        Map<Long, AnalysisRun> map = new HashMap<>();
        for (AnalysisRun run : latestRunsForAccessibleRepos()) {
            map.put(run.getRepository().getId(), run);
        }
        return map;
    }

    @Transactional(readOnly = true)
    public AnalysisRun getRun(Long runId) {
        AnalysisRun run = analysisRunRepository.findDetailedById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Analysis run not found"));
        accessControl.requireRepositoryAccess(run.getRepository().getId());
        return run;
    }

    @Transactional(readOnly = true)
    public Optional<AnalysisRun> latestRunForRepository(Long repositoryId) {
        accessControl.requireRepositoryAccess(repositoryId);
        return analysisRunRepository.findFirstByRepositoryIdOrderByAnalyzedAtDesc(repositoryId);
    }

    @Transactional(readOnly = true)
    public List<AnalysisRun> runsForRepository(Long repositoryId) {
        accessControl.requireRepositoryAccess(repositoryId);
        return analysisRunRepository.findByRepositoryIdOrderByAnalyzedAtDesc(repositoryId);
    }

    @Transactional(readOnly = true)
    public List<AnalysisDependency> dependencyTree(Long runId) {
        getRun(runId);
        return dependencyRepository.findByAnalysisRunIdOrderByArtifactIdAsc(runId);
    }

    @Transactional(readOnly = true)
    public List<StoredAnalysisIssue> issues(Long runId) {
        getRun(runId);
        List<StoredAnalysisIssue> issues = issueRepository.findByAnalysisRunIdOrderBySeverityAscTitleAsc(runId);
        enrichFixedVersions(issues);
        return issues;
    }

    private void enrichFixedVersions(List<StoredAnalysisIssue> issues) {
        for (StoredAnalysisIssue issue : issues) {
            if (issue.getFixedVersion() != null && !issue.getFixedVersion().isBlank()) {
                continue;
            }
            if (issue.getCve() == null || issue.getCve().isBlank()) {
                continue;
            }
            String pkg = issue.packageName();
            if ("—".equals(pkg)) {
                continue;
            }
            Optional<Vulnerability> match = vulnerabilityRepository.findByPackageNameIgnoreCase(pkg).stream()
                    .filter(v -> issue.getCve().equalsIgnoreCase(v.getCveId()))
                    .filter(v -> v.getRawJson() != null && !v.getRawJson().isBlank())
                    .findFirst();
            if (match.isEmpty()) {
                match = vulnerabilityRepository.findByCveIdIgnoreCase(issue.getCve()).stream()
                        .filter(v -> v.getRawJson() != null && !v.getRawJson().isBlank())
                        .findFirst();
            }
            match.ifPresent(v -> {
                Matcher m = FIXED_EVENT.matcher(v.getRawJson());
                StringBuilder fixed = new StringBuilder();
                while (m.find()) {
                    if (!fixed.isEmpty()) {
                        fixed.append(", ");
                    }
                    fixed.append(m.group(1));
                }
                if (!fixed.isEmpty()) {
                    issue.setFixedVersion(fixed.toString());
                }
            });
        }
    }

    @Transactional(readOnly = true)
    public String sbomJsonForRun(Long runId) {
        AnalysisRun run = getRun(runId);
        List<AnalysisDependency> deps = dependencyRepository.findByAnalysisRunIdOrderByArtifactIdAsc(runId);
        return sbomExporter.export(run, deps);
    }

    @Transactional(readOnly = true)
    public String sbomJsonForLatestRun(long repositoryId) {
        AnalysisRun run = latestRunForRepository(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("No scan found for repository " + repositoryId));
        return sbomJsonForRun(run.getId());
    }

    @Transactional(readOnly = true)
    public List<StoredAnalysisIssue> accessibleVulnerabilities() {
        var user = accessControl.currentUser();
        boolean admin = accessControl.isAdmin(user);
        if (admin) {
            return issueRepository.findAll().stream()
                    .filter(i -> IssueType.VULNERABILITY.name().equals(i.getIssueType()))
                    .toList();
        }
        return issueRepository.findAccessibleVulnerabilities(user.getUsername(), false);
    }
}
