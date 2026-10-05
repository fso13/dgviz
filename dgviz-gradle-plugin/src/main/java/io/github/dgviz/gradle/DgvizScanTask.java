package io.github.dgviz.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Collects the Gradle dependency tree, posts it to DGViz, writes MR-friendly report files.
 */
public abstract class DgvizScanTask extends DefaultTask {

    @Input
    public abstract Property<String> getHost();

    @Input
    public abstract Property<Long> getRepositoryId();

    @Input
    public abstract Property<String> getToken();

    @Input
    public abstract Property<Boolean> getCreateIssues();

    @Input
    public abstract Property<Boolean> getFailOnIssues();

    @Input
    @Optional
    public abstract Property<String> getConfigurationName();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @TaskAction
    public void scan() throws Exception {
        String host = trimSlash(require(getHost().getOrNull(), "dgviz.host"));
        long repositoryId = require(getRepositoryId().getOrNull(), "dgviz.repositoryId");
        String token = require(getToken().getOrNull(), "dgviz.token");
        String configurationName = getConfigurationName().getOrElse("runtimeClasspath");

        Configuration configuration = getProject().getConfigurations().findByName(configurationName);
        if (configuration == null) {
            throw new IllegalStateException("Configuration not found: " + configurationName
                    + ". Apply Java/Java-library plugin or set dgviz.configurationName");
        }

        ResolvedComponentResult root = configuration.getIncoming().getResolutionResult().getRoot();
        TreeNode tree = buildTree(root, configurationName, new LinkedHashSet<>());
        String projectName = getProject().getName();
        String sbomJson = buildCycloneDx(projectName, tree);
        String payload = buildPayload(projectName, tree, sbomJson, getCreateIssues().get());

        String url = host + "/api/v1/repositories/" + repositoryId + "/scan";
        getLogger().lifecycle("DGViz: POST {}", url);

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-DGViz-Token", token)
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("DGViz scan failed HTTP " + response.statusCode() + ": " + response.body());
        }

        String body = response.body();
        var outDir = getOutputDir().get().getAsFile().toPath();
        Files.createDirectories(outDir);
        Files.writeString(outDir.resolve("report.json"), body, StandardCharsets.UTF_8);
        String markdown = extractJsonString(body, "markdown");
        if (markdown == null || markdown.isBlank()) {
            markdown = "# DGViz report\n\nSee report.json\n";
        }
        Files.writeString(outDir.resolve("report.md"), markdown, StandardCharsets.UTF_8);
        getLogger().lifecycle("DGViz report written to {}", outDir);

        String status = extractJsonString(body, "status");
        if (Boolean.TRUE.equals(getFailOnIssues().get()) && "FAILED".equalsIgnoreCase(status)) {
            throw new IllegalStateException("DGViz found dependency issues — see " + outDir.resolve("report.md"));
        }
    }

    private TreeNode buildTree(
            ResolvedComponentResult component,
            String configuration,
            Set<String> visited
    ) {
        boolean projectRoot = !(component.getId() instanceof ModuleComponentIdentifier);
        String id = component.getId().getDisplayName();
        if (!visited.add(id)) {
            return leafFrom(component, configuration, false);
        }
        String group = "";
        String name = "";
        String version = "";
        if (component.getId() instanceof ModuleComponentIdentifier module) {
            group = module.getGroup();
            name = module.getModule();
            version = module.getVersion();
        } else {
            name = getProject().getName();
            group = String.valueOf(getProject().getGroup());
            version = String.valueOf(getProject().getVersion());
        }
        List<TreeNode> children = new ArrayList<>();
        for (DependencyResult dep : component.getDependencies()) {
            if (dep instanceof ResolvedDependencyResult resolved) {
                TreeNode child = buildTree(resolved.getSelected(), configuration, visited);
                // direct deps are those attached to the project root component
                children.add(new TreeNode(
                        child.group, child.name, child.version, child.configuration,
                        projectRoot, child.dependencies));
            }
        }
        return new TreeNode(group, name, version, configuration, false, children);
    }

    private TreeNode leafFrom(ResolvedComponentResult component, String configuration, boolean direct) {
        if (component.getId() instanceof ModuleComponentIdentifier module) {
            return new TreeNode(module.getGroup(), module.getModule(), module.getVersion(),
                    configuration, direct, List.of());
        }
        return new TreeNode("", getProject().getName(), "", configuration, direct, List.of());
    }

    private String buildPayload(String projectName, TreeNode tree, String sbomJson, boolean createIssues) {
        String treeJson = tree.toJson();
        return "{"
                + "\"projectName\":" + jsonString(projectName) + ","
                + "\"buildSystem\":\"gradle\","
                + "\"createIssues\":" + createIssues + ","
                + "\"dependencyTree\":" + treeJson + ","
                + "\"sbom\":" + sbomJson
                + "}";
    }

    private String buildCycloneDx(String projectName, TreeNode tree) {
        List<TreeNode> flat = new ArrayList<>();
        flatten(tree, flat);
        StringBuilder components = new StringBuilder("[");
        boolean first = true;
        Map<String, String> bomRefs = new LinkedHashMap<>();
        for (TreeNode n : flat) {
            if (n.group == null || n.group.isBlank() || n.name == null || n.name.isBlank()) {
                continue;
            }
            String bomRef = "pkg:maven/" + n.group + "/" + n.name + "@" + n.version;
            if (bomRefs.containsKey(bomRef)) {
                continue;
            }
            bomRefs.put(bomRef, bomRef);
            if (!first) {
                components.append(',');
            }
            first = false;
            components.append('{')
                    .append("\"type\":\"library\",")
                    .append("\"bom-ref\":").append(jsonString(bomRef)).append(',')
                    .append("\"group\":").append(jsonString(n.group)).append(',')
                    .append("\"name\":").append(jsonString(n.name)).append(',')
                    .append("\"version\":").append(jsonString(n.version == null ? "" : n.version)).append(',')
                    .append("\"purl\":").append(jsonString(bomRef))
                    .append('}');
        }
        components.append(']');
        return "{"
                + "\"bomFormat\":\"CycloneDX\","
                + "\"specVersion\":\"1.5\","
                + "\"metadata\":{\"component\":{\"type\":\"application\",\"name\":" + jsonString(projectName) + "}},"
                + "\"components\":" + components
                + "}";
    }

    private void flatten(TreeNode node, List<TreeNode> out) {
        out.add(node);
        for (TreeNode child : node.dependencies) {
            flatten(child, out);
        }
    }

    private static String extractJsonString(String json, String field) {
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) {
            return null;
        }
        int colon = json.indexOf(':', idx + key.length());
        if (colon < 0) {
            return null;
        }
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        if (i >= json.length()) {
            return null;
        }
        if (json.charAt(i) == '"') {
            StringBuilder sb = new StringBuilder();
            i++;
            while (i < json.length()) {
                char c = json.charAt(i++);
                if (c == '\\' && i < json.length()) {
                    char n = json.charAt(i++);
                    switch (n) {
                        case 'n' -> sb.append('\n');
                        case 't' -> sb.append('\t');
                        case 'r' -> sb.append('\r');
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        default -> sb.append(n);
                    }
                } else if (c == '"') {
                    break;
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
        int end = i;
        while (end < json.length() && ",}]".indexOf(json.charAt(end)) < 0) {
            end++;
        }
        return json.substring(i, end).trim();
    }

    private static String trimSlash(String host) {
        return host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
    }

    private static <T> T require(T value, String name) {
        if (value == null || (value instanceof String s && s.isBlank())) {
            throw new IllegalStateException("Missing required property: " + name);
        }
        return value;
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }

    private record TreeNode(
            String group,
            String name,
            String version,
            String configuration,
            boolean direct,
            List<TreeNode> dependencies
    ) {
        String toJson() {
            StringBuilder deps = new StringBuilder("[");
            for (int i = 0; i < dependencies.size(); i++) {
                if (i > 0) {
                    deps.append(',');
                }
                deps.append(dependencies.get(i).toJson());
            }
            deps.append(']');
            return "{"
                    + "\"group\":" + jsonString(group) + ","
                    + "\"name\":" + jsonString(name) + ","
                    + "\"version\":" + jsonString(version == null ? "" : version) + ","
                    + "\"configuration\":" + jsonString(configuration) + ","
                    + "\"direct\":" + direct + ","
                    + "\"dependencies\":" + deps
                    + "}";
        }
    }
}
