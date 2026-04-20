package com.example.panel.background;

import com.example.panel.service.KnowledgeBaseNotionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeBaseNotionSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseNotionSyncScheduler.class);

    private final KnowledgeBaseNotionService knowledgeBaseNotionService;
    private final boolean enabled;

    public KnowledgeBaseNotionSyncScheduler(KnowledgeBaseNotionService knowledgeBaseNotionService,
                                            @Value("${panel.knowledge.notion-sync.enabled:true}") boolean enabled) {
        this.knowledgeBaseNotionService = knowledgeBaseNotionService;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${panel.knowledge.notion-sync.cron:0 20 * * * *}")
    public void syncChangedArticles() {
        if (!enabled) {
            return;
        }
        try {
            var result = knowledgeBaseNotionService.syncLinkedArticlesFromNotion();
            log.info("Scheduled Notion knowledge sync finished: total={}, changed={}, updated={}, skipped={}",
                result.totalPages(), result.selectedPages(), result.updated(), result.skipped());
        } catch (Exception ex) {
            log.warn("Scheduled Notion knowledge sync failed: {}", ex.getMessage());
        }
    }
}
