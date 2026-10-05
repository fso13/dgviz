package io.github.dgviz.export;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import io.github.dgviz.analysis.store.AnalysisDependency;
import io.github.dgviz.analysis.store.AnalysisRun;
import io.github.dgviz.analysis.store.StoredAnalysisIssue;
import io.github.dgviz.repository.CodeRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Exports dependency trees and vulnerability reports (CSV / XLSX / PDF / TXT / JSON).
 */
@Service
public class ReportExportService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public byte[] exportDependencyTree(
            CodeRepository repo,
            AnalysisRun run,
            List<AnalysisDependency> deps,
            String format
    ) {
        String f = format == null ? "txt" : format.trim().toLowerCase(Locale.ROOT);
        return switch (f) {
            case "json" -> treeJson(repo, run, deps);
            case "csv" -> treeCsv(deps);
            default -> treeTxt(repo, run, deps);
        };
    }

    public byte[] exportVulnerabilities(
            CodeRepository repo,
            AnalysisRun run,
            List<StoredAnalysisIssue> issues,
            String format
    ) {
        List<StoredAnalysisIssue> vulns = issues.stream()
                .filter(i -> "VULNERABILITY".equalsIgnoreCase(i.getIssueType()))
                .sorted(Comparator
                        .comparing(StoredAnalysisIssue::getSeverity, Comparator.nullsLast(String::compareTo))
                        .thenComparing(StoredAnalysisIssue::getCve, Comparator.nullsLast(String::compareTo)))
                .toList();
        String f = format == null ? "csv" : format.trim().toLowerCase(Locale.ROOT);
        return switch (f) {
            case "xlsx", "xls" -> vulnerabilitiesXlsx(repo, run, vulns);
            case "pdf" -> vulnerabilitiesPdf(repo, run, vulns);
            default -> vulnerabilitiesCsv(vulns);
        };
    }

    public String contentType(String kind, String format) {
        String f = format == null ? "" : format.trim().toLowerCase(Locale.ROOT);
        if ("tree".equals(kind)) {
            return switch (f) {
                case "json" -> "application/json";
                case "csv" -> "text/csv; charset=UTF-8";
                default -> "text/plain; charset=UTF-8";
            };
        }
        return switch (f) {
            case "xlsx", "xls" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "pdf" -> "application/pdf";
            default -> "text/csv; charset=UTF-8";
        };
    }

    public String filename(CodeRepository repo, String kind, String format) {
        String safe = (repo.getName() == null ? "repo" : repo.getName())
                .replaceAll("[^a-zA-Z0-9._-]+", "-");
        String f = format == null ? "bin" : format.trim().toLowerCase(Locale.ROOT);
        if ("xlsx".equals(f) || "xls".equals(f)) {
            f = "xlsx";
        }
        return "dgviz-" + safe + "-" + kind + "." + f;
    }

    private byte[] treeTxt(CodeRepository repo, AnalysisRun run, List<AnalysisDependency> deps) {
        StringBuilder sb = new StringBuilder();
        sb.append("DGViz dependency tree\n");
        sb.append("Repository: ").append(repo.getName()).append(" (id=").append(repo.getId()).append(")\n");
        sb.append("Scan: ").append(formatInstant(run)).append(" · nodes=").append(run.getNodeCount()).append('\n');
        sb.append('\n');
        Map<String, List<AnalysisDependency>> children = childrenByParent(deps);
        List<AnalysisDependency> roots = roots(deps);
        for (AnalysisDependency root : roots) {
            appendTreeLine(sb, root, children, 0);
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void appendTreeLine(
            StringBuilder sb,
            AnalysisDependency node,
            Map<String, List<AnalysisDependency>> children,
            int depth
    ) {
        sb.append("  ".repeat(depth))
                .append(blank(node.getGroupId())).append(':')
                .append(blank(node.getArtifactId())).append(':')
                .append(blank(node.getVersion()))
                .append(" [").append(blank(node.getOrigin())).append('/')
                .append(blank(node.getScope())).append("]\n");
        for (AnalysisDependency child : children.getOrDefault(node.getNodeId(), List.of())) {
            appendTreeLine(sb, child, children, depth + 1);
        }
    }

    private byte[] treeCsv(List<AnalysisDependency> deps) {
        StringBuilder sb = new StringBuilder();
        sb.append("depth,groupId,artifactId,version,scope,origin,parentGroupId,parentArtifactId,parentVersion,nodeId,parentNodeId\n");
        Map<String, AnalysisDependency> byId = indexByNodeId(deps);
        Map<String, List<AnalysisDependency>> children = childrenByParent(deps);
        for (AnalysisDependency root : roots(deps)) {
            appendTreeCsv(sb, root, byId, children, 0);
        }
        return withBom(sb.toString());
    }

    private void appendTreeCsv(
            StringBuilder sb,
            AnalysisDependency node,
            Map<String, AnalysisDependency> byId,
            Map<String, List<AnalysisDependency>> children,
            int depth
    ) {
        AnalysisDependency parent = node.getParentNodeId() == null ? null : byId.get(node.getParentNodeId());
        sb.append(depth).append(',')
                .append(csv(node.getGroupId())).append(',')
                .append(csv(node.getArtifactId())).append(',')
                .append(csv(node.getVersion())).append(',')
                .append(csv(node.getScope())).append(',')
                .append(csv(node.getOrigin())).append(',')
                .append(csv(parent == null ? "" : parent.getGroupId())).append(',')
                .append(csv(parent == null ? "" : parent.getArtifactId())).append(',')
                .append(csv(parent == null ? "" : parent.getVersion())).append(',')
                .append(csv(node.getNodeId())).append(',')
                .append(csv(node.getParentNodeId())).append('\n');
        for (AnalysisDependency child : children.getOrDefault(node.getNodeId(), List.of())) {
            appendTreeCsv(sb, child, byId, children, depth + 1);
        }
    }

    private byte[] treeJson(CodeRepository repo, AnalysisRun run, List<AnalysisDependency> deps) {
        Map<String, List<AnalysisDependency>> children = childrenByParent(deps);
        StringBuilder sb = new StringBuilder();
        sb.append('{')
                .append("\"repositoryId\":").append(repo.getId()).append(',')
                .append("\"repositoryName\":").append(json(repo.getName())).append(',')
                .append("\"analysisRunId\":").append(run.getId()).append(',')
                .append("\"analyzedAt\":").append(json(formatInstant(run))).append(',')
                .append("\"nodeCount\":").append(run.getNodeCount()).append(',')
                .append("\"roots\":[");
        List<AnalysisDependency> roots = roots(deps);
        for (int i = 0; i < roots.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            appendTreeJson(sb, roots.get(i), children);
        }
        sb.append("]}");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void appendTreeJson(
            StringBuilder sb,
            AnalysisDependency node,
            Map<String, List<AnalysisDependency>> children
    ) {
        sb.append('{')
                .append("\"groupId\":").append(json(node.getGroupId())).append(',')
                .append("\"artifactId\":").append(json(node.getArtifactId())).append(',')
                .append("\"version\":").append(json(node.getVersion())).append(',')
                .append("\"scope\":").append(json(node.getScope())).append(',')
                .append("\"origin\":").append(json(node.getOrigin())).append(',')
                .append("\"dependencies\":[");
        List<AnalysisDependency> kids = children.getOrDefault(node.getNodeId(), List.of());
        for (int i = 0; i < kids.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            appendTreeJson(sb, kids.get(i), children);
        }
        sb.append("]}");
    }

    private byte[] vulnerabilitiesCsv(List<StoredAnalysisIssue> vulns) {
        StringBuilder sb = new StringBuilder();
        sb.append("severity,package,version,fixedVersion,cve,cvss,title,recommendation,description,advisoryUrl\n");
        for (StoredAnalysisIssue i : vulns) {
            sb.append(csv(i.getSeverity())).append(',')
                    .append(csv(i.packageName())).append(',')
                    .append(csv(i.packageVersion())).append(',')
                    .append(csv(i.getFixedVersion())).append(',')
                    .append(csv(i.getCve())).append(',')
                    .append(i.getCvss() == null ? "" : i.getCvss()).append(',')
                    .append(csv(i.getTitle())).append(',')
                    .append(csv(i.getRecommendation())).append(',')
                    .append(csv(i.getDescription())).append(',')
                    .append(csv(i.advisoryUrl())).append('\n');
        }
        return withBom(sb.toString());
    }

    private byte[] vulnerabilitiesXlsx(CodeRepository repo, AnalysisRun run, List<StoredAnalysisIssue> vulns) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet meta = wb.createSheet("Summary");
            meta.createRow(0).createCell(0).setCellValue("Repository");
            meta.getRow(0).createCell(1).setCellValue(repo.getName());
            meta.createRow(1).createCell(0).setCellValue("Repository ID");
            meta.getRow(1).createCell(1).setCellValue(repo.getId());
            meta.createRow(2).createCell(0).setCellValue("Analysis run");
            meta.getRow(2).createCell(1).setCellValue(run.getId());
            meta.createRow(3).createCell(0).setCellValue("Analyzed at");
            meta.getRow(3).createCell(1).setCellValue(formatInstant(run));
            meta.createRow(4).createCell(0).setCellValue("Vulnerabilities");
            meta.getRow(4).createCell(1).setCellValue(vulns.size());

            Sheet sheet = wb.createSheet("Vulnerabilities");
            CellStyle header = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font bold = wb.createFont();
            bold.setBold(true);
            header.setFont(bold);
            String[] cols = {
                    "Severity", "Package", "Version", "Fixed in", "CVE", "CVSS",
                    "Title", "Recommendation", "Description", "Advisory URL"
            };
            Row headerRow = sheet.createRow(0);
            for (int c = 0; c < cols.length; c++) {
                Cell cell = headerRow.createCell(c);
                cell.setCellValue(cols[c]);
                cell.setCellStyle(header);
            }
            int r = 1;
            for (StoredAnalysisIssue i : vulns) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(blank(i.getSeverity()));
                row.createCell(1).setCellValue(i.packageName());
                row.createCell(2).setCellValue(i.packageVersion());
                row.createCell(3).setCellValue(blank(i.getFixedVersion()));
                row.createCell(4).setCellValue(blank(i.getCve()));
                if (i.getCvss() != null) {
                    row.createCell(5).setCellValue(i.getCvss());
                } else {
                    row.createCell(5).setCellValue("");
                }
                row.createCell(6).setCellValue(blank(i.getTitle()));
                row.createCell(7).setCellValue(blank(i.getRecommendation()));
                row.createCell(8).setCellValue(blank(i.getDescription()));
                row.createCell(9).setCellValue(blank(i.advisoryUrl()));
            }
            for (int c = 0; c < cols.length; c++) {
                sheet.autoSizeColumn(c);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to build XLSX report", ex);
        }
    }

    private byte[] vulnerabilitiesPdf(CodeRepository repo, AnalysisRun run, List<StoredAnalysisIssue> vulns) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4.rotate(), 28, 28, 28, 28);
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 9);
            Font small = FontFactory.getFont(FontFactory.HELVETICA, 8);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);

            doc.add(new Paragraph("DGViz vulnerability report", titleFont));
            doc.add(new Paragraph(
                    "Repository: " + repo.getName()
                            + "  ·  scan " + formatInstant(run)
                            + "  ·  findings: " + vulns.size(),
                    normal));
            doc.add(new Paragraph(" ", normal));

            PdfPTable table = new PdfPTable(new float[]{1.1f, 2.4f, 1.1f, 1.1f, 1.6f, 0.8f, 3.2f});
            table.setWidthPercentage(100);
            String[] headers = {"Severity", "Package", "Version", "Fixed", "CVE", "CVSS", "Title"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
                cell.setBackgroundColor(new Color(230, 230, 235));
                cell.setPadding(4);
                table.addCell(cell);
            }
            for (StoredAnalysisIssue i : vulns) {
                table.addCell(cell(blank(i.getSeverity()), small));
                table.addCell(cell(i.packageName(), small));
                table.addCell(cell(i.packageVersion(), small));
                table.addCell(cell(blank(i.getFixedVersion()), small));
                table.addCell(cell(blank(i.getCve()), small));
                table.addCell(cell(i.getCvss() == null ? "" : String.valueOf(i.getCvss()), small));
                table.addCell(cell(blank(i.getTitle()), small));
            }
            doc.add(table);
            doc.close();
            return out.toByteArray();
        } catch (DocumentException | IOException ex) {
            throw new IllegalStateException("Failed to build PDF report", ex);
        }
    }

    private static PdfPCell cell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
        cell.setPadding(3);
        cell.setVerticalAlignment(Element.ALIGN_TOP);
        return cell;
    }

    private static Map<String, List<AnalysisDependency>> childrenByParent(List<AnalysisDependency> deps) {
        Map<String, List<AnalysisDependency>> map = new HashMap<>();
        Map<String, AnalysisDependency> byId = indexByNodeId(deps);
        for (AnalysisDependency d : deps) {
            String parent = d.getParentNodeId();
            if (parent == null || parent.isBlank() || !byId.containsKey(parent)) {
                continue;
            }
            map.computeIfAbsent(parent, k -> new ArrayList<>()).add(d);
        }
        return map;
    }

    private static List<AnalysisDependency> roots(List<AnalysisDependency> deps) {
        Map<String, AnalysisDependency> byId = indexByNodeId(deps);
        List<AnalysisDependency> roots = new ArrayList<>();
        for (AnalysisDependency d : deps) {
            String parent = d.getParentNodeId();
            if (parent == null || parent.isBlank() || !byId.containsKey(parent)) {
                roots.add(d);
            }
        }
        return roots;
    }

    private static Map<String, AnalysisDependency> indexByNodeId(List<AnalysisDependency> deps) {
        Map<String, AnalysisDependency> byId = new HashMap<>();
        for (AnalysisDependency d : deps) {
            byId.put(d.getNodeId(), d);
        }
        return byId;
    }

    private static String formatInstant(AnalysisRun run) {
        if (run.getAnalyzedAt() == null) {
            return "";
        }
        return TS.format(run.getAnalyzedAt().atOffset(ZoneOffset.UTC));
    }

    private static byte[] withBom(String csv) {
        byte[] body = csv.getBytes(StandardCharsets.UTF_8);
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] out = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, out, 0, bom.length);
        System.arraycopy(body, 0, out, bom.length, body.length);
        return out;
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        String v = value.replace("\r\n", "\n").replace('\r', '\n');
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private static String json(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }
}
