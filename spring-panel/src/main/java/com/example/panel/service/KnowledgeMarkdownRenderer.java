package com.example.panel.service;

import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class KnowledgeMarkdownRenderer {

    private final Parser parser;
    private final HtmlRenderer renderer;

    public KnowledgeMarkdownRenderer() {
        List<Extension> extensions = List.of(
            AutolinkExtension.create(),
            StrikethroughExtension.create(),
            TablesExtension.create(),
            TaskListItemsExtension.create()
        );
        this.parser = Parser.builder()
            .extensions(extensions)
            .build();
        this.renderer = HtmlRenderer.builder()
            .extensions(extensions)
            .escapeHtml(true)
            .sanitizeUrls(true)
            .build();
    }

    public String render(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return "";
        }
        Node document = parser.parse(markdown.trim());
        return renderer.render(document);
    }
}
