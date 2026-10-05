package io.github.dgviz.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Directed dependency graph (expected to be acyclic for resolved trees).
 */
public final class DependencyGraph {

    private final String rootName;
    private final Map<String, DependencyNode> nodes = new LinkedHashMap<>();
    private final Set<DependencyEdge> edges = new LinkedHashSet<>();
    private final List<DependencyNode> roots = new ArrayList<>();

    public DependencyGraph(String rootName) {
        this.rootName = Objects.requireNonNull(rootName, "rootName");
    }

    public String rootName() {
        return rootName;
    }

    public void addRoot(DependencyNode node) {
        addNode(node);
        roots.add(node);
    }

    public void addNode(DependencyNode node) {
        nodes.putIfAbsent(node.id(), node);
    }

    public void addEdge(DependencyNode from, DependencyNode to) {
        addNode(from);
        addNode(to);
        edges.add(new DependencyEdge(nodes.get(from.id()), nodes.get(to.id())));
    }

    public Collection<DependencyNode> nodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    public Set<DependencyEdge> edges() {
        return Collections.unmodifiableSet(edges);
    }

    public List<DependencyNode> roots() {
        return Collections.unmodifiableList(roots);
    }

    public Optional<DependencyNode> findByGav(String gav) {
        return nodes.values().stream()
                .filter(n -> n.coordinate().gavKey().equals(gav))
                .findFirst();
    }

    public List<DependencyNode> findByGa(String groupId, String artifactId) {
        return nodes.values().stream()
                .filter(n -> n.coordinate().groupId().equals(groupId)
                        && n.coordinate().artifactId().equals(artifactId))
                .collect(Collectors.toList());
    }

    public int size() {
        return nodes.size();
    }
}
