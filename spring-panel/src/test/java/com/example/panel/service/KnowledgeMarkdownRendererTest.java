package com.example.panel.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeMarkdownRendererTest {

    private final KnowledgeMarkdownRenderer renderer = new KnowledgeMarkdownRenderer();

    @Test
    void rendersCommonNotionMarkdownStructures() {
        String html = renderer.render("""
            # Заголовок

            - [x] Первый пункт
            - [ ] Второй пункт

            | Колонка | Значение |
            | --- | --- |
            | Автор | Руслан Тарасов |

            [Ссылка](https://example.com)
            """);

        assertTrue(html.contains("<h1>Заголовок</h1>"));
        assertTrue(html.contains("<input"));
        assertTrue(html.contains("<table>"));
        assertTrue(html.contains("href=\"https://example.com\""));
        assertTrue(html.contains(">Ссылка<"));
    }

    @Test
    void escapesRawHtmlFromMarkdown() {
        String html = renderer.render("<script>alert('xss')</script>");

        assertTrue(html.contains("&lt;script&gt;"));
    }
}
