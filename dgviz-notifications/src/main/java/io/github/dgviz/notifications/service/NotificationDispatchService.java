package io.github.dgviz.notifications.service;

import io.github.dgviz.notifications.domain.NotificationLog;
import io.github.dgviz.notifications.domain.NotificationLogRepository;
import io.github.dgviz.notifications.domain.NotificationTransportConfig;
import io.github.dgviz.notifications.domain.NotificationTransportConfigRepository;
import io.github.dgviz.notifications.domain.TransportType;
import io.github.dgviz.notifications.transport.NotificationMessage;
import io.github.dgviz.notifications.transport.NotificationTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);

    private final Map<TransportType, NotificationTransport> transports;
    private final NotificationTransportConfigRepository configRepository;
    private final NotificationLogRepository logRepository;
    private final ObjectMapper objectMapper;

    public NotificationDispatchService(
            List<NotificationTransport> transportList,
            NotificationTransportConfigRepository configRepository,
            NotificationLogRepository logRepository,
            ObjectMapper objectMapper
    ) {
        this.transports = new EnumMap<>(TransportType.class);
        for (NotificationTransport t : transportList) {
            this.transports.put(t.type(), t);
            log.info("Registered notification transport strategy: {}", t.type());
        }
        this.configRepository = configRepository;
        this.logRepository = logRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<NotificationTransportConfig> listConfigs() {
        return configRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<NotificationLog> recentLogs() {
        return logRepository.findTop50ByOrderByCreatedAtDesc();
    }

    @Transactional
    public NotificationTransportConfig updateConfig(TransportType type, boolean enabled, Map<String, Object> config) {
        NotificationTransportConfig entity = configRepository.findByTransport(type)
                .orElseThrow(() -> new IllegalArgumentException("Unknown transport: " + type));
        Map<String, Object> merged = new LinkedHashMap<>(readConfigMap(entity.getConfigJson()));
        if (config != null) {
            for (Map.Entry<String, Object> e : config.entrySet()) {
                Object value = e.getValue();
                if (value == null) {
                    continue;
                }
                if (value instanceof String s && isMaskedSecret(s)) {
                    log.debug("Keeping existing secret for transport {} field {}", type, e.getKey());
                    continue;
                }
                merged.put(e.getKey(), value);
            }
        }
        entity.setEnabled(enabled);
        entity.setConfigJson(objectMapper.writeValueAsString(merged));
        entity.setUpdatedAt(Instant.now());
        NotificationTransportConfig saved = configRepository.save(entity);
        log.info("Updated transport {} enabled={} configKeys={}", type, enabled, merged.keySet());
        return saved;
    }

    @Transactional
    public List<SendResult> send(NotificationMessage message, List<TransportType> targets) {
        List<TransportType> types = targets == null || targets.isEmpty()
                ? List.of(TransportType.values())
                : targets;
        log.info(
                "Dispatching notification subject='{}' recipients={} transports={}",
                message.subject(),
                message.recipients(),
                types
        );
        List<SendResult> results = new ArrayList<>();
        for (TransportType type : types) {
            NotificationTransportConfig cfg = configRepository.findByTransport(type).orElse(null);
            if (cfg == null || !cfg.isEnabled()) {
                String reason = cfg == null ? "transport missing in DB" : "transport disabled";
                log.warn("Skipping transport {}: {}", type, reason);
                persistLog(type, firstRecipient(message), message.subject(), "SKIPPED", reason);
                results.add(new SendResult(type, "SKIPPED", reason));
                continue;
            }
            NotificationTransport transport = transports.get(type);
            if (transport == null) {
                log.error("No strategy bean for transport {}", type);
                persistLog(type, firstRecipient(message), message.subject(), "FAILED", "no strategy");
                results.add(new SendResult(type, "FAILED", "no strategy"));
                continue;
            }
            try {
                log.info("Sending via {} to {}", type, message.recipients());
                transport.send(message, cfg.getConfigJson());
                log.info("Transport {} SENT subject='{}'", type, message.subject());
                persistLog(type, firstRecipient(message), message.subject(), "SENT", null);
                results.add(new SendResult(type, "SENT", null));
            } catch (Exception ex) {
                log.error("Transport {} FAILED subject='{}': {}", type, message.subject(), ex.toString(), ex);
                persistLog(type, firstRecipient(message), message.subject(), "FAILED", ex.getMessage());
                results.add(new SendResult(type, "FAILED", ex.getMessage()));
            }
        }
        log.info("Dispatch finished results={}", results);
        return results;
    }

    public Map<String, Object> maskedConfig(NotificationTransportConfig entity) {
        Map<String, Object> map = new LinkedHashMap<>(readConfigMap(entity.getConfigJson()));
        for (String key : List.of("password", "botToken")) {
            Object v = map.get(key);
            if (v instanceof String s && !s.isBlank()) {
                map.put(key, mask(s));
            }
        }
        return map;
    }

    private Map<String, Object> readConfigMap(String json) {
        try {
            return objectMapper.readValue(
                    json == null || json.isBlank() ? "{}" : json,
                    new TypeReference<Map<String, Object>>() {
                    }
            );
        } catch (Exception ex) {
            log.warn("Failed to parse transport config JSON: {}", ex.toString());
            return new LinkedHashMap<>();
        }
    }

    private void persistLog(TransportType type, String recipient, String subject, String status, String error) {
        NotificationLog entry = new NotificationLog();
        entry.setTransport(type);
        entry.setRecipient(recipient);
        entry.setSubject(subject);
        entry.setStatus(status);
        entry.setErrorMessage(error);
        logRepository.save(entry);
    }

    private static String firstRecipient(NotificationMessage message) {
        if (message.recipients() == null || message.recipients().isEmpty()) {
            return null;
        }
        return message.recipients().getFirst();
    }

    private static boolean isMaskedSecret(String value) {
        return value.contains("••••") || value.contains("****");
    }

    private static String mask(String value) {
        if (value.length() <= 4) {
            return "••••";
        }
        return value.substring(0, 2) + "••••" + value.substring(value.length() - 2);
    }

    public record SendResult(TransportType transport, String status, String message) {
    }
}
