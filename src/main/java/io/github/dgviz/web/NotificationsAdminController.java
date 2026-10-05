package io.github.dgviz.web;

import io.github.dgviz.notifications.NotificationsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/notifications")
public class NotificationsAdminController {

    private static final Logger log = LoggerFactory.getLogger(NotificationsAdminController.class);

    private final NotificationsClient notificationsClient;

    public NotificationsAdminController(NotificationsClient notificationsClient) {
        this.notificationsClient = notificationsClient;
    }

    @GetMapping
    public String page(Model model) {
        boolean reachable = notificationsClient.isReachable();
        model.addAttribute("reachable", reachable);
        if (reachable) {
            try {
                model.addAttribute("transports", notificationsClient.listTransports());
                model.addAttribute("logs", notificationsClient.listLogs());
            } catch (Exception ex) {
                log.error("Failed to load notification settings", ex);
                model.addAttribute("error", "Failed to load notification settings: " + ex.getMessage());
                model.addAttribute("transports", List.of());
                model.addAttribute("logs", List.of());
            }
        } else {
            model.addAttribute("transports", List.of());
            model.addAttribute("logs", List.of());
        }
        return "admin/notifications";
    }

    @PostMapping("/transports/{type}")
    public String update(
            @PathVariable String type,
            @RequestParam(defaultValue = "false") boolean enabled,
            @RequestParam Map<String, String> params,
            RedirectAttributes ra
    ) {
        try {
            Map<String, Object> config = new HashMap<>();
            for (Map.Entry<String, String> e : params.entrySet()) {
                String key = e.getKey();
                if ("enabled".equals(key) || "_csrf".equals(key) || key.startsWith("_")) {
                    continue;
                }
                if (key.startsWith("config.")) {
                    config.put(key.substring("config.".length()), e.getValue());
                }
            }
            if ("EMAIL".equalsIgnoreCase(type) && params.containsKey("config.port")) {
                try {
                    config.put("port", Integer.parseInt(params.get("config.port")));
                } catch (NumberFormatException ignored) {
                    config.put("port", 587);
                }
            }
            if ("EMAIL".equalsIgnoreCase(type)) {
                config.put("starttls", params.containsKey("config.starttls"));
            }
            log.info("Admin saving transport {} enabled={} keys={}", type, enabled, config.keySet());
            notificationsClient.updateTransport(type.toUpperCase(), enabled, config);
            ra.addFlashAttribute("success", type + " settings saved (enabled=" + enabled + ")");
        } catch (Exception ex) {
            log.error("Failed to save transport {}", type, ex);
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/notifications";
    }

    @PostMapping("/test")
    public String test(
            @RequestParam String transport,
            @RequestParam String subject,
            @RequestParam String body,
            @RequestParam(required = false) String recipients,
            RedirectAttributes ra
    ) {
        try {
            List<String> to = recipients == null || recipients.isBlank()
                    ? List.of()
                    : List.of(recipients.split("\\s*,\\s*"));
            log.info("Admin test send transport={} recipients={}", transport, to);
            List<NotificationsClient.SendResultView> results =
                    notificationsClient.sendTest(subject, body, to, transport.toUpperCase());
            String summary = results.stream()
                    .map(r -> r.transport() + "=" + r.status()
                            + (r.message() == null || r.message().isBlank() ? "" : (" (" + r.message() + ")")))
                    .collect(Collectors.joining("; "));
            boolean failed = results.stream().anyMatch(r -> "FAILED".equalsIgnoreCase(r.status()));
            boolean skippedOnly = !results.isEmpty()
                    && results.stream().allMatch(r -> "SKIPPED".equalsIgnoreCase(r.status()));
            if (failed || skippedOnly) {
                ra.addFlashAttribute("error", "Send result: " + summary);
            } else {
                ra.addFlashAttribute("success", "Send result: " + summary);
            }
        } catch (Exception ex) {
            log.error("Admin test send failed", ex);
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/notifications";
    }
}
