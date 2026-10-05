package io.github.dgviz.scan;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.github.dgviz.analysis.AnalysisEngine;
import io.github.dgviz.analysis.store.AnalysisDependency;
import io.github.dgviz.analysis.store.AnalysisDependencyRepository;
import io.github.dgviz.analysis.store.AnalysisRun;
import io.github.dgviz.analysis.store.AnalysisRunRepository;
import io.github.dgviz.analysis.store.StoredAnalysisIssue;
import io.github.dgviz.export.JsonExporter;
import io.github.dgviz.model.AnalysisResult;
import io.github.dgviz.model.ArtifactCoordinate;
import io.github.dgviz.model.DependencyEdge;
import io.github.dgviz.model.DependencyGraph;
import io.github.dgviz.model.DependencyNode;
import io.github.dgviz.model.DependencyOrigin;
import io.github.dgviz.model.DependencyScope;
import io.github.dgviz.model.Issue;
import io.github.dgviz.model.IssueType;
import io.github.dgviz.repository.CodeRepository;
import io.github.dgviz.repository.CodeRepositoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ScanService {

    private final CodeRepositoryRepository repositoryRepository;
    private final DependencyGraphIngestor ingestor;
    private final AnalysisEngine analysisEngine;
    private final AnalysisRunRepository analysisRunRepository;
    private final AnalysisDependencyRepository dependencyRepository;
    private final ObjectMapper objectMapper;
    private final IssuePublisher issuePublisher;
    private final JsonExporter jsonExporter = new JsonExporter();

    public ScanService(
            CodeRepositoryRepository repositoryRepository,
            DependencyGraphIngestor ingestor,
            AnalysisEngine analysisEngine,
            AnalysisRunRepository analysisRunRepository,
            AnalysisDependencyRepository dependencyRepository,
            ObjectMapper objectMapper,
            IssuePublisher issuePublisher
    ) {
        this.repositoryRepository = repositoryRepository;
        this.ingestor = ingestor;
        this.analysisEngine = analysisEngine;
        this.analysisRunRepository = analysisRunRepository;
        this.dependencyRepository = dependencyRepository;
        this.objectMapper = objectMapper;
        this.issuePublisher = issuePublisher;
    }

    @Transactional
    public ScanReport scan(long repositoryId, String scanToken, ScanRequest request) {
        CodeRepository repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));
        if (repo.getScanToken() == null || !repo.getScanToken().equals(scanToken)) {
            throw new SecurityException("Invalid scan token");
        }

        DependencyGraph graph = ingestor.fromRequest(request);
        String projectName = blankTo(request.projectName(), repo.getName());
        AnalysisResult result = analysisEngine.analyzeGraph(projectName, "gradle-plugin", graph);

        String sbomJson = null;
        try {
            if (request.sbom() != null && !request.sbom().isNull()) {
                sbomJson = objectMapper.writeValueAsString(request.sbom());
            }
        } catch (Exception ignored) {
            // leave null
        }
        AnalysisRun run = persist(repo, result, "Plugin scan", sbomJson);
        return toReport(repo, run, projectName, result, maybePublishIssues(repo, request.createIssues(), result));
    }

    /**
     * Re-analyze the latest stored SBOM / dependency tree against the current CVE DB (no Gradle plugin).
     */
    @Transactional
    public ScanReport rescanFromStored(long repositoryId) {
        CodeRepository repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));
        AnalysisRun previous = analysisRunRepository.findFirstByRepositoryIdOrderByAnalyzedAtDesc(repositoryId)
                .orElseThrow(() -> new IllegalStateException(
                        "Нет сохранённого SBOM/дерева — сначала выполните ./gradlew dgvizScan"));

        String projectName = blankTo(previous.getProjectName(), repo.getName());
        DependencyGraph graph = graphFromStoredRun(previous);
        AnalysisResult result = analysisEngine.analyzeGraph(projectName, "web-rescan", graph);
        AnalysisRun run = persist(repo, result, "Web rescan (stored SBOM/tree)", previous.getSbomJson());
        return toReport(repo, run, projectName, result, maybePublishIssues(repo, null, result));
    }

    private DependencyGraph graphFromStoredRun(AnalysisRun previous) {
        if (previous.getSbomJson() != null && !previous.getSbomJson().isBlank()) {
            try {
                JsonNode sbom = objectMapper.readTree(previous.getSbomJson());
                return ingestor.fromRequest(new ScanRequest(
                        previous.getProjectName(),
                        "gradle",
                        false,
                        null,
                        sbom
                ));
            } catch (Exception ex) {
                // fall through to dependency rows
            }
        }
        List<AnalysisDependency> deps = dependencyRepository
                .findByAnalysisRunIdOrderByArtifactIdAsc(previous.getId());
        if (deps.isEmpty()) {
            throw new IllegalStateException(
                    "В последнем скане нет SBOM и зависимостей — выполните ./gradlew dgvizScan");
        }
        return graphFromDependencies(blankTo(previous.getProjectName(), "project"), deps);
    }

    private DependencyGraph graphFromDependencies(String projectName, List<AnalysisDependency> deps) {
        DependencyGraph graph = new DependencyGraph(projectName);
        Map<String, DependencyNode> byStoredId = new HashMap<>();
        for (AnalysisDependency d : deps) {
            DependencyOrigin origin = parseOrigin(d.getOrigin());
            DependencyScope scope = parseScope(d.getScope());
            DependencyNode node = new DependencyNode(
                    new ArtifactCoordinate(
                            blankTo(d.getGroupId(), "unknown"),
                            blankTo(d.getArtifactId(), "unknown"),
                            blankTo(d.getVersion(), "")
                    ),
                    scope,
                    origin,
                    blankTo(d.getModulePath(), "")
            );
            byStoredId.put(d.getNodeId(), node);
        }
        for (AnalysisDependency d : deps) {
            DependencyNode node = byStoredId.get(d.getNodeId());
            String parentId = d.getParentNodeId();
            if (parentId == null || parentId.isBlank() || !byStoredId.containsKey(parentId)) {
                graph.addRoot(node);
            } else {
                graph.addEdge(byStoredId.get(parentId), node);
            }
        }
        if (graph.size() == 0) {
            throw new IllegalStateException("Не удалось восстановить дерево зависимостей");
        }
        return graph;
    }

    private List<String> maybePublishIssues(CodeRepository repo, Boolean createIssuesFlag, AnalysisResult result) {
        boolean createIssues = createIssuesFlag != null
                ? createIssuesFlag
                : repo.isCreateIssuesDefault();
        return createIssues ? issuePublisher.publish(repo, result) : List.of();
    }

    private ScanReport toReport(
            CodeRepository repo,
            AnalysisRun run,
            String projectName,
            AnalysisResult result,
            List<String> created
    ) {
        List<ScanReport.Finding> findings = result.issues().stream()
                .map(i -> new ScanReport.Finding(
                        i.type().name(),
                        i.severity().name(),
                        i.title(),
                        i.description(),
                        i.recommendation(),
                        i.cve(),
                        i.cvss(),
                        i.artifactKeys()
                ))
                .toList();
        return new ScanReport(
                repo.getId(),
                run.getId(),
                projectName,
                result.hasProblems() ? "FAILED" : "SUCCESS",
                result.graph().size(),
                result.issues().size(),
                (int) result.vulnerabilityCount(),
                (int) result.conflictCount(),
                findings,
                toMarkdown(projectName, result, created),
                created
        );
    }

    private AnalysisRun persist(CodeRepository repo, AnalysisResult result, String messagePrefix, String sbomJson) {
        AnalysisRun run = new AnalysisRun();
        run.setRepository(repo);
        run.setAnalyzedAt(Instant.now());
        run.setProjectName(result.projectName());
        run.setNodeCount(result.graph().size());
        run.setIssueCount(result.issues().size());
        run.setConflictCount((int) result.conflictCount());
        run.setVulnerabilityCount((int) result.vulnerabilityCount());
        run.setStatus(result.hasProblems() ? "ISSUES" : "SUCCESS");
        run.setMessage(messagePrefix + ": " + result.graph().size() + " nodes");
        try {
            run.setGraphJson(jsonExporter.toJsonString(result));
            run.setIssuesJson(objectMapper.writeValueAsString(result.issues()));
            run.setSbomJson(sbomJson);
        } catch (Exception ex) {
            run.setGraphJson("{}");
            run.setIssuesJson("[]");
        }
        run = analysisRunRepository.save(run);

        Map<String, String> parentByChild = new HashMap<>();
        for (DependencyEdge edge : result.graph().edges()) {
            parentByChild.put(edge.to().id(), edge.from().id());
        }
        for (DependencyNode node : result.graph().nodes()) {
            AnalysisDependency dep = new AnalysisDependency();
            dep.setAnalysisRun(run);
            dep.setNodeId(node.id());
            dep.setParentNodeId(parentByChild.get(node.id()));
            dep.setGroupId(node.coordinate().groupId());
            dep.setArtifactId(node.coordinate().artifactId());
            dep.setVersion(node.coordinate().version());
            dep.setScope(node.scope().name());
            dep.setOrigin(node.origin().name());
            dep.setModulePath(node.modulePath());
            run.getDependencies().add(dep);
        }
        for (Issue issue : result.issues()) {
            StoredAnalysisIssue stored = new StoredAnalysisIssue();
            stored.setAnalysisRun(run);
            stored.setIssueType(issue.type().name());
            stored.setSeverity(issue.severity().name());
            stored.setTitle(issue.title());
            stored.setDescription(issue.description());
            stored.setRecommendation(issue.recommendation());
            stored.setCve(issue.cve());
            stored.setCvss(issue.cvss());
            stored.setArtifactKeys(String.join(",", issue.artifactKeys()));
            stored.setFixedVersion(issue.fixedVersion());
            run.getIssues().add(stored);
        }
        return analysisRunRepository.save(run);
    }

    private static DependencyOrigin parseOrigin(String raw) {
        if (raw == null || raw.isBlank()) {
            return DependencyOrigin.DIRECT;
        }
        try {
            return DependencyOrigin.valueOf(raw.trim().toUpperCase());
        } catch (Exception ex) {
            return DependencyOrigin.DIRECT;
        }
    }

    private static DependencyScope parseScope(String raw) {
        if (raw == null || raw.isBlank()) {
            return DependencyScope.UNKNOWN;
        }
        try {
            return DependencyScope.valueOf(raw.trim().toUpperCase());
        } catch (Exception ex) {
            return DependencyScope.UNKNOWN;
        }
    }

    static String toMarkdown(String projectName, AnalysisResult result, List<String> createdIssues) {
        StringBuilder md = new StringBuilder();
        md.append("## DGViz dependency scan: ").append(projectName).append("\n\n");
        md.append("| Metric | Value |\n|---|---|\n");
        md.append("| Nodes | ").append(result.graph().size()).append(" |\n");
        md.append("| Vulnerabilities | ").append(result.vulnerabilityCount()).append(" |\n");
        md.append("| Conflicts | ").append(result.conflictCount()).append(" |\n");
        md.append("| Duplicates | ").append(result.duplicateCount()).append(" |\n");
        md.append("| Status | ").append(result.hasProblems() ? "❌ issues found" : "✅ clean").append(" |\n\n");

        List<Issue> vulns = result.issues().stream()
                .filter(i -> i.type() == IssueType.VULNERABILITY)
                .toList();
        if (!vulns.isEmpty()) {
            md.append("### Vulnerabilities\n\n");
            md.append("| Severity | CVE | Artifact | Title |\n|---|---|---|---|\n");
            for (Issue i : vulns) {
                md.append("| ").append(i.severity())
                        .append(" | ").append(blankTo(i.cve(), "-"))
                        .append(" | `").append(i.artifactKeys().isEmpty() ? "-" : i.artifactKeys().getFirst()).append("`")
                        .append(" | ").append(escapeMd(i.title()))
                        .append(" |\n");
            }
            md.append("\n");
        }

        List<Issue> other = result.issues().stream()
                .filter(i -> i.type() != IssueType.VULNERABILITY)
                .toList();
        if (!other.isEmpty()) {
            md.append("### Other findings\n\n");
            for (Issue i : other) {
                md.append("- **").append(i.severity()).append("** ").append(escapeMd(i.title()));
                if (!i.recommendation().isBlank()) {
                    md.append(" — ").append(escapeMd(i.recommendation()));
                }
                md.append("\n");
            }
            md.append("\n");
        }

        if (createdIssues != null && !createdIssues.isEmpty()) {
            md.append("### Created issues\n\n");
            for (String url : createdIssues) {
                md.append("- ").append(url).append("\n");
            }
        }
        return md.toString();
    }

    private static String escapeMd(String value) {
        return value == null ? "" : value.replace("|", "\\|").replace("\n", " ");
    }

    private static String blankTo(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v;
    }
}
