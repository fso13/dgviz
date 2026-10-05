package io.github.dgviz.export;

import io.github.dgviz.analysis.store.AnalysisDependency;
import io.github.dgviz.analysis.store.AnalysisRun;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CycloneDX 1.5 JSON from stored SBOM or from persisted dependency rows.
 */
@Component
public class CycloneDxSbomExporter {

    public String export(AnalysisRun run, List<AnalysisDependency> dependencies) {
        if (run.getSbomJson() != null && !run.getSbomJson().isBlank()) {
            return run.getSbomJson();
        }
        return buildFromDependencies(run.getProjectName(), dependencies);
    }

    public String buildFromDependencies(String projectName, List<AnalysisDependency> dependencies) {
        String appName = projectName == null || projectName.isBlank() ? "application" : projectName;
        StringBuilder components = new StringBuilder("[");
        boolean first = true;
        Map<String, String> bomRefs = new LinkedHashMap<>();
        for (AnalysisDependency d : dependencies) {
            if (d.getGroupId() == null || d.getGroupId().isBlank()
                    || d.getArtifactId() == null || d.getArtifactId().isBlank()) {
                continue;
            }
            String version = d.getVersion() == null ? "" : d.getVersion();
            String bomRef = "pkg:maven/" + d.getGroupId() + "/" + d.getArtifactId() + "@" + version;
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
                    .append("\"group\":").append(jsonString(d.getGroupId())).append(',')
                    .append("\"name\":").append(jsonString(d.getArtifactId())).append(',')
                    .append("\"version\":").append(jsonString(version)).append(',')
                    .append("\"purl\":").append(jsonString(bomRef))
                    .append('}');
        }
        components.append(']');
        return "{"
                + "\"bomFormat\":\"CycloneDX\","
                + "\"specVersion\":\"1.5\","
                + "\"metadata\":{\"component\":{\"type\":\"application\",\"name\":" + jsonString(appName) + "}},"
                + "\"components\":" + components
                + "}";
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
}
