package io.github.dgviz.export;

import io.github.dgviz.model.AnalysisResult;
import io.github.dgviz.model.DependencyEdge;
import io.github.dgviz.model.DependencyNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * PlantUML component diagram exporter.
 */
public final class PlantUmlExporter implements ReportExporter {

    @Override
    public String formatName() {
        return "plantuml";
    }

    @Override
    public void export(AnalysisResult result, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent() == null ? Path.of(".") : outputFile.getParent());
        Map<String, String> colors = DotExporter.colorMap(result);
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("title Dependency Graph: ").append(result.projectName()).append("\n");
        sb.append("left to right direction\n");

        Map<String, String> aliases = new HashMap<>();
        int i = 0;
        for (DependencyNode node : result.graph().nodes()) {
            String alias = "C" + (i++);
            aliases.put(node.id(), alias);
            String color = colors.getOrDefault(node.coordinate().gaKey(),
                    colors.getOrDefault(node.coordinate().gavKey(), "#DDDDDD"));
            String plantColor = switch (color) {
                case "red" -> "#FF6666";
                case "yellow" -> "#FFEE66";
                case "orange" -> "#FFAA55";
                default -> "#DDDDDD";
            };
            sb.append("component \"").append(escape(node.coordinate().gavKey())).append("\" as ")
                    .append(alias).append(" ").append(plantColor).append("\n");
        }
        for (DependencyEdge edge : result.graph().edges()) {
            sb.append(aliases.get(edge.from().id())).append(" --> ")
                    .append(aliases.get(edge.to().id())).append("\n");
        }
        sb.append("@enduml\n");
        Files.writeString(outputFile, sb.toString());
    }

    private static String escape(String value) {
        return value.replace("\"", "'");
    }
}
