package com.example.panel.background;

import com.example.panel.service.AnalyticsService;
import com.example.panel.storage.AttachmentService;
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

    public HousekeepingScheduler(CacheManager cacheManager,
                                 AnalyticsService analyticsService,
                                 AttachmentService attachmentService) {
        this.cacheManager = cacheManager;
        this.analyticsService = analyticsService;
        this.attachmentService = attachmentService;
    }

    @Scheduled(cron = "0 */15 * * * *")
    public void warmUpAnalyticsCache() {
        log.debug("Refreshing analytics cache");
        analyticsService.loadTicketSummary();
        analyticsService.loadClientSummary();
    }

    @Scheduled(cron = "0 0 * * * *")
    public void cleanupDrafts() {
        try {
            attachmentService.purgeDraftAttachments("draft_");
        } catch (Exception ex) {
            log.warn("Failed to purge draft attachments", ex);
        }
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
