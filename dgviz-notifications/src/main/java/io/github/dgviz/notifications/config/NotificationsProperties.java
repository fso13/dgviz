package io.github.dgviz.notifications.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dgviz.notifications")
public class NotificationsProperties {

    private String apiKey = "dgviz-notify-dev-key";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
