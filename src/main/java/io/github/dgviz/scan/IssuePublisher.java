package io.github.dgviz.scan;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import io.github.dgviz.model.AnalysisResult;
import io.github.dgviz.model.Issue;
import io.github.dgviz.model.IssueSeverity;
import io.github.dgviz.model.IssueType;
import io.github.dgviz.repository.CodeRepository;
import io.github.dgviz.repository.RepositorySourceType;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Optionally opens GitLab/GitHub issues for critical/high vulnerabilities.
 */
@Component
public class IssuePublisher {

    private static final Logger log = LoggerFactory.getLogger(IssuePublisher.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient http = new OkHttpClient.Builder()
            .callTimeout(Duration.ofSeconds(30))
            .build();
    private final ObjectMapper mapper;

    public IssuePublisher(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public List<String> publish(CodeRepository repo, AnalysisResult result) {
        if (!repo.hasAccessToken()) {
            log.warn("createIssues requested but repository {} has no access_token", repo.getId());
            return List.of();
        }
        List<Issue> targets = result.issues().stream()
                .filter(i -> i.type() == IssueType.VULNERABILITY)
                .filter(i -> i.severity() == IssueSeverity.CRITICAL || i.severity() == IssueSeverity.ERROR)
                .limit(20)
                .toList();
        if (targets.isEmpty()) {
            return List.of();
        }
        List<String> created = new ArrayList<>();
        try {
            if (repo.getSourceType() == RepositorySourceType.GITHUB
                    || (repo.getVcsUrl() != null && repo.getVcsUrl().contains("github.com"))) {
                created.addAll(createGitHubIssues(repo, targets));
            } else {
                created.addAll(createGitLabIssues(repo, targets));
            }
        } catch (Exception ex) {
            log.error("Failed to create issues for repo {}: {}", repo.getId(), ex.getMessage());
        }
        return created;
    }

    private List<String> createGitLabIssues(CodeRepository repo, List<Issue> issues) throws Exception {
        String host = blankTo(repo.getRemoteHost(), "https://gitlab.com");
        if (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }
        String path = repo.getGitlabPath();
        if ((path == null || path.isBlank()) && repo.getVcsUrl() != null) {
            path = extractPathFromUrl(repo.getVcsUrl());
        }
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("GitLab path is required to create issues");
        }
        String encoded = path.replace("/", "%2F");
        List<String> urls = new ArrayList<>();
        for (Issue issue : issues) {
            ObjectNode body = mapper.createObjectNode();
            body.put("title", "[DGViz] " + issue.title());
            body.put("description", issue.description() + "\n\n**Recommendation:** " + issue.recommendation()
                    + "\n\nArtifacts: " + String.join(", ", issue.artifactKeys()));
            body.put("labels", "security,dgviz");
            Request request = new Request.Builder()
                    .url(host + "/api/v4/projects/" + encoded + "/issues")
                    .header("PRIVATE-TOKEN", repo.getAccessToken())
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(body.toString().getBytes(StandardCharsets.UTF_8), JSON))
                    .build();
            try (Response response = http.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    var node = mapper.readTree(response.body().byteStream());
                    String web = node.path("web_url").asText("");
                    if (!web.isBlank()) {
                        urls.add(web);
                    }
                } else {
                    log.warn("GitLab create issue HTTP {}", response.code());
                }
            }
        }
        return urls;
    }

    private List<String> createGitHubIssues(CodeRepository repo, List<Issue> issues) throws Exception {
        String path = repo.getGitlabPath();
        if ((path == null || path.isBlank()) && repo.getVcsUrl() != null) {
            path = extractPathFromUrl(repo.getVcsUrl());
        }
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("GitHub owner/repo path is required");
        }
        List<String> urls = new ArrayList<>();
        for (Issue issue : issues) {
            ObjectNode body = mapper.createObjectNode();
            body.put("title", "[DGViz] " + issue.title());
            body.put("body", issue.description() + "\n\n**Recommendation:** " + issue.recommendation()
                    + "\n\nArtifacts: " + String.join(", ", issue.artifactKeys()));
            Request request = new Request.Builder()
                    .url("https://api.github.com/repos/" + path + "/issues")
                    .header("Authorization", "Bearer " + repo.getAccessToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(body.toString().getBytes(StandardCharsets.UTF_8), JSON))
                    .build();
            try (Response response = http.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    var node = mapper.readTree(response.body().byteStream());
                    String web = node.path("html_url").asText("");
                    if (!web.isBlank()) {
                        urls.add(web);
                    }
                } else {
                    log.warn("GitHub create issue HTTP {}", response.code());
                }
            }
        }
        return urls;
    }

    private static String extractPathFromUrl(String url) {
        String u = url.replace(".git", "");
        int idx = u.indexOf("github.com/");
        if (idx >= 0) {
            return u.substring(idx + "github.com/".length()).replaceAll("^/+", "");
        }
        idx = u.indexOf("gitlab.com/");
        if (idx >= 0) {
            return u.substring(idx + "gitlab.com/".length()).replaceAll("^/+", "");
        }
        // self-hosted: take last two segments after host
        try {
            var uri = java.net.URI.create(u);
            String p = uri.getPath();
            if (p != null && p.length() > 1) {
                return p.replaceAll("^/+", "");
            }
        } catch (Exception ignored) {
            // fall through
        }
        return null;
    }

    private static String blankTo(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v.trim();
    }
}
