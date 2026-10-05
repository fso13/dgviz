package io.github.dgviz.notifications.transport;

import io.github.dgviz.notifications.domain.TransportType;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class TelegramNotificationTransport implements NotificationTransport {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotificationTransport.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final ObjectMapper objectMapper;
    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    public TelegramNotificationTransport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public TransportType type() {
        return TransportType.TELEGRAM;
    }

    @Override
    public void send(NotificationMessage message, String configJson) throws Exception {
        JsonNode cfg = objectMapper.readTree(configJson == null ? "{}" : configJson);
        String botToken = text(cfg, "botToken");
        String defaultChatId = text(cfg, "chatId");
        if (botToken == null || botToken.isBlank()) {
            throw new IllegalStateException("TELEGRAM transport: botToken is required");
        }

        List<String> chats = message.recipients() == null || message.recipients().isEmpty()
                ? (defaultChatId == null || defaultChatId.isBlank() ? List.of() : List.of(defaultChatId))
                : message.recipients();
        if (chats.isEmpty()) {
            throw new IllegalStateException("TELEGRAM transport: chatId / recipients required");
        }

        String text = buildText(message);
        log.info("Telegram send chats={} subject='{}' textLength={}", chats, message.subject(), text.length());
        for (String chatId : chats) {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("chat_id", chatId);
            payload.put("text", text);
            payload.put("disable_web_page_preview", true);
            Request request = new Request.Builder()
                    .url("https://api.telegram.org/bot" + botToken + "/sendMessage")
                    .post(RequestBody.create(payload.toString(), JSON))
                    .build();
            try (Response response = http.newCall(request).execute()) {
                String body = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) {
                    log.error("Telegram API chatId={} HTTP {} body={}", chatId, response.code(), body);
                    throw new IllegalStateException("Telegram API " + response.code() + ": " + body);
                }
                log.info("Telegram API accepted chatId={} HTTP {}", chatId, response.code());
            }
        }
    }

    private static String buildText(NotificationMessage message) {
        String subject = message.subject() == null ? "" : message.subject().trim();
        String body;
        if (message.textBody() != null && !message.textBody().isBlank()) {
            body = message.textBody().trim();
        } else {
            body = message.body() == null ? "" : message.body().trim();
            if (message.html() && !body.isBlank()) {
                body = body.replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                        .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                        .replaceAll("(?i)<br\\s*/?>", "\n")
                        .replaceAll("(?i)</p>", "\n")
                        .replaceAll("(?i)</tr>", "\n")
                        .replaceAll("(?i)</div>", "\n")
                        .replaceAll("<[^>]+>", " ")
                        .replace("&nbsp;", " ")
                        .replace("&amp;", "&")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replaceAll("[ \\t]+", " ")
                        .replaceAll("\\n{3,}", "\n\n")
                        .trim();
            }
        }
        if (subject.isEmpty()) {
            return body;
        }
        if (body.isEmpty()) {
            return subject;
        }
        return subject + "\n\n" + body;
    }

    private static String text(JsonNode cfg, String field) {
        JsonNode n = cfg.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }
}
