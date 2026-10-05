package io.github.dgviz.parser;

import io.github.dgviz.model.DependencyGraph;

import java.nio.file.Path;

/**
 * SPI for build-system dependency extraction.
 */
public interface BuildSystemParser {

    /**
     * @return true if this parser can handle the given project root
     */
    boolean supports(Path projectRoot);

    /**
     * Parse declared (and where possible transitive) dependencies into a graph.
     */
    DependencyGraph parse(Path projectRoot);

    /**
     * Short human-readable name of the build system.
     */
    String name();
}
