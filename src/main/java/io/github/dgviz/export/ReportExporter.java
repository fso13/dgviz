package io.github.dgviz.export;

import io.github.dgviz.model.AnalysisResult;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Exports an analysis result to a concrete format.
 */
public interface ReportExporter {

    void export(AnalysisResult result, Path outputFile) throws IOException;

    String formatName();
}
