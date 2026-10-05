package io.github.dgviz.settings;

import io.github.dgviz.config.DgvizProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class AppSettingsService {

    public static final String KEY_DEFAULT_CRON = "sync.default-cron";
    public static final String KEY_NVD_API_KEY = "sync.nvd-api-key";
    public static final String KEY_SNYK_TOKEN = "sync.snyk-api-token";
    public static final String KEY_PAGE_SIZE = "sync.page-size";
    public static final String KEY_MAX_PAGES = "sync.max-pages-per-run";
    public static final String KEY_INVITE_TTL_DAYS = "invite.ttl-days";
    public static final String KEY_INVITE_PUBLIC_BASE_URL = "invite.public-base-url";

    private final AppSettingRepository repository;
    private final DgvizProperties properties;

    public AppSettingsService(AppSettingRepository repository, DgvizProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public List<AppSetting> findAll() {
        return repository.findAllByOrderByKeyAsc();
    }

    public AppSetting getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Setting not found: " + id));
    }

    public Optional<String> findValue(String key) {
        return repository.findByKey(key).map(AppSetting::getValue);
    }

    public String get(String key, String defaultValue) {
        return findValue(key).filter(v -> v != null && !v.isBlank()).orElse(defaultValue);
    }

    public String getNvdApiKey() {
        return get(KEY_NVD_API_KEY, properties.getSync().getNvdApiKey());
    }

    public String getSnykApiToken() {
        return get(KEY_SNYK_TOKEN, properties.getSync().getSnykApiToken());
    }

    public String getDefaultCron() {
        return get(KEY_DEFAULT_CRON, properties.getSync().getDefaultCron());
    }

    public int getPageSize() {
        return parseInt(get(KEY_PAGE_SIZE, String.valueOf(properties.getSync().getPageSize())),
                properties.getSync().getPageSize());
    }

    public int getMaxPagesPerRun() {
        return parseInt(get(KEY_MAX_PAGES, String.valueOf(properties.getSync().getMaxPagesPerRun())),
                properties.getSync().getMaxPagesPerRun());
    }

    public int getInviteTtlDays() {
        return Math.max(1, parseInt(
                get(KEY_INVITE_TTL_DAYS, String.valueOf(properties.getInvite().getTtlDays())),
                properties.getInvite().getTtlDays()));
    }

    public String getInvitePublicBaseUrl() {
        return get(KEY_INVITE_PUBLIC_BASE_URL, properties.getInvite().getPublicBaseUrl()).replaceAll("/+$", "");
    }

    @Transactional
    public AppSetting create(String key, String value, String description, boolean secret) {
        if (repository.existsByKey(key)) {
            throw new IllegalArgumentException("Setting already exists: " + key);
        }
        AppSetting setting = new AppSetting();
        setting.setKey(key.trim());
        setting.setValue(value);
        setting.setDescription(description);
        setting.setSecret(secret);
        setting.setUpdatedAt(Instant.now());
        return repository.save(setting);
    }

    @Transactional
    public AppSetting update(Long id, String value, String description, boolean secret) {
        AppSetting setting = getById(id);
        // Keep previous secret if UI posted masked placeholder
        if (setting.isSecret() && value != null && value.contains("••••")) {
            // leave value unchanged
        } else {
            setting.setValue(value);
        }
        setting.setDescription(description);
        setting.setSecret(secret);
        setting.setUpdatedAt(Instant.now());
        return repository.save(setting);
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception ex) {
            return fallback;
        }
    }
}
