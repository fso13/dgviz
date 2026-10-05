package io.github.dgviz.notifications.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    List<NotificationLog> findTop50ByOrderByCreatedAtDesc();
}
