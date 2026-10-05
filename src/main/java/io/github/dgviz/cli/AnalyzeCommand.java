package io.github.dgviz.cli;

import io.github.dgviz.analysis.AnalysisEngine;
import io.github.dgviz.config.DgvizConfig;
import io.github.dgviz.export.ReportService;
import io.github.dgviz.gitlab.GitLabClient;
import io.github.dgviz.model.AnalysisResult;
import io.github.dgviz.model.ArtifactCoordinate;
import io.github.dgviz.model.DependencyGraph;
import io.github.dgviz.model.DependencyNode;
import io.github.dgviz.model.DependencyOrigin;
import io.github.dgviz.model.DependencyScope;
import io.github.dgviz.model.Issue;
import io.github.dgviz.model.IssueSeverity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "analyze",
        description = "Analyze local or GitLab-backed Java project dependencies"
)
public class AnalyzeCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(AnalyzeCommand.class);

    @Parameters(index = "0", arity = "0..1", description = "Local project path (default: .)")
    Path projectPath;

    @Option(names = "--config", description = "Path to dgviz.yml / dgviz.json")
    Path configPath;

    @Option(names = {"-o", "--output"}, description = "Output directory for reports")
    Path outputDir;

    @Option(names = "--gitlab-url", description = "GitLab base URL")
    String gitlabUrl;

    @Option(names = "--gitlab-token", description = "GitLab personal access / job token")
    String gitlabToken;

    @Option(names = "--project", description = "GitLab project id or path (enables remote mode)")
    String gitlabProject;

    @Option(names = "--ref", description = "Git reference for remote files", defaultValue = "main")
    String ref;

    @Option(names = "--use-gitlab-deps", description = "Also pull GitLab Dependencies API data")
    boolean useGitlabDeps;

    @Override
    public Integer call() {
        try {
            DgvizConfig config = DgvizConfig.load(configPath);
            if (gitlabUrl != null) {
                config.setGitlabUrl(gitlabUrl);
            }
            if (gitlabToken != null) {
                config.setGitlabToken(gitlabToken);
            }
            Path root = resolveProjectRoot(config);
            AnalysisEngine engine = AnalysisEngine.createDefault(config.getFailOnCvss());
            AnalysisResult result = engine.analyze(root);

            if (useGitlabDeps && gitlabProject != null) {
                result = enrichWithGitlabDeps(config, result);
            }

            Path out = outputDir != null ? outputDir : Path.of(config.getOutputDir());
            new ReportService().exportAll(result, out);
            printSummary(result, out);
            return ExitCodes.SUCCESS;
        } catch (Exception e) {
            log.error("Analyze failed", e);
            System.err.println("Error: " + e.getMessage());
            return ExitCodes.EXECUTION_ERROR;
        }
    }

    private Path resolveProjectRoot(DgvizConfig config) throws Exception {
        if (gitlabProject != null) {
            String token = firstNonBlank(gitlabToken, config.getGitlabToken());
            GitLabClient client = new GitLabClient(
                    firstNonBlank(gitlabUrl, config.getGitlabUrl()),
                    token
            );
            Path temp = Files.createTempDirectory("dgviz-gitlab-");
            // Prefer pom.xml; fall back to build.gradle
            try {
                client.downloadProjectFiles(gitlabProject, List.of("pom.xml"), ref, temp);
            } catch (Exception pomEx) {
                client.downloadProjectFiles(gitlabProject,
                        List.of("build.gradle", "build.gradle.kts", "settings.gradle.kts", "settings.gradle"),
                        ref, temp);
            }
            return temp;
        }
        if (projectPath != null) {
            return projectPath;
        }
        return Path.of(config.getProjectPath());
    }

    private AnalysisResult enrichWithGitlabDeps(DgvizConfig config, AnalysisResult local) throws Exception {
        GitLabClient client = new GitLabClient(
                firstNonBlank(gitlabUrl, config.getGitlabUrl()),
                firstNonBlank(gitlabToken, config.getGitlabToken())
        );
        List<GitLabClient.GitLabDependency> deps = client.listDependencies(gitlabProject);
        DependencyGraph graph = local.graph();
        DependencyNode root = graph.roots().isEmpty()
                ? new DependencyNode(
                new ArtifactCoordinate("gitlab", gitlabProject, "remote"),
                DependencyScope.COMPILE,
                DependencyOrigin.PROJECT,
                "")
                : graph.roots().get(0);

        for (GitLabClient.GitLabDependency dep : deps) {
            String name = dep.name() == null ? "" : dep.name();
            String group = name.contains("/") ? name.substring(0, name.indexOf('/')) : "unknown";
            String artifact = name.contains("/") ? name.substring(name.indexOf('/') + 1) : name;
            DependencyNode node = new DependencyNode(
                    new ArtifactCoordinate(group, artifact, dep.version() == null ? "" : dep.version()),
                    DependencyScope.COMPILE,
                    DependencyOrigin.DIRECT,
                    dep.dependency_file_path() == null ? "" : dep.dependency_file_path()
            );
            graph.addEdge(root, node);
        }

        AnalysisEngine engine = AnalysisEngine.createDefault(config.getFailOnCvss());
        AnalysisResult analyzed = engine.analyzeGraph(local.projectName(), local.sourcePath(), graph);

        // Merge GitLab vulnerability metadata
        java.util.ArrayList<Issue> issues = new java.util.ArrayList<>(analyzed.issues());
        for (GitLabClient.GitLabDependency dep : deps) {
            if (dep.vulnerabilities() == null) {
                continue;
            }
            for (GitLabClient.GitLabVulnerability v : dep.vulnerabilities()) {
                issues.add(Issue.vulnerability(
                        dep.name() + ":" + dep.version(),
                        v.cve() == null ? v.name() : v.cve(),
                        mapSeverity(v.severity()),
                        "Reported by GitLab Dependencies API",
                        null,
                        "Review GitLab Security Dashboard and upgrade the dependency."
                ));
            }
        }
        return new AnalysisResult(
                analyzed.projectName(),
                analyzed.sourcePath(),
                analyzed.analyzedAt(),
                analyzed.graph(),
                issues
        );
    }

    private static IssueSeverity mapSeverity(String severity) {
        if (severity == null) {
            return IssueSeverity.WARNING;
        }
        return switch (severity.toLowerCase()) {
            case "critical" -> IssueSeverity.CRITICAL;
            case "high" -> IssueSeverity.ERROR;
            case "medium" -> IssueSeverity.WARNING;
            default -> IssueSeverity.INFO;
        };
    }

    private void printSummary(AnalysisResult result, Path out) {
        System.out.printf(
                "Analyzed %s (%d nodes). Conflicts=%d, duplicates=%d, vulnerabilities=%d%nReports: %s%n",
                result.projectName(),
                result.graph().size(),
                result.conflictCount(),
                result.duplicateCount(),
                result.vulnerabilityCount(),
                out.toAbsolutePath()
        );
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }
}
