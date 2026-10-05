package io.github.dgviz.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;

/**
 * Persist HTTP sessions in PostgreSQL so logins survive application restarts.
 */
@Configuration
@EnableJdbcHttpSession(maxInactiveIntervalInSeconds = 12 * 60 * 60)
public class SessionConfiguration {
}
