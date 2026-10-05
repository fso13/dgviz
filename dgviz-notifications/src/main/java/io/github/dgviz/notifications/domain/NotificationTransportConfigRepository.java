package io.github.dgviz.notifications.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationTransportConfigRepository extends JpaRepository<NotificationTransportConfig, Long> {

    Optional<NotificationTransportConfig> findByTransport(TransportType transport);
}
