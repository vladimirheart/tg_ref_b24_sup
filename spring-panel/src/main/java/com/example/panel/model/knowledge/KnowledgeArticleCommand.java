package com.example.panel.model.knowledge;

public record KnowledgeArticleCommand(Long id,
                                      String title,
                                      String department,
                                      String articleType,
                                      String status,
                                      String author,
                                      String direction,
                                      String directionSubtype,
                                      String summary,
                                      String content) {
}