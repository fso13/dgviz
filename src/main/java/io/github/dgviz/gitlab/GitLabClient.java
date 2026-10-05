package io.github.dgviz.gitlab;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Minimal GitLab API client (auth, projects, files, dependencies).
 */
public final class GitLabClient {

    private static final Logger log = LoggerFactory.getLogger(GitLabClient.class);

    private final String baseUrl;
    private final String token;
    private final OkHttpClient http;
    private final ObjectMapper mapper;

    public GitLabClient(String baseUrl, String token) {
        this(baseUrl, token, defaultClient(), tools.jackson.databind.json.JsonMapper.builder().build());
    }

    public GitLabClient(String baseUrl, String token, OkHttpClient http, ObjectMapper mapper) {
        this.baseUrl = trimSlash(Objects.requireNonNull(baseUrl, "baseUrl"));
        this.token = Objects.requireNonNull(token, "token");
        if (token.isBlank()) {
            throw new IllegalArgumentException("GitLab token must not be blank (set GITLAB_TOKEN)");
        }
        this.http = http;
        this.mapper = mapper;
    }

    private static OkHttpClient defaultClient() {
        return new OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(60))
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    public List<GitLabProject> listProjects() throws IOException {
        List<GitLabProject> all = new ArrayList<>();
        int page = 1;
        while (true) {
            HttpUrl url = HttpUrl.parse(baseUrl + "/api/v4/projects").newBuilder()
                    .addQueryParameter("membership", "true")
                    .addQueryParameter("simple", "true")
                    .addQueryParameter("per_page", "100")
                    .addQueryParameter("page", String.valueOf(page))
                    .build();
            List<GitLabProject> pageItems = getJson(url, new TypeReference<>() {
            });
            if (pageItems.isEmpty()) {
                break;
            }
            all.addAll(pageItems);
            if (pageItems.size() < 100) {
                break;
            }
            page++;
        }
        return all;
    }

    public GitLabProject getProject(String projectIdOrPath) throws IOException {
        String encoded = urlEncodePath(projectIdOrPath);
        HttpUrl url = HttpUrl.parse(baseUrl + "/api/v4/projects/" + encoded);
        return getJson(url, new TypeReference<>() {
        });
    }

    public String getFileContent(String projectIdOrPath, String filePath, String ref) throws IOException {
        String encodedProject = urlEncodePath(projectIdOrPath);
        String encodedFile = urlEncodePath(filePath);
        HttpUrl url = HttpUrl.parse(baseUrl + "/api/v4/projects/" + encodedProject
                        + "/repository/files/" + encodedFile + "/raw")
                .newBuilder()
                .addQueryParameter("ref", ref == null ? "main" : ref)
                .build();
        Request request = authorized(url).build();
        try (Response response = http.newCall(request).execute()) {
            ensureSuccess(response, url);
            ResponseBody body = response.body();
            return body == null ? "" : body.string();
        }
    }

    public Path downloadProjectFiles(
            String projectIdOrPath,
            List<String> relativePaths,
            String ref,
            Path targetDir
    ) throws IOException {
        Files.createDirectories(targetDir);
        for (String relative : relativePaths) {
            String content = getFileContent(projectIdOrPath, relative, ref);
            Path out = targetDir.resolve(relative);
            Files.createDirectories(out.getParent());
            Files.writeString(out, content, StandardCharsets.UTF_8);
        }
        return targetDir;
    }

    /**
     * Starts a CI pipeline on the given ref (branch/tag).
     *
     * @return pipeline id
     */
    public long triggerPipeline(String projectIdOrPath, String ref) throws IOException {
        String encoded = urlEncodePath(projectIdOrPath);
        HttpUrl url = HttpUrl.parse(baseUrl + "/api/v4/projects/" + encoded + "/pipeline");
        String body = "{\"ref\":" + jsonString(ref == null || ref.isBlank() ? "main" : ref) + "}";
        Request request = authorized(url)
                .post(RequestBody.create(body, MediaType.get("application/json; charset=utf-8")))
                .build();
        try (Response response = http.newCall(request).execute()) {
            ensureSuccess(response, url);
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new GitLabApiException(response.code(), "Empty pipeline response");
            }
            var node = mapper.readTree(responseBody.byteStream());
            return node.path("id").asLong(0L);
        }
    }

    private static String jsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    public List<GitLabDependency> listDependencies(String projectIdOrPath) throws IOException {
        List<GitLabDependency> all = new ArrayList<>();
        int page = 1;
        String encoded = urlEncodePath(projectIdOrPath);
        while (true) {
            HttpUrl url = HttpUrl.parse(baseUrl + "/api/v4/projects/" + encoded + "/dependencies")
                    .newBuilder()
                    .addQueryParameter("package_manager", "maven,gradle")
                    .addQueryParameter("per_page", "100")
                    .addQueryParameter("page", String.valueOf(page))
                    .build();
            List<GitLabDependency> pageItems;
            try {
                pageItems = getJson(url, new TypeReference<>() {
                });
            } catch (GitLabApiException ex) {
                if (ex.getStatusCode() == 403 || ex.getStatusCode() == 404) {
                    log.warn("Dependencies API unavailable (status {}). Falling back to empty list.",
                            ex.getStatusCode());
                    return List.of();
                }
                throw ex;
            }
            if (pageItems.isEmpty()) {
                break;
            }
            all.addAll(pageItems);
            if (pageItems.size() < 100) {
                break;
            }
            page++;
        }
        return all;
    }

    private <T> T getJson(HttpUrl url, TypeReference<T> type) throws IOException {
        Request request = authorized(url).build();
        try (Response response = http.newCall(request).execute()) {
            ensureSuccess(response, url);
            ResponseBody body = response.body();
            if (body == null) {
                throw new GitLabApiException(response.code(), "Empty response from " + safeUrl(url));
            }
            return mapper.readValue(body.byteStream(), type);
        }
    }

    private Request.Builder authorized(HttpUrl url) {
        return new Request.Builder()
                .url(url)
                .header("PRIVATE-TOKEN", token)
                .header("Accept", "application/json");
    }

    private void ensureSuccess(Response response, HttpUrl url) throws IOException {
        if (response.isSuccessful()) {
            return;
        }
        String body = response.body() == null ? "" : response.body().string();
        throw new GitLabApiException(response.code(),
                "GitLab API error " + response.code() + " for " + safeUrl(url) + ": " + body);
    }

    /**
     * Never log the token; only host + path.
     */
    static String safeUrl(HttpUrl url) {
        return url.newBuilder().query(null).build().toString();
    }

    private static String trimSlash(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    private static String urlEncodePath(String value) {
        return value.replace("/", "%2F");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitLabProject(Long id, String name, String path_with_namespace, String web_url) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitLabDependency(
            String name,
            String version,
            String package_manager,
            String dependency_file_path,
            List<GitLabVulnerability> vulnerabilities
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitLabVulnerability(String name, String severity, String cve) {
    }
}
