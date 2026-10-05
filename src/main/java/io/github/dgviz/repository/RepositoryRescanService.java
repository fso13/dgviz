package io.github.dgviz.repository;

import io.github.dgviz.gitlab.GitLabClient;
import io.github.dgviz.scan.ScanReport;
import io.github.dgviz.scan.ScanService;
import io.github.dgviz.security.AccessControlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RepositoryRescanService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryRescanService.class);

    private final AccessControlService accessControl;
    private final ScanService scanService;

    public RepositoryRescanService(AccessControlService accessControl, ScanService scanService) {
        this.accessControl = accessControl;
        this.scanService = scanService;
    }

    /**
     * Re-check stored SBOM / dependency tree against the current CVE database (no Gradle plugin).
     */
    public RescanResult rescanFromStored(long repositoryId) {
        accessControl.getAccessibleRepository(repositoryId);
        try {
            ScanReport report = scanService.rescanFromStored(repositoryId);
            return new RescanResult(
                    "Перескан без плагина завершён: status=" + report.status()
                            + ", nodes=" + report.nodeCount()
                            + ", vulns=" + report.vulnerabilityCount()
                            + ", conflicts=" + report.conflictCount()
                            + " (run #" + report.analysisRunId() + "). "
                            + "Дерево зависимостей не обновлялось — только повторный анализ по сохранённому SBOM/дереву.",
                    true
            );
        } catch (IllegalStateException | IllegalArgumentException ex) {
            return new RescanResult(ex.getMessage(), false);
        }
    }

    /**
     * Ask GitLab CI to run a fresh {@code dgvizScan} (updates the dependency tree itself).
     */
    public RescanResult triggerRemoteRefresh(long repositoryId) {
        CodeRepository repo = accessControl.getAccessibleRepository(repositoryId);
        if (repo.getSourceType() != RepositorySourceType.GITLAB || !repo.hasAccessToken()) {
            return new RescanResult(
                    "Для обновления дерева из CI нужен тип GITLAB, path и access token. "
                            + "Иначе запустите ./gradlew dgvizScan локально/в пайплайне.",
                    false
            );
        }
        String path = resolveGitLabPath(repo);
        if (path == null) {
            return new RescanResult("Не задан GitLab path / VCS URL для запуска pipeline.", false);
        }
        try {
            String host = blankTo(repo.getRemoteHost(), "https://gitlab.com");
            GitLabClient client = new GitLabClient(host, repo.getAccessToken());
            long pipelineId = client.triggerPipeline(path, repo.getDefaultBranch());
            return new RescanResult(
                    "Запущен GitLab pipeline #" + pipelineId
                            + " (ветка " + repo.getDefaultBranch()
                            + "). В CI должен быть шаг ./gradlew dgvizScan — он обновит дерево и SBOM.",
                    true
            );
        } catch (Exception ex) {
            log.warn("GitLab pipeline trigger failed for repo {}: {}", repositoryId, ex.getMessage());
            return new RescanResult("Не удалось запустить GitLab pipeline: " + ex.getMessage(), false);
        }
    }

    private static String resolveGitLabPath(CodeRepository repo) {
        if (repo.getGitlabPath() != null && !repo.getGitlabPath().isBlank()) {
            return repo.getGitlabPath().trim();
        }
        String url = repo.getVcsUrl();
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.endsWith(".git")) {
            trimmed = trimmed.substring(0, trimmed.length() - 4);
        }
        int idx = trimmed.indexOf("://");
        if (idx >= 0) {
            trimmed = trimmed.substring(idx + 3);
            int slash = trimmed.indexOf('/');
            if (slash >= 0) {
                return trimmed.substring(slash + 1);
            }
        }
        return null;
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public record RescanResult(String message, boolean success) {
    }
}
