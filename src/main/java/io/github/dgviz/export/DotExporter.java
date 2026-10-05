package io.github.dgviz.export;

import io.github.dgviz.model.AnalysisResult;
import io.github.dgviz.model.DependencyEdge;
import io.github.dgviz.model.DependencyNode;
import io.github.dgviz.model.Issue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Graphviz DOT exporter with issue-based coloring.
 */
public final class DotExporter implements ReportExporter {

    @Override
    public String formatName() {
        return "dot";
    }

    @Override
    public void export(AnalysisResult result, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent() == null ? Path.of(".") : outputFile.getParent());
        Map<String, String> colors = colorMap(result);
        StringBuilder sb = new StringBuilder();
        sb.append("digraph dependencies {\n");
        sb.append("  rankdir=LR;\n");
        sb.append("  node [shape=box, style=filled, fontname=\"Helvetica\"];\n");

        Map<String, String> ids = new HashMap<>();
        int i = 0;
        for (DependencyNode node : result.graph().nodes()) {
            String id = "n" + (i++);
            ids.put(node.id(), id);
            String label = escape(node.coordinate().gavKey());
            String color = colors.getOrDefault(node.coordinate().gaKey(),
                    colors.getOrDefault(node.coordinate().gavKey(), "lightgrey"));
            sb.append("  ").append(id).append(" [label=\"").append(label)
                    .append("\", fillcolor=\"").append(color).append("\"];\n");
        }
        for (DependencyEdge edge : result.graph().edges()) {
            sb.append("  ").append(ids.get(edge.from().id())).append(" -> ")
                    .append(ids.get(edge.to().id())).append(";\n");
        }
        sb.append("}\n");
        Files.writeString(outputFile, sb.toString());
    }

    static Map<String, String> colorMap(AnalysisResult result) {
        Map<String, String> colors = new HashMap<>();
        for (Issue issue : result.issues()) {
            String color = switch (issue.type()) {
                case VERSION_CONFLICT -> "red";
                case DUPLICATE -> "yellow";
                case VULNERABILITY -> "orange";
            };
            for (String key : issue.artifactKeys()) {
                colors.put(key, color);
                int lastColon = key.lastIndexOf(':');
                if (lastColon > 0 && key.indexOf(':') != lastColon) {
                    colors.putIfAbsent(key.substring(0, lastColon), color);
                }
            }
        }
        return colors;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
