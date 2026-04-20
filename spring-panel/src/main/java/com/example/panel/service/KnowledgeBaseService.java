package com.example.panel.service;

import com.example.panel.entity.KnowledgeArticle;
import com.example.panel.entity.KnowledgeArticleFile;
import com.example.panel.model.knowledge.KnowledgeArticleCommand;
import com.example.panel.model.knowledge.KnowledgeArticleDetails;
import com.example.panel.model.knowledge.KnowledgeArticleSummary;
import com.example.panel.model.knowledge.KnowledgeAttachmentDto;
import com.example.panel.repository.KnowledgeArticleFileRepository;
import com.example.panel.repository.KnowledgeArticleRepository;
import org.springframework.data.domain.Sort;
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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional
public class KnowledgeBaseService {

    private static final Pattern NOTION_TAG_PATTERN = Pattern.compile("(?i)<(?:table_of_contents|empty-block)(?:\\s+[^>]*)?\\s*/>");
    private static final Pattern NOTION_BREAK_PATTERN = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s)\\]>\"']+");
    private static final List<String> LOCAL_MEDIA_EXTENSIONS = List.of(
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "pdf", "zip", "7z", "rar", "doc", "docx", "xls", "xlsx", "ppt", "pptx"
    );

    private final KnowledgeArticleRepository articleRepository;
    private final KnowledgeArticleFileRepository fileRepository;
    private final KnowledgeMarkdownRenderer markdownRenderer;

    public KnowledgeBaseService(KnowledgeArticleRepository articleRepository,
                                KnowledgeArticleFileRepository fileRepository,
                                KnowledgeMarkdownRenderer markdownRenderer) {
        this.articleRepository = articleRepository;
        this.fileRepository = fileRepository;
        this.markdownRenderer = markdownRenderer;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeArticleSummary> listArticles() {
        return articleRepository.findAll(Sort.by(Sort.Direction.DESC, "updatedAt")).stream()
                .map(article -> new KnowledgeArticleSummary(
                        article.getId(),
                        article.getTitle(),
                        article.getDepartment(),
                        article.getStatus(),
                        article.getAuthor(),
                        article.getSummary(),
                        article.getUpdatedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<KnowledgeArticleDetails> findArticle(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return articleRepository.findById(id).map(article -> toDetails(article, fileRepository.findByArticleId(id)));
    }

    public KnowledgeArticleDetails saveArticle(KnowledgeArticleCommand command) {
        KnowledgeArticle article = command.id() != null
                ? articleRepository.findById(command.id()).orElseGet(KnowledgeArticle::new)
                : new KnowledgeArticle();
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
        KnowledgeArticle saved = articleRepository.save(article);
        List<KnowledgeArticleFile> attachments = fileRepository.findByArticleId(saved.getId());
        return toDetails(saved, attachments);
    }

    private KnowledgeArticleDetails toDetails(KnowledgeArticle article, List<KnowledgeArticleFile> files) {
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
                article.getDepartment(),
                article.getArticleType(),
                article.getStatus(),
                article.getAuthor(),
                article.getDirection(),
                article.getDirectionSubtype(),
                article.getSummary(),
                article.getContent(),
                renderContent(article, files),
                article.getCreatedAt(),
                article.getUpdatedAt(),
                attachments
        );
    }

    String renderContent(KnowledgeArticle article, List<KnowledgeArticleFile> files) {
        return markdownRenderer.render(prepareContentForRender(article, files));
    }

    String prepareContentForRender(KnowledgeArticle article, List<KnowledgeArticleFile> files) {
        if (article == null || !StringUtils.hasText(article.getContent())) {
            return "";
        }
        String content = article.getContent();
        content = NOTION_TAG_PATTERN.matcher(content).replaceAll("");
        content = NOTION_BREAK_PATTERN.matcher(content).replaceAll("  \n");
        content = rewriteEmbeddedMediaUrls(content, article.getExternalId(), files);
        return content.trim();
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
        for (KnowledgeArticleFile file : files) {
            if (expectedStoredPath.equals(file.getStoredPath())) {
                return "/api/attachments/knowledge-base/" + URLEncoder.encode(file.getStoredPath(), StandardCharsets.UTF_8);
            }
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

    private static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
