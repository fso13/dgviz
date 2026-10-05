package io.github.dgviz.config;

import io.github.dgviz.settings.AppSettingRepository;
import io.github.dgviz.settings.AppSettingsService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fills blank DB settings from environment / application properties on startup.
 */
@Component
@Order(2)
public class SettingsEnvBootstrap implements ApplicationRunner {

    private final AppSettingRepository settingRepository;
    private final DgvizProperties properties;

    public SettingsEnvBootstrap(AppSettingRepository settingRepository, DgvizProperties properties) {
        this.settingRepository = settingRepository;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        fillIfBlank(AppSettingsService.KEY_NVD_API_KEY, properties.getSync().getNvdApiKey());
        fillIfBlank(AppSettingsService.KEY_SNYK_TOKEN, properties.getSync().getSnykApiToken());
        fillIfBlank(AppSettingsService.KEY_DEFAULT_CRON, properties.getSync().getDefaultCron());
        ensureSetting(
                AppSettingsService.KEY_INVITE_TTL_DAYS,
                String.valueOf(properties.getInvite().getTtlDays()),
                "Срок действия invite-ссылки в днях",
                false);
        ensureSetting(
                AppSettingsService.KEY_INVITE_PUBLIC_BASE_URL,
                properties.getInvite().getPublicBaseUrl(),
                "Публичный URL приложения для invite-ссылок",
                false);
    }

    private void fillIfBlank(String key, String envValue) {
        if (envValue == null || envValue.isBlank()) {
            return;
        }
        settingRepository.findByKey(key).ifPresent(setting -> {
            if (setting.getValue() == null || setting.getValue().isBlank()) {
                setting.setValue(envValue);
                settingRepository.save(setting);
            }
        });
    }

    private void ensureSetting(String key, String value, String description, boolean secret) {
        if (settingRepository.findByKey(key).isPresent()) {
            return;
        }
        var setting = new io.github.dgviz.settings.AppSetting();
        setting.setKey(key);
        setting.setValue(value);
        setting.setDescription(description);
        setting.setSecret(secret);
        setting.setUpdatedAt(java.time.Instant.now());
        settingRepository.save(setting);
    }
}
