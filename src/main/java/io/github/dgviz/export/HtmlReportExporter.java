package io.github.dgviz.export;

import io.github.dgviz.model.AnalysisResult;
import io.github.dgviz.model.Issue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Self-contained HTML report with embedded D3 force-directed graph.
 */
public final class HtmlReportExporter implements ReportExporter {

    private final JsonExporter jsonExporter = new JsonExporter();

    @Override
    public String formatName() {
        return "html";
    }

    @Override
    public void export(AnalysisResult result, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent() == null ? Path.of(".") : outputFile.getParent());
        String json = jsonExporter.toJsonString(result);
        String html = TEMPLATE
                .replace("{{PROJECT}}", escapeHtml(result.projectName()))
                .replace("{{SUMMARY}}", summary(result))
                .replace("{{ISSUES}}", issuesHtml(result))
                .replace("{{GRAPH_JSON}}", json);
        Files.writeString(outputFile, html, StandardCharsets.UTF_8);
    }

    private String summary(AnalysisResult result) {
        return "Nodes: " + result.graph().size()
                + " · Conflicts: " + result.conflictCount()
                + " · Duplicates: " + result.duplicateCount()
                + " · Vulnerabilities: " + result.vulnerabilityCount();
    }

    private String issuesHtml(AnalysisResult result) {
        if (result.issues().isEmpty()) {
            return "<p class=\"ok\">No issues detected.</p>";
        }
        StringBuilder sb = new StringBuilder("<ul class=\"issues\">");
        for (Issue issue : result.issues()) {
            sb.append("<li class=\"").append(issue.type().name().toLowerCase()).append("\">")
                    .append("<strong>").append(escapeHtml(issue.title())).append("</strong>")
                    .append("<div>").append(escapeHtml(issue.description())).append("</div>")
                    .append("<em>").append(escapeHtml(issue.recommendation())).append("</em>")
                    .append("</li>");
        }
        sb.append("</ul>");
        return sb.toString();
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static final String TEMPLATE = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8"/>
              <meta name="viewport" content="width=device-width, initial-scale=1"/>
              <title>DGViz — {{PROJECT}}</title>
              <script src="https://cdn.jsdelivr.net/npm/d3@7"></script>
              <style>
                :root {
                  --bg: #0f1419;
                  --panel: #1a2332;
                  --text: #e7ecf3;
                  --muted: #9aa7b8;
                  --accent: #3d9cf0;
                  --conflict: #e25c5c;
                  --duplicate: #e0c35a;
                  --vuln: #e08a3c;
                }
                * { box-sizing: border-box; }
                body {
                  margin: 0;
                  font-family: "IBM Plex Sans", "Segoe UI", sans-serif;
                  background: radial-gradient(1200px 600px at 10% -10%, #1b2a40, var(--bg));
                  color: var(--text);
                  min-height: 100vh;
                }
                header {
                  padding: 1.25rem 1.5rem 0.5rem;
                }
                h1 { margin: 0; font-size: 1.6rem; letter-spacing: 0.02em; }
                .summary { color: var(--muted); margin-top: 0.35rem; }
                .layout {
                  display: grid;
                  grid-template-columns: 320px 1fr;
                  gap: 1rem;
                  padding: 1rem 1.5rem 1.5rem;
                  min-height: calc(100vh - 90px);
                }
                aside, .canvas-wrap {
                  background: color-mix(in srgb, var(--panel) 92%, black);
                  border: 1px solid #2a3a50;
                  border-radius: 12px;
                }
                aside { padding: 1rem; overflow: auto; }
                .canvas-wrap { position: relative; overflow: hidden; }
                #graph { width: 100%; height: 100%; min-height: 640px; }
                .controls {
                  position: absolute; top: 12px; left: 12px; display: flex; gap: 0.5rem; z-index: 2;
                }
                .controls input, .controls select, .controls button {
                  background: #0f1722; color: var(--text); border: 1px solid #334155;
                  border-radius: 8px; padding: 0.4rem 0.6rem;
                }
                .issues { list-style: none; padding: 0; margin: 0; }
                .issues li {
                  margin-bottom: 0.75rem; padding: 0.75rem; border-radius: 8px;
                  background: #121a26; border-left: 4px solid var(--accent);
                }
                .issues li.version_conflict { border-left-color: var(--conflict); }
                .issues li.duplicate { border-left-color: var(--duplicate); }
                .issues li.vulnerability { border-left-color: var(--vuln); }
                .ok { color: #7dcea0; }
                .legend { font-size: 0.85rem; color: var(--muted); margin-bottom: 1rem; }
                .swatch { display:inline-block; width:10px; height:10px; border-radius:2px; margin-right:4px; }
                @media (max-width: 900px) {
                  .layout { grid-template-columns: 1fr; }
                }
              </style>
            </head>
            <body>
              <header>
                <h1>DGViz · {{PROJECT}}</h1>
                <div class="summary">{{SUMMARY}}</div>
              </header>
              <div class="layout">
                <aside>
                  <div class="legend">
                    <div><span class="swatch" style="background:var(--conflict)"></span> conflict</div>
                    <div><span class="swatch" style="background:var(--duplicate)"></span> duplicate</div>
                    <div><span class="swatch" style="background:var(--vuln)"></span> vulnerability</div>
                  </div>
                  <h2>Issues</h2>
                  {{ISSUES}}
                </aside>
                <div class="canvas-wrap">
                  <div class="controls">
                    <input id="search" type="search" placeholder="Search artifact / CVE"/>
                    <select id="scopeFilter">
                      <option value="">All scopes</option>
                      <option>COMPILE</option>
                      <option>RUNTIME</option>
                      <option>TEST</option>
                      <option>IMPLEMENTATION</option>
                      <option>API</option>
                    </select>
                    <button id="resetZoom" type="button">Reset</button>
                  </div>
                  <svg id="graph"></svg>
                </div>
              </div>
              <script>
                const data = {{GRAPH_JSON}};
                const issueColor = {};
                (data.issues || []).forEach(issue => {
                  const color = issue.type === 'VERSION_CONFLICT' ? '#e25c5c'
                    : issue.type === 'DUPLICATE' ? '#e0c35a' : '#e08a3c';
                  (issue.artifactKeys || []).forEach(k => { issueColor[k] = color; });
                });

                const width = document.getElementById('graph').clientWidth || 900;
                const height = Math.max(640, window.innerHeight - 160);
                const svg = d3.select('#graph').attr('viewBox', [0, 0, width, height]);
                const g = svg.append('g');

                const zoom = d3.zoom().scaleExtent([0.2, 4]).on('zoom', (event) => {
                  g.attr('transform', event.transform);
                });
                svg.call(zoom);

                const nodes = (data.nodes || []).map(n => ({
                  ...n,
                  label: n.groupId + ':' + n.artifactId + ':' + n.version
                }));
                const nodeById = Object.fromEntries(nodes.map(n => [n.id, n]));
                const links = (data.edges || [])
                  .filter(e => nodeById[e.from] && nodeById[e.to])
                  .map(e => ({ source: e.from, target: e.to }));

                const simulation = d3.forceSimulation(nodes)
                  .force('link', d3.forceLink(links).id(d => d.id).distance(90))
                  .force('charge', d3.forceManyBody().strength(-220))
                  .force('center', d3.forceCenter(width / 2, height / 2));

                const link = g.append('g').attr('stroke', '#4b5d73').attr('stroke-opacity', 0.7)
                  .selectAll('line').data(links).join('line').attr('stroke-width', 1.2);

                const node = g.append('g').selectAll('g').data(nodes).join('g').call(drag(simulation));
                node.append('circle')
                  .attr('r', d => d.origin === 'PROJECT' ? 10 : 7)
                  .attr('fill', d => issueColor[d.groupId + ':' + d.artifactId]
                    || issueColor[d.groupId + ':' + d.artifactId + ':' + d.version]
                    || (d.origin === 'PROJECT' ? '#3d9cf0' : '#7f8ea3'))
                  .attr('stroke', '#0b1220').attr('stroke-width', 1.5);
                node.append('title').text(d => d.label + ' [' + d.scope + '/' + d.origin + ']');
                node.append('text')
                  .text(d => d.artifactId)
                  .attr('x', 10).attr('y', 3)
                  .attr('fill', '#c9d4e3').attr('font-size', 10);

                simulation.on('tick', () => {
                  link.attr('x1', d => d.source.x).attr('y1', d => d.source.y)
                      .attr('x2', d => d.target.x).attr('y2', d => d.target.y);
                  node.attr('transform', d => `translate(${d.x},${d.y})`);
                });

                function drag(simulation) {
                  function dragstarted(event) {
                    if (!event.active) simulation.alphaTarget(0.3).restart();
                    event.subject.fx = event.subject.x;
                    event.subject.fy = event.subject.y;
                  }
                  function dragged(event) {
                    event.subject.fx = event.x;
                    event.subject.fy = event.y;
                  }
                  function dragended(event) {
                    if (!event.active) simulation.alphaTarget(0);
                    event.subject.fx = null;
                    event.subject.fy = null;
                  }
                  return d3.drag().on('start', dragstarted).on('drag', dragged).on('end', dragended);
                }

                function applyFilters() {
                  const q = document.getElementById('search').value.toLowerCase();
                  const scope = document.getElementById('scopeFilter').value;
                  node.style('opacity', d => {
                    const matchQ = !q || d.label.toLowerCase().includes(q)
                      || (data.issues || []).some(i => (i.cve || '').toLowerCase().includes(q)
                        && (i.artifactKeys || []).some(k => k.includes(d.groupId + ':' + d.artifactId)));
                    const matchScope = !scope || d.scope === scope;
                    return matchQ && matchScope ? 1 : 0.12;
                  });
                  link.style('opacity', l => {
                    const s = typeof l.source === 'object' ? l.source : nodeById[l.source];
                    const t = typeof l.target === 'object' ? l.target : nodeById[l.target];
                    return (node.filter(d => d.id === s.id).style('opacity') === '1'
                      && node.filter(d => d.id === t.id).style('opacity') === '1') ? 0.7 : 0.05;
                  });
                }
                document.getElementById('search').addEventListener('input', applyFilters);
                document.getElementById('scopeFilter').addEventListener('change', applyFilters);
                document.getElementById('resetZoom').addEventListener('click', () => {
                  svg.transition().duration(300).call(zoom.transform, d3.zoomIdentity);
                });
              </script>
            </body>
            </html>
            """;
}
