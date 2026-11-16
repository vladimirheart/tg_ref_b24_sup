package com.example.panel.model.knowledge;

public record KnowledgeWikiLink(Long articleId,
                                String title,
                                String sourceReference,
                                String label,
                                boolean accessible) {
}
