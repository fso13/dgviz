package io.github.dgviz.cli;

import io.github.dgviz.analysis.AnalysisEngine;
import io.github.dgviz.config.DgvizConfig;
import io.github.dgviz.export.DotExporter;
import io.github.dgviz.export.HtmlReportExporter;
import io.github.dgviz.export.JsonExporter;
import io.github.dgviz.export.PlantUmlExporter;
import io.github.dgviz.export.ReportExporter;
import io.github.dgviz.model.AnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Callable;

@Command(name = "report", description = "Generate a dependency report in a chosen format")
public class ReportCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(ReportCommand.class);

    @Parameters(index = "0", arity = "0..1", description = "Local project path (default: .)")
    Path projectPath;

    @Option(names = "--config", description = "Path to dgviz.yml / dgviz.json")
    Path configPath;

    @Option(names = {"-f", "--format"}, description = "json|html|dot|plantuml", defaultValue = "html")
    String format;

    @Option(names = {"-o", "--output"}, description = "Output file path", required = true)
    Path output;

    @Override
    public Integer call() {
        try {
            DgvizConfig config = DgvizConfig.load(configPath);
            Path root = projectPath != null ? projectPath : Path.of(config.getProjectPath());
            AnalysisResult result = AnalysisEngine.createDefault(config.getFailOnCvss()).analyze(root);
            ReportExporter exporter = switch (format.toLowerCase(Locale.ROOT)) {
                case "json" -> new JsonExporter();
                case "dot" -> new DotExporter();
                case "plantuml", "puml" -> new PlantUmlExporter();
                case "html" -> new HtmlReportExporter();
                default -> throw new IllegalArgumentException("Unsupported format: " + format);
            };
            exporter.export(result, output);
            System.out.println("Report written to " + output.toAbsolutePath());
            return ExitCodes.SUCCESS;
        } catch (Exception e) {
            log.error("Report failed", e);
            System.err.println("Error: " + e.getMessage());
            return ExitCodes.EXECUTION_ERROR;
        }
    }
}
