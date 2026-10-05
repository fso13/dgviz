package io.github.dgviz.export;

import io.github.dgviz.model.AnalysisResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes all standard report formats into an output directory.
 */
public final class ReportService {

    private final List<ReportExporter> exporters;

    public ReportService() {
        this(List.of(
                new JsonExporter(),
                new DotExporter(),
                new PlantUmlExporter(),
                new HtmlReportExporter()
        ));
    }

    public ReportService(List<ReportExporter> exporters) {
        this.exporters = List.copyOf(exporters);
    }

    public void exportAll(AnalysisResult result, Path outputDir) throws IOException {
        for (ReportExporter exporter : exporters) {
            Path file = switch (exporter.formatName()) {
                case "json" -> outputDir.resolve("dependency-graph.json");
                case "dot" -> outputDir.resolve("dependency-graph.dot");
                case "plantuml" -> outputDir.resolve("dependency-graph.puml");
                case "html" -> outputDir.resolve("dependency-graph.html");
                default -> outputDir.resolve("dependency-graph." + exporter.formatName());
            };
            exporter.export(result, file);
        }
    }
}
