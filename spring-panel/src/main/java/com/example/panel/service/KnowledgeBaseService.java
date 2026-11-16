package com.example.panel.service;

import com.example.panel.entity.KnowledgeArticle;
import com.example.panel.entity.KnowledgeArticleFile;
import com.example.panel.model.knowledge.KnowledgeArticleCommand;
import com.example.panel.model.knowledge.KnowledgeArticleDetails;
import com.example.panel.model.knowledge.KnowledgeArticleSummary;
import com.example.panel.model.knowledge.KnowledgeAttachmentDto;
import com.example.panel.model.knowledge.KnowledgeWikiLink;
import com.example.panel.repository.KnowledgeArticleFileRepository;
import com.example.panel.repository.KnowledgeArticleRepository;
import com.example.panel.storage.AttachmentService;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional
public class KnowledgeBaseService {

    private static final Pattern NOTION_TABLE_OF_CONTENTS_PATTERN = Pattern.compile("(?i)<table_of_contents(?:\\s+[^>]*)?\\s*/>");
    private static final Pattern NOTION_EMPTY_BLOCK_PATTERN = Pattern.compile("(?i)<empty-block(?:\\s+[^>]*)?\\s*/>");
    private static final Pattern NOTION_BREAK_PATTERN = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern NOTION_CALLOUT_PATTERN = Pattern.compile("(?is)<callout([^>]*)>(.*?)</callout>");
    private static final Pattern XML_ATTRIBUTE_PATTERN = Pattern.compile("(\\w+)\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s)\\]>\"']+");
    private static final Pattern WIKI_LINK_PATTERN = Pattern.compile("\\[\\[([^\\[\\]|]{1,500})(?:\\|([^\\[\\]]{1,500}))?\\]\\]");
    private static final List<String> LOCAL_MEDIA_EXTENSIONS = List.of(
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "pdf", "zip", "7z", "rar", "doc", "docx", "xls", "xlsx", "ppt", "pptx"
    );

    private final KnowledgeArticleRepository articleRepository;
    private final KnowledgeArticleFileRepository fileRepository;
    private final KnowledgeMarkdownRenderer markdownRenderer;
    private final KnowledgeAccessService accessService;
    private final JdbcTemplate jdbc;

    @org.springframework.beans.factory.annotation.Autowired
    public KnowledgeBaseService(KnowledgeArticleRepository articleRepository,
                                KnowledgeArticleFileRepository fileRepository,
                                KnowledgeMarkdownRenderer markdownRenderer,
                                KnowledgeAccessService accessService,
                                JdbcTemplate jdbc) {
        this.articleRepository = articleRepository;
        this.fileRepository = fileRepository;
        this.markdownRenderer = markdownRenderer;
        this.accessService = accessService;
        this.jdbc = jdbc;
    }

    KnowledgeBaseService(KnowledgeArticleRepository articleRepository,
                         KnowledgeArticleFileRepository fileRepository,
                         KnowledgeMarkdownRenderer markdownRenderer) {
        this(articleRepository, fileRepository, markdownRenderer, null, null);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeArticleSummary> listArticles(Authentication authentication) {
        return articleRepository.findAll(Sort.by(Sort.Direction.DESC, "updatedAt")).stream()
                .filter(article -> accessService != null && accessService.canViewArticle(authentication, article.getId()))
                .map(article -> new KnowledgeArticleSummary(
                        article.getId(),
                        article.getTitle(),
                        article.getSectionId(),
                        sectionTitle(authentication, article.getSectionId()),
                        article.getDepartment(),
                        article.getStatus(),
                        article.getAuthor(),
                        article.getSummary(),
                        article.getUpdatedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<KnowledgeArticleDetails> findArticle(Long id, Authentication authentication) {
        if (id == null) {
            return Optional.empty();
        }
        return articleRepository.findById(id)
            .filter(article -> accessService != null && accessService.canViewArticle(authentication, id))
            .map(article -> toDetails(article, fileRepository.findByArticleId(id), authentication));
    }

    public KnowledgeArticleDetails saveArticle(KnowledgeArticleCommand command, Authentication authentication) {
        if (command == null) {
            throw new IllegalArgumentException("Данные статьи не переданы");
        }
        KnowledgeArticle article = command.id() != null
                ? articleRepository.findById(command.id()).orElseGet(KnowledgeArticle::new)
                : new KnowledgeArticle();
        if (article.getId() == null) {
            if (accessService == null || !accessService.canCreateArticle(authentication)) {
                throw new AccessDeniedException("Недостаточно прав для создания статьи");
            }
        } else if (accessService == null || !accessService.canEditArticle(authentication, article.getId())) {
            throw new AccessDeniedException("Недостаточно прав для изменения статьи");
        }
        Long sectionId = command.sectionId() != null ? command.sectionId() : article.getSectionId();
        if (sectionId == null) {
            sectionId = defaultSectionId();
        }
        boolean sectionChanged = article.getId() == null || !Objects.equals(article.getSectionId(), sectionId);
        if (sectionId == null || accessService == null
                || (sectionChanged && !accessService.canEditSection(authentication, sectionId))) {
            throw new AccessDeniedException("Нет доступа к выбранному разделу");
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (article.getCreatedAt() == null) {
            article.setCreatedAt(now);
        }
        article.setUpdatedAt(now);
        article.setTitle(trim(command.title()));
        article.setDepartment(trim(command.department()));
        article.setArticleType(trim(command.articleType()));
        article.setStatus(trim(command.status()));
        article.setAuthor(trim(command.author()));
        article.setDirection(trim(command.direction()));
        article.setDirectionSubtype(trim(command.directionSubtype()));
        article.setSummary(trim(command.summary()));
        article.setContent(command.content());
        article.setSectionId(sectionId);
        KnowledgeArticle saved = articleRepository.save(article);
        syncWikiLinks(saved, authentication);
        List<KnowledgeArticleFile> attachments = fileRepository.findByArticleId(saved.getId());
        return toDetails(saved, attachments, authentication);
    }

    public void attachFile(long articleId, AttachmentService.AttachmentUploadMetadata metadata, Authentication authentication) {
        if (metadata == null || !StringUtils.hasText(metadata.storedName())
            || accessService == null || !accessService.canEditArticle(authentication, articleId)) {
            throw new AccessDeniedException("Недостаточно прав для добавления вложения");
        }
        KnowledgeArticle article = articleRepository.findById(articleId)
            .orElseThrow(() -> new IllegalArgumentException("Статья не найдена"));
        KnowledgeArticleFile file = new KnowledgeArticleFile();
        file.setArticle(article);
        file.setStoredPath(metadata.storedName());
        file.setOriginalName(metadata.originalName());
        file.setMimeType(metadata.mimeType());
        file.setFileSize(metadata.size());
        file.setUploadedAt(metadata.uploadedAt());
        fileRepository.save(file);
    }

    private KnowledgeArticleDetails toDetails(KnowledgeArticle article, List<KnowledgeArticleFile> files, Authentication authentication) {
        List<KnowledgeWikiLink> outgoing = loadOutgoingLinks(article.getId(), authentication);
        List<KnowledgeWikiLink> incoming = loadIncomingLinks(article.getId(), authentication);
        List<KnowledgeAttachmentDto> attachments = files.stream()
                .map(file -> new KnowledgeAttachmentDto(
                        file.getId(),
                        file.getOriginalName(),
                        file.getStoredPath(),
                        file.getMimeType(),
                        file.getFileSize(),
                        file.getUploadedAt()
                ))
                .toList();
        return new KnowledgeArticleDetails(
                article.getId(),
                article.getTitle(),
                article.getSectionId(),
                sectionTitle(authentication, article.getSectionId()),
                article.getDepartment(),
                article.getArticleType(),
                article.getStatus(),
                article.getAuthor(),
                article.getDirection(),
                article.getDirectionSubtype(),
                article.getSummary(),
                sanitizeSourceForViewer(article.getContent(), outgoing),
                renderContent(article, files, outgoing),
                article.getExternalSource(),
                article.getExternalId(),
                article.getExternalUrl(),
                article.getExternalUpdatedAt(),
                article.getCreatedAt(),
                article.getUpdatedAt(),
                attachments,
                outgoing.stream().filter(KnowledgeWikiLink::accessible).toList(),
                incoming
        );
    }

    String renderContent(KnowledgeArticle article, List<KnowledgeArticleFile> files) {
        return renderContent(article, files, List.of());
    }

    String renderContent(KnowledgeArticle article, List<KnowledgeArticleFile> files, List<KnowledgeWikiLink> links) {
        return markdownRenderer.render(prepareContentForRender(article, files, links));
    }

    String prepareContentForRender(KnowledgeArticle article, List<KnowledgeArticleFile> files) {
        return prepareContentForRender(article, files, List.of());
    }

    String prepareContentForRender(KnowledgeArticle article, List<KnowledgeArticleFile> files, List<KnowledgeWikiLink> links) {
        if (article == null || !StringUtils.hasText(article.getContent())) {
            return "";
        }
        String content = article.getContent();
        content = NOTION_TABLE_OF_CONTENTS_PATTERN.matcher(content)
            .replaceAll("\n\n" + KnowledgeMarkdownRenderer.TABLE_OF_CONTENTS_TOKEN + "\n\n");
        content = NOTION_EMPTY_BLOCK_PATTERN.matcher(content)
            .replaceAll("\n\n" + KnowledgeMarkdownRenderer.EMPTY_BLOCK_TOKEN + "\n\n");
        content = transformNotionCallouts(content);
        content = NOTION_BREAK_PATTERN.matcher(content).replaceAll("  \n");
        content = rewriteEmbeddedMediaUrls(content, article.getExternalId(), files);
        content = renderWikiLinks(content, links);
        return content.trim();
    }

    private String transformNotionCallouts(String content) {
        Matcher matcher = NOTION_CALLOUT_PATTERN.matcher(content);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String attributes = matcher.group(1);
            String body = matcher.group(2);
            String icon = readXmlAttribute(attributes, "icon");
            String color = readXmlAttribute(attributes, "color");
            String replacement = buildCalloutMarkdown(color, icon, body);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String buildCalloutMarkdown(String color, String icon, String body) {
        String normalizedBody = StringUtils.hasText(body) ? body.trim() : "";
        String marker = KnowledgeMarkdownRenderer.calloutToken(trim(color), trim(icon));
        StringBuilder builder = new StringBuilder();
        builder.append("\n\n> ").append(marker);
        if (!normalizedBody.isEmpty()) {
            builder.append("\n>");
            for (String line : normalizedBody.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
                builder.append("\n> ");
                builder.append(line);
            }
        }
        builder.append("\n\n");
        return builder.toString();
    }

    private String readXmlAttribute(String attributes, String name) {
        if (!StringUtils.hasText(attributes) || !StringUtils.hasText(name)) {
            return null;
        }
        Matcher matcher = XML_ATTRIBUTE_PATTERN.matcher(attributes);
        while (matcher.find()) {
            if (name.equalsIgnoreCase(matcher.group(1))) {
                return matcher.group(2);
            }
        }
        return null;
    }

    private String rewriteEmbeddedMediaUrls(String markdown, String externalId, List<KnowledgeArticleFile> files) {
        if (!StringUtils.hasText(markdown) || !StringUtils.hasText(externalId) || files == null || files.isEmpty()) {
            return markdown;
        }
        List<String> rewritten = new ArrayList<>();
        Matcher matcher = URL_PATTERN.matcher(markdown);
        while (matcher.find()) {
            String rawUrl = trimTrailingPunctuation(matcher.group());
            String replacement = resolveLocalMediaUrl(rawUrl, externalId, files);
            if (replacement != null && !replacement.equals(rawUrl)) {
                rewritten.add(rawUrl);
                rewritten.add(replacement);
            }
        }
        String result = markdown;
        for (int i = 0; i < rewritten.size(); i += 2) {
            result = result.replace(rewritten.get(i), rewritten.get(i + 1));
        }
        return result;
    }

    private String resolveLocalMediaUrl(String rawUrl, String externalId, List<KnowledgeArticleFile> files) {
        if (!StringUtils.hasText(rawUrl) || !isLikelyLocalizableMedia(rawUrl)) {
            return null;
        }
        String originalName = extractFileNameFromUrl(rawUrl);
        if (!StringUtils.hasText(originalName)) {
            return null;
        }
        String expectedStoredPath = buildStoredAttachmentName(externalId, rawUrl, originalName);
        String legacyStoredPath = buildLegacyStoredAttachmentName(externalId, rawUrl, originalName);
        for (KnowledgeArticleFile file : files) {
            if (expectedStoredPath.equals(file.getStoredPath()) || legacyStoredPath.equals(file.getStoredPath())) {
                return "/api/attachments/knowledge-base/" + URLEncoder.encode(file.getStoredPath(), StandardCharsets.UTF_8);
            }
        }
        List<KnowledgeArticleFile> byOriginalName = files.stream()
            .filter(file -> originalName.equalsIgnoreCase(trim(file.getOriginalName())))
            .filter(file -> StringUtils.hasText(file.getStoredPath()) && file.getStoredPath().startsWith(buildNotionAttachmentPrefix(externalId)))
            .toList();
        if (byOriginalName.size() == 1) {
            return "/api/attachments/knowledge-base/" + URLEncoder.encode(byOriginalName.get(0).getStoredPath(), StandardCharsets.UTF_8);
        }
        return null;
    }

    private boolean isLikelyLocalizableMedia(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl);
            String path = uri.getPath();
            if (!StringUtils.hasText(path) || !path.contains(".")) {
                return false;
            }
            String extension = path.substring(path.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            return LOCAL_MEDIA_EXTENSIONS.contains(extension);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private String extractFileNameFromUrl(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl);
            String path = uri.getPath();
            if (!StringUtils.hasText(path)) {
                return null;
            }
            String filename = path.substring(path.lastIndexOf('/') + 1);
            return StringUtils.hasText(filename) ? filename : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String buildStoredAttachmentName(String externalId, String url, String originalName) {
        return buildNotionAttachmentPrefix(externalId) + shortHash(normalizeAttachmentKey(url)) + "_" + sanitizeStoredFileName(originalName);
    }

    private String buildLegacyStoredAttachmentName(String externalId, String url, String originalName) {
        return buildNotionAttachmentPrefix(externalId) + shortHash(url) + "_" + sanitizeStoredFileName(originalName);
    }

    private String buildNotionAttachmentPrefix(String externalId) {
        return "notion_" + externalId.replaceAll("[^A-Za-z0-9]", "") + "_";
    }

    private String sanitizeStoredFileName(String originalName) {
        String cleaned = StringUtils.cleanPath(StringUtils.hasText(originalName) ? originalName : "attachment.bin");
        cleaned = cleaned.replace('\\', '_').replace('/', '_').replaceAll("[^A-Za-z0-9._-]", "_");
        return StringUtils.hasText(cleaned) ? cleaned : "attachment.bin";
    }

    private String shortHash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((raw != null ? raw : "").getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < Math.min(bytes.length, 6); i++) {
                builder.append(String.format("%02x", bytes[i]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toHexString((raw != null ? raw : "").hashCode());
        }
    }

    private String trimTrailingPunctuation(String url) {
        String trimmed = trim(url);
        while (StringUtils.hasText(trimmed) && ".,;)]".contains(trimmed.substring(trimmed.length() - 1))) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String normalizeAttachmentKey(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            return "";
        }
        try {
            URI uri = URI.create(rawUrl);
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "https";
            String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";
            String path = uri.getPath() != null ? uri.getPath() : rawUrl;
            return scheme + "://" + host + path;
        } catch (IllegalArgumentException ex) {
            return rawUrl;
        }
    }

    private Long defaultSectionId() {
        if (jdbc == null) {
            return null;
        }
        List<Long> ids = jdbc.queryForList("SELECT id FROM knowledge_sections WHERE code = 'general'", Long.class);
        return ids.size() == 1 ? ids.get(0) : null;
    }

    private String sectionTitle(Authentication authentication, Long sectionId) {
        if (jdbc == null || sectionId == null || accessService == null
                || !accessService.canViewSection(authentication, sectionId)) {
            return null;
        }
        List<String> titles = jdbc.queryForList("SELECT title FROM knowledge_sections WHERE id = ?", String.class, sectionId);
        return titles.size() == 1 ? titles.get(0) : null;
    }

    private void syncWikiLinks(KnowledgeArticle source, Authentication authentication) {
        if (jdbc == null || source == null || source.getId() == null) {
            return;
        }
        Map<String, StoredWikiLink> existing = new LinkedHashMap<>();
        jdbc.query("""
            SELECT source_reference, target_article_id, link_label
              FROM knowledge_article_links WHERE source_article_id = ?
            """, (rs, row) -> {
                existing.put(rs.getString("source_reference"),
                    new StoredWikiLink(rs.getLong("target_article_id"), rs.getString("link_label")));
                return 0;
            }, source.getId());

        LinkedHashSet<String> retainedReferences = new LinkedHashSet<>();
        Matcher matcher = WIKI_LINK_PATTERN.matcher(source.getContent() == null ? "" : source.getContent());
        while (matcher.find()) {
            String reference = matcher.group(1).trim();
            String label = matcher.group(2) == null ? null : matcher.group(2).trim();
            if (!StringUtils.hasText(reference) || !retainedReferences.add(reference)) {
                continue;
            }
            StoredWikiLink current = existing.get(reference);
            Long targetId = current != null && articleRepository.existsById(current.targetArticleId())
                ? current.targetArticleId() : resolveAccessibleArticleId(reference, authentication);
            if (targetId == null) {
                continue;
            }
            jdbc.update("""
                INSERT INTO knowledge_article_links(source_article_id, target_article_id, source_reference, link_label)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (source_article_id, source_reference)
                DO UPDATE SET target_article_id = EXCLUDED.target_article_id, link_label = EXCLUDED.link_label
                """, source.getId(), targetId, reference, StringUtils.hasText(label) ? label : null);
        }
        existing.keySet().stream().filter(reference -> !retainedReferences.contains(reference)).forEach(reference ->
            jdbc.update("DELETE FROM knowledge_article_links WHERE source_article_id = ? AND source_reference = ?", source.getId(), reference));
    }

    private Long resolveAccessibleArticleId(String reference, Authentication authentication) {
        List<KnowledgeArticle> matches = articleRepository.findByTitleIgnoreCase(reference).stream()
            .filter(article -> article.getId() != null && accessService.canViewArticle(authentication, article.getId()))
            .toList();
        return matches.size() == 1 ? matches.get(0).getId() : null;
    }

    private List<KnowledgeWikiLink> loadOutgoingLinks(Long sourceArticleId, Authentication authentication) {
        if (jdbc == null || sourceArticleId == null) {
            return List.of();
        }
        return jdbc.query("""
            SELECT l.target_article_id, target.title, l.source_reference, l.link_label
              FROM knowledge_article_links l
              JOIN knowledge_articles target ON target.id = l.target_article_id
             WHERE l.source_article_id = ?
             ORDER BY l.id
            """, (rs, row) -> {
                long targetId = rs.getLong("target_article_id");
                boolean visible = accessService.canViewArticle(authentication, targetId);
                return new KnowledgeWikiLink(targetId, visible ? rs.getString("title") : null,
                    rs.getString("source_reference"), rs.getString("link_label"), visible);
            }, sourceArticleId);
    }

    private List<KnowledgeWikiLink> loadIncomingLinks(Long targetArticleId, Authentication authentication) {
        if (jdbc == null || targetArticleId == null) {
            return List.of();
        }
        return jdbc.query("""
            SELECT l.source_article_id, source.title, l.source_reference, l.link_label
              FROM knowledge_article_links l
              JOIN knowledge_articles source ON source.id = l.source_article_id
             WHERE l.target_article_id = ?
             ORDER BY lower(source.title), source.id
            """, (rs, row) -> {
                long sourceId = rs.getLong("source_article_id");
                boolean visible = accessService.canViewArticle(authentication, sourceId);
                return new KnowledgeWikiLink(sourceId, visible ? rs.getString("title") : null,
                    rs.getString("source_reference"), rs.getString("link_label"), visible);
            }, targetArticleId).stream().filter(KnowledgeWikiLink::accessible).toList();
    }

    private String renderWikiLinks(String content, List<KnowledgeWikiLink> links) {
        if (!StringUtils.hasText(content) || links == null || links.isEmpty()) {
            return content;
        }
        Map<String, KnowledgeWikiLink> byReference = new LinkedHashMap<>();
        links.forEach(link -> byReference.put(link.sourceReference(), link));
        Matcher matcher = WIKI_LINK_PATTERN.matcher(content);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String reference = matcher.group(1).trim();
            KnowledgeWikiLink link = byReference.get(reference);
            if (link == null) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            String replacement;
            if (!link.accessible()) {
                replacement = "Материал с ограниченным доступом";
            } else {
                String label = StringUtils.hasText(link.label()) ? link.label() : link.title();
                replacement = "[" + escapeMarkdown(label) + "](/knowledge-base/" + link.articleId()
                    + "?wiki=" + URLEncoder.encode(link.sourceReference(), StandardCharsets.UTF_8) + ")";
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String sanitizeSourceForViewer(String content, List<KnowledgeWikiLink> links) {
        if (!StringUtils.hasText(content) || links == null || links.isEmpty()) {
            return content;
        }
        Map<String, KnowledgeWikiLink> byReference = new LinkedHashMap<>();
        links.forEach(link -> byReference.put(link.sourceReference(), link));
        Matcher matcher = WIKI_LINK_PATTERN.matcher(content);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            KnowledgeWikiLink link = byReference.get(matcher.group(1).trim());
            if (link != null && !link.accessible()) {
                matcher.appendReplacement(result, Matcher.quoteReplacement("Материал с ограниченным доступом"));
            } else {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String escapeMarkdown(String value) {
        return (value == null ? "" : value).replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]");
    }

    private record StoredWikiLink(long targetArticleId, String label) { }

    private static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
