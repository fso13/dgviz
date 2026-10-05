package io.github.dgviz.mail;

import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MailTemplateService {

    private static final DateTimeFormatter TS = DateTimeFormatter
            .ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private final TemplateEngine templateEngine;

    public MailTemplateService(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public String render(String templateName, Map<String, Object> variables) {
        Context context = new Context(Locale.forLanguageTag("ru"));
        if (variables != null) {
            context.setVariables(variables);
        }
        return templateEngine.process(templateName, context);
    }

    public String formatInstant(Instant instant) {
        return instant == null ? "—" : TS.format(instant);
    }

    public record Detail(String label, String value) {
    }

    public record SeverityCount(String severity, long count, String color) {
    }

    public static List<SeverityCount> severityCounts(Map<String, Long> bySeverity) {
        return List.of("CRITICAL", "HIGH", "MEDIUM", "LOW", "UNKNOWN").stream()
                .filter(sev -> bySeverity.getOrDefault(sev, 0L) > 0)
                .map(sev -> new SeverityCount(sev, bySeverity.get(sev), severityColor(sev)))
                .toList();
    }

    public static String severityColor(String severity) {
        if (severity == null) {
            return "#6272a4";
        }
        return switch (severity.toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> "#ff5555";
            case "HIGH" -> "#ffb86c";
            case "MEDIUM" -> "#f1fa8c";
            case "LOW" -> "#8be9fd";
            default -> "#6272a4";
        };
    }
}
