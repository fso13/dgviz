package io.github.dgviz.notifications.api;

import io.github.dgviz.notifications.domain.NotificationLog;
import io.github.dgviz.notifications.domain.NotificationTransportConfig;
import io.github.dgviz.notifications.domain.TransportType;
import io.github.dgviz.notifications.service.NotificationDispatchService;
import io.github.dgviz.notifications.transport.NotificationAttachment;
import io.github.dgviz.notifications.transport.NotificationMessage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class NotificationApiController {

    private static final Logger log = LoggerFactory.getLogger(NotificationApiController.class);

    private final NotificationDispatchService dispatchService;

    public NotificationApiController(NotificationDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping("/transports")
    public List<TransportView> transports() {
        return dispatchService.listConfigs().stream()
                .map(this::toView)
                .toList();
    }

    @PutMapping("/transports/{type}")
    public TransportView update(
            @PathVariable TransportType type,
            @Valid @RequestBody UpdateTransportRequest request
    ) {
        log.info("API update transport {} enabled={}", type, request.enabled());
        NotificationTransportConfig saved = dispatchService.updateConfig(
                type,
                request.enabled(),
                request.config()
        );
        return toView(saved);
    }

    @PostMapping("/notifications")
    public ResponseEntity<SendResponse> send(@Valid @RequestBody SendRequest request) {
        log.info(
                "API send subject='{}' recipients={} transports={}",
                request.subject(),
                request.recipients(),
                request.transports()
        );
        NotificationMessage message = new NotificationMessage(
                request.subject(),
                request.body(),
                request.recipients() == null ? List.of() : request.recipients(),
                request.attachments() == null ? List.of() : request.attachments(),
                Boolean.TRUE.equals(request.html()),
                request.textBody()
        );
        List<NotificationDispatchService.SendResult> results = dispatchService.send(message, request.transports());
        boolean anyFailed = results.stream().anyMatch(r -> "FAILED".equals(r.status()));
        boolean anySent = results.stream().anyMatch(r -> "SENT".equals(r.status()));
        if (anyFailed && !anySent) {
            return ResponseEntity.unprocessableEntity().body(new SendResponse(results));
        }
        return ResponseEntity.accepted().body(new SendResponse(results));
    }

    @GetMapping("/logs")
    public List<LogView> logs() {
        return dispatchService.recentLogs().stream()
                .map(l -> new LogView(
                        l.getId(),
                        l.getTransport().name(),
                        l.getRecipient(),
                        l.getSubject(),
                        l.getStatus(),
                        l.getErrorMessage(),
                        l.getCreatedAt()
                ))
                .toList();
    }

    private TransportView toView(NotificationTransportConfig cfg) {
        return new TransportView(
                cfg.getTransport().name(),
                cfg.isEnabled(),
                dispatchService.maskedConfig(cfg),
                cfg.getUpdatedAt()
        );
    }

    public record UpdateTransportRequest(boolean enabled, Map<String, Object> config) {
    }

    public record SendRequest(
            @NotBlank String subject,
            String body,
            List<String> recipients,
            List<TransportType> transports,
            List<NotificationAttachment> attachments,
            Boolean html,
            String textBody
    ) {
    }

    public record SendResponse(List<NotificationDispatchService.SendResult> results) {
    }

    public record TransportView(String transport, boolean enabled, Map<String, Object> config, Instant updatedAt) {
    }

    public record LogView(
            Long id,
            String transport,
            String recipient,
            String subject,
            String status,
            String errorMessage,
            Instant createdAt
    ) {
    }
}
