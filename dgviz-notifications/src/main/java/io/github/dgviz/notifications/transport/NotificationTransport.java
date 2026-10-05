package io.github.dgviz.notifications.transport;

import io.github.dgviz.notifications.domain.TransportType;

public interface NotificationTransport {

    TransportType type();

    void send(NotificationMessage message, String configJson) throws Exception;
}
