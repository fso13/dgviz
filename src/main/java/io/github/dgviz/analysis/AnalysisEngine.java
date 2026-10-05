package io.github.dgviz.analysis;

import io.github.dgviz.model.AnalysisResult;
import io.github.dgviz.model.DependencyGraph;
import io.github.dgviz.model.Issue;
import io.github.dgviz.parser.BuildSystemParser;
import io.github.dgviz.parser.ParserRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates parsing and issue detection.
 */
public final class AnalysisEngine {

    private static final Logger log = LoggerFactory.getLogger(AnalysisEngine.class);

    private final ParserRegistry parserRegistry;
    private final List<IssueDetector> detectors;

    public AnalysisEngine(ParserRegistry parserRegistry, List<IssueDetector> detectors) {
        this.parserRegistry = parserRegistry;
        this.detectors = List.copyOf(detectors);
    }

    public static AnalysisEngine createDefault(double failOnCvss) {
        return new AnalysisEngine(
                ParserRegistry.withDefaults(),
                List.of(
                        new ConflictDetector(),
                        new DuplicateDetector(),
                        new VulnerabilityDetector(failOnCvss)
                )
        );
    }

    public AnalysisResult analyze(Path projectRoot) {
        BuildSystemParser parser = parserRegistry.resolve(projectRoot);
        log.info("Using {} parser for {}", parser.name(), projectRoot);
        DependencyGraph graph = parser.parse(projectRoot);

        List<Issue> issues = new ArrayList<>();
        for (IssueDetector detector : detectors) {
            issues.addAll(detector.detect(graph));
        }

        return new AnalysisResult(
                graph.rootName(),
                projectRoot.toAbsolutePath().normalize().toString(),
                Instant.now(),
                graph,
                issues
        );
    }

    public AnalysisResult analyzeGraph(String projectName, String source, DependencyGraph graph) {
        List<Issue> issues = new ArrayList<>();
        for (IssueDetector detector : detectors) {
            issues.addAll(detector.detect(graph));
        }
        return new AnalysisResult(projectName, source, Instant.now(), graph, issues);
    }
}
