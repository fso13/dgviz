package io.github.dgviz.export;

import io.github.dgviz.analysis.store.AnalysisDependency;
import io.github.dgviz.analysis.store.AnalysisRun;
import io.github.dgviz.analysis.store.RepositoryAnalysisService;
import io.github.dgviz.analysis.store.StoredAnalysisIssue;
import io.github.dgviz.mail.MailTemplateService;
import io.github.dgviz.notifications.NotificationsClient;
import io.github.dgviz.repository.CodeRepository;
import io.github.dgviz.security.AccessControlService;
import io.github.dgviz.settings.AppSettingsService;
import io.github.dgviz.user.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportMailService {

    private static final Logger log = LoggerFactory.getLogger(ReportMailService.class);

    private final AccessControlService accessControl;
    private final RepositoryAnalysisService analysisService;
    private final ReportExportService reportExportService;
    private final NotificationsClient notificationsClient;
    private final AppSettingsService settingsService;
    private final MailTemplateService mailTemplates;

    public ReportMailService(
            AccessControlService accessControl,
            RepositoryAnalysisService analysisService,
            ReportExportService reportExportService,
            NotificationsClient notificationsClient,
            AppSettingsService settingsService,
            MailTemplateService mailTemplates
    ) {
        this.accessControl = accessControl;
        this.analysisService = analysisService;
        this.reportExportService = reportExportService;
        this.notificationsClient = notificationsClient;
        this.settingsService = settingsService;
        this.mailTemplates = mailTemplates;
    }

    @Transactional(readOnly = true)
    public SendReportResult emailLatestReport(Long repositoryId) {
        CodeRepository repo = accessControl.getAccessibleRepository(repositoryId);
        AnalysisRun run = analysisService.latestRunForRepository(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("No scan found for repository " + repositoryId));

        List<AppUser> recipients = accessControl.usersWithRepositoryAccessEmail(repo);
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException(
                    "No recipients: project group has no enabled members with email");
        }

        List<StoredAnalysisIssue> issues = analysisService.issues(run.getId());
        List<AnalysisDependency> deps = analysisService.dependencyTree(run.getId());
        List<StoredAnalysisIssue> vulns = issues.stream()
                .filter(i -> "VULNERABILITY".equalsIgnoreCase(i.getIssueType()))
                .toList();

        byte[] pdf = reportExportService.exportVulnerabilities(repo, run, issues, "pdf");
        byte[] csv = reportExportService.exportVulnerabilities(repo, run, issues, "csv");
        byte[] tree = reportExportService.exportDependencyTree(repo, run, deps, "txt");

        String pdfName = reportExportService.filename(repo, "vulnerabilities", "pdf");
        String csvName = reportExportService.filename(repo, "vulnerabilities", "csv");
        String treeName = reportExportService.filename(repo, "tree", "txt");

        List<NotificationsClient.Attachment> attachments = List.of(
                NotificationsClient.attachment(pdfName, "application/pdf", pdf),
                NotificationsClient.attachment(csvName, "text/csv; charset=UTF-8", csv),
                NotificationsClient.attachment(treeName, "text/plain; charset=UTF-8", tree)
        );

        Map<String, Long> bySeverity = vulns.stream()
                .collect(Collectors.groupingBy(
                        i -> i.getSeverity() == null ? "UNKNOWN" : i.getSeverity().toUpperCase(Locale.ROOT),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
        long criticalCount = bySeverity.getOrDefault("CRITICAL", 0L);
        String subject = "DGViz: " + repo.getName() + " — " + vulns.size() + " "
                + pluralRu(vulns.size(), "уязвимость", "уязвимости", "уязвимостей");
        String link = settingsService.getInvitePublicBaseUrl() + "/workspace/repositories/" + repo.getId();
        String generatedAt = mailTemplates.formatInstant(run.getAnalyzedAt());
        List<MailTemplateService.Detail> details = List.of(
                new MailTemplateService.Detail("Репозиторий", repo.getName() + " (id=" + repo.getId() + ")"),
                new MailTemplateService.Detail(
                        "Проект",
                        repo.getProject() == null ? "—" : repo.getProject().getName()
                ),
                new MailTemplateService.Detail("Скан", generatedAt),
                new MailTemplateService.Detail("Статус", String.valueOf(run.getStatus())),
                new MailTemplateService.Detail("Узлы дерева", String.valueOf(run.getNodeCount())),
                new MailTemplateService.Detail("Уязвимости", String.valueOf(vulns.size()))
        );
        List<MailTemplateService.SeverityCount> severities = MailTemplateService.severityCounts(bySeverity);
        String subtitle = vulns.size() + " "
                + pluralRu(vulns.size(), "уязвимость", "уязвимости", "уязвимостей");
        String recipientFooter = "отправлено " + recipients.size() + " "
                + pluralRu(recipients.size(), "получателю", "получателям", "получателям");

        List<String> sentEmails = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (AppUser recipient : recipients) {
            String email = recipient.getEmail().trim();
            String textBody = buildTextBody(repo, run, vulns, bySeverity, link, recipient.getDisplayName());
            Map<String, Object> model = new LinkedHashMap<>();
            model.put("subject", subject);
            model.put("title", repo.getName());
            model.put("subtitle", subtitle);
            model.put("criticalCount", criticalCount);
            model.put("generatedAt", generatedAt);
            model.put("recipientName", recipient.getDisplayName());
            model.put("intro", "По репозиторию, к которому у вас есть доступ, сформирован отчёт по уязвимостям и дереву зависимостей. Файлы приложены к письму.");
            model.put("details", details);
            model.put("severities", severities);
            model.put("ctaUrl", link);
            model.put("recipientCount", recipients.size());
            model.put("recipientFooter", recipientFooter);
            String htmlBody = mailTemplates.render("mail/report", model);
            try {
                List<NotificationsClient.SendResultView> results = notificationsClient.sendHtmlEmail(
                        subject, htmlBody, textBody, List.of(email), attachments);
                boolean sent = results.stream().anyMatch(r -> "SENT".equalsIgnoreCase(r.status()));
                if (sent) {
                    sentEmails.add(email);
                } else {
                    String detail = results.stream()
                            .map(r -> r.transport() + "=" + r.status()
                                    + (r.message() == null ? "" : (" (" + r.message() + ")")))
                            .collect(Collectors.joining("; "));
                    failures.add(email + ": " + detail);
                }
            } catch (Exception ex) {
                log.error("Failed to email report for repo {} to {}", repositoryId, email, ex);
                failures.add(email + ": " + ex.getMessage());
            }
        }

        if (sentEmails.isEmpty()) {
            throw new IllegalStateException(
                    "Failed to send report email: " + String.join("; ", failures));
        }
        log.info("Report emailed for repo {} to {} recipients: {}", repo.getId(), sentEmails.size(), sentEmails);
        if (!failures.isEmpty()) {
            log.warn("Partial report email failures for repo {}: {}", repo.getId(), failures);
        }
        return new SendReportResult(true, sentEmails.size(), sentEmails,
                failures.isEmpty() ? null : "Partial failures: " + String.join("; ", failures));
    }

    private String buildTextBody(
            CodeRepository repo,
            AnalysisRun run,
            List<StoredAnalysisIssue> vulns,
            Map<String, Long> bySeverity,
            String link,
            String recipientName
    ) {
        List<String> severityLines = new ArrayList<>();
        for (String sev : List.of("CRITICAL", "HIGH", "MEDIUM", "LOW", "UNKNOWN")) {
            Long n = bySeverity.get(sev);
            if (n != null && n > 0) {
                severityLines.add("  " + sev + ": " + n);
            }
        }
        return """
                Здравствуйте, %s!

                Отчёт DGViz по репозиторию %s

                Проект: %s
                Скан: %s
                Статус: %s
                Узлы: %s
                Уязвимости: %d
                %s

                Вложения: PDF, CSV, дерево зависимостей (TXT).

                Открыть в DGViz: %s
                """.formatted(
                recipientName,
                repo.getName(),
                repo.getProject() == null ? "—" : repo.getProject().getName(),
                mailTemplates.formatInstant(run.getAnalyzedAt()),
                run.getStatus(),
                run.getNodeCount(),
                vulns.size(),
                severityLines.isEmpty() ? "  (нет)" : String.join("\n", severityLines),
                link
        );
    }

    private static String pluralRu(int n, String one, String few, String many) {
        int mod10 = Math.abs(n) % 10;
        int mod100 = Math.abs(n) % 100;
        if (mod10 == 1 && mod100 != 11) {
            return one;
        }
        if (mod10 >= 2 && mod10 <= 4 && !(mod100 >= 12 && mod100 <= 14)) {
            return few;
        }
        return many;
    }

    public record SendReportResult(boolean sent, int recipientCount, List<String> emails, String message) {
    }
}
