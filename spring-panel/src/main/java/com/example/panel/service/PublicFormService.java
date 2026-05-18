package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.entity.ChatHistory;
import com.example.panel.entity.Message;
import com.example.panel.entity.Ticket;
import com.example.panel.entity.TicketId;
import com.example.panel.entity.WebFormSession;
import com.example.panel.model.publicform.PublicFormConfig;
import com.example.panel.model.publicform.PublicFormQuestion;
import com.example.panel.model.publicform.PublicFormSessionDto;
import com.example.panel.model.publicform.PublicFormSubmission;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.repository.ChatHistoryRepository;
import com.example.panel.repository.MessageRepository;
import com.example.panel.repository.TicketRepository;
import com.example.panel.repository.WebFormSessionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Transactional
public class PublicFormService {

    private static final Logger log = LoggerFactory.getLogger(PublicFormService.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[+]?[-()\\s0-9]{6,20}$");
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("(?i)<\\/?[a-z][^>]*>");
    private static final Pattern CONTROL_CHARS_PATTERN = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]");
    private static final int DEFAULT_MESSAGE_MAX_LENGTH = 4000;
    private static final int DEFAULT_ANSWERS_TOTAL_MAX_LENGTH = 6000;
    private static final int DEFAULT_ALERT_MIN_VIEWS = 20;
    private static final double DEFAULT_ALERT_ERROR_RATE = 0.35d;
    private static final double DEFAULT_ALERT_CAPTCHA_FAILURE_RATE = 0.20d;
    private static final double DEFAULT_ALERT_RATE_LIMIT_REJECTION_RATE = 0.20d;
    private static final double DEFAULT_ALERT_SESSION_LOOKUP_MISS_RATE = 0.30d;
    private static final Set<String> LOCATION_FIELD_IDS = Set.of("business", "location_type", "city", "location_name");

    private final ChannelRepository channelRepository;
    private final WebFormSessionRepository sessionRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private final TicketRepository ticketRepository;
    private final MessageRepository messageRepository;
    private final ObjectMapper objectMapper;
    private final PublicFormRuntimeConfigService publicFormRuntimeConfigService;
    private final PublicFormMetricsService publicFormMetricsService;
    private final PublicFormSessionService publicFormSessionService;
    private final PublicFormAntiAbuseService publicFormAntiAbuseService;
    private final SettingsCatalogService settingsCatalogService;
    private final IikoDepartmentLocationCatalogService locationCatalogService;
    private final DialogAuditService dialogAuditService;
    private final AlertQueueService alertQueueService;
    private final AtomicLong syntheticMessageId = new AtomicLong(System.currentTimeMillis());
    private final HttpClient httpClient = HttpClient.newBuilder().build();

    public PublicFormService(ChannelRepository channelRepository,
                             WebFormSessionRepository sessionRepository,
                             ChatHistoryRepository chatHistoryRepository,
                             TicketRepository ticketRepository,
                             MessageRepository messageRepository,
                             ObjectMapper objectMapper,
                             PublicFormRuntimeConfigService publicFormRuntimeConfigService,
                             PublicFormMetricsService publicFormMetricsService,
                             PublicFormSessionService publicFormSessionService,
                             PublicFormAntiAbuseService publicFormAntiAbuseService,
                             SettingsCatalogService settingsCatalogService,
                             IikoDepartmentLocationCatalogService locationCatalogService,
                             DialogAuditService dialogAuditService,
                             AlertQueueService alertQueueService) {
        this.channelRepository = channelRepository;
        this.sessionRepository = sessionRepository;
        this.chatHistoryRepository = chatHistoryRepository;
        this.ticketRepository = ticketRepository;
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
        this.publicFormRuntimeConfigService = publicFormRuntimeConfigService;
        this.publicFormMetricsService = publicFormMetricsService;
        this.publicFormSessionService = publicFormSessionService;
        this.publicFormAntiAbuseService = publicFormAntiAbuseService;
        this.settingsCatalogService = settingsCatalogService;
        this.locationCatalogService = locationCatalogService;
        this.dialogAuditService = dialogAuditService;
        this.alertQueueService = alertQueueService;
    }

    @Transactional(readOnly = true)
    public Optional<PublicFormConfig> loadConfig(String channelRef) {
        return loadConfigRaw(channelRef).filter(PublicFormConfig::enabled);
    }

    @Transactional(readOnly = true)
    public Optional<PublicFormConfig> loadConfigRaw(String channelRef) {
        if (channelRef != null && channelRef.trim().equalsIgnoreCase("demo")) {
            return Optional.of(buildDemoConfig());
        }
        return resolveChannel(channelRef).map(this::toConfig);
    }

    public PublicFormSessionDto createSession(String channelRef, PublicFormSubmission submission, String requesterKey) {
        Channel channel = resolveChannel(channelRef)
                .orElseThrow(() -> new IllegalArgumentException("Канал не найден"));
        if (!Boolean.TRUE.equals(channel.getActive())) {
            throw new IllegalArgumentException("Форма канала временно отключена");
        }

        PublicFormConfig config = toConfig(channel);
        if (!config.enabled()) {
            throw new IllegalArgumentException("Форма канала временно отключена");
        }
        boolean stripHtmlTags = readDialogConfigBoolean("public_form_strip_html_tags", true);
        PublicFormSubmission normalizedSubmission = normalizeSubmission(submission, stripHtmlTags);
        Map<String, String> answers = sanitizeAnswers(submission.answers(), stripHtmlTags);
        PublicFormAntiAbuseService.SubmissionFingerprint fingerprint =
                publicFormAntiAbuseService.prepareSubmissionFingerprint(normalizedSubmission, answers);
        Optional<PublicFormSessionDto> duplicate =
                publicFormAntiAbuseService.findIdempotentSession(channel, requesterKey, fingerprint);
        if (duplicate.isPresent()) {
            log.info("Public form idempotency hit for channel {} requesterHash {} requestId {}",
                    channel.getId(), publicFormAntiAbuseService.summarizeRequester(requesterKey), fingerprint.requestId());
            return duplicate.get();
        }

        publicFormAntiAbuseService.enforceRateLimit(channel, requesterKey);
        enforceCaptcha(config, normalizedSubmission);
        validateSubmission(config, normalizedSubmission, answers);
        String combinedMessage = buildCombinedMessage(config, answers, normalizedSubmission.message());

        WebFormSession session = new WebFormSession();
        session.setChannel(channel);
        session.setToken(generateToken());
        session.setTicketId(generateTicketId());
        session.setUserId(generateSyntheticUserId(channel.getId(), requesterKey));
        session.setAnswersJson(writeJson(answers));
        session.setClientName(resolveClientName(normalizedSubmission, answers));
        session.setClientContact(trim(normalizedSubmission.clientContact()));
        session.setUsername(StringUtils.hasText(normalizedSubmission.username()) ? normalizedSubmission.username().trim() : "web_form");
        OffsetDateTime now = OffsetDateTime.now();
        session.setCreatedAt(now);
        session.setLastActiveAt(now);
        createTicketProjection(channel, session, normalizedSubmission, answers, now);
        WebFormSession saved = sessionRepository.save(session);

        ChatHistory history = new ChatHistory();
        history.setChannel(channel);
        history.setTicketId(saved.getTicketId());
        history.setSender("user");
        history.setMessage(combinedMessage);
        history.setTimestamp(now);
        history.setMessageType("text");
        chatHistoryRepository.save(history);

        PublicFormSessionDto result = new PublicFormSessionDto(
                saved.getToken(),
                saved.getTicketId(),
                channel.getId(),
                channel.getPublicId(),
                saved.getClientName(),
                saved.getClientContact(),
                saved.getUsername(),
                saved.getCreatedAt()
        );
        dialogAuditService.logDialogActionAudit(
                saved.getTicketId(),
                saved.getUsername(),
                "public_form_submit",
                "success",
                "channel=" + channel.getId() + ", source=web_form"
        );
        alertQueueService.notifyQueueForNewPublicAppeal(channel, saved.getTicketId(), combinedMessage);
        publicFormAntiAbuseService.cacheIdempotentSession(channel, requesterKey, fingerprint, result);
        return result;
    }

    private void createTicketProjection(Channel channel,
                                        WebFormSession session,
                                        PublicFormSubmission submission,
                                        Map<String, String> answers,
                                        OffsetDateTime now) {
        Ticket ticket = new Ticket();
        TicketId ticketId = new TicketId();
        ticketId.setTicketId(session.getTicketId());
        ticketId.setUserId(session.getUserId());
        ticket.setId(ticketId);
        ticket.setGroupMessageId(nextSyntheticMessageId());
        ticket.setStatus("open");
        ticket.setChannel(channel);
        ticket.setReopenCount(0);
        ticket.setClosedCount(0);
        ticket.setWorkTimeTotalSec(0L);
        ticketRepository.save(ticket);

        Message message = new Message();
        message.setId(ticket.getGroupMessageId());
        message.setUserId(session.getUserId());
        message.setBusiness(firstNonBlankAnswer(answers, "business", "department"));
        message.setCity(firstNonBlankAnswer(answers, "city"));
        message.setLocationName(firstNonBlankAnswer(answers, "location", "location_name", "office"));
        message.setProblem(trim(submission.message()));
        message.setCreatedAt(now);
        message.setUsername(session.getUsername());
        message.setTicketId(session.getTicketId());
        message.setCreatedDate(now.toLocalDate());
        message.setCreatedTime(now.toLocalTime().withNano(0).toString());
        message.setClientName(session.getClientName());
        message.setChannel(channel);
        message.setUpdatedAt(now);
        message.setUpdatedBy("public_form");
        messageRepository.save(message);
    }

    private Long nextSyntheticMessageId() {
        return syntheticMessageId.incrementAndGet();
    }

    private long generateSyntheticUserId(Long channelId, String requesterKey) {
        String seed = (channelId == null ? "0" : channelId.toString()) + "|"
                + (requesterKey == null ? "anon" : requesterKey.trim());
        return 900_000_000L + Integer.toUnsignedLong(seed.hashCode());
    }

    private String firstNonBlankAnswer(Map<String, String> answers, String... keys) {
        for (String key : keys) {
            String value = answers.get(key);
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    public void recordConfigView(Long channelId) {
        publicFormMetricsService.recordConfigView(channelId);
    }

    public void recordSubmitSuccess(Long channelId) {
        publicFormMetricsService.recordSubmitSuccess(channelId);
    }

    public void recordSubmitError(Long channelId, String reason) {
        publicFormMetricsService.recordSubmitError(channelId, reason);
    }

    public void recordSessionLookup(Long channelId, boolean found) {
        publicFormMetricsService.recordSessionLookup(channelId, found);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> loadMetricsSnapshot(Long channelId) {
        return publicFormMetricsService.loadMetricsSnapshot(channelId);
    }

    public Optional<PublicFormSessionDto> findSession(String channelRef, String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        return resolveChannel(channelRef)
                .flatMap(channel -> publicFormSessionService.findSession(channel, token));
    }

    @Transactional(readOnly = true)
    public Optional<Long> resolveChannelId(String channelRef) {
        return resolveChannel(channelRef).map(Channel::getId);
    }

    private void validateSubmission(PublicFormConfig config, PublicFormSubmission submission, Map<String, String> answers) {
        if (!StringUtils.hasText(submission.message())) {
            throw new IllegalArgumentException("Опишите проблему");
        }
        int maxLength = readDialogConfigInt("public_form_message_max_length", DEFAULT_MESSAGE_MAX_LENGTH, 300, 20000);
        if (submission.message().trim().length() > maxLength) {
            throw new IllegalArgumentException("Сообщение слишком длинное (макс. " + maxLength + " символов)");
        }
        int answersPayloadMaxLength = readDialogConfigInt("public_form_answers_total_max_length", DEFAULT_ANSWERS_TOTAL_MAX_LENGTH, 200, 50000);
        int answersPayloadLength = answers.values().stream()
                .filter(StringUtils::hasText)
                .mapToInt(String::length)
                .sum();
        if (answersPayloadLength > answersPayloadMaxLength) {
            throw new IllegalArgumentException("Суммарный объём ответов формы превышает лимит "
                    + answersPayloadMaxLength + " символов");
        }
        for (PublicFormQuestion question : config.questions()) {
            String value = answers.get(question.id());
            if (isRequired(question) && !StringUtils.hasText(value)) {
                throw new IllegalArgumentException("Заполните поле: " + questionLabel(question));
            }
            if (!StringUtils.hasText(value)) {
                continue;
            }
            int minLength = metadataInt(question, "minLength", 0);
            int maxQuestionLength = metadataInt(question, "maxLength", 500);
            if (value.length() < minLength) {
                throw new IllegalArgumentException("Поле «" + questionLabel(question) + "» должно содержать минимум " + minLength + " символов");
            }
            if (value.length() > maxQuestionLength) {
                throw new IllegalArgumentException("Поле «" + questionLabel(question) + "» превышает лимит " + maxQuestionLength + " символов");
            }
            validateByType(question, value, answers);
        }
    }

    private void validateByType(PublicFormQuestion question, String value, Map<String, String> answers) {
        String type = Optional.ofNullable(question.type()).orElse("text").toLowerCase(Locale.ROOT);
        if ("checkbox".equals(type)) {
            if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value) && !"1".equals(value) && !"0".equals(value)) {
                throw new IllegalArgumentException("Поле «" + questionLabel(question) + "» должно быть булевым значением");
            }
            if (isRequired(question) && !("true".equalsIgnoreCase(value) || "1".equals(value))) {
                throw new IllegalArgumentException("Подтвердите поле: " + questionLabel(question));
            }
            return;
        }
        if ("email".equals(type) && !EMAIL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Поле «" + questionLabel(question) + "» должно содержать корректный email");
        }
        if ("phone".equals(type) && !PHONE_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Поле «" + questionLabel(question) + "» должно содержать корректный телефон");
        }
        if ("select".equals(type)) {
            List<String> options = resolveSelectOptions(question, answers);
            boolean allowCustom = metadataBoolean(question, "allowCustom", false);
            boolean validOption = options.stream().anyMatch(value::equalsIgnoreCase);
            boolean rejectValue = LOCATION_FIELD_IDS.contains(question.id())
                    ? !validOption
                    : !options.isEmpty() && !validOption;
            if (!allowCustom && rejectValue) {
                throw new IllegalArgumentException("Поле «" + questionLabel(question) + "» содержит недопустимое значение");
            }
        }
    }

    public String buildRequesterKey(String requesterIp, String fingerprint) {
        return publicFormAntiAbuseService.buildRequesterKey(requesterIp, fingerprint);
    }

    private Optional<Channel> resolveChannel(String channelRef) {
        if (!StringUtils.hasText(channelRef)) {
            return Optional.empty();
        }
        String trimmed = channelRef.trim();
        Optional<Channel> direct = channelRepository.findByPublicIdIgnoreCase(trimmed);
        if (direct.isPresent()) {
            return direct;
        }
        try {
            long id = Long.parseLong(trimmed);
            return channelRepository.findById(id);
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private PublicFormConfig toConfig(Channel channel) {
        List<PublicFormQuestion> questions = parseQuestions(channel);
        String publicId = StringUtils.hasText(channel.getPublicId())
                ? channel.getPublicId()
                : String.valueOf(channel.getId());
        ParsedPublicFormSettings settings = parseSettings(channel);
        return new PublicFormConfig(channel.getId(), publicId, channel.getChannelName(), settings.schemaVersion(), settings.enabled(),
                settings.captchaEnabled(), settings.disabledStatus(), settings.successInstruction(),
                settings.responseEtaMinutes(), questions);
    }

    private PublicFormConfig buildDemoConfig() {
        List<PublicFormQuestion> demoQuestions = List.of(
                new PublicFormQuestion("client_name", "Как вас зовут?", "text", 1, Map.of("required", true)),
                new PublicFormQuestion("contact", "Как с вами связаться?", "text", 2, Map.of("required", true)),
                new PublicFormQuestion("urgency", "Насколько срочно решить вопрос?", "select", 3, Map.of(
                        "required", true,
                        "options", List.of("Срочно", "В течение дня", "Не горит"),
                        "placeholder", "Выберите приоритет"
                )),
                new PublicFormQuestion("location", "Где возникла проблема?", "text", 4, Map.of("placeholder", "Адрес или подразделение")),
                new PublicFormQuestion("details", "Опишите ситуацию подробнее", "textarea", 5, Map.of("rows", 3, "maxLength", 1000))
        );

        return new PublicFormConfig(0L, "demo", "Демо-канал", 1, true, false, 404,
                "Обычно отвечаем в течение рабочего дня.", 240, demoQuestions);
    }

    private List<PublicFormQuestion> parseQuestions(Channel channel) {
        ParsedPublicFormSettings settings = parseSettings(channel);
        if (settings.fields().isEmpty()) {
            return List.of();
        }
        Map<String, Map<String, Object>> locationFields = loadLocationPresetFields();
        AtomicInteger index = new AtomicInteger(0);
        return settings.fields().stream()
                .map(entry -> normalizeQuestion(entry, index.incrementAndGet()))
                .map(question -> enrichLocationQuestion(question, locationFields))
                .sorted((a, b) -> Integer.compare(Optional.ofNullable(a.order()).orElse(0), Optional.ofNullable(b.order()).orElse(0)))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> loadLocationPresetFields() {
        try {
            IikoDepartmentLocationCatalogService.LocationCatalogSnapshot catalog = locationCatalogService.loadCatalog();
            Map<String, Object> presets = settingsCatalogService.buildLocationPresets(catalog.tree(), catalog.statuses());
            Object locationsGroup = presets.get("locations");
            if (!(locationsGroup instanceof Map<?, ?> groupMap)) {
                return Map.of();
            }
            Object fieldsRaw = groupMap.get("fields");
            if (!(fieldsRaw instanceof Map<?, ?> fieldsMap)) {
                return Map.of();
            }
            LinkedHashMap<String, Map<String, Object>> result = new LinkedHashMap<>();
            fieldsMap.forEach((key, value) -> {
                if (key != null && value instanceof Map<?, ?> fieldMap) {
                    LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
                    fieldMap.forEach((metaKey, metaValue) -> {
                        if (metaKey != null) {
                            metadata.put(String.valueOf(metaKey), metaValue);
                        }
                    });
                    result.put(String.valueOf(key), metadata);
                }
            });
            return result;
        } catch (Exception ex) {
            log.warn("Failed to load location presets for public form: {}", ex.getMessage());
            return Map.of();
        }
    }

    private PublicFormQuestion enrichLocationQuestion(PublicFormQuestion question, Map<String, Map<String, Object>> locationFields) {
        if (question == null || !LOCATION_FIELD_IDS.contains(question.id())) {
            return question;
        }
        Map<String, Object> presetMetadata = locationFields.get(question.id());
        if (presetMetadata == null || presetMetadata.isEmpty()) {
            return question;
        }
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>(Optional.ofNullable(question.metadata()).orElse(Map.of()));
        if (presetMetadata.containsKey("options")) {
            metadata.put("options", presetMetadata.get("options"));
        }
        if (presetMetadata.containsKey("tree")) {
            metadata.put("tree", presetMetadata.get("tree"));
        } else {
            metadata.remove("tree");
        }
        if (presetMetadata.containsKey("option_dependencies")) {
            metadata.put("option_dependencies", presetMetadata.get("option_dependencies"));
        } else {
            metadata.remove("option_dependencies");
        }
        if (!metadata.containsKey("placeholder")) {
            metadata.put("placeholder", "Выберите вариант");
        }
        return new PublicFormQuestion(question.id(), question.text(), "select", question.order(), metadata);
    }

    private ParsedPublicFormSettings parseSettings(Channel channel) {
        String payload = channel.getQuestionsCfg();
        if (!StringUtils.hasText(payload)) {
            return ParsedPublicFormSettings.defaults();
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root.isArray()) {
                List<Map<String, Object>> fields = objectMapper.convertValue(root, new TypeReference<List<Map<String, Object>>>() {
                });
                return new ParsedPublicFormSettings(1, true, false, 404,
                        null, null, null, fields, null, null);
            }
            if (root.isObject()) {
                int schemaVersion = Math.max(1, root.path("schemaVersion").asInt(1));
                boolean enabled = !root.has("enabled") || root.path("enabled").asBoolean(true);
                boolean captchaEnabled = root.path("captchaEnabled").asBoolean(false);
                int disabledStatus = normalizeDisabledStatus(root.path("disabledStatus").asInt(404));
                JsonNode fieldsNode = root.path("fields");
                List<Map<String, Object>> fields = fieldsNode.isArray()
                        ? objectMapper.convertValue(fieldsNode, new TypeReference<List<Map<String, Object>>>() {
                        })
                        : List.of();
                Boolean rateLimitEnabled = root.has("rateLimitEnabled")
                        ? root.path("rateLimitEnabled").asBoolean(false)
                        : null;
                Integer rateLimitWindowSeconds = root.has("rateLimitWindowSeconds")
                        ? normalizeRange(root.path("rateLimitWindowSeconds").asInt(60), 10, 3600)
                        : null;
                Integer rateLimitMaxRequests = root.has("rateLimitMaxRequests")
                        ? normalizeRange(root.path("rateLimitMaxRequests").asInt(5), 1, 500)
                        : null;
                String successInstruction = trim(value(root.get("successInstruction")));
                Integer responseEtaMinutes = root.has("responseEtaMinutes")
                        ? normalizeRange(root.path("responseEtaMinutes").asInt(0), 0, 7 * 24 * 60)
                        : null;
                return new ParsedPublicFormSettings(schemaVersion, enabled, captchaEnabled, disabledStatus,
                        rateLimitEnabled, rateLimitWindowSeconds, rateLimitMaxRequests,
                        fields, successInstruction, responseEtaMinutes);
            }
            return ParsedPublicFormSettings.defaults();
        } catch (Exception ex) {
            log.warn("Failed to parse questions configuration for channel {}: {}", channel.getId(), ex.getMessage());
            return ParsedPublicFormSettings.defaults();
        }
    }

    private void enforceCaptcha(PublicFormConfig config, PublicFormSubmission submission) {
        if (!config.captchaEnabled()) {
            return;
        }
        String mode = readDialogConfigString("public_form_captcha_mode", "shared_secret").trim().toLowerCase(Locale.ROOT);
        if ("turnstile".equals(mode)) {
            verifyTurnstileCaptcha(submission.captchaToken());
            return;
        }
        String expected = readDialogConfigString("public_form_captcha_shared_secret", "");
        String token = submission.captchaToken();
        if (!StringUtils.hasText(expected)) {
            throw new IllegalArgumentException("CAPTCHA включена, но секрет не настроен");
        }
        if (!StringUtils.hasText(token) || !expected.trim().equals(token.trim())) {
            throw new IllegalArgumentException("Проверка CAPTCHA не пройдена");
        }
    }

    private void verifyTurnstileCaptcha(String token) {
        String secret = readDialogConfigString("public_form_turnstile_secret_key", "");
        if (!StringUtils.hasText(secret)) {
            throw new IllegalArgumentException("Turnstile включён, но secret key не настроен");
        }
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("Проверка CAPTCHA не пройдена");
        }
        String verifyUrl = readDialogConfigString("public_form_turnstile_verify_url",
                "https://challenges.cloudflare.com/turnstile/v0/siteverify");
        int timeoutMs = readDialogConfigInt("public_form_turnstile_timeout_ms", 4000, 500, 15000);
        String payload = "secret=" + urlEncode(secret)
                + "&response=" + urlEncode(token.trim());
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(verifyUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(java.time.Duration.ofMillis(timeoutMs))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalArgumentException("CAPTCHA-сервис недоступен");
            }
            JsonNode root = objectMapper.readTree(Optional.ofNullable(response.body()).orElse("{}"));
            if (!root.path("success").asBoolean(false)) {
                throw new IllegalArgumentException("Проверка CAPTCHA не пройдена");
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Turnstile verification failed: {}", ex.getMessage());
            throw new IllegalArgumentException("Не удалось проверить CAPTCHA, попробуйте позже");
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(Optional.ofNullable(value).orElse(""), StandardCharsets.UTF_8);
    }

    private boolean isMetricsEnabled() {
        return publicFormRuntimeConfigService.isMetricsEnabled();
    }

    public int resolveAnswersPayloadMaxLength() {
        return publicFormRuntimeConfigService.resolveAnswersPayloadMaxLength();
    }

    public boolean isSessionPollingEnabled() {
        return publicFormRuntimeConfigService.isSessionPollingEnabled();
    }

    public int resolveSessionPollingIntervalSeconds() {
        return publicFormRuntimeConfigService.resolveSessionPollingIntervalSeconds();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> buildContinuationOptions(String channelRef, String sessionToken) {
        return resolveChannel(channelRef)
                .map(channel -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    String platform = Optional.ofNullable(channel.getPlatform()).filter(StringUtils::hasText).orElse("telegram").trim().toLowerCase(Locale.ROOT);
                    String command = StringUtils.hasText(sessionToken) ? "/continue " + sessionToken.trim() : "/continue <token>";
                    payload.put("enabled", true);
                    payload.put("platform", platform);
                    payload.put("platformLabel", switch (platform) {
                        case "vk" -> "VK";
                        case "max" -> "MAX";
                        default -> "Telegram";
                    });
                    payload.put("channelName", channel.getChannelName());
                    payload.put("botName", Optional.ofNullable(channel.getBotName()).orElse(""));
                    payload.put("botUsername", Optional.ofNullable(channel.getBotUsername()).orElse(""));
                    payload.put("command", command);
                    payload.put("token", Optional.ofNullable(sessionToken).orElse(""));
                    payload.put("openUrl", buildContinuationOpenUrl(channel, platform, sessionToken));
                    payload.put("hint", switch (platform) {
                        case "vk", "max" -> "Откройте бота и отправьте команду продолжения, чтобы привязать внешний диалог.";
                        default -> "Откройте бота по ссылке или отправьте команду продолжения.";
                    });
                    return payload;
                })
                .orElseGet(() -> Map.of(
                        "enabled", false,
                        "platform", "telegram",
                        "platformLabel", "Telegram",
                        "command", "/continue <token>",
                        "token", "",
                        "openUrl", "",
                        "hint", ""
                ));
    }

    public String resolveUiLocale() {
        return publicFormRuntimeConfigService.resolveUiLocale();
    }

    private PublicFormQuestion normalizeQuestion(Map<String, Object> raw, int index) {
        String id = value(raw.getOrDefault("id", "q" + index));
        String text = value(raw.get("text"));
        String type = value(raw.getOrDefault("type", "text"));
        Integer order = raw.get("order") instanceof Number number ? number.intValue() : index;
        Map<String, Object> metadata = raw.entrySet().stream()
                .filter(entry -> !List.of("id", "text", "type", "order").contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b, LinkedHashMap::new));
        return new PublicFormQuestion(id, text, type, order, metadata);
    }

    private List<String> resolveSelectOptions(PublicFormQuestion question, Map<String, String> answers) {
        List<String> flatOptions = metadataStringList(question, "options");
        if (question == null || !LOCATION_FIELD_IDS.contains(question.id())) {
            return flatOptions;
        }
        Map<String, Object> tree = metadataMap(question, "tree");
        if (tree.isEmpty()) {
            return flatOptions;
        }
        return switch (question.id()) {
            case "business" -> flatOptions;
            case "location_type" -> {
                String business = answers.get("business");
                yield toStringList(findNestedValue(tree, business));
            }
            case "city" -> {
                String business = answers.get("business");
                String locationType = answers.get("location_type");
                Map<String, Object> businessNode = toStringObjectMap(findNestedValue(tree, business));
                yield toStringList(findNestedValue(businessNode, locationType));
            }
            case "location_name" -> {
                String business = answers.get("business");
                String locationType = answers.get("location_type");
                String city = answers.get("city");
                Map<String, Object> businessNode = toStringObjectMap(findNestedValue(tree, business));
                Map<String, Object> typeNode = toStringObjectMap(findNestedValue(businessNode, locationType));
                yield toStringList(findNestedValue(typeNode, city));
            }
            default -> flatOptions;
        };
    }

    private Object findNestedValue(Map<String, Object> source, String key) {
        if (source == null || source.isEmpty() || !StringUtils.hasText(key)) {
            return null;
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key.trim())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Map<String, Object> metadataMap(PublicFormQuestion question, String key) {
        if (question == null || question.metadata() == null) {
            return Map.of();
        }
        return toStringObjectMap(question.metadata().get(key));
    }

    private Map<String, Object> toStringObjectMap(Object rawValue) {
        if (rawValue instanceof Map<?, ?> rawMap) {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            rawMap.forEach((key, value) -> {
                if (key != null) {
                    result.put(String.valueOf(key), value);
                }
            });
            return result;
        }
        return Map.of();
    }

    private List<String> toStringList(Object rawValue) {
        if (rawValue instanceof List<?> list) {
            return list.stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .toList();
        }
        if (rawValue instanceof Iterable<?> iterable) {
            List<String> result = new java.util.ArrayList<>();
            for (Object item : iterable) {
                String value = item != null ? item.toString().trim() : "";
                if (StringUtils.hasText(value)) {
                    result.add(value);
                }
            }
            return result;
        }
        return List.of();
    }

    private Map<String, String> sanitizeAnswers(Map<String, String> source, boolean stripHtmlTags) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
                String normalizedValue = normalizeFreeText(value, stripHtmlTags);
                if (StringUtils.hasText(normalizedValue)) {
                    result.put(key.trim(), normalizedValue);
                }
            }
        });
        return result;
    }

    private PublicFormSubmission normalizeSubmission(PublicFormSubmission submission, boolean stripHtmlTags) {
        return new PublicFormSubmission(
                normalizeFreeText(submission.message(), stripHtmlTags),
                normalizeFreeText(submission.clientName(), stripHtmlTags),
                normalizeFreeText(submission.clientContact(), stripHtmlTags),
                normalizeFreeText(submission.username(), stripHtmlTags),
                submission.captchaToken(),
                submission.answers(),
                submission.requestId()
        );
    }

    private String normalizeFreeText(String value, boolean stripHtmlTags) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (stripHtmlTags) {
            normalized = HTML_TAG_PATTERN.matcher(normalized).replaceAll("");
        }
        normalized = CONTROL_CHARS_PATTERN.matcher(normalized).replaceAll("");
        normalized = normalized.trim();
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private String buildCombinedMessage(PublicFormConfig config, Map<String, String> answers, String message) {
        List<String> summary = config.questions().stream()
                .filter(q -> answers.containsKey(q.id()))
                .map(q -> formatSummaryLine(q, answers.get(q.id())))
                .filter(StringUtils::hasText)
                .toList();
        StringBuilder builder = new StringBuilder();
        if (!summary.isEmpty()) {
            builder.append("Ответы формы:\n");
            summary.forEach(line -> builder.append(line).append('\n'));
            if (StringUtils.hasText(message)) {
                builder.append('\n');
            }
        }
        if (StringUtils.hasText(message)) {
            builder.append(message.trim());
        }
        return builder.length() > 0 ? builder.toString() : message;
    }

    private String formatSummaryLine(PublicFormQuestion question, String answer) {
        if (!StringUtils.hasText(answer)) {
            return null;
        }
        return questionLabel(question) + ": " + answer.trim();
    }

    private String writeJson(Map<String, String> answers) {
        try {
            return objectMapper.writeValueAsString(answers);
        } catch (Exception ex) {
            log.warn("Failed to serialize answers: {}", ex.getMessage());
            return "{}";
        }
    }

    private String resolveClientName(PublicFormSubmission submission, Map<String, String> answers) {
        if (StringUtils.hasText(submission.clientName())) {
            return submission.clientName().trim();
        }
        for (String key : List.of("client_name", "name", "full_name")) {
            if (answers.containsKey(key) && StringUtils.hasText(answers.get(key))) {
                return answers.get(key);
            }
        }
        return "Клиент веб-формы";
    }

    private boolean isRequired(PublicFormQuestion question) {
        return metadataBoolean(question, "required", false);
    }

    private String questionLabel(PublicFormQuestion question) {
        return StringUtils.hasText(question.text()) ? question.text().trim() : "Вопрос";
    }

    @SuppressWarnings("unchecked")
    private boolean metadataBoolean(PublicFormQuestion question, String key, boolean defaultValue) {
        if (question.metadata() == null) {
            return defaultValue;
        }
        Object raw = question.metadata().get(key);
        if (raw instanceof Boolean bool) {
            return bool;
        }
        if (raw instanceof Number number) {
            return number.intValue() != 0;
        }
        if (raw instanceof String text) {
            return "true".equalsIgnoreCase(text.trim()) || "1".equals(text.trim());
        }
        return defaultValue;
    }

    private int metadataInt(PublicFormQuestion question, String key, int defaultValue) {
        if (question.metadata() == null) {
            return defaultValue;
        }
        Object raw = question.metadata().get(key);
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private List<String> metadataStringList(PublicFormQuestion question, String key) {
        if (question.metadata() == null) {
            return List.of();
        }
        Object raw = question.metadata().get(key);
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).map(String::trim).filter(StringUtils::hasText).toList();
        }
        if (raw instanceof String text) {
            return Arrays.stream(text.split(",")).map(String::trim).filter(StringUtils::hasText).toList();
        }
        return List.of();
    }

    private boolean readDialogConfigBoolean(String key, boolean defaultValue) {
        return publicFormRuntimeConfigService.readDialogConfigBoolean(key, defaultValue);
    }

    private int readDialogConfigInt(String key, int defaultValue, int minValue, int maxValue) {
        return publicFormRuntimeConfigService.readDialogConfigInt(key, defaultValue, minValue, maxValue);
    }

    private double readDialogConfigDouble(String key, double defaultValue, double minValue, double maxValue) {
        return publicFormRuntimeConfigService.readDialogConfigDouble(key, defaultValue, minValue, maxValue);
    }

    private String readDialogConfigString(String key, String defaultValue) {
        return publicFormRuntimeConfigService.readDialogConfigString(key, defaultValue);
    }

    private String generateToken() {
        return UUID.randomUUID().toString();
    }

    private String buildContinuationOpenUrl(Channel channel, String platform, String sessionToken) {
        if (!StringUtils.hasText(sessionToken)) {
            return "";
        }
        if ("telegram".equals(platform)) {
            String botUsername = Optional.ofNullable(channel.getBotUsername()).map(String::trim).orElse("");
            if (StringUtils.hasText(botUsername)) {
                String normalized = botUsername.startsWith("@") ? botUsername.substring(1) : botUsername;
                return "https://t.me/" + normalized + "?start=web_" + urlEncode(sessionToken.trim());
            }
            return "";
        }
        return "";
    }

    private String generateTicketId() {
        return "web-" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8).toLowerCase(Locale.ROOT);
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String value(Object value) {
        return value != null ? value.toString() : null;
    }

    private int normalizeDisabledStatus(int value) {
        return publicFormRuntimeConfigService.normalizeDisabledStatus(value);
    }

    private int normalizeRange(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record ParsedPublicFormSettings(int schemaVersion,
                                            boolean enabled,
                                            boolean captchaEnabled,
                                            int disabledStatus,
                                            Boolean rateLimitEnabled,
                                            Integer rateLimitWindowSeconds,
                                            Integer rateLimitMaxRequests,
                                            List<Map<String, Object>> fields,
                                            String successInstruction,
                                            Integer responseEtaMinutes) {
        private static ParsedPublicFormSettings defaults() {
            return new ParsedPublicFormSettings(1, true, false, 404,
                    null, null, null, List.of(), null, null);
        }
    }

}
