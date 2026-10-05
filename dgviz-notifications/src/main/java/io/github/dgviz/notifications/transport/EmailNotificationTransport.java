package io.github.dgviz.notifications.transport;

import io.github.dgviz.notifications.domain.TransportType;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

@Component
public class EmailNotificationTransport implements NotificationTransport {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationTransport.class);

    private final ObjectMapper objectMapper;

    public EmailNotificationTransport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public TransportType type() {
        return TransportType.EMAIL;
    }

    @Override
    public void send(NotificationMessage message, String configJson) throws Exception {
        JsonNode cfg = objectMapper.readTree(configJson == null ? "{}" : configJson);
        String host = text(cfg, "host");
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("EMAIL transport: host is required");
        }
        int port = cfg.path("port").asInt(587);
        if (port <= 0) {
            port = 587;
        }
        String username = text(cfg, "username");
        String password = text(cfg, "password");
        String from = text(cfg, "from");
        if (from == null || from.isBlank()) {
            from = username;
        }
        if (from == null || from.isBlank()) {
            throw new IllegalStateException("EMAIL transport: from/username is required");
        }
        boolean starttls = cfg.path("starttls").asBoolean(true);
        List<String> recipients = message.recipients() == null ? List.of() : message.recipients().stream()
                .filter(r -> r != null && !r.isBlank())
                .toList();
        if (recipients.isEmpty()) {
            throw new IllegalStateException("EMAIL transport: at least one recipient is required");
        }

        log.info(
                "SMTP send host={} port={} starttls={} auth={} from={} to={} subject='{}'",
                host,
                port,
                starttls,
                username != null && !username.isBlank(),
                from,
                recipients,
                message.subject()
        );

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        if (username != null && !username.isBlank()) {
            sender.setUsername(username);
            sender.setPassword(password == null ? "" : password);
        }
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", username != null && !username.isBlank() ? "true" : "false");
        props.put("mail.smtp.starttls.enable", String.valueOf(starttls));
        props.put("mail.smtp.connectiontimeout", "15000");
        props.put("mail.smtp.timeout", "20000");
        props.put("mail.smtp.writetimeout", "20000");

        List<NotificationAttachment> attachments = message.attachments();
        boolean html = message.html();
        String plain = message.textBody();
        // multipart needed for attachments and for text+html alternative
        boolean multipart = !attachments.isEmpty() || (html && plain != null && !plain.isBlank());
        JavaMailSender mailSender = sender;
        for (String recipient : recipients) {
            log.debug("Creating MIME message for {} multipart={} html={} attachments={}",
                    recipient, multipart, html, attachments.size());
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, multipart, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(recipient);
            helper.setSubject(message.subject() == null ? "DGViz notification" : message.subject());
            String body = message.body() == null ? "" : message.body();
            if (html && plain != null && !plain.isBlank()) {
                helper.setText(plain, body);
            } else {
                helper.setText(body, html);
            }
            for (NotificationAttachment attachment : attachments) {
                if (attachment == null || attachment.contentBase64() == null || attachment.contentBase64().isBlank()) {
                    continue;
                }
                String name = attachment.filename() == null || attachment.filename().isBlank()
                        ? "attachment.bin"
                        : attachment.filename();
                String type = attachment.contentType() == null || attachment.contentType().isBlank()
                        ? "application/octet-stream"
                        : attachment.contentType();
                byte[] bytes = Base64.getDecoder().decode(attachment.contentBase64());
                helper.addAttachment(name, new org.springframework.core.io.ByteArrayResource(bytes) {
                    @Override
                    public String getFilename() {
                        return name;
                    }
                }, type);
            }
            mailSender.send(mime);
            log.info("SMTP accepted message for {}", recipient);
        }
    }

    private static String text(JsonNode cfg, String field) {
        JsonNode n = cfg.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }
}
