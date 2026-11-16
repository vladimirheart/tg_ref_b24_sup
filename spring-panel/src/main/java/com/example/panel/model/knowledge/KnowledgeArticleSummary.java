package com.example.panel.model.knowledge;

import java.time.OffsetDateTime;

public record KnowledgeArticleSummary(Long id,
                                      String title,
                                      Long sectionId,
                                      String sectionTitle,
                                      String department,
                                      String status,
                                      String author,
                                      String summary,
                                      OffsetDateTime updatedAt) {
}
