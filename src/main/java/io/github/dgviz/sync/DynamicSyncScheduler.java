package io.github.dgviz.sync;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Registers per-source cron jobs from admin-editable {@link SyncSourceConfig}.
 */
@Component
public class DynamicSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(DynamicSyncScheduler.class);

    private final SyncSourceConfigRepository configRepository;
    private final VulnerabilitySyncService syncService;
    private final ThreadPoolTaskScheduler taskScheduler;
    private final Map<String, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    public DynamicSyncScheduler(
            SyncSourceConfigRepository configRepository,
            VulnerabilitySyncService syncService,
            ThreadPoolTaskScheduler taskScheduler
    ) {
        this.configRepository = configRepository;
        this.syncService = syncService;
        this.taskScheduler = taskScheduler;
    }

    @PostConstruct
    public void init() {
        reloadAll();
    }

    public synchronized void reloadAll() {
        futures.values().forEach(f -> f.cancel(false));
        futures.clear();
        List<SyncSourceConfig> configs = configRepository.findAll();
        for (SyncSourceConfig config : configs) {
            schedule(config);
        }
        log.info("Reloaded {} vulnerability sync schedules", futures.size());
    }

    public synchronized void reload(String sourceCode) {
        ScheduledFuture<?> existing = futures.remove(sourceCode);
        if (existing != null) {
            existing.cancel(false);
        }
        configRepository.findBySourceCode(sourceCode).ifPresent(this::schedule);
    }

    private void schedule(SyncSourceConfig config) {
        if (!config.isEnabled()) {
            log.info("Sync source {} disabled — not scheduled", config.getSourceCode());
            return;
        }
        try {
            CronTrigger trigger = new CronTrigger(config.getCronExpression(), ZoneOffset.UTC);
            ScheduledFuture<?> future = taskScheduler.schedule(
                    () -> syncService.syncAsync(config.getSourceCode()),
                    trigger
            );
            futures.put(config.getSourceCode(), future);
            log.info("Scheduled {} with cron '{}'", config.getSourceCode(), config.getCronExpression());
        } catch (Exception ex) {
            log.error("Invalid cron for {}: {}", config.getSourceCode(), config.getCronExpression(), ex);
        }
    }
}
