package com.example.panel.model.knowledge;

import java.time.OffsetDateTime;
import java.util.List;

public record KnowledgeArticleDetails(Long id,
                                      String title,
                                      String department,
                                      String articleType,
                                      String status,
                                      String author,
                                      String direction,
                                      String directionSubtype,
                                      String summary,
                                      String content,
                                      OffsetDateTime createdAt,
                                      OffsetDateTime updatedAt,
                                      List<KnowledgeAttachmentDto> attachments) {
}
