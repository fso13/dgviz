package io.github.dgviz.export;

import io.github.dgviz.analysis.store.AnalysisDependency;
import io.github.dgviz.analysis.store.AnalysisRun;
import io.github.dgviz.analysis.store.StoredAnalysisIssue;
import io.github.dgviz.repository.CodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportExportServiceTest {

    private final ReportExportService service = new ReportExportService();

    private CodeRepository repo;
    private AnalysisRun run;
    private List<AnalysisDependency> deps;
    private List<StoredAnalysisIssue> issues;

    @BeforeEach
    void setUp() {
        repo = new CodeRepository();
        repo.setId(7L);
        repo.setName("demo-app");

        run = new AnalysisRun();
        run.setId(42L);
        run.setAnalyzedAt(Instant.parse("2026-10-05T10:00:00Z"));
        run.setNodeCount(2);

        AnalysisDependency root = dep("n1", null, "com.demo", "app", "1.0", "COMPILE", "PROJECT");
        AnalysisDependency child = dep("n2", "n1", "org.example", "lib", "2.1.0", "COMPILE", "MAVEN");
        deps = List.of(root, child);

        StoredAnalysisIssue vuln = new StoredAnalysisIssue();
        vuln.setIssueType("VULNERABILITY");
        vuln.setSeverity("HIGH");
        vuln.setTitle("Demo CVE");
        vuln.setDescription("desc");
        vuln.setRecommendation("upgrade");
        vuln.setCve("CVE-2024-0001");
        vuln.setCvss(7.5);
        vuln.setFixedVersion("2.2.0");
        vuln.setArtifactKeys("org.example:lib:2.1.0");

        StoredAnalysisIssue conflict = new StoredAnalysisIssue();
        conflict.setIssueType("VERSION_CONFLICT");
        conflict.setSeverity("WARNING");
        conflict.setTitle("conflict");

        issues = List.of(vuln, conflict);
    }

    @Test
    @DisplayName("Should export indented dependency tree as TXT")
    void shouldExportTreeTxt() {
        byte[] bytes = service.exportDependencyTree(repo, run, deps, "txt");
        String text = new String(bytes, StandardCharsets.UTF_8);

        assertThat(text).contains("DGViz dependency tree");
        assertThat(text).contains("com.demo:app:1.0");
        assertThat(text).contains("  org.example:lib:2.1.0");
        assertThat(service.contentType("tree", "txt")).startsWith("text/plain");
        assertThat(service.filename(repo, "tree", "txt")).isEqualTo("dgviz-demo-app-tree.txt");
    }

    @Test
    @DisplayName("Should export dependency tree as CSV with BOM")
    void shouldExportTreeCsv() {
        byte[] bytes = service.exportDependencyTree(repo, run, deps, "csv");

        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        String text = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        assertThat(text).contains("depth,groupId,artifactId");
        assertThat(text).contains("0,com.demo,app,1.0");
        assertThat(text).contains("1,org.example,lib,2.1.0");
    }

    @Test
    @DisplayName("Should export nested dependency tree as JSON")
    void shouldExportTreeJson() {
        byte[] bytes = service.exportDependencyTree(repo, run, deps, "json");
        String json = new String(bytes, StandardCharsets.UTF_8);

        assertThat(json).contains("\"repositoryId\":7");
        assertThat(json).contains("\"groupId\":\"org.example\"");
        assertThat(json).contains("\"dependencies\":[{");
        assertThat(service.contentType("tree", "json")).isEqualTo("application/json");
    }

    @Test
    @DisplayName("Should export vulnerabilities as CSV excluding non-vuln issues")
    void shouldExportVulnerabilitiesCsv() {
        byte[] bytes = service.exportVulnerabilities(repo, run, issues, "csv");
        String text = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);

        assertThat(text).contains("severity,package,version,fixedVersion,cve");
        assertThat(text).contains("HIGH");
        assertThat(text).contains("CVE-2024-0001");
        assertThat(text).contains("2.2.0");
        assertThat(text).doesNotContain("VERSION_CONFLICT");
        assertThat(text).doesNotContain("conflict");
    }

    @Test
    @DisplayName("Should export vulnerabilities as XLSX workbook")
    void shouldExportVulnerabilitiesXlsx() {
        byte[] bytes = service.exportVulnerabilities(repo, run, issues, "xlsx");

        assertThat(bytes).hasSizeGreaterThan(100);
        assertThat(bytes[0]).isEqualTo((byte) 'P');
        assertThat(bytes[1]).isEqualTo((byte) 'K');
        assertThat(service.contentType("vulns", "xlsx"))
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(service.filename(repo, "vulnerabilities", "xlsx"))
                .isEqualTo("dgviz-demo-app-vulnerabilities.xlsx");
    }

    @Test
    @DisplayName("Should export vulnerabilities as PDF")
    void shouldExportVulnerabilitiesPdf() {
        byte[] bytes = service.exportVulnerabilities(repo, run, issues, "pdf");
        String head = new String(bytes, 0, Math.min(8, bytes.length), StandardCharsets.ISO_8859_1);

        assertThat(head).startsWith("%PDF");
        assertThat(service.contentType("vulns", "pdf")).isEqualTo("application/pdf");
        assertThat(service.filename(repo, "vulnerabilities", "pdf"))
                .isEqualTo("dgviz-demo-app-vulnerabilities.pdf");
    }

    private static AnalysisDependency dep(
            String nodeId,
            String parentNodeId,
            String groupId,
            String artifactId,
            String version,
            String scope,
            String origin
    ) {
        AnalysisDependency d = new AnalysisDependency();
        d.setNodeId(nodeId);
        d.setParentNodeId(parentNodeId);
        d.setGroupId(groupId);
        d.setArtifactId(artifactId);
        d.setVersion(version);
        d.setScope(scope);
        d.setOrigin(origin);
        return d;
    }
}
