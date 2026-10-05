package io.github.dgviz.cli;

import io.github.dgviz.analysis.AnalysisEngine;
import io.github.dgviz.config.DgvizConfig;
import io.github.dgviz.export.ReportService;
import io.github.dgviz.model.AnalysisResult;
import io.github.dgviz.model.Issue;
import io.github.dgviz.model.IssueSeverity;
import io.github.dgviz.model.IssueType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "ci-check",
        description = "CI-friendly analysis with non-zero exit code when problems are found"
)
public class CiCheckCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(CiCheckCommand.class);

    @Parameters(index = "0", arity = "0..1", description = "Local project path (default: .)")
    Path projectPath;

    @Option(names = "--config", description = "Path to dgviz.yml / dgviz.json")
    Path configPath;

    @Option(names = {"-o", "--output"}, description = "Output directory for artifacts")
    Path outputDir;

    @Option(names = "--gitlab-token", description = "GitLab token (accepted for CI templates; not logged)")
    String gitlabToken;

    @Option(names = "--gitlab-url", description = "GitLab base URL")
    String gitlabUrl;

    @Option(names = "--project", description = "GitLab project id (optional metadata)")
    String project;

    @Option(names = "--fail-on-conflict", description = "Fail when version conflicts are present")
    boolean failOnConflict;

    @Option(names = "--fail-on-cvss", description = "Fail when vulnerability CVSS >= threshold", defaultValue = "0")
    double failOnCvss;

    @Override
    public Integer call() {
        try {
            DgvizConfig config = DgvizConfig.load(configPath);
            if (gitlabToken != null) {
                config.setGitlabToken(gitlabToken);
            }
            if (gitlabUrl != null) {
                config.setGitlabUrl(gitlabUrl);
            }
            boolean shouldFailOnConflict = failOnConflict || config.isFailOnConflict();
            double cvssThreshold = failOnCvss > 0 ? failOnCvss : config.getFailOnCvss();

            Path root = projectPath != null ? projectPath : Path.of(config.getProjectPath());
            AnalysisResult result = AnalysisEngine.createDefault(cvssThreshold).analyze(root);

            Path out = outputDir != null ? outputDir : Path.of(config.getOutputDir());
            new ReportService().exportAll(result, out);

            boolean problems = false;
            if (shouldFailOnConflict && result.conflictCount() > 0) {
                problems = true;
            }
            for (Issue issue : result.issues()) {
                if (issue.type() == IssueType.VULNERABILITY) {
                    if (issue.cvss() != null && issue.cvss() >= cvssThreshold && cvssThreshold > 0) {
                        problems = true;
                    }
                    if (cvssThreshold <= 0 && (issue.severity() == IssueSeverity.ERROR
                            || issue.severity() == IssueSeverity.CRITICAL)) {
                        problems = true;
                    }
                }
                if (shouldFailOnConflict && issue.type() == IssueType.VERSION_CONFLICT) {
                    problems = true;
                }
            }

            System.out.printf(
                    "ci-check: conflicts=%d duplicates=%d vulnerabilities=%d → %s%n",
                    result.conflictCount(),
                    result.duplicateCount(),
                    result.vulnerabilityCount(),
                    problems ? "FAIL" : "PASS"
            );

            return problems ? ExitCodes.PROBLEMS_FOUND : ExitCodes.SUCCESS;
        } catch (Exception e) {
            log.error("ci-check failed", e);
            System.err.println("Error: " + e.getMessage());
            return ExitCodes.EXECUTION_ERROR;
        }
    }
}
