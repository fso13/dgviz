package io.github.dgviz.notifications.security;

import io.github.dgviz.notifications.config.NotificationsProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthFilter.class);

    public static final String HEADER = "X-DGViz-Notify-Key";

    private final NotificationsProperties properties;

    public ApiKeyAuthFilter(NotificationsProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        if ("/actuator/health".equals(path) || "/health".equals(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        String key = request.getHeader(HEADER);
        if (key == null || !key.equals(properties.getApiKey())) {
            log.warn("Unauthorized {} {} — missing/invalid {}", request.getMethod(), path, HEADER);
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"unauthorized\"}");
            return;
        }
        log.debug("Authorized {} {}", request.getMethod(), path);
        filterChain.doFilter(request, response);
    }
}
