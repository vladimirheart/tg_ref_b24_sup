package com.example.panel.model.knowledge;

import java.time.OffsetDateTime;

public record KnowledgeNotionImportPreviewItem(String externalId,
                                               String title,
                                               String author,
                                               String summary,
                                               String department,
                                               String articleType,
                                               String notionStatus,
                                               String localStatus,
                                               String externalUrl,
                                               OffsetDateTime externalUpdatedAt,
                                               boolean alreadyImported,
                                               boolean changedInNotion,
                                               String syncStateLabel) {

    public String actionLabel() {
        return alreadyImported ? "Обновить" : "Создать";
    }
}
