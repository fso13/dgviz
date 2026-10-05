package io.github.dgviz.sync;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class SyncConfigService {

    private final SyncSourceConfigRepository configRepository;
    private final SyncRunRepository syncRunRepository;
    private final DynamicSyncScheduler scheduler;
    private final VulnerabilitySyncService syncService;

    public SyncConfigService(
            SyncSourceConfigRepository configRepository,
            SyncRunRepository syncRunRepository,
            DynamicSyncScheduler scheduler,
            VulnerabilitySyncService syncService
    ) {
        this.configRepository = configRepository;
        this.syncRunRepository = syncRunRepository;
        this.scheduler = scheduler;
        this.syncService = syncService;
    }

    public List<SyncSourceConfig> findAll() {
        return configRepository.findAllByOrderBySourceCodeAsc();
    }

    public List<SyncRun> recentRuns() {
        return syncRunRepository.findTop20ByOrderByStartedAtDesc();
    }

    public SyncSourceConfig getById(Long id) {
        return configRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sync config not found: " + id));
    }

    @Transactional
    public SyncSourceConfig update(Long id, boolean enabled, String cronExpression, String apiBaseUrl) {
        SyncSourceConfig config = getById(id);
        config.setEnabled(enabled);
        config.setCronExpression(cronExpression);
        config.setApiBaseUrl(apiBaseUrl);
        config.setUpdatedAt(Instant.now());
        SyncSourceConfig saved = configRepository.save(config);
        scheduler.reload(saved.getSourceCode());
        return saved;
    }

    public void triggerAsync(Long id) {
        SyncSourceConfig config = getById(id);
        syncService.syncAsync(config.getSourceCode());
    }
}
