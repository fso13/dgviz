package io.github.dgviz.notifications.transport;

/**
 * File attachment for EMAIL transport. {@code contentBase64} is raw file bytes, Base64-encoded.
 */
public record NotificationAttachment(
        String filename,
        String contentType,
        String contentBase64
) {
}
