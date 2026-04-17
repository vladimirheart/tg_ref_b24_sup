package com.example.panel.model.knowledge;

import java.util.List;

public record KnowledgeNotionImportPreview(String mode,
                                           int totalPages,
                                           int matchedPages,
                                           List<KnowledgeNotionImportPreviewItem> items) {
}
