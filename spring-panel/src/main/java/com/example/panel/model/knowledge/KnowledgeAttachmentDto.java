package com.example.panel.model.knowledge;

import java.time.OffsetDateTime;

public record KnowledgeAttachmentDto(Long id,
                                     String originalName,
                                     String storedPath,
                                     String mimeType,
                                     Long size,
                                     OffsetDateTime uploadedAt) {
}
