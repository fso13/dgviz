package io.github.dgviz.notifications;

import io.github.dgviz.config.DgvizProperties;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class NotificationsClient {

    private static final Logger log = LoggerFactory.getLogger(NotificationsClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String API_KEY_HEADER = "X-DGViz-Notify-Key";

    private final DgvizProperties properties;
    private final ObjectMapper objectMapper;
    private final OkHttpClient http = new OkHttpClient.Builder()
            .callTimeout(Duration.ofSeconds(90))
            .build();

    public NotificationsClient(DgvizProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean isReachable() {
        try {
            Request request = new Request.Builder()
                    .url(baseUrl() + "/health")
                    .get()
                    .build();
            try (Response response = http.newCall(request).execute()) {
                boolean ok = response.isSuccessful();
                if (!ok) {
                    log.warn("Notifications health check HTTP {}", response.code());
                }
                return ok;
            }
        } catch (Exception ex) {
            log.warn("Notifications service unreachable at {}: {}", baseUrl(), ex.toString());
            return false;
        }
    }

    public List<Map<String, Object>> listTransports() throws IOException {
        return getList("/api/v1/transports");
    }

    public List<Map<String, Object>> listLogs() throws IOException {
        return getList("/api/v1/logs");
    }

    public void updateTransport(String type, boolean enabled, Map<String, Object> config) throws IOException {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("enabled", enabled);
        body.set("config", objectMapper.valueToTree(config));
        String url = baseUrl() + "/api/v1/transports/" + type;
        log.info("Updating notification transport {} enabled={} url={}", type, enabled, url);
        Request request = new Request.Builder()
                .url(url)
                .header(API_KEY_HEADER, properties.getNotifications().getApiKey())
                .put(RequestBody.create(body.toString(), JSON))
                .build();
        try (Response response = http.newCall(request).execute()) {
            String responseBody = bodyString(response);
            if (!response.isSuccessful()) {
                log.error("Update transport {} failed HTTP {} body={}", type, response.code(), responseBody);
                throw new IOException("Update transport failed: HTTP " + response.code() + " " + responseBody);
            }
            log.info("Transport {} updated OK", type);
        }
    }

    public List<SendResultView> sendTest(
            String subject,
            String body,
            List<String> recipients,
            String transport
    ) throws IOException {
        return send(subject, body, recipients, transport);
    }

    public List<SendResultView> sendEmail(String subject, String body, List<String> recipients) throws IOException {
        return send(subject, body, recipients, "EMAIL", List.of(), false, null);
    }

    public List<SendResultView> sendEmail(
            String subject,
            String body,
            List<String> recipients,
            List<Attachment> attachments
    ) throws IOException {
        return send(subject, body, recipients, "EMAIL", attachments, false, null);
    }

    public List<SendResultView> sendHtmlEmail(
            String subject,
            String htmlBody,
            String textBody,
            List<String> recipients,
            List<Attachment> attachments
    ) throws IOException {
        return send(subject, htmlBody, recipients, "EMAIL",
                attachments == null ? List.of() : attachments, true, textBody);
    }

    public List<SendResultView> send(
            String subject,
            String body,
            List<String> recipients,
            String transport
    ) throws IOException {
        return send(subject, body, recipients, transport, List.of(), false, null);
    }

    public List<SendResultView> send(
            String subject,
            String body,
            List<String> recipients,
            String transport,
            List<Attachment> attachments
    ) throws IOException {
        return send(subject, body, recipients, transport, attachments, false, null);
    }

    public List<SendResultView> send(
            String subject,
            String body,
            List<String> recipients,
            String transport,
            List<Attachment> attachments,
            boolean html,
            String textBody
    ) throws IOException {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("subject", subject);
        payload.put("body", body);
        payload.put("html", html);
        if (textBody != null) {
            payload.put("textBody", textBody);
        }
        payload.set("recipients", objectMapper.valueToTree(recipients));
        if (transport != null && !transport.isBlank()) {
            payload.set("transports", objectMapper.valueToTree(List.of(transport)));
        }
        if (attachments != null && !attachments.isEmpty()) {
            payload.set("attachments", objectMapper.valueToTree(attachments));
        }
        String url = baseUrl() + "/api/v1/notifications";
        log.info(
                "Sending notification transport={} recipients={} attachments={} html={} subject='{}' url={}",
                transport,
                recipients,
                attachments == null ? 0 : attachments.size(),
                html,
                subject,
                url
        );
        Request request = new Request.Builder()
                .url(url)
                .header(API_KEY_HEADER, properties.getNotifications().getApiKey())
                .post(RequestBody.create(payload.toString(), JSON))
                .build();
        try (Response response = http.newCall(request).execute()) {
            String responseBody = bodyString(response);
            log.info("Notifications API HTTP {} body={}", response.code(), responseBody);
            if (!response.isSuccessful() && response.code() != 202) {
                throw new IOException("Send failed: HTTP " + response.code() + " " + responseBody);
            }
            return parseResults(responseBody);
        }
    }

    public static Attachment attachment(String filename, String contentType, byte[] content) {
        return new Attachment(
                filename,
                contentType,
                Base64.getEncoder().encodeToString(content == null ? new byte[0] : content)
        );
    }

    private List<SendResultView> parseResults(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        JsonNode results = root.get("results");
        List<SendResultView> out = new ArrayList<>();
        if (results == null || !results.isArray()) {
            return out;
        }
        for (JsonNode item : results) {
            out.add(new SendResultView(
                    text(item, "transport"),
                    text(item, "status"),
                    text(item, "message")
            ));
        }
        return out;
    }

    private List<Map<String, Object>> getList(String path) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl() + path)
                .header(API_KEY_HEADER, properties.getNotifications().getApiKey())
                .get()
                .build();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = bodyString(response);
                log.error("GET {} failed HTTP {} body={}", path, response.code(), body);
                throw new IOException("GET " + path + " failed: HTTP " + response.code());
            }
            String json = bodyString(response);
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
        }
    }

    private String baseUrl() {
        String url = properties.getNotifications().getBaseUrl();
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    private static String bodyString(Response response) throws IOException {
        return response.body() == null ? "" : response.body().string();
    }

    private static String text(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }

    public record SendResultView(String transport, String status, String message) {
    }

    public record Attachment(String filename, String contentType, String contentBase64) {
    }
}
