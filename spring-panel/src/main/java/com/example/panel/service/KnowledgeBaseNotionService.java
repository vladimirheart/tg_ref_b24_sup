package com.example.panel.service;

import com.example.panel.entity.KnowledgeArticle;
import com.example.panel.entity.KnowledgeArticleFile;
import com.example.panel.model.knowledge.KnowledgeBaseNotionConfigForm;
import com.example.panel.model.knowledge.KnowledgeNotionImportPreview;
import com.example.panel.model.knowledge.KnowledgeNotionImportPreviewItem;
import com.example.panel.repository.KnowledgeArticleFileRepository;
import com.example.panel.repository.KnowledgeArticleRepository;
import com.example.panel.storage.AttachmentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional
public class KnowledgeBaseNotionService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseNotionService.class);

    private static final String SETTINGS_KEY = "knowledge_base_config";
    private static final String NOTION_VERSION = "2026-03-11";
    private static final String LEGACY_NOTION_VERSION = "2022-06-28";
    private static final String NOTION_SOURCE = "notion";
    private static final Pattern DOWNLOAD_URL_PATTERN = Pattern.compile("https?://[^\\s)\\]>\"']+");
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "([0-9a-fA-F]{32}|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})");
    private static final Set<String> DOWNLOADABLE_EXTENSIONS = Set.of(
        "7z", "bz2", "csv", "doc", "docm", "docx", "epub", "gz", "json", "log", "md", "odp", "ods", "odt",
        "pdf", "ppt", "pptm", "pptx", "rar", "rtf", "tar", "tgz", "txt", "xls", "xlsb", "xlsm", "xlsx",
        "xml", "yaml", "yml", "zip"
    );
    private static final List<String> DEFAULT_AUTHORS = List.of(
        "Синицын Владимир",
        "Лемешкин Андрей",
        "Egor",
        "Канделаки Данил",
        "Шумкова Дарья"
    );

    private final SharedConfigService sharedConfigService;
    private final KnowledgeArticleRepository knowledgeArticleRepository;
    private final KnowledgeArticleFileRepository knowledgeArticleFileRepository;
    private final AttachmentService attachmentService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public KnowledgeBaseNotionService(SharedConfigService sharedConfigService,
                                      KnowledgeArticleRepository knowledgeArticleRepository,
                                      KnowledgeArticleFileRepository knowledgeArticleFileRepository,
                                      AttachmentService attachmentService,
                                      ObjectMapper objectMapper) {
        this.sharedConfigService = sharedConfigService;
        this.knowledgeArticleRepository = knowledgeArticleRepository;
        this.knowledgeArticleFileRepository = knowledgeArticleFileRepository;
        this.attachmentService = attachmentService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    @Transactional(readOnly = true)
    public KnowledgeBaseNotionConfigForm buildForm() {
        NotionConfig config = loadConfig(false);
        KnowledgeBaseNotionConfigForm form = new KnowledgeBaseNotionConfigForm();
        form.setEnabled(config.enabled());
        form.setSourceUrl(config.sourceUrl());
        form.setAuthors(String.join("\n", config.authors()));
        form.setTitleProperty(config.titleProperty());
        form.setAuthorProperty(config.authorProperty());
        form.setSummaryProperty(config.summaryProperty());
        form.setDepartmentProperty(config.departmentProperty());
        form.setArticleTypeProperty(config.articleTypeProperty());
        form.setDirectionProperty(config.directionProperty());
        form.setDirectionSubtypeProperty(config.directionSubtypeProperty());
        form.setStatusProperty(config.statusProperty());
        return form;
    }

    @Transactional(readOnly = true)
    public boolean hasSavedToken() {
        return StringUtils.hasText(loadConfig(false).token());
    }

    public void saveConfig(KnowledgeBaseNotionConfigForm form) {
        NotionConfig current = loadConfig(false);
        NotionConfig merged = mergeConfig(current, form);
        validateSourceReferenceFormat(merged.sourceUrl());
        Map<String, Object> settings = new LinkedHashMap<>(sharedConfigService.loadSettings());
        settings.put(SETTINGS_KEY, toSettingsMap(merged));
        sharedConfigService.saveSettings(settings);
    }

    @Transactional(readOnly = true)
    public ConnectionCheckResult testConnection() {
        NotionConfig config = loadConfig(true);
        QueryResult result = queryPages(config);
        return new ConnectionCheckResult(result.mode(), result.matchedPages().size(), result.totalPages(), config.sourceUrl());
    }

    @Transactional(readOnly = true)
    public KnowledgeNotionImportPreview previewImportArticles() {
        NotionConfig config = loadConfig(true);
        QueryResult queryResult = queryPages(config);
        Map<String, String> relationDisplayCache = new HashMap<>();
        return new KnowledgeNotionImportPreview(
            queryResult.mode(),
            queryResult.totalPages(),
            queryResult.matchedPages().size(),
            buildPreviewItems(config, queryResult.matchedPages(), relationDisplayCache)
        );
    }

    public ImportResult importArticles() {
        return importArticlesInternal(null, true);
    }

    public ImportResult importArticles(List<String> selectedExternalIds) {
        return importArticlesInternal(selectedExternalIds, false);
    }

    private ImportResult importArticlesInternal(List<String> selectedExternalIds, boolean importAllWhenSelectionEmpty) {
        NotionConfig config = loadConfig(true);
        QueryResult queryResult = queryPages(config);
        List<JsonNode> selectedPages = filterSelectedPages(queryResult.matchedPages(), selectedExternalIds, importAllWhenSelectionEmpty);
        Map<String, String> relationDisplayCache = new HashMap<>();
        int created = 0;
        int updated = 0;
        int skipped = 0;

        for (JsonNode page : selectedPages) {
            ImportedArticle article = toImportedArticle(config, page, relationDisplayCache);
            if (!StringUtils.hasText(article.title()) || !StringUtils.hasText(article.content())) {
                skipped++;
                continue;
            }
            KnowledgeArticle entity = knowledgeArticleRepository
                .findFirstByExternalSourceAndExternalId(NOTION_SOURCE, article.externalId())
                .orElseGet(KnowledgeArticle::new);
            boolean isNew = entity.getId() == null;
            OffsetDateTime now = OffsetDateTime.now();
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(now);
            }
            entity.setUpdatedAt(now);
            entity.setTitle(trim(article.title()));
            entity.setDepartment(trim(article.department()));
            entity.setArticleType(trim(article.articleType()));
            entity.setStatus(resolveLocalStatus(article.status()));
            entity.setAuthor(trim(article.author()));
            entity.setDirection(trim(article.direction()));
            entity.setDirectionSubtype(trim(article.directionSubtype()));
            entity.setSummary(trim(article.summary()));
            entity.setContent(article.content());
            entity.setExternalSource(NOTION_SOURCE);
            entity.setExternalId(article.externalId());
            entity.setExternalUrl(trim(article.externalUrl()));
            entity.setExternalUpdatedAt(article.externalUpdatedAt());
            entity = knowledgeArticleRepository.save(entity);
            syncImportedAttachments(config.token(), entity, page, article.content());
            if (isNew) {
                created++;
            } else {
                updated++;
            }
        }

        return new ImportResult(
            queryResult.mode(),
            queryResult.matchedPages().size(),
            selectedPages.size(),
            queryResult.totalPages(),
            created,
            updated,
            skipped
        );
    }

    private NotionConfig loadConfig(boolean requireReady) {
        Map<String, Object> settings = sharedConfigService.loadSettings();
        Object raw = settings.get(SETTINGS_KEY);
        Map<?, ?> configMap = raw instanceof Map<?, ?> map ? map : Map.of();
        List<String> authors = normalizeAuthors(configMap.get("authors"));
        NotionConfig config = new NotionConfig(
            asBoolean(configMap.get("enabled")),
            stringValue(configMap.get("source_url")),
            stringValue(configMap.get("token")),
            authors.isEmpty() ? DEFAULT_AUTHORS : authors,
            defaultIfBlank(stringValue(configMap.get("title_property")), "Name"),
            defaultIfBlank(stringValue(configMap.get("author_property")), "Автор"),
            defaultIfBlank(stringValue(configMap.get("summary_property")), "Краткое описание"),
            defaultIfBlank(stringValue(configMap.get("department_property")), "Подразделение"),
            defaultIfBlank(stringValue(configMap.get("article_type_property")), "Тип статьи"),
            defaultIfBlank(stringValue(configMap.get("direction_property")), "Направление"),
            defaultIfBlank(stringValue(configMap.get("direction_subtype_property")), "Поднаправление"),
            defaultIfBlank(stringValue(configMap.get("status_property")), "Статус")
        );
        if (requireReady) {
            validateConfig(config);
        }
        return config;
    }

    private NotionConfig mergeConfig(NotionConfig current, KnowledgeBaseNotionConfigForm form) {
        List<String> authors = normalizeAuthors(form.getAuthors());
        return new NotionConfig(
            form.isEnabled(),
            trim(form.getSourceUrl()),
            StringUtils.hasText(form.getToken()) ? form.getToken().trim() : current.token(),
            authors.isEmpty() ? DEFAULT_AUTHORS : authors,
            defaultIfBlank(trim(form.getTitleProperty()), "Name"),
            defaultIfBlank(trim(form.getAuthorProperty()), "Автор"),
            defaultIfBlank(trim(form.getSummaryProperty()), "Краткое описание"),
            defaultIfBlank(trim(form.getDepartmentProperty()), "Подразделение"),
            defaultIfBlank(trim(form.getArticleTypeProperty()), "Тип статьи"),
            defaultIfBlank(trim(form.getDirectionProperty()), "Направление"),
            defaultIfBlank(trim(form.getDirectionSubtypeProperty()), "Поднаправление"),
            defaultIfBlank(trim(form.getStatusProperty()), "Статус")
        );
    }

    private Map<String, Object> toSettingsMap(NotionConfig config) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", config.enabled());
        result.put("source_url", config.sourceUrl());
        result.put("token", config.token());
        result.put("authors", config.authors());
        result.put("title_property", config.titleProperty());
        result.put("author_property", config.authorProperty());
        result.put("summary_property", config.summaryProperty());
        result.put("department_property", config.departmentProperty());
        result.put("article_type_property", config.articleTypeProperty());
        result.put("direction_property", config.directionProperty());
        result.put("direction_subtype_property", config.directionSubtypeProperty());
        result.put("status_property", config.statusProperty());
        return result;
    }

    private void validateConfig(NotionConfig config) {
        if (!config.enabled()) {
            throw new IllegalStateException("Интеграция Notion выключена. Сначала включите её и сохраните настройки.");
        }
        if (!StringUtils.hasText(config.token())) {
            throw new IllegalStateException("Не задан integration token Notion.");
        }
        if (!StringUtils.hasText(config.sourceUrl())) {
            throw new IllegalStateException("Не задана ссылка или ID базы Notion.");
        }
        validateSourceReferenceFormat(config.sourceUrl());
    }

    private QueryResult queryPages(NotionConfig config) {
        String sourceId = extractSourceId(config.sourceUrl());
        String resolvedDataSourceId = resolveDataSourceId(config.token(), sourceId);
        ApiResult result = StringUtils.hasText(resolvedDataSourceId)
            ? queryDataSource(config.token(), resolvedDataSourceId)
            : queryLegacyDatabase(config.token(), sourceId);
        if (!result.success()) {
            throw new IllegalStateException(decorateNotionApiMessage(sourceId, result.message()));
        }

        List<JsonNode> totalPages = collectResults(result.body());
        Set<String> authorFilter = normalizeAuthorSet(config.authors());
        Map<String, String> relationDisplayCache = new HashMap<>();
        List<JsonNode> matchedPages = new ArrayList<>();
        for (JsonNode page : totalPages) {
            JsonNode authorProperty = findProperty(page.path("properties"), config.authorProperty(), null);
            if (matchesAuthors(authorProperty, authorFilter, config.token(), relationDisplayCache)) {
                matchedPages.add(page);
            }
        }
        return new QueryResult(result.mode(), totalPages.size(), matchedPages);
    }

    private String resolveDataSourceId(String token, String sourceId) {
        ApiResult directQuery = queryDataSource(token, sourceId);
        if (directQuery.success()) {
            return sourceId;
        }
        ApiResult databaseInfo = retrieveDatabase(token, sourceId);
        if (databaseInfo.success()) {
            JsonNode dataSources = databaseInfo.body().path("data_sources");
            if (dataSources.isArray() && dataSources.size() > 0) {
                String resolved = dataSources.get(0).path("id").asText(null);
                if (StringUtils.hasText(resolved)) {
                    return resolved;
                }
            }
            throw new IllegalStateException(buildMissingDataSourceMessage(sourceId, null));
        }
        if (containsMissingDataSourceAccessMessage(databaseInfo.message())) {
            throw new IllegalStateException(buildMissingDataSourceMessage(sourceId, databaseInfo.message()));
        }
        return null;
    }

    private ApiResult queryDataSource(String token, String dataSourceId) {
        String url = "https://api.notion.com/v1/data_sources/" + dataSourceId + "/query";
        return executePagedPost(token, NOTION_VERSION, url, "data_source");
    }

    private ApiResult queryLegacyDatabase(String token, String databaseId) {
        String url = "https://api.notion.com/v1/databases/" + databaseId + "/query";
        return executePagedPost(token, LEGACY_NOTION_VERSION, url, "database");
    }

    private ApiResult retrieveDatabase(String token, String databaseId) {
        String url = "https://api.notion.com/v1/databases/" + databaseId;
        return executeGet(token, NOTION_VERSION, url, "database");
    }

    private ApiResult executePagedPost(String token, String notionVersion, String url, String mode) {
        JsonNode root = null;
        String nextCursor = null;
        try {
            do {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("page_size", 100);
                if (StringUtils.hasText(nextCursor)) {
                    payload.put("start_cursor", nextCursor);
                }
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Notion-Version", notionVersion)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                JsonNode body = readJson(response.body());
                if (response.statusCode() >= 400) {
                    return new ApiResult(false, mode, body, extractApiError(body, "Не удалось выполнить запрос к Notion."));
                }
                if (root == null) {
                    root = body.deepCopy();
                } else {
                    appendResults(root, body.path("results"));
                }
                nextCursor = body.path("has_more").asBoolean(false) ? body.path("next_cursor").asText(null) : null;
            } while (StringUtils.hasText(nextCursor));
        } catch (IOException ex) {
            throw new IllegalStateException("Не удалось сериализовать запрос к Notion: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Запрос к Notion был прерван.", ex);
        }
        return new ApiResult(true, mode, root != null ? root : objectMapper.createObjectNode(), null);
    }

    private ApiResult executeGet(String token, String notionVersion, String url, String mode) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Notion-Version", notionVersion)
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode body = readJson(response.body());
            if (response.statusCode() >= 400) {
                return new ApiResult(false, mode, body, extractApiError(body, "Не удалось получить данные из Notion."));
            }
            return new ApiResult(true, mode, body, null);
        } catch (IOException ex) {
            throw new IllegalStateException("Не удалось прочитать ответ Notion: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Запрос к Notion был прерван.", ex);
        }
    }

    private ImportedArticle toImportedArticle(NotionConfig config, JsonNode page, Map<String, String> relationDisplayCache) {
        JsonNode properties = page.path("properties");
        String title = firstNonBlank(
            extractPropertyText(properties, config.titleProperty(), "title", config.token(), relationDisplayCache),
            extractPropertyText(properties, null, "title", config.token(), relationDisplayCache),
            "Статья без названия"
        );
        String markdown = fetchPageMarkdown(config.token(), page.path("id").asText());
        String summary = firstNonBlank(
            extractPropertyText(properties, config.summaryProperty(), null, config.token(), relationDisplayCache),
            buildSummaryFromMarkdown(markdown)
        );
        return new ImportedArticle(
            title,
            extractPropertyText(properties, config.authorProperty(), null, config.token(), relationDisplayCache),
            summary,
            extractPropertyText(properties, config.departmentProperty(), null, config.token(), relationDisplayCache),
            extractPropertyText(properties, config.articleTypeProperty(), null, config.token(), relationDisplayCache),
            extractPropertyText(properties, config.directionProperty(), null, config.token(), relationDisplayCache),
            extractPropertyText(properties, config.directionSubtypeProperty(), null, config.token(), relationDisplayCache),
            extractPropertyText(properties, config.statusProperty(), null, config.token(), relationDisplayCache),
            trim(markdown),
            page.path("id").asText(),
            page.path("url").asText(null),
            parseOffsetDateTime(page.path("last_edited_time").asText(null))
        );
    }

    private List<KnowledgeNotionImportPreviewItem> buildPreviewItems(NotionConfig config,
                                                                     List<JsonNode> pages,
                                                                     Map<String, String> relationDisplayCache) {
        Map<String, KnowledgeArticle> existingByExternalId = loadExistingArticlesByExternalId(pages);
        List<KnowledgeNotionImportPreviewItem> items = new ArrayList<>();
        for (JsonNode page : pages) {
            PreviewArticle article = toPreviewArticle(config, page, relationDisplayCache);
            items.add(new KnowledgeNotionImportPreviewItem(
                article.externalId(),
                article.title(),
                article.author(),
                article.summary(),
                article.department(),
                article.articleType(),
                article.notionStatus(),
                article.localStatus(),
                article.externalUrl(),
                article.externalUpdatedAt(),
                existingByExternalId.containsKey(article.externalId())
            ));
        }
        return items;
    }

    private PreviewArticle toPreviewArticle(NotionConfig config, JsonNode page, Map<String, String> relationDisplayCache) {
        JsonNode properties = page.path("properties");
        String notionStatus = extractPropertyText(properties, config.statusProperty(), null, config.token(), relationDisplayCache);
        return new PreviewArticle(
            firstNonBlank(
                extractPropertyText(properties, config.titleProperty(), "title", config.token(), relationDisplayCache),
                extractPropertyText(properties, null, "title", config.token(), relationDisplayCache),
                "Статья без названия"
            ),
            extractPropertyText(properties, config.authorProperty(), null, config.token(), relationDisplayCache),
            extractPropertyText(properties, config.summaryProperty(), null, config.token(), relationDisplayCache),
            extractPropertyText(properties, config.departmentProperty(), null, config.token(), relationDisplayCache),
            extractPropertyText(properties, config.articleTypeProperty(), null, config.token(), relationDisplayCache),
            notionStatus,
            resolveLocalStatus(notionStatus),
            page.path("id").asText(),
            page.path("url").asText(null),
            parseOffsetDateTime(page.path("last_edited_time").asText(null))
        );
    }

    private Map<String, KnowledgeArticle> loadExistingArticlesByExternalId(List<JsonNode> pages) {
        Set<String> externalIds = new LinkedHashSet<>();
        for (JsonNode page : pages) {
            String externalId = page.path("id").asText(null);
            if (StringUtils.hasText(externalId)) {
                externalIds.add(externalId);
            }
        }
        if (externalIds.isEmpty()) {
            return Map.of();
        }
        Map<String, KnowledgeArticle> existing = new LinkedHashMap<>();
        for (KnowledgeArticle article : knowledgeArticleRepository.findAllByExternalSourceAndExternalIdIn(NOTION_SOURCE, externalIds)) {
            if (StringUtils.hasText(article.getExternalId())) {
                existing.put(article.getExternalId(), article);
            }
        }
        return existing;
    }

    List<JsonNode> filterSelectedPages(List<JsonNode> matchedPages,
                                       List<String> selectedExternalIds,
                                       boolean importAllWhenSelectionEmpty) {
        if (matchedPages == null || matchedPages.isEmpty()) {
            return List.of();
        }
        Set<String> selectedIds = normalizeSelectedIds(selectedExternalIds);
        if (selectedIds.isEmpty()) {
            return importAllWhenSelectionEmpty ? List.copyOf(matchedPages) : List.of();
        }
        List<JsonNode> selectedPages = new ArrayList<>();
        for (JsonNode page : matchedPages) {
            String externalId = page.path("id").asText(null);
            if (StringUtils.hasText(externalId) && selectedIds.contains(externalId)) {
                selectedPages.add(page);
            }
        }
        return selectedPages;
    }

    private String fetchPageMarkdown(String token, String pageId) {
        String url = "https://api.notion.com/v1/pages/" + pageId + "/markdown";
        ApiResult result = executeGet(token, NOTION_VERSION, url, "page_markdown");
        if (!result.success()) {
            throw new IllegalStateException(result.message());
        }
        String markdown = result.body().path("markdown").asText(null);
        if (!StringUtils.hasText(markdown)) {
            log.warn("Notion page {} returned empty markdown payload", pageId);
        }
        return markdown;
    }

    private void syncImportedAttachments(String token, KnowledgeArticle article, JsonNode page, String markdown) {
        if (article == null || article.getId() == null || !StringUtils.hasText(article.getExternalId())
            || knowledgeArticleFileRepository == null || attachmentService == null) {
            return;
        }
        List<NotionAttachmentRef> remoteAttachments = collectPageAttachments(token, page, markdown);
        String prefix = buildNotionAttachmentPrefix(article.getExternalId());
        Map<String, KnowledgeArticleFile> existingImportedFiles = new LinkedHashMap<>();
        for (KnowledgeArticleFile file : knowledgeArticleFileRepository.findByArticleId(article.getId())) {
            if (StringUtils.hasText(file.getStoredPath()) && file.getStoredPath().startsWith(prefix)) {
                existingImportedFiles.put(file.getStoredPath(), file);
            }
        }
        for (NotionAttachmentRef attachment : remoteAttachments) {
            try {
                StoredAttachment storedAttachment = downloadAndStoreAttachment(article, attachment);
                if (storedAttachment == null) {
                    continue;
                }
                KnowledgeArticleFile file = existingImportedFiles.remove(storedAttachment.storedName());
                if (file == null) {
                    file = new KnowledgeArticleFile();
                }
                file.setArticle(article);
                file.setDraftToken(null);
                file.setStoredPath(storedAttachment.storedName());
                file.setOriginalName(storedAttachment.originalName());
                file.setMimeType(storedAttachment.mimeType());
                file.setFileSize(storedAttachment.size());
                file.setUploadedAt(storedAttachment.uploadedAt());
                knowledgeArticleFileRepository.save(file);
            } catch (Exception ex) {
                log.warn("Failed to import Notion attachment {} for article {}: {}", attachment.url(), article.getExternalId(), ex.getMessage());
            }
        }
        for (KnowledgeArticleFile staleFile : existingImportedFiles.values()) {
            try {
                attachmentService.deleteKnowledgeBaseFile(staleFile.getStoredPath());
            } catch (IOException ex) {
                log.warn("Failed to delete stale Notion attachment {}: {}", staleFile.getStoredPath(), ex.getMessage());
            }
            knowledgeArticleFileRepository.delete(staleFile);
        }
    }

    private List<NotionAttachmentRef> collectPageAttachments(String token, JsonNode page, String markdown) {
        Map<String, NotionAttachmentRef> attachments = new LinkedHashMap<>();
        collectPropertyAttachments(page.path("properties"), attachments);
        String pageId = page.path("id").asText(null);
        if (StringUtils.hasText(pageId)) {
            collectBlockAttachments(token, pageId, new LinkedHashSet<>(), attachments);
        }
        for (String url : extractMarkdownAttachmentUrls(markdown)) {
            attachments.putIfAbsent(url, new NotionAttachmentRef(url, extractFileNameFromUrl(url), null));
        }
        return new ArrayList<>(attachments.values());
    }

    List<String> extractMarkdownAttachmentUrls(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return List.of();
        }
        List<String> urls = new ArrayList<>();
        Matcher matcher = DOWNLOAD_URL_PATTERN.matcher(markdown);
        while (matcher.find()) {
            String url = trimTrailingPunctuation(matcher.group());
            if (isLikelyDownloadableUrl(url)) {
                urls.add(url);
            }
        }
        return urls.stream().distinct().toList();
    }

    private void collectPropertyAttachments(JsonNode properties, Map<String, NotionAttachmentRef> attachments) {
        if (properties == null || properties.isMissingNode()) {
            return;
        }
        properties.forEach(property -> {
            if ("files".equals(property.path("type").asText())) {
                property.path("files").forEach(file -> collectAttachmentFromFileObject(file, attachments, file.path("name").asText(null), null));
            }
        });
    }

    private void collectBlockAttachments(String token,
                                         String blockId,
                                         Set<String> visitedBlockIds,
                                         Map<String, NotionAttachmentRef> attachments) {
        if (!StringUtils.hasText(blockId) || !visitedBlockIds.add(blockId)) {
            return;
        }
        ApiResult result = listBlockChildren(token, blockId);
        if (!result.success()) {
            log.warn("Notion block children request failed for {}: {}", blockId, result.message());
            return;
        }
        for (JsonNode block : collectResults(result.body())) {
            collectAttachmentFromBlock(block, attachments);
            if (block.path("has_children").asBoolean(false)) {
                collectBlockAttachments(token, block.path("id").asText(null), visitedBlockIds, attachments);
            }
        }
    }

    private ApiResult listBlockChildren(String token, String blockId) {
        return executePagedGet(
            token,
            NOTION_VERSION,
            "https://api.notion.com/v1/blocks/" + blockId + "/children?page_size=100",
            "block_children"
        );
    }

    private ApiResult executePagedGet(String token, String notionVersion, String baseUrl, String mode) {
        JsonNode root = null;
        String nextCursor = null;
        try {
            do {
                String url = baseUrl;
                if (StringUtils.hasText(nextCursor)) {
                    url += (baseUrl.contains("?") ? "&" : "?") + "start_cursor=" + URLEncoder.encode(nextCursor, StandardCharsets.UTF_8);
                }
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Notion-Version", notionVersion)
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                JsonNode body = readJson(response.body());
                if (response.statusCode() >= 400) {
                    return new ApiResult(false, mode, body, extractApiError(body, "Не удалось получить данные из Notion."));
                }
                if (root == null) {
                    root = body.deepCopy();
                } else {
                    appendResults(root, body.path("results"));
                }
                nextCursor = body.path("has_more").asBoolean(false) ? body.path("next_cursor").asText(null) : null;
            } while (StringUtils.hasText(nextCursor));
        } catch (IOException ex) {
            throw new IllegalStateException("Не удалось прочитать ответ Notion: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Запрос к Notion был прерван.", ex);
        }
        return new ApiResult(true, mode, root != null ? root : objectMapper.createObjectNode(), null);
    }

    private void collectAttachmentFromBlock(JsonNode block, Map<String, NotionAttachmentRef> attachments) {
        if (block == null || block.isMissingNode()) {
            return;
        }
        String type = block.path("type").asText("");
        JsonNode payload = block.path(type);
        switch (type) {
            case "file", "pdf", "audio", "video" -> collectAttachmentFromFileObject(payload, attachments, readRichText(payload.path("caption")), null);
            default -> {
            }
        }
        collectRichTextAttachmentUrls(payload.path("rich_text"), attachments);
        collectRichTextAttachmentUrls(payload.path("caption"), attachments);
        if ("table_row".equals(type)) {
            payload.path("cells").forEach(cell -> collectRichTextAttachmentUrls(cell, attachments));
        }
    }

    private void collectRichTextAttachmentUrls(JsonNode richText, Map<String, NotionAttachmentRef> attachments) {
        if (richText == null || !richText.isArray()) {
            return;
        }
        for (JsonNode item : richText) {
            String url = firstNonBlank(item.path("href").asText(null), item.path("text").path("link").path("url").asText(null));
            if (isLikelyDownloadableUrl(url)) {
                attachments.putIfAbsent(url, new NotionAttachmentRef(url, extractFileNameFromUrl(url), null));
            }
        }
    }

    private void collectAttachmentFromFileObject(JsonNode fileObject,
                                                 Map<String, NotionAttachmentRef> attachments,
                                                 String fallbackName,
                                                 String fallbackMimeType) {
        if (fileObject == null || fileObject.isMissingNode()) {
            return;
        }
        String type = fileObject.path("type").asText("");
        String url = switch (type) {
            case "file" -> fileObject.path("file").path("url").asText(null);
            case "external" -> fileObject.path("external").path("url").asText(null);
            default -> firstNonBlank(fileObject.path("url").asText(null), fileObject.path("href").asText(null));
        };
        if (!StringUtils.hasText(url)) {
            return;
        }
        String originalName = firstNonBlank(fileObject.path("name").asText(null), fallbackName, extractFileNameFromUrl(url));
        String mimeType = firstNonBlank(fileObject.path("mime_type").asText(null), fallbackMimeType);
        attachments.putIfAbsent(url, new NotionAttachmentRef(url, originalName, mimeType));
    }

    private StoredAttachment downloadAndStoreAttachment(KnowledgeArticle article, NotionAttachmentRef attachment)
        throws IOException, InterruptedException {
        if (!StringUtils.hasText(attachment.url())) {
            return null;
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(attachment.url()))
            .timeout(Duration.ofSeconds(60))
            .GET()
            .build();
        HttpResponse<java.io.InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() >= 400) {
            log.warn("Notion attachment download failed for {} with status {}", attachment.url(), response.statusCode());
            return null;
        }
        String originalName = resolveAttachmentFileName(attachment, response);
        String storedName = buildStoredAttachmentName(article.getExternalId(), attachment.url(), originalName);
        String mimeType = firstNonBlank(
            response.headers().firstValue("Content-Type").orElse(null),
            attachment.mimeType(),
            "application/octet-stream"
        );
        try (var body = response.body()) {
            var metadata = attachmentService.storeImportedKnowledgeBaseFile(storedName, originalName, mimeType, body);
            return new StoredAttachment(
                metadata.originalName(),
                metadata.storedName(),
                metadata.mimeType(),
                metadata.size(),
                metadata.uploadedAt()
            );
        }
    }

    private String resolveAttachmentFileName(NotionAttachmentRef attachment, HttpResponse<?> response) {
        return firstNonBlank(
            trim(attachment.originalName()),
            extractFileNameFromContentDisposition(response.headers().firstValue("Content-Disposition").orElse(null)),
            extractFileNameFromUrl(attachment.url()),
            "attachment.bin"
        );
    }

    private String buildStoredAttachmentName(String externalId, String url, String originalName) {
        return buildNotionAttachmentPrefix(externalId) + shortHash(url) + "_" + sanitizeStoredFileName(originalName);
    }

    private String buildNotionAttachmentPrefix(String externalId) {
        return "notion_" + (externalId != null ? externalId.replaceAll("[^A-Za-z0-9]", "") : "page") + "_";
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

    private String extractFileNameFromUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            if (!StringUtils.hasText(path)) {
                return null;
            }
            String candidate = path.substring(path.lastIndexOf('/') + 1);
            if (!StringUtils.hasText(candidate)) {
                return null;
            }
            return URLDecoder.decode(candidate, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String extractFileNameFromContentDisposition(String header) {
        if (!StringUtils.hasText(header)) {
            return null;
        }
        for (String part : header.split(";")) {
            String trimmed = trim(part);
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            if (trimmed.startsWith("filename*=")) {
                String encoded = trimmed.substring("filename*=".length()).replaceFirst("^[^']*''", "");
                return URLDecoder.decode(encoded.replace("\"", ""), StandardCharsets.UTF_8);
            }
            if (trimmed.startsWith("filename=")) {
                return trimmed.substring("filename=".length()).replace("\"", "");
            }
        }
        return null;
    }

    private String trimTrailingPunctuation(String url) {
        String trimmed = trim(url);
        while (StringUtils.hasText(trimmed) && ".,;)]".contains(trimmed.substring(trimmed.length() - 1))) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private boolean isLikelyDownloadableUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            if (!StringUtils.hasText(path) || !path.contains(".")) {
                return false;
            }
            String extension = path.substring(path.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            return DOWNLOADABLE_EXTENSIONS.contains(extension);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private JsonNode findProperty(JsonNode properties, String propertyName, String fallbackType) {
        if (properties == null || properties.isMissingNode()) {
            return null;
        }
        if (StringUtils.hasText(propertyName) && properties.has(propertyName)) {
            return properties.path(propertyName);
        }
        if (!StringUtils.hasText(fallbackType)) {
            return null;
        }
        for (JsonNode property : properties) {
            if (fallbackType.equals(property.path("type").asText())) {
                return property;
            }
        }
        return null;
    }

    private String extractPropertyText(JsonNode properties, String propertyName, String fallbackType) {
        return extractPropertyText(properties, propertyName, fallbackType, null, Map.of());
    }

    private String extractPropertyText(JsonNode properties,
                                       String propertyName,
                                       String fallbackType,
                                       String token,
                                       Map<String, String> relationDisplayCache) {
        List<String> values = extractPropertyValues(findProperty(properties, propertyName, fallbackType), token, relationDisplayCache);
        return values.isEmpty() ? null : String.join(", ", values);
    }

    private List<String> extractPropertyValues(JsonNode property) {
        return extractPropertyValues(property, null, Map.of());
    }

    private List<String> extractPropertyValues(JsonNode property, String token, Map<String, String> relationDisplayCache) {
        List<String> values = new ArrayList<>();
        if (property == null || property.isMissingNode()) {
            return values;
        }
        String type = property.path("type").asText("");
        switch (type) {
            case "title", "rich_text" -> addIfHasText(values, readRichText(property.path(type)));
            case "select", "status" -> addIfHasText(values, property.path(type).path("name").asText(null));
            case "multi_select" -> property.path(type).forEach(item -> addIfHasText(values, item.path("name").asText(null)));
            case "people" -> property.path(type).forEach(item -> addIfHasText(values, readUserName(item)));
            case "created_by", "last_edited_by" -> addIfHasText(values, readUserName(property.path(type)));
            case "email", "url", "phone_number" -> addIfHasText(values, property.path(type).asText(null));
            case "number" -> addIfHasText(values, property.path(type).asText(null));
            case "checkbox" -> values.add(Boolean.toString(property.path(type).asBoolean(false)));
            case "date" -> {
                String start = property.path(type).path("start").asText(null);
                String end = property.path(type).path("end").asText(null);
                addIfHasText(values, StringUtils.hasText(end) ? start + " - " + end : start);
            }
            case "formula" -> addIfHasText(values, readFormulaValue(property.path(type)));
            case "rollup" -> values.addAll(readRollupValues(property.path(type), token, relationDisplayCache));
            case "relation" -> property.path(type).forEach(item -> addIfHasText(
                values,
                resolveRelationDisplayValue(item.path("id").asText(null), token, relationDisplayCache)
            ));
            case "unique_id" -> {
                JsonNode unique = property.path(type);
                addIfHasText(values, (unique.path("prefix").asText("") + unique.path("number").asText("")).trim());
            }
            default -> {
                if (property.isTextual()) {
                    addIfHasText(values, property.asText());
                }
            }
        }
        return values.stream().distinct().toList();
    }

    private String readRichText(JsonNode richText) {
        if (richText == null || !richText.isArray()) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode item : richText) {
            String plain = item.path("plain_text").asText("");
            if (!plain.isBlank()) {
                text.append(plain);
            }
        }
        return text.toString().trim();
    }

    private String readFormulaValue(JsonNode formula) {
        String type = formula.path("type").asText("");
        return switch (type) {
            case "string" -> formula.path("string").asText(null);
            case "number" -> formula.path("number").asText(null);
            case "boolean" -> Boolean.toString(formula.path("boolean").asBoolean(false));
            case "date" -> formula.path("date").path("start").asText(null);
            default -> null;
        };
    }

    private List<String> readRollupValues(JsonNode rollup, String token, Map<String, String> relationDisplayCache) {
        List<String> values = new ArrayList<>();
        String type = rollup.path("type").asText("");
        switch (type) {
            case "array" -> rollup.path("array").forEach(item -> values.addAll(extractPropertyValues(item, token, relationDisplayCache)));
            case "number" -> addIfHasText(values, rollup.path("number").asText(null));
            case "date" -> addIfHasText(values, rollup.path("date").path("start").asText(null));
            default -> addIfHasText(values, rollup.asText(null));
        }
        return values;
    }

    private String readUserName(JsonNode user) {
        if (user == null || user.isMissingNode()) {
            return null;
        }
        String name = user.path("name").asText(null);
        if (StringUtils.hasText(name)) {
            return name;
        }
        return user.path("person").path("email").asText(null);
    }

    private String resolveRelationDisplayValue(String relationId, String token, Map<String, String> relationDisplayCache) {
        if (!StringUtils.hasText(relationId)) {
            return null;
        }
        if (!StringUtils.hasText(token)) {
            return relationId;
        }
        if (relationDisplayCache.containsKey(relationId)) {
            return relationDisplayCache.get(relationId);
        }
        ApiResult result = executeGet(token, NOTION_VERSION, "https://api.notion.com/v1/pages/" + relationId, "relation_page");
        String resolved = relationId;
        if (result.success()) {
            resolved = firstNonBlank(readPageTitle(result.body()), relationId);
        }
        relationDisplayCache.put(relationId, resolved);
        return resolved;
    }

    private String readPageTitle(JsonNode page) {
        if (page == null || page.isMissingNode()) {
            return null;
        }
        JsonNode properties = page.path("properties");
        if (properties == null || properties.isMissingNode()) {
            return null;
        }
        for (JsonNode property : properties) {
            if ("title".equals(property.path("type").asText())) {
                return readRichText(property.path("title"));
            }
        }
        return null;
    }

    private List<String> normalizeAuthors(Object raw) {
        List<String> authors = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                addIfHasText(authors, item != null ? item.toString() : null);
            }
        } else if (raw instanceof String text) {
            for (String item : text.split("[\\r\\n,;]+")) {
                addIfHasText(authors, item);
            }
        }
        return authors.stream().distinct().toList();
    }

    private String resolveLocalStatus(String rawStatus) {
        String normalized = normalizeValue(rawStatus);
        if (!StringUtils.hasText(normalized)) {
            return "published";
        }
        if (normalized.contains("архив") || normalized.contains("archive")) {
            return "archived";
        }
        if (normalized.contains("черновик") || normalized.contains("draft")) {
            return "draft";
        }
        return "published";
    }

    private Set<String> normalizeAuthorSet(List<String> authors) {
        Set<String> values = new LinkedHashSet<>();
        for (String author : authors) {
            String normalized = normalizeValue(author);
            if (StringUtils.hasText(normalized)) {
                values.add(normalized);
            }
        }
        return values;
    }

    private Set<String> normalizeSelectedIds(List<String> selectedExternalIds) {
        Set<String> values = new HashSet<>();
        if (selectedExternalIds == null) {
            return values;
        }
        for (String selectedExternalId : selectedExternalIds) {
            String trimmed = trim(selectedExternalId);
            if (StringUtils.hasText(trimmed)) {
                values.add(trimmed);
            }
        }
        return values;
    }

    private boolean matchesAuthors(JsonNode property,
                                   Set<String> authorFilter,
                                   String token,
                                   Map<String, String> relationDisplayCache) {
        if (authorFilter.isEmpty()) {
            return true;
        }
        for (String value : extractPropertyValues(property, token, relationDisplayCache)) {
            for (String part : splitCompositeValue(value)) {
                String normalized = normalizeValue(part);
                if (authorFilter.contains(normalized)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String extractSourceId(String rawSource) {
        String source = trim(rawSource);
        if (!StringUtils.hasText(source)) {
            throw new IllegalStateException("Не задана ссылка или ID базы Notion.");
        }
        validateSourceReferenceFormat(source);
        try {
            URI uri = URI.create(source);
            String path = uri.getPath();
            if (StringUtils.hasText(path)) {
                for (String segment : path.split("/")) {
                    String candidate = extractUuid(segment);
                    if (StringUtils.hasText(candidate)) {
                        return candidate;
                    }
                }
            }
        } catch (IllegalArgumentException ignored) {
        }
        String direct = extractUuid(source);
        if (!StringUtils.hasText(direct)) {
            throw new IllegalStateException("Не удалось извлечь ID базы Notion из ссылки.");
        }
        return direct;
    }

    void validateSourceReferenceFormat(String rawSource) {
        String source = trim(rawSource);
        if (!StringUtils.hasText(source)) {
            return;
        }
        if (StringUtils.hasText(extractUuid(source))) {
            return;
        }
        try {
            URI uri = URI.create(source);
            String host = trim(uri.getHost());
            String path = trim(uri.getPath());
            String normalizedPath = path != null ? path.replace("/", "") : "";
            if (StringUtils.hasText(host) && host.contains("notion.so") && !StringUtils.hasText(normalizedPath)) {
                throw new IllegalStateException(
                    "Укажите ссылку на конкретную базу Notion или прямой UUID data source. Главная страница Notion без UUID не подходит."
                );
            }
        } catch (IllegalArgumentException ignored) {
        }
        throw new IllegalStateException(
            "Не удалось извлечь UUID из значения источника Notion. Укажите ссылку на конкретную базу или прямой UUID data source / database."
        );
    }

    String decorateNotionApiMessage(String sourceIdOrUrl, String message) {
        if (!StringUtils.hasText(message)) {
            return message;
        }
        if (containsMissingDataSourceAccessMessage(message)) {
            return buildMissingDataSourceMessage(extractUuid(sourceIdOrUrl), message);
        }
        return message;
    }

    boolean containsMissingDataSourceAccessMessage(String message) {
        return StringUtils.hasText(message)
            && message.toLowerCase(Locale.ROOT).contains("does not contain any data sources accessible by this api bot");
    }

    String buildMissingDataSourceMessage(String sourceId, String originalMessage) {
        StringBuilder builder = new StringBuilder();
        builder.append("Notion не нашёл доступный data source");
        if (StringUtils.hasText(sourceId)) {
            builder.append(" для базы ").append(sourceId);
        }
        builder.append(". Обычно это значит, что вы указали database, linked database/view, wiki database или integration не подключён к исходной базе.");
        builder.append(" Откройте original database в Notion, добавьте integration через Add connections и вставьте в поле прямой data_source_id.");
        if (StringUtils.hasText(originalMessage)) {
            builder.append(" Ответ Notion: ").append(originalMessage);
        }
        return builder.toString();
    }

    private String extractUuid(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        Matcher matcher = UUID_PATTERN.matcher(raw);
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(1).replace("-", "").toLowerCase(Locale.ROOT);
        return value.replaceFirst(
            "([0-9a-f]{8})([0-9a-f]{4})([0-9a-f]{4})([0-9a-f]{4})([0-9a-f]{12})",
            "$1-$2-$3-$4-$5"
        );
    }

    private JsonNode readJson(String body) throws IOException {
        if (!StringUtils.hasText(body)) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(body);
    }

    private String extractApiError(JsonNode body, String fallback) {
        if (body == null || body.isMissingNode()) {
            return fallback;
        }
        String message = body.path("message").asText(null);
        return StringUtils.hasText(message) ? message : fallback;
    }

    private List<JsonNode> collectResults(JsonNode root) {
        List<JsonNode> results = new ArrayList<>();
        if (root == null || root.isMissingNode()) {
            return results;
        }
        root.path("results").forEach(results::add);
        return results;
    }

    private void appendResults(JsonNode root, JsonNode additions) {
        if (root == null || additions == null || !additions.isArray()) {
            return;
        }
        JsonNode existing = root.path("results");
        if (!existing.isArray()) {
            return;
        }
        additions.forEach(item -> ((com.fasterxml.jackson.databind.node.ArrayNode) existing).add(item));
    }

    private List<String> splitCompositeValue(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        for (String item : value.split("[\\r\\n,;/]+")) {
            addIfHasText(parts, item);
        }
        return parts.isEmpty() ? List.of(value.trim()) : parts;
    }

    private String buildSummaryFromMarkdown(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return null;
        }
        String plain = markdown
            .replaceAll("```[\\s\\S]*?```", " ")
            .replaceAll("[#>*_\\-\\[\\]()|`]", " ")
            .replaceAll("\\s+", " ")
            .trim();
        if (!StringUtils.hasText(plain)) {
            return null;
        }
        return plain.length() > 220 ? plain.substring(0, 217) + "..." : plain;
    }

    private OffsetDateTime parseOffsetDateTime(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return OffsetDateTime.parse(raw);
    }

    private String normalizeValue(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static boolean asBoolean(Object raw) {
        if (raw instanceof Boolean value) {
            return value;
        }
        if (raw instanceof Number number) {
            return number.intValue() != 0;
        }
        return raw != null && Boolean.parseBoolean(raw.toString());
    }

    private static String stringValue(Object raw) {
        return raw != null ? raw.toString().trim() : null;
    }

    private static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static void addIfHasText(List<String> values, String raw) {
        if (StringUtils.hasText(raw)) {
            values.add(raw.trim());
        }
    }

    private record NotionConfig(boolean enabled,
                                String sourceUrl,
                                String token,
                                List<String> authors,
                                String titleProperty,
                                String authorProperty,
                                String summaryProperty,
                                String departmentProperty,
                                String articleTypeProperty,
                                String directionProperty,
                                String directionSubtypeProperty,
                                String statusProperty) {
    }

    private record QueryResult(String mode, int totalPages, List<JsonNode> matchedPages) {
    }

    private record ImportedArticle(String title,
                                   String author,
                                   String summary,
                                   String department,
                                   String articleType,
                                   String direction,
                                   String directionSubtype,
                                   String status,
                                   String content,
                                   String externalId,
                                   String externalUrl,
                                   OffsetDateTime externalUpdatedAt) {
    }

    private record PreviewArticle(String title,
                                  String author,
                                  String summary,
                                  String department,
                                  String articleType,
                                  String notionStatus,
                                  String localStatus,
                                  String externalId,
                                  String externalUrl,
                                  OffsetDateTime externalUpdatedAt) {
    }

    private record NotionAttachmentRef(String url, String originalName, String mimeType) {
    }

    private record StoredAttachment(String originalName,
                                    String storedName,
                                    String mimeType,
                                    long size,
                                    OffsetDateTime uploadedAt) {
    }

    private record ApiResult(boolean success, String mode, JsonNode body, String message) {
    }

    public record ConnectionCheckResult(String mode, int matchedPages, int totalPages, String sourceUrl) {
    }

    public record ImportResult(String mode,
                               int matchedPages,
                               int selectedPages,
                               int totalPages,
                               int created,
                               int updated,
                               int skipped) {
    }
}
