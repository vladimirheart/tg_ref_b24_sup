package com.example.panel.model.knowledge;

import java.time.OffsetDateTime;

public record KnowledgeNotionSyncStatus(boolean running,
                                        String trigger,
                                        OffsetDateTime startedAt,
                                        OffsetDateTime finishedAt,
                                        int scanned,
                                        int changed,
                                        int created,
                                        int updated,
                                        int skipped,
                                        String message,
                                        String error) {

    public static KnowledgeNotionSyncStatus idle() {
        return new KnowledgeNotionSyncStatus(false, null, null, null, 0, 0, 0, 0, 0, null, null);
    }
}
