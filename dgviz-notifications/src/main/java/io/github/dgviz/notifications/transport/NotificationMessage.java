package io.github.dgviz.notifications.transport;

import java.util.List;

public record NotificationMessage(
        String subject,
        String body,
        List<String> recipients,
        List<NotificationAttachment> attachments,
        boolean html,
        String textBody
) {
    public NotificationMessage(String subject, String body, List<String> recipients) {
        this(subject, body, recipients, List.of(), false, null);
    }

    public NotificationMessage(
            String subject,
            String body,
            List<String> recipients,
            List<NotificationAttachment> attachments
    ) {
        this(subject, body, recipients, attachments, false, null);
    }

    public List<NotificationAttachment> attachments() {
        return attachments == null ? List.of() : attachments;
    }
}
