package io.github.dgviz.parser.gradle;

import io.github.dgviz.model.ArtifactCoordinate;
import io.github.dgviz.model.DependencyGraph;
import io.github.dgviz.model.DependencyNode;
import io.github.dgviz.model.DependencyOrigin;
import io.github.dgviz.model.DependencyScope;
import io.github.dgviz.parser.BuildSystemParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Lightweight Gradle parser for declared dependencies in {@code build.gradle},
 * {@code build.gradle.kts}, and version catalogs ({@code libs.versions.toml}).
 * Does not invoke the Gradle Tooling API (no full transitive resolution).
 */
public final class GradleBuildParser implements BuildSystemParser {

    private static final Logger log = LoggerFactory.getLogger(GradleBuildParser.class);

    private static final Pattern STRING_DEP = Pattern.compile(
            "(implementation|api|compileOnly|runtimeOnly|testImplementation|compile|runtime|testCompile)\\s*[(]?\\s*[\"']([^\"']+)[\"']\\s*[)]?"
    );

    private static final Pattern MAP_DEP = Pattern.compile(
            "(implementation|api|compileOnly|runtimeOnly|testImplementation)\\s*[(]?\\s*"
                    + "(?:group\\s*[:=]\\s*[\"']([^\"']+)[\"']\\s*,\\s*name\\s*[:=]\\s*[\"']([^\"']+)[\"']"
                    + "(?:\\s*,\\s*version\\s*[:=]\\s*[\"']([^\"']+)[\"'])?)"
    );

    private static final Pattern KTS_DEP = Pattern.compile(
            "(implementation|api|compileOnly|runtimeOnly|testImplementation)\\s*\\(\\s*[\"']([^\"']+)[\"']\\s*\\)"
    );

    private static final Pattern CATALOG_DEP = Pattern.compile(
            "(implementation|api|compileOnly|runtimeOnly|testImplementation)\\s*[(]?\\s*libs\\.([a-zA-Z0-9.]+)"
    );

    private static final Pattern VERSION_CATALOG_LIB = Pattern.compile(
            "^\\s*([a-zA-Z0-9-]+)\\s*=\\s*\\{\\s*module\\s*=\\s*\"([^\"]+)\"\\s*,\\s*version\\s*(?:\\.ref)?\\s*=\\s*\"([^\"]+)\"\\s*\\}",
            Pattern.MULTILINE
    );

    private static final Pattern VERSION_CATALOG_VERSION = Pattern.compile(
            "^\\s*([a-zA-Z0-9-]+)\\s*=\\s*\"([^\"]+)\"",
            Pattern.MULTILINE
    );

    @Override
    public boolean supports(Path projectRoot) {
        return Files.isRegularFile(projectRoot.resolve("build.gradle"))
                || Files.isRegularFile(projectRoot.resolve("build.gradle.kts"))
                || Files.isRegularFile(projectRoot.resolve("settings.gradle"))
                || Files.isRegularFile(projectRoot.resolve("settings.gradle.kts"));
    }

    @Override
    public String name() {
        return "Gradle";
    }

    @Override
    public DependencyGraph parse(Path projectRoot) {
        String rootName = projectRoot.getFileName() == null
                ? "gradle-project"
                : projectRoot.getFileName().toString();
        DependencyGraph graph = new DependencyGraph(rootName);
        DependencyNode root = new DependencyNode(
                new ArtifactCoordinate("project", rootName, "local"),
                DependencyScope.IMPLEMENTATION,
                DependencyOrigin.PROJECT,
                ""
        );
        graph.addRoot(root);

        Map<String, ArtifactCoordinate> catalog = loadVersionCatalog(projectRoot);
        Set<Path> buildFiles = findBuildFiles(projectRoot);

        for (Path buildFile : buildFiles) {
            String modulePath = relativize(projectRoot, buildFile.getParent());
            DependencyNode moduleNode = root;
            if (!modulePath.isBlank()) {
                moduleNode = new DependencyNode(
                        new ArtifactCoordinate("project", modulePath.replace('/', '.'), "local"),
                        DependencyScope.IMPLEMENTATION,
                        DependencyOrigin.PROJECT,
                        modulePath
                );
                graph.addEdge(root, moduleNode);
            }
            parseBuildFile(graph, moduleNode, buildFile, catalog);
        }

        log.info("Parsed Gradle project '{}' with {} dependency nodes", rootName, graph.size());
        return graph;
    }

    private void parseBuildFile(
            DependencyGraph graph,
            DependencyNode parent,
            Path buildFile,
            Map<String, ArtifactCoordinate> catalog
    ) {
        String content;
        try {
            content = Files.readString(buildFile);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + buildFile, e);
        }

        matchAndAdd(graph, parent, STRING_DEP, content, catalog);
        matchAndAdd(graph, parent, KTS_DEP, content, catalog);

        Matcher mapMatcher = MAP_DEP.matcher(content);
        while (mapMatcher.find()) {
            DependencyScope scope = DependencyScope.fromGradle(mapMatcher.group(1));
            String version = mapMatcher.group(4) == null ? "" : mapMatcher.group(4);
            addDep(graph, parent, mapMatcher.group(2), mapMatcher.group(3), version, scope);
        }

        Matcher catalogMatcher = CATALOG_DEP.matcher(content);
        while (catalogMatcher.find()) {
            DependencyScope scope = DependencyScope.fromGradle(catalogMatcher.group(1));
            String alias = catalogMatcher.group(2).replace('.', '-');
            ArtifactCoordinate coordinate = catalog.get(alias);
            if (coordinate == null) {
                // libs.spring.boot -> spring-boot
                coordinate = catalog.get(catalogMatcher.group(2).replace('.', '-'));
            }
            if (coordinate != null) {
                graph.addEdge(parent, new DependencyNode(
                        coordinate, scope, DependencyOrigin.DIRECT, parent.modulePath()));
            }
        }
    }

    private void matchAndAdd(
            DependencyGraph graph,
            DependencyNode parent,
            Pattern pattern,
            String content,
            Map<String, ArtifactCoordinate> catalog
    ) {
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            DependencyScope scope = DependencyScope.fromGradle(matcher.group(1));
            String notation = matcher.group(2);
            if (notation.startsWith("project(") || notation.startsWith(":")) {
                continue;
            }
            String[] parts = notation.split(":");
            if (parts.length >= 2) {
                String version = parts.length >= 3 ? parts[2] : "";
                addDep(graph, parent, parts[0], parts[1], version, scope);
            }
        }
    }

    private void addDep(
            DependencyGraph graph,
            DependencyNode parent,
            String group,
            String artifact,
            String version,
            DependencyScope scope
    ) {
        DependencyNode node = new DependencyNode(
                new ArtifactCoordinate(group, artifact, version),
                scope,
                DependencyOrigin.DIRECT,
                parent.modulePath()
        );
        graph.addEdge(parent, node);
    }

    private Map<String, ArtifactCoordinate> loadVersionCatalog(Path projectRoot) {
        Path catalogPath = projectRoot.resolve("gradle/libs.versions.toml");
        Map<String, ArtifactCoordinate> result = new HashMap<>();
        if (!Files.isRegularFile(catalogPath)) {
            return result;
        }
        try {
            String content = Files.readString(catalogPath);
            Map<String, String> versions = new HashMap<>();
            boolean inVersions = false;
            boolean inLibraries = false;
            for (String rawLine : content.split("\\R")) {
                String line = rawLine.trim();
                if (line.startsWith("[") && line.endsWith("]")) {
                    inVersions = line.equals("[versions]");
                    inLibraries = line.equals("[libraries]");
                    continue;
                }
                if (inVersions) {
                    Matcher vm = VERSION_CATALOG_VERSION.matcher(rawLine);
                    if (vm.find()) {
                        versions.put(vm.group(1), vm.group(2));
                    }
                }
                if (inLibraries) {
                    Matcher lm = VERSION_CATALOG_LIB.matcher(rawLine);
                    if (lm.find()) {
                        String alias = lm.group(1);
                        String module = lm.group(2);
                        String versionOrRef = lm.group(3);
                        String version = versions.getOrDefault(versionOrRef, versionOrRef);
                        String[] ga = module.split(":");
                        if (ga.length == 2) {
                            result.put(alias, new ArtifactCoordinate(ga[0], ga[1], version));
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read version catalog {}", catalogPath, e);
        }
        return result;
    }

    private Set<Path> findBuildFiles(Path projectRoot) {
        Set<Path> files = new LinkedHashSet<>();
        addIfExists(files, projectRoot.resolve("build.gradle"));
        addIfExists(files, projectRoot.resolve("build.gradle.kts"));
        try (Stream<Path> walk = Files.walk(projectRoot, 4)) {
            walk.filter(p -> {
                        String name = p.getFileName().toString();
                        return name.equals("build.gradle") || name.equals("build.gradle.kts");
                    })
                    .filter(p -> !p.toString().contains("/build/"))
                    .forEach(files::add);
        } catch (IOException e) {
            log.warn("Failed to walk project tree {}", projectRoot, e);
        }
        return files;
    }

    private void addIfExists(Set<Path> files, Path path) {
        if (Files.isRegularFile(path)) {
            files.add(path);
        }
    }

    private String relativize(Path root, Path dir) {
        if (dir == null || root.equals(dir)) {
            return "";
        }
        try {
            return root.relativize(dir).toString().replace('\\', '/');
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }
}
