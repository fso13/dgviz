package io.github.dgviz.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dgviz")
public class DgvizProperties {

    private final Admin admin = new Admin();
    private final Sync sync = new Sync();
    private final Notifications notifications = new Notifications();
    private final Invite invite = new Invite();
    private String workspaceDir = "/data/dgviz";

    public Admin getAdmin() {
        return admin;
    }

    public Sync getSync() {
        return sync;
    }

    public Notifications getNotifications() {
        return notifications;
    }

    public Invite getInvite() {
        return invite;
    }

    public String getWorkspaceDir() {
        return workspaceDir;
    }

    public void setWorkspaceDir(String workspaceDir) {
        this.workspaceDir = workspaceDir;
    }

    public static class Admin {
        private String defaultUsername = "admin";
        private String defaultPassword = "admin";

        public String getDefaultUsername() {
            return defaultUsername;
        }

        public void setDefaultUsername(String defaultUsername) {
            this.defaultUsername = defaultUsername;
        }

        public String getDefaultPassword() {
            return defaultPassword;
        }

        public void setDefaultPassword(String defaultPassword) {
            this.defaultPassword = defaultPassword;
        }
    }

    public static class Notifications {
        private String baseUrl = "http://localhost:8082";
        private String apiKey = "dgviz-notify-dev-key";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }

    public static class Invite {
        private int ttlDays = 2;
        private String publicBaseUrl = "http://localhost:8080";

        public int getTtlDays() {
            return ttlDays;
        }

        public void setTtlDays(int ttlDays) {
            this.ttlDays = ttlDays;
        }

        public String getPublicBaseUrl() {
            return publicBaseUrl;
        }

        public void setPublicBaseUrl(String publicBaseUrl) {
            this.publicBaseUrl = publicBaseUrl;
        }
    }

    public static class Sync {
        private String defaultCron = "0 0 2 * * *";
        private String nvdApiKey = "";
        private String snykApiToken = "";
        private int pageSize = 2000;
        private int maxPagesPerRun = 5;

        public String getDefaultCron() {
            return defaultCron;
        }

        public void setDefaultCron(String defaultCron) {
            this.defaultCron = defaultCron;
        }

        public String getNvdApiKey() {
            return nvdApiKey;
        }

        public void setNvdApiKey(String nvdApiKey) {
            this.nvdApiKey = nvdApiKey;
        }

        public String getSnykApiToken() {
            return snykApiToken;
        }

        public void setSnykApiToken(String snykApiToken) {
            this.snykApiToken = snykApiToken;
        }

        public int getPageSize() {
            return pageSize;
        }

        public void setPageSize(int pageSize) {
            this.pageSize = pageSize;
        }

        public int getMaxPagesPerRun() {
            return maxPagesPerRun;
        }

        public void setMaxPagesPerRun(int maxPagesPerRun) {
            this.maxPagesPerRun = maxPagesPerRun;
        }
    }
}
