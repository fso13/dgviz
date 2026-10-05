package io.github.dgviz.web;

import io.github.dgviz.analysis.store.AnalysisDependency;
import io.github.dgviz.analysis.store.AnalysisRun;
import io.github.dgviz.analysis.store.RepositoryAnalysisService;
import io.github.dgviz.analysis.store.StoredAnalysisIssue;
import io.github.dgviz.export.ReportExportService;
import io.github.dgviz.export.ReportMailService;
import io.github.dgviz.repository.CodeRepository;
import io.github.dgviz.repository.RepositoryRescanService;
import io.github.dgviz.security.AccessControlService;
import io.github.dgviz.vulnerability.Vulnerability;
import io.github.dgviz.vulnerability.VulnerabilityRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/workspace")
public class WorkspaceController {

    private final AccessControlService accessControl;
    private final RepositoryAnalysisService analysisService;
    private final VulnerabilityRepository vulnerabilityRepository;
    private final RepositoryRescanService rescanService;
    private final ReportExportService reportExportService;
    private final ReportMailService reportMailService;

    public WorkspaceController(
            AccessControlService accessControl,
            RepositoryAnalysisService analysisService,
            VulnerabilityRepository vulnerabilityRepository,
            RepositoryRescanService rescanService,
            ReportExportService reportExportService,
            ReportMailService reportMailService
    ) {
        this.accessControl = accessControl;
        this.analysisService = analysisService;
        this.vulnerabilityRepository = vulnerabilityRepository;
        this.rescanService = rescanService;
        this.reportExportService = reportExportService;
        this.reportMailService = reportMailService;
    }

    @GetMapping("/projects")
    public String projects(Model model) {
        model.addAttribute("projects", accessControl.accessibleProjects());
        return "workspace/projects";
    }

    @GetMapping("/projects/{id}")
    public String projectDetail(@PathVariable Long id, Model model) {
        var project = accessControl.getAccessibleProject(id);
        model.addAttribute("project", project);
        model.addAttribute("repositories", accessControl.repositoriesForProject(id));
        model.addAttribute("latestRuns", analysisService.latestRunMap());
        model.addAttribute("canManageRepos",
                accessControl.canManageRepositories(accessControl.currentUser()));
        return "workspace/project";
    }

    @GetMapping("/repositories")
    public String repositories(Model model) {
        model.addAttribute("repositories", accessControl.accessibleRepositories());
        model.addAttribute("latestRuns", analysisService.latestRunMap());
        return "workspace/repositories";
    }

    @GetMapping("/repositories/{id}")
    public String repositoryDetail(
            @PathVariable Long id,
            @RequestParam(defaultValue = "overview") String tab,
            @RequestParam(required = false) String severity,
            Model model
    ) {
        CodeRepository repo = accessControl.getAccessibleRepository(id);
        String activeTab = normalizeTab(tab);
        AnalysisRun latest = analysisService.latestRunForRepository(id).orElse(null);

        model.addAttribute("repo", repo);
        model.addAttribute("tab", activeTab);
        model.addAttribute("latestRun", latest);
        model.addAttribute("scanHistory", analysisService.runsForRepository(id));
        model.addAttribute("severityFilter", severity == null ? "" : severity.trim());

        if ("tree".equals(activeTab) && latest != null) {
            model.addAttribute("treeRoots", buildTree(analysisService.dependencyTree(latest.getId())));
        } else {
            model.addAttribute("treeRoots", List.of());
        }
        if (("issues".equals(activeTab) || "overview".equals(activeTab)) && latest != null) {
            List<StoredAnalysisIssue> issues = analysisService.issues(latest.getId());
            List<StoredAnalysisIssue> vulns = issues.stream()
                    .filter(i -> "VULNERABILITY".equals(i.getIssueType()))
                    .toList();
            model.addAttribute("issueSeverities", vulns.stream()
                    .map(StoredAnalysisIssue::getSeverity)
                    .distinct()
                    .sorted()
                    .toList());
            if (severity != null && !severity.isBlank()) {
                String sev = severity.trim().toUpperCase(Locale.ROOT);
                issues = issues.stream()
                        .filter(i -> sev.equalsIgnoreCase(i.getSeverity()))
                        .toList();
                vulns = vulns.stream()
                        .filter(i -> sev.equalsIgnoreCase(i.getSeverity()))
                        .toList();
            }
            model.addAttribute("issues", issues);
            model.addAttribute("vulnerabilities", vulns);
        } else {
            model.addAttribute("issues", List.of());
            model.addAttribute("vulnerabilities", List.of());
            model.addAttribute("issueSeverities", List.of());
        }
        model.addAttribute("canRescanFromStored", latest != null);
        model.addAttribute("canTriggerGitLabPipeline",
                repo.getSourceType() == io.github.dgviz.repository.RepositorySourceType.GITLAB
                        && repo.hasAccessToken());
        return "workspace/repository";
    }

    @PostMapping("/repositories/{id}/rescan")
    public String rescan(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        RepositoryRescanService.RescanResult result = rescanService.rescanFromStored(id);
        redirectAttributes.addFlashAttribute(result.success() ? "success" : "error", result.message());
        return "redirect:/workspace/repositories/" + id;
    }

    @PostMapping("/repositories/{id}/refresh-ci")
    public String refreshFromCi(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        RepositoryRescanService.RescanResult result = rescanService.triggerRemoteRefresh(id);
        redirectAttributes.addFlashAttribute(result.success() ? "success" : "error", result.message());
        return "redirect:/workspace/repositories/" + id;
    }

    @PostMapping("/repositories/{id}/export/email")
    public String emailReport(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            ReportMailService.SendReportResult result = reportMailService.emailLatestReport(id);
            String msg = "Report emailed to " + result.recipientCount() + " recipient(s): "
                    + String.join(", ", result.emails());
            if (result.message() != null && !result.message().isBlank()) {
                msg = msg + ". " + result.message();
            }
            redirectAttributes.addFlashAttribute("success", msg);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/workspace/repositories/" + id;
    }

    @GetMapping(value = "/repositories/{id}/sbom.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> downloadLatestSbom(@PathVariable Long id) {
        accessControl.getAccessibleRepository(id);
        String json = analysisService.sbomJsonForLatestRun(id);
        String filename = "dgviz-repo-" + id + "-sbom.json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(json.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping(value = "/analysis/{runId}/sbom.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> downloadRunSbom(@PathVariable Long runId) {
        AnalysisRun run = analysisService.getRun(runId);
        String json = analysisService.sbomJsonForRun(runId);
        String filename = "dgviz-run-" + runId + "-sbom.json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(json.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/repositories/{id}/export/tree")
    public ResponseEntity<byte[]> exportTree(
            @PathVariable Long id,
            @RequestParam(defaultValue = "txt") String format
    ) {
        CodeRepository repo = accessControl.getAccessibleRepository(id);
        AnalysisRun run = analysisService.latestRunForRepository(id)
                .orElseThrow(() -> new IllegalArgumentException("No scan found for repository " + id));
        List<AnalysisDependency> deps = analysisService.dependencyTree(run.getId());
        byte[] body = reportExportService.exportDependencyTree(repo, run, deps, format);
        String filename = reportExportService.filename(repo, "tree", format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(reportExportService.contentType("tree", format)))
                .body(body);
    }

    @GetMapping("/repositories/{id}/export/vulnerabilities")
    public ResponseEntity<byte[]> exportVulnerabilities(
            @PathVariable Long id,
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) String severity
    ) {
        CodeRepository repo = accessControl.getAccessibleRepository(id);
        AnalysisRun run = analysisService.latestRunForRepository(id)
                .orElseThrow(() -> new IllegalArgumentException("No scan found for repository " + id));
        List<StoredAnalysisIssue> issues = analysisService.issues(run.getId());
        if (severity != null && !severity.isBlank()) {
            String sev = severity.trim().toUpperCase(Locale.ROOT);
            issues = issues.stream()
                    .filter(i -> sev.equalsIgnoreCase(i.getSeverity()))
                    .toList();
        }
        byte[] body = reportExportService.exportVulnerabilities(repo, run, issues, format);
        String filename = reportExportService.filename(repo, "vulnerabilities", format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(reportExportService.contentType("vulns", format)))
                .body(body);
    }

    @GetMapping("/analysis/{runId}/tree")
    public String tree(@PathVariable Long runId) {
        AnalysisRun run = analysisService.getRun(runId);
        return "redirect:/workspace/repositories/" + run.getRepository().getId() + "?tab=tree";
    }

    @GetMapping("/analysis/{runId}/issues")
    public String issues(@PathVariable Long runId) {
        AnalysisRun run = analysisService.getRun(runId);
        return "redirect:/workspace/repositories/" + run.getRepository().getId() + "?tab=issues";
    }

    @GetMapping("/vulnerabilities")
    public String vulnerabilities(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            Model model
    ) {
        int pageSize = 50;
        Page<Vulnerability> result = vulnerabilityRepository.search(
                blank(source), blank(severity), blank(q), PageRequest.of(Math.max(page, 0), pageSize));
        model.addAttribute("vulnerabilities", result.getContent());
        model.addAttribute("page", result);
        model.addAttribute("source", source == null ? "" : source);
        model.addAttribute("severity", severity == null ? "" : severity);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("sources", vulnerabilityRepository.distinctSources());
        model.addAttribute("severities", vulnerabilityRepository.distinctSeverities());
        model.addAttribute("totalCount", vulnerabilityRepository.count());
        model.addAttribute("findings", analysisService.accessibleVulnerabilities());
        return "workspace/vulnerabilities";
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeTab(String tab) {
        if (tab == null || tab.isBlank()) {
            return "overview";
        }
        return switch (tab.trim().toLowerCase(Locale.ROOT)) {
            case "tree", "issues", "history" -> tab.trim().toLowerCase(Locale.ROOT);
            default -> "overview";
        };
    }

    private List<TreeNode> buildTree(List<AnalysisDependency> deps) {
        Map<String, TreeNode> byId = new HashMap<>();
        for (AnalysisDependency d : deps) {
            byId.put(d.getNodeId(), new TreeNode(d, new ArrayList<>()));
        }
        List<TreeNode> roots = new ArrayList<>();
        for (AnalysisDependency d : deps) {
            TreeNode node = byId.get(d.getNodeId());
            String parent = d.getParentNodeId();
            if (parent == null || parent.isBlank() || !byId.containsKey(parent)) {
                roots.add(node);
            } else {
                byId.get(parent).children().add(node);
            }
        }
        return roots;
    }

    public record TreeNode(AnalysisDependency dependency, List<TreeNode> children) {
    }
}
