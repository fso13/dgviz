package io.github.dgviz;

import io.github.dgviz.analysis.AnalysisEngine;
import io.github.dgviz.export.ReportService;
import io.github.dgviz.model.AnalysisResult;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Public embedding API for using DGViz as a library.
 */
public final class Dgviz {

    private Dgviz() {
    }

    public static AnalysisResult analyze(Path projectRoot) {
        return AnalysisEngine.createDefault(0).analyze(projectRoot);
    }

    public static AnalysisResult analyze(Path projectRoot, double failOnCvss) {
        return AnalysisEngine.createDefault(failOnCvss).analyze(projectRoot);
    }

    public static void exportReports(AnalysisResult result, Path outputDir) throws IOException {
        new ReportService().exportAll(result, outputDir);
    }
}
