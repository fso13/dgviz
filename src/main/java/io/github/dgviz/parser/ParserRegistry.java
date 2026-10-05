package io.github.dgviz.parser;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Discovers {@link BuildSystemParser} implementations via ServiceLoader and classpath.
 */
public final class ParserRegistry {

    private final List<BuildSystemParser> parsers;

    public ParserRegistry(List<BuildSystemParser> parsers) {
        this.parsers = List.copyOf(parsers);
    }

    public static ParserRegistry withDefaults() {
        List<BuildSystemParser> discovered = new ArrayList<>();
        ServiceLoader.load(BuildSystemParser.class).forEach(discovered::add);
        if (discovered.isEmpty()) {
            discovered.add(new io.github.dgviz.parser.maven.MavenPomParser());
            discovered.add(new io.github.dgviz.parser.gradle.GradleBuildParser());
        }
        return new ParserRegistry(discovered);
    }

    public BuildSystemParser resolve(Path projectRoot) {
        return parsers.stream()
                .filter(p -> p.supports(projectRoot))
                .findFirst()
                .orElseThrow(() -> new UnsupportedBuildSystemException(
                        "No parser supports project at " + projectRoot
                                + ". Expected pom.xml or build.gradle(.kts)."));
    }

    public List<BuildSystemParser> parsers() {
        return parsers;
    }
}
