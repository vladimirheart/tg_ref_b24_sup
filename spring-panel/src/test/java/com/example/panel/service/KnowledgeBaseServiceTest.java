package com.example.panel.service;

import com.example.panel.entity.KnowledgeArticle;
import com.example.panel.entity.KnowledgeArticleFile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeBaseServiceTest {

    private final KnowledgeBaseService service = new KnowledgeBaseService(
        null,
        null,
        new KnowledgeMarkdownRenderer()
    );

    @Test
    void stripsNotionPseudoTagsAndBreakMarkupFromRenderedContent() {
        KnowledgeArticle article = new KnowledgeArticle();
        article.setContent("""
            <table_of_contents color="gray"/>
            <empty-block/>
            Текст<br>ещё строка
            """);

        String html = service.renderContent(article, List.of());

        assertFalse(html.contains("table_of_contents"));
        assertFalse(html.contains("empty-block"));
        assertFalse(html.contains("&lt;br&gt;"));
        assertTrue(html.contains("Текст"));
        assertTrue(html.contains("ещё строка"));
    }

    @Test
    void rewritesImportedNotionMediaUrlsToLocalAttachmentEndpoint() {
        KnowledgeArticle article = new KnowledgeArticle();
        article.setExternalId("ab2c3c4e-0584-46fd-a00b-ff55b90fcd84");
        article.setContent("![image](https://example.com/media/manual.png)");

        KnowledgeArticleFile file = new KnowledgeArticleFile();
        file.setStoredPath("notion_ab2c3c4e058446fda00bff55b90fcd84_5d6eee8368e8_manual.png");
        file.setOriginalName("manual.png");
        file.setMimeType("image/png");

        String html = service.renderContent(article, List.of(file));

        assertTrue(html.contains("/api/attachments/knowledge-base/notion_ab2c3c4e058446fda00bff55b90fcd84_5d6eee8368e8_manual.png"));
        assertFalse(html.contains("https://example.com/media/manual.png"));
    }
}
