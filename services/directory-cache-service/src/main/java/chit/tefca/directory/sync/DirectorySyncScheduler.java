package chit.tefca.directory.sync;

import chit.tefca.directory.config.DirectoryProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled trigger for periodic directory synchronization.
 * Delegates to DirectorySyncService for actual sync logic.
 */
@Component
@RequiredArgsConstructor
public class DirectorySyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(DirectorySyncScheduler.class);

    private final DirectorySyncService syncService;
    private final DirectoryProperties properties;

    @Scheduled(fixedDelayString = "${tefca.directory.sync-interval-ms:3600000}",
               initialDelayString = "${tefca.directory.sync-initial-delay-ms:60000}")
    public void scheduledSync() {
        if (!properties.isSyncEnabled()) {
            log.debug("Directory sync is disabled, skipping scheduled run");
            return;
        }
        log.info("Starting scheduled directory sync");
        try {
            syncService.syncFromUpstream();
        } catch (Exception e) {
            log.error("Scheduled directory sync failed", e);
        }
    }
}
