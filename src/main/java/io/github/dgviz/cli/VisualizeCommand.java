package io.github.dgviz.cli;

import io.github.dgviz.analysis.AnalysisEngine;
import io.github.dgviz.config.DgvizConfig;
import io.github.dgviz.model.AnalysisResult;
import io.github.dgviz.ui.VisualizationServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "visualize", description = "Open interactive dependency graph in a local web UI")
public class VisualizeCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(VisualizeCommand.class);

    @Parameters(index = "0", arity = "0..1", description = "Local project path (default: .)")
    Path projectPath;

    @Option(names = "--config", description = "Path to dgviz.yml / dgviz.json")
    Path configPath;

    @Option(names = {"-p", "--port"}, description = "HTTP port")
    Integer port;

    @Override
    public Integer call() {
        try {
            DgvizConfig config = DgvizConfig.load(configPath);
            Path root = projectPath != null ? projectPath : Path.of(config.getProjectPath());
            AnalysisResult result = AnalysisEngine.createDefault(config.getFailOnCvss()).analyze(root);
            int listenPort = port != null ? port : config.getServerPort();
            new VisualizationServer(listenPort, result).startBlocking();
            return ExitCodes.SUCCESS;
        } catch (Exception e) {
            log.error("Visualize failed", e);
            System.err.println("Error: " + e.getMessage());
            return ExitCodes.EXECUTION_ERROR;
        }
    }
}
