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
    void preservesNotionStructureTokensAsRenderedBlocks() {
        KnowledgeArticle article = new KnowledgeArticle();
        article.setContent("""
            <table_of_contents color="gray"/>
            <empty-block/>
            # Раздел
            Текст<br>ещё строка
            """);

        String html = service.renderContent(article, List.of());

        assertFalse(html.contains("&lt;table_of_contents"));
        assertFalse(html.contains("&lt;empty-block"));
        assertFalse(html.contains("&lt;br&gt;"));
        assertTrue(html.contains("knowledge-toc"));
        assertTrue(html.contains("knowledge-empty-block"));
        assertTrue(html.contains("Текст"));
        assertTrue(html.contains("ещё строка"));
    }

    @Test
    void rendersNotionCalloutAsFormattedBlock() {
        KnowledgeArticle article = new KnowledgeArticle();
        article.setContent("""
            <callout icon="❗" color="gray_bg">Перед закрытием смены проверьте открытые заказы.</callout>
            """);

        String html = service.renderContent(article, List.of());

        assertTrue(html.contains("knowledge-callout"));
        assertTrue(html.contains("knowledge-callout--gray-bg"));
        assertTrue(html.contains("❗"));
        assertTrue(html.contains("Перед закрытием смены проверьте открытые заказы."));
        assertFalse(html.contains("&lt;callout"));
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

    @Test
    void rewritesImportedNotionMediaUrlsUsingOriginalFileNameFallback() {
        KnowledgeArticle article = new KnowledgeArticle();
        article.setExternalId("ab2c3c4e-0584-46fd-a00b-ff55b90fcd84");
        article.setContent("![](https://prod-files-secure.s3.us-west-2.amazonaws.com/ws/file-id/image.png?X-Amz-Expires=3600)");

        KnowledgeArticleFile file = new KnowledgeArticleFile();
        file.setStoredPath("notion_ab2c3c4e058446fda00bff55b90fcd84_legacyhash_image.png");
        file.setOriginalName("image.png");
        file.setMimeType("image/png");

        String html = service.renderContent(article, List.of(file));

        assertTrue(html.contains("/api/attachments/knowledge-base/notion_ab2c3c4e058446fda00bff55b90fcd84_legacyhash_image.png"));
        assertFalse(html.contains("prod-files-secure.s3.us-west-2.amazonaws.com"));
    }
}
