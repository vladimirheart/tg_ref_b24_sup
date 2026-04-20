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
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class KnowledgeMarkdownRenderer {

    static final String TABLE_OF_CONTENTS_TOKEN = "[[[KNOWLEDGE_TOC]]]";
    static final String EMPTY_BLOCK_TOKEN = "[[[KNOWLEDGE_EMPTY_BLOCK]]]";

    private static final Pattern HEADING_PATTERN = Pattern.compile("<h([1-6])>(.*?)</h\\1>", Pattern.DOTALL);
    private static final Pattern TOC_PARAGRAPH_PATTERN = Pattern.compile("<p>\\Q" + TABLE_OF_CONTENTS_TOKEN + "\\E</p>");
    private static final Pattern EMPTY_BLOCK_PARAGRAPH_PATTERN = Pattern.compile("<p>\\Q" + EMPTY_BLOCK_TOKEN + "\\E</p>");
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern NON_SLUG_PATTERN = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}]+");
    private static final Pattern EDGE_HYPHEN_PATTERN = Pattern.compile("(^-+|-+$)");

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
        String html = renderer.render(document);
        return postProcessHtml(html);
    }

    private String postProcessHtml(String html) {
        List<HeadingEntry> headings = new ArrayList<>();
        String htmlWithHeadingIds = injectHeadingIds(html, headings);
        String htmlWithToc = replaceTokenParagraphs(htmlWithHeadingIds, TOC_PARAGRAPH_PATTERN, buildTableOfContents(headings));
        return replaceTokenParagraphs(htmlWithToc, EMPTY_BLOCK_PARAGRAPH_PATTERN, "<div class=\"knowledge-empty-block\" aria-hidden=\"true\"></div>");
    }

    private String injectHeadingIds(String html, List<HeadingEntry> headings) {
        Matcher matcher = HEADING_PATTERN.matcher(html);
        StringBuffer buffer = new StringBuffer();
        Map<String, Integer> usedIds = new HashMap<>();
        while (matcher.find()) {
            int level = Integer.parseInt(matcher.group(1));
            String innerHtml = matcher.group(2);
            String text = extractHeadingText(innerHtml);
            String headingId = nextHeadingId(text, usedIds);
            headings.add(new HeadingEntry(level, text, headingId));
            String replacement = "<h" + level + " id=\"" + headingId + "\">" + innerHtml + "</h" + level + ">";
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String buildTableOfContents(List<HeadingEntry> headings) {
        if (headings.isEmpty()) {
            return "";
        }
        StringBuilder html = new StringBuilder();
        html.append("<nav class=\"knowledge-toc\" aria-label=\"Оглавление\">");
        html.append("<div class=\"knowledge-toc__title\">Оглавление</div>");
        html.append("<ul class=\"knowledge-toc__list\">");
        for (HeadingEntry heading : headings) {
            html.append("<li class=\"knowledge-toc__item knowledge-toc__item--level-")
                .append(heading.level())
                .append("\">");
            html.append("<a class=\"knowledge-toc__link\" href=\"#")
                .append(heading.id())
                .append("\">")
                .append(HtmlUtils.htmlEscape(heading.text()))
                .append("</a>");
            html.append("</li>");
        }
        html.append("</ul>");
        html.append("</nav>");
        return html.toString();
    }

    private String replaceTokenParagraphs(String html, Pattern tokenPattern, String replacement) {
        String normalizedReplacement = replacement != null ? replacement : "";
        return tokenPattern.matcher(html).replaceAll(Matcher.quoteReplacement(normalizedReplacement));
    }

    private String extractHeadingText(String innerHtml) {
        String text = HTML_TAG_PATTERN.matcher(innerHtml).replaceAll(" ");
        text = HtmlUtils.htmlUnescape(text);
        text = text.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
        return StringUtils.hasText(text) ? text : "section";
    }

    private String nextHeadingId(String text, Map<String, Integer> usedIds) {
        String slug = NON_SLUG_PATTERN.matcher(text.toLowerCase(Locale.ROOT)).replaceAll("-");
        slug = EDGE_HYPHEN_PATTERN.matcher(slug).replaceAll("");
        if (!StringUtils.hasText(slug)) {
            slug = "section";
        }
        int counter = usedIds.getOrDefault(slug, 0);
        usedIds.put(slug, counter + 1);
        return counter == 0 ? slug : slug + "-" + (counter + 1);
    }

    private record HeadingEntry(int level, String text, String id) {
    }
}
