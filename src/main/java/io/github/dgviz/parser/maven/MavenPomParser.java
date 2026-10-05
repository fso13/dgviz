package io.github.dgviz.parser.maven;

import io.github.dgviz.model.ArtifactCoordinate;
import io.github.dgviz.model.DependencyGraph;
import io.github.dgviz.model.DependencyNode;
import io.github.dgviz.model.DependencyOrigin;
import io.github.dgviz.model.DependencyScope;
import io.github.dgviz.parser.BuildSystemParser;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;

/**
 * Parses Maven {@code pom.xml} files including multi-module projects.
 * Extracts declared dependencies; parent/property interpolation is applied locally.
 */
public final class MavenPomParser implements BuildSystemParser {

    private static final Logger log = LoggerFactory.getLogger(MavenPomParser.class);

    @Override
    public boolean supports(Path projectRoot) {
        return Files.isRegularFile(projectRoot.resolve("pom.xml"));
    }

    @Override
    public String name() {
        return "Maven";
    }

    @Override
    public DependencyGraph parse(Path projectRoot) {
        Path rootPom = projectRoot.resolve("pom.xml");
        if (!Files.isRegularFile(rootPom)) {
            throw new IllegalArgumentException("pom.xml not found in " + projectRoot);
        }

        Model rootModel = readModel(rootPom);
        String rootName = coordinateOf(rootModel, Map.of()).gavKey();
        DependencyGraph graph = new DependencyGraph(rootName);

        Map<String, String> rootProps = collectProperties(rootModel, Map.of());
        DependencyNode rootNode = projectNode(rootModel, rootProps, "");
        graph.addRoot(rootNode);

        Set<Path> visited = new HashSet<>();
        Queue<ModuleContext> queue = new ArrayDeque<>();
        queue.add(new ModuleContext(projectRoot, rootModel, rootProps, rootNode));

        while (!queue.isEmpty()) {
            ModuleContext ctx = queue.poll();
            Path pomPath = ctx.dir().resolve("pom.xml");
            if (!visited.add(pomPath.toAbsolutePath().normalize())) {
                continue;
            }

            parseDependencies(graph, ctx);

            List<String> modules = ctx.model().getModules();
            if (modules == null) {
                continue;
            }
            for (String module : modules) {
                Path moduleDir = ctx.dir().resolve(module).normalize();
                Path modulePom = moduleDir.resolve("pom.xml");
                if (!Files.isRegularFile(modulePom)) {
                    log.warn("Skipping missing module pom: {}", modulePom);
                    continue;
                }
                Model moduleModel = readModel(modulePom);
                Map<String, String> moduleProps = collectProperties(moduleModel, ctx.properties());
                applyParentCoords(moduleModel, ctx.model(), moduleProps);
                DependencyNode moduleNode = projectNode(moduleModel, moduleProps, relative(projectRoot, moduleDir));
                graph.addEdge(ctx.projectNode(), moduleNode);
                queue.add(new ModuleContext(moduleDir, moduleModel, moduleProps, moduleNode));
            }
        }

        log.info("Parsed Maven project '{}' with {} dependency nodes", rootName, graph.size());
        return graph;
    }

    private void parseDependencies(DependencyGraph graph, ModuleContext ctx) {
        List<Dependency> dependencies = ctx.model().getDependencies();
        if (dependencies == null) {
            return;
        }
        for (Dependency dep : dependencies) {
            String groupId = interpolate(dep.getGroupId(), ctx.properties());
            String artifactId = interpolate(dep.getArtifactId(), ctx.properties());
            String version = interpolate(firstNonBlank(dep.getVersion(), ctx.properties().get(artifactId + ".version")),
                    ctx.properties());
            if (isBlank(groupId) || isBlank(artifactId)) {
                continue;
            }
            if (isBlank(version)) {
                version = resolveManagedVersion(ctx.model(), groupId, artifactId, ctx.properties());
            }

            ArtifactCoordinate coordinate = new ArtifactCoordinate(groupId, artifactId, version == null ? "" : version);
            DependencyScope scope = DependencyScope.fromMaven(dep.getScope());
            DependencyNode depNode = new DependencyNode(
                    coordinate,
                    scope,
                    DependencyOrigin.DIRECT,
                    ctx.projectNode().modulePath()
            );
            graph.addEdge(ctx.projectNode(), depNode);
        }
    }

    private String resolveManagedVersion(
            Model model,
            String groupId,
            String artifactId,
            Map<String, String> props
    ) {
        if (model.getDependencyManagement() == null
                || model.getDependencyManagement().getDependencies() == null) {
            return "";
        }
        for (Dependency managed : model.getDependencyManagement().getDependencies()) {
            String mg = interpolate(managed.getGroupId(), props);
            String ma = interpolate(managed.getArtifactId(), props);
            if (groupId.equals(mg) && artifactId.equals(ma)) {
                return interpolate(managed.getVersion(), props);
            }
        }
        return "";
    }

    private DependencyNode projectNode(Model model, Map<String, String> props, String modulePath) {
        return new DependencyNode(
                coordinateOf(model, props),
                DependencyScope.COMPILE,
                DependencyOrigin.PROJECT,
                modulePath
        );
    }

    private ArtifactCoordinate coordinateOf(Model model, Map<String, String> props) {
        String groupId = firstNonBlank(model.getGroupId(),
                model.getParent() != null ? model.getParent().getGroupId() : null);
        String artifactId = model.getArtifactId();
        String version = firstNonBlank(model.getVersion(),
                model.getParent() != null ? model.getParent().getVersion() : null);
        return new ArtifactCoordinate(
                interpolate(groupId, props),
                interpolate(artifactId, props),
                interpolate(version, props)
        );
    }

    private void applyParentCoords(Model child, Model parent, Map<String, String> props) {
        if (child.getGroupId() == null && parent.getGroupId() != null) {
            child.setGroupId(parent.getGroupId());
        }
        if (child.getVersion() == null && parent.getVersion() != null) {
            child.setVersion(parent.getVersion());
        }
        Parent p = child.getParent();
        if (p != null) {
            props.putIfAbsent("project.parent.groupId", interpolate(p.getGroupId(), props));
            props.putIfAbsent("project.parent.artifactId", interpolate(p.getArtifactId(), props));
            props.putIfAbsent("project.parent.version", interpolate(p.getVersion(), props));
        }
    }

    private Map<String, String> collectProperties(Model model, Map<String, String> parentProps) {
        Map<String, String> props = new HashMap<>(parentProps);
        props.put("project.groupId", firstNonBlank(model.getGroupId(),
                model.getParent() != null ? model.getParent().getGroupId() : ""));
        props.put("project.artifactId", nullToEmpty(model.getArtifactId()));
        props.put("project.version", firstNonBlank(model.getVersion(),
                model.getParent() != null ? model.getParent().getVersion() : ""));
        props.put("groupId", props.get("project.groupId"));
        props.put("artifactId", props.get("project.artifactId"));
        props.put("version", props.get("project.version"));

        Properties modelProps = model.getProperties();
        if (modelProps != null) {
            modelProps.forEach((k, v) -> props.put(String.valueOf(k), String.valueOf(v)));
        }
        // Second pass for nested property references
        props.replaceAll((k, v) -> interpolate(v, props));
        return props;
    }

    private String interpolate(String value, Map<String, String> props) {
        if (value == null) {
            return "";
        }
        String result = value;
        for (int i = 0; i < 10 && result.contains("${"); i++) {
            String previous = result;
            for (Map.Entry<String, String> entry : props.entrySet()) {
                result = result.replace("${" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
            }
            if (result.equals(previous)) {
                break;
            }
        }
        return result;
    }

    private Model readModel(Path pomPath) {
        try (InputStream in = Files.newInputStream(pomPath)) {
            return new MavenXpp3Reader().read(in);
        } catch (IOException | XmlPullParserException e) {
            throw new IllegalStateException("Failed to read pom: " + pomPath, e);
        }
    }

    private String relative(Path root, Path moduleDir) {
        try {
            return root.relativize(moduleDir).toString().replace('\\', '/');
        } catch (IllegalArgumentException ex) {
            return moduleDir.toString();
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (!isBlank(a)) {
            return a;
        }
        return b;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record ModuleContext(
            Path dir,
            Model model,
            Map<String, String> properties,
            DependencyNode projectNode
    ) {
    }
}
