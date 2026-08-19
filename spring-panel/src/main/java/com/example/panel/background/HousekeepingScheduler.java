package com.example.panel.background;

import com.example.panel.service.AnalyticsService;
import com.example.panel.service.RuntimeCoordinationService;
import com.example.panel.storage.AttachmentService;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HousekeepingScheduler {

    private static final Logger log = LoggerFactory.getLogger(HousekeepingScheduler.class);

    private final CacheManager cacheManager;
    private final AnalyticsService analyticsService;
    private final AttachmentService attachmentService;
    private final RuntimeCoordinationService runtimeCoordinationService;

    public HousekeepingScheduler(CacheManager cacheManager,
                                 AnalyticsService analyticsService,
                                 AttachmentService attachmentService,
                                 RuntimeCoordinationService runtimeCoordinationService) {
        this.cacheManager = cacheManager;
        this.analyticsService = analyticsService;
        this.attachmentService = attachmentService;
        this.runtimeCoordinationService = runtimeCoordinationService;
    }

    @Scheduled(cron = "0 */15 * * * *")
    public void warmUpAnalyticsCache() {
        log.debug("Refreshing analytics cache");
        analyticsService.loadTicketSummary();
        analyticsService.loadClientSummary();
    }

    @Scheduled(cron = "0 0 * * * *")
    public void cleanupDrafts() {
        runtimeCoordinationService.runWithLease("housekeeping-draft-cleanup", Duration.ofMinutes(10), () -> {
            try {
                attachmentService.purgeDraftAttachments("draft_");
            } catch (Exception ex) {
                log.warn("Failed to purge draft attachments", ex);
            }
        });
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });
    }
}
