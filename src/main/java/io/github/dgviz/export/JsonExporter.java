package io.github.dgviz.export;

import io.github.dgviz.model.AnalysisResult;
import io.github.dgviz.model.DependencyEdge;
import io.github.dgviz.model.DependencyNode;
import io.github.dgviz.model.Issue;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON report exporter.
 */
public final class JsonExporter implements ReportExporter {

    private final ObjectMapper mapper;

    public JsonExporter() {
        this.mapper = JsonMapper.builder()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .build();
    }

    @Override
    public String formatName() {
        return "json";
    }

    @Override
    public void export(AnalysisResult result, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent() == null ? Path.of(".") : outputFile.getParent());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectName", result.projectName());
        payload.put("sourcePath", result.sourcePath());
        payload.put("analyzedAt", result.analyzedAt().toString());
        payload.put("summary", Map.of(
                "nodes", result.graph().size(),
                "edges", result.graph().edges().size(),
                "conflicts", result.conflictCount(),
                "duplicates", result.duplicateCount(),
                "vulnerabilities", result.vulnerabilityCount()
        ));
        payload.put("nodes", result.graph().nodes().stream().map(this::nodeToMap).toList());
        payload.put("edges", result.graph().edges().stream().map(this::edgeToMap).toList());
        payload.put("issues", result.issues().stream().map(this::issueToMap).toList());
        mapper.writeValue(outputFile.toFile(), payload);
    }

    private Map<String, Object> nodeToMap(DependencyNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", node.id());
        map.put("groupId", node.coordinate().groupId());
        map.put("artifactId", node.coordinate().artifactId());
        map.put("version", node.coordinate().version());
        map.put("scope", node.scope().name());
        map.put("origin", node.origin().name());
        map.put("modulePath", node.modulePath());
        return map;
    }

    private Map<String, Object> edgeToMap(DependencyEdge edge) {
        return Map.of("from", edge.from().id(), "to", edge.to().id());
    }

    private Map<String, Object> issueToMap(Issue issue) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", issue.type().name());
        map.put("severity", issue.severity().name());
        map.put("title", issue.title());
        map.put("description", issue.description());
        map.put("recommendation", issue.recommendation());
        map.put("artifactKeys", issue.artifactKeys());
        map.put("cve", issue.cve());
        map.put("cvss", issue.cvss());
        map.put("fixedVersion", issue.fixedVersion());
        return map;
    }

    public String toJsonString(AnalysisResult result) throws IOException {
        Path temp = Files.createTempFile("dgviz-", ".json");
        try {
            export(result, temp);
            return Files.readString(temp);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
