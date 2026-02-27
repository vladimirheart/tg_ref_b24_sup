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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final int DEFAULT_MESSAGE_MAX_LENGTH = 4000;
    private static final int DEFAULT_ANSWERS_TOTAL_MAX_LENGTH = 6000;

    private final ChannelRepository channelRepository;
    private final WebFormSessionRepository sessionRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private final TicketRepository ticketRepository;
    private final MessageRepository messageRepository;
    private final ObjectMapper objectMapper;
    private final SharedConfigService sharedConfigService;
    private final DialogService dialogService;
    private final Map<String, Deque<Long>> rateLimitBuckets = new ConcurrentHashMap<>();
    private final Map<String, IdempotencyEntry> idempotencyCache = new ConcurrentHashMap<>();
    private final Map<Long, PublicFormMetricsAccumulator> metricsByChannel = new ConcurrentHashMap<>();
    private final AtomicLong syntheticMessageId = new AtomicLong(System.currentTimeMillis());

    public PublicFormService(ChannelRepository channelRepository,
                             WebFormSessionRepository sessionRepository,
                             ChatHistoryRepository chatHistoryRepository,
                             TicketRepository ticketRepository,
                             MessageRepository messageRepository,
                             ObjectMapper objectMapper,
                             SharedConfigService sharedConfigService,
                             DialogService dialogService) {
        this.channelRepository = channelRepository;
        this.sessionRepository = sessionRepository;
        this.chatHistoryRepository = chatHistoryRepository;
        this.ticketRepository = ticketRepository;
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
        this.sharedConfigService = sharedConfigService;
        this.dialogService = dialogService;
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
        Map<String, String> answers = sanitizeAnswers(submission.answers());
        String requestId = normalizeRequestId(submission.requestId());
        String payloadHash = buildPayloadHash(submission, answers);
        Optional<PublicFormSessionDto> duplicate = findIdempotentSession(channel, requesterKey, requestId, payloadHash);
        if (duplicate.isPresent()) {
            log.info("Public form idempotency hit for channel {} requester {} requestId {}", channel.getId(), requesterKey, requestId);
            return duplicate.get();
        }

        enforceRateLimit(channel, requesterKey);
        enforceCaptcha(config, submission);
        validateSubmission(config, submission, answers);
        String combinedMessage = buildCombinedMessage(config, answers, submission.message());

        WebFormSession session = new WebFormSession();
        session.setChannel(channel);
        session.setToken(generateToken());
        session.setTicketId(generateTicketId());
        session.setUserId(generateSyntheticUserId(channel.getId(), requesterKey));
        session.setAnswersJson(writeJson(answers));
        session.setClientName(resolveClientName(submission, answers));
        session.setClientContact(trim(submission.clientContact()));
        session.setUsername(StringUtils.hasText(submission.username()) ? submission.username().trim() : "web_form");
        OffsetDateTime now = OffsetDateTime.now();
        session.setCreatedAt(now);
        session.setLastActiveAt(now);
        createTicketProjection(channel, session, submission, answers, now);
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
        dialogService.logDialogActionAudit(
                saved.getTicketId(),
                saved.getUsername(),
                "public_form_submit",
                "success",
                "channel=" + channel.getId() + ", source=web_form"
        );
        cacheIdempotentSession(channel, requesterKey, requestId, payloadHash, result);
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
        if (!isMetricsEnabled() || channelId == null) {
            return;
        }
        metrics(channelId).views.incrementAndGet();
        metrics(channelId).touch();
    }

    public void recordSubmitSuccess(Long channelId) {
        if (!isMetricsEnabled() || channelId == null) {
            return;
        }
        metrics(channelId).submits.incrementAndGet();
        metrics(channelId).touch();
    }

    public void recordSubmitError(Long channelId, String reason) {
        if (!isMetricsEnabled() || channelId == null) {
            return;
        }
        PublicFormMetricsAccumulator accumulator = metrics(channelId);
        accumulator.submitErrors.incrementAndGet();
        String normalizedReason = Optional.ofNullable(reason).orElse("unknown").trim().toLowerCase(Locale.ROOT);
        if (normalizedReason.contains("captcha")) {
            accumulator.captchaFailures.incrementAndGet();
        }
        if (normalizedReason.contains("слишком много запросов")) {
            accumulator.rateLimitRejections.incrementAndGet();
        }
        accumulator.touch();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> loadMetricsSnapshot(Long channelId) {
        List<Map<String, Object>> channels = metricsByChannel.entrySet().stream()
                .filter(entry -> channelId == null || channelId.equals(entry.getKey()))
                .sorted(Comparator.comparingLong(Map.Entry::getKey))
                .map(entry -> {
                    PublicFormMetricsAccumulator metric = entry.getValue();
                    long views = metric.views.get();
                    long submits = metric.submits.get();
                    long submitErrors = metric.submitErrors.get();
                    long captchaFailures = metric.captchaFailures.get();
                    long rateLimitRejections = metric.rateLimitRejections.get();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("channelId", entry.getKey());
                    row.put("views", views);
                    row.put("submits", submits);
                    row.put("submitErrors", submitErrors);
                    row.put("captchaFailures", captchaFailures);
                    row.put("rateLimitRejections", rateLimitRejections);
                    row.put("conversion", views > 0 ? (double) submits / views : 0.0d);
                    row.put("errorRate", submits > 0 ? (double) submitErrors / submits : 0.0d);
                    row.put("updatedAt", OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(metric.lastUpdatedAtMs.get()), java.time.ZoneOffset.UTC));
                    return row;
                })
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enabled", isMetricsEnabled());
        payload.put("channels", channels);
        return payload;
    }

    @Transactional(readOnly = true)
    public Optional<PublicFormSessionDto> findSession(String channelRef, String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        return resolveChannel(channelRef)
                .flatMap(channel -> sessionRepository.findByToken(token)
                        .filter(session -> session.getChannel() != null && session.getChannel().getId().equals(channel.getId()))
                        .filter(this::isSessionActive)
                        .map(session -> new PublicFormSessionDto(
                                session.getToken(),
                                session.getTicketId(),
                                channel.getId(),
                                channel.getPublicId(),
                                session.getClientName(),
                                session.getClientContact(),
                                session.getUsername(),
                                session.getCreatedAt()
                        )));
    }

    private boolean isSessionActive(WebFormSession session) {
        int ttlHours = readDialogConfigInt("public_form_session_ttl_hours", 72, 1, 24 * 30);
        OffsetDateTime createdAt = session.getCreatedAt();
        if (createdAt == null) {
            return true;
        }
        return createdAt.plusHours(ttlHours).isAfter(OffsetDateTime.now());
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
            validateByType(question, value);
        }
    }

    private void validateByType(PublicFormQuestion question, String value) {
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
            List<String> options = metadataStringList(question, "options");
            boolean allowCustom = metadataBoolean(question, "allowCustom", false);
            if (!allowCustom && !options.isEmpty() && options.stream().noneMatch(value::equalsIgnoreCase)) {
                throw new IllegalArgumentException("Поле «" + questionLabel(question) + "» содержит недопустимое значение");
            }
        }
    }

    private Optional<PublicFormSessionDto> findIdempotentSession(Channel channel,
                                                              String requesterKey,
                                                              String requestId,
                                                              String payloadHash) {
        if (!StringUtils.hasText(requestId)) {
            return Optional.empty();
        }
        purgeExpiredIdempotencyEntries();
        IdempotencyEntry entry = idempotencyCache.get(idempotencyCacheKey(channel, requesterKey, requestId));
        if (entry == null) {
            return Optional.empty();
        }
        if (!entry.payloadHash().equals(payloadHash)) {
            throw new IllegalArgumentException("Запрос с таким requestId уже был отправлен с другим payload");
        }
        return Optional.of(entry.session());
    }

    private void cacheIdempotentSession(Channel channel,
                                        String requesterKey,
                                        String requestId,
                                        String payloadHash,
                                        PublicFormSessionDto session) {
        if (!StringUtils.hasText(requestId)) {
            return;
        }
        purgeExpiredIdempotencyEntries();
        idempotencyCache.put(idempotencyCacheKey(channel, requesterKey, requestId),
                new IdempotencyEntry(payloadHash, session, System.currentTimeMillis()));
    }

    private void purgeExpiredIdempotencyEntries() {
        long now = System.currentTimeMillis();
        int ttlSeconds = readDialogConfigInt("public_form_idempotency_ttl_seconds", 300, 30, 3600);
        long ttlMs = ttlSeconds * 1000L;
        idempotencyCache.entrySet().removeIf(entry -> (now - entry.getValue().createdAtMs()) > ttlMs);
    }

    private String idempotencyCacheKey(Channel channel, String requesterKey, String requestId) {
        String requester = StringUtils.hasText(requesterKey) ? requesterKey.trim() : "unknown";
        return channel.getId() + "|" + requester + "|" + requestId;
    }

    private String normalizeRequestId(String requestId) {
        if (!StringUtils.hasText(requestId)) {
            return null;
        }
        String normalized = requestId.trim();
        if (normalized.length() > 128) {
            throw new IllegalArgumentException("requestId слишком длинный");
        }
        return normalized;
    }

    private String buildPayloadHash(PublicFormSubmission submission, Map<String, String> answers) {
        StringBuilder builder = new StringBuilder()
                .append(Optional.ofNullable(submission.message()).orElse("").trim())
                .append('|')
                .append(Optional.ofNullable(submission.clientName()).orElse("").trim())
                .append('|')
                .append(Optional.ofNullable(submission.clientContact()).orElse("").trim());
        for (Map.Entry<String, String> entry : answers.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            builder.append('|').append(entry.getKey()).append('=')
                    .append(Optional.ofNullable(entry.getValue()).orElse(""));
        }
        return hashValue(builder.toString());
    }

    private String hashValue(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Optional.ofNullable(payload).orElse("").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) {
            throw new IllegalStateException("Не удалось подготовить хеш запроса", ex);
        }
    }

    public String buildRequesterKey(String requesterIp, String fingerprint) {
        String ipPart = StringUtils.hasText(requesterIp) ? requesterIp.trim() : "anon";
        if (!readDialogConfigBoolean("public_form_rate_limit_use_fingerprint", true)) {
            return ipPart;
        }
        if (!StringUtils.hasText(fingerprint)) {
            return ipPart;
        }
        String normalizedFingerprint = fingerprint.trim();
        if (normalizedFingerprint.length() > 256) {
            normalizedFingerprint = normalizedFingerprint.substring(0, 256);
        }
        return ipPart + "|fp:" + hashValue(normalizedFingerprint);
    }

    private void enforceRateLimit(Channel channel, String requesterKey) {
        boolean enabled = readDialogConfigBoolean("public_form_rate_limit_enabled", true);
        if (!enabled) {
            return;
        }
        int windowSeconds = readDialogConfigInt("public_form_rate_limit_window_seconds", 60, 10, 3600);
        int maxRequests = readDialogConfigInt("public_form_rate_limit_max_requests", 5, 1, 500);
        String bucketKey = (channel.getId() == null ? "unknown" : channel.getId()) + ":" + (StringUtils.hasText(requesterKey) ? requesterKey : "anon");
        long now = System.currentTimeMillis();
        long threshold = now - (windowSeconds * 1000L);
        Deque<Long> bucket = rateLimitBuckets.computeIfAbsent(bucketKey, key -> new ArrayDeque<>());
        synchronized (bucket) {
            while (!bucket.isEmpty() && bucket.peekFirst() < threshold) {
                bucket.removeFirst();
            }
            if (bucket.size() >= maxRequests) {
                throw new IllegalArgumentException("Слишком много запросов. Попробуйте чуть позже.");
            }
            bucket.addLast(now);
        }
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
                settings.captchaEnabled(), settings.disabledStatus(), questions);
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

        return new PublicFormConfig(0L, "demo", "Демо-канал", 1, true, false, 404, demoQuestions);
    }

    private List<PublicFormQuestion> parseQuestions(Channel channel) {
        ParsedPublicFormSettings settings = parseSettings(channel);
        if (settings.fields().isEmpty()) {
            return List.of();
        }
        AtomicInteger index = new AtomicInteger(0);
        return settings.fields().stream()
                .map(entry -> normalizeQuestion(entry, index.incrementAndGet()))
                .sorted((a, b) -> Integer.compare(Optional.ofNullable(a.order()).orElse(0), Optional.ofNullable(b.order()).orElse(0)))
                .toList();
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
                return new ParsedPublicFormSettings(1, true, false, 404, fields);
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
                return new ParsedPublicFormSettings(schemaVersion, enabled, captchaEnabled, disabledStatus, fields);
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
        String expected = value(readDialogConfig().get("public_form_captcha_shared_secret"));
        String token = submission.captchaToken();
        if (!StringUtils.hasText(expected)) {
            throw new IllegalArgumentException("CAPTCHA включена, но секрет не настроен");
        }
        if (!StringUtils.hasText(token) || !expected.trim().equals(token.trim())) {
            throw new IllegalArgumentException("Проверка CAPTCHA не пройдена");
        }
    }

    private boolean isMetricsEnabled() {
        return readDialogConfigBoolean("public_form_metrics_enabled", true);
    }

    public int resolveAnswersPayloadMaxLength() {
        return readDialogConfigInt("public_form_answers_total_max_length", DEFAULT_ANSWERS_TOTAL_MAX_LENGTH, 200, 50000);
    }

    public boolean isSessionPollingEnabled() {
        return readDialogConfigBoolean("public_form_session_polling_enabled", true);
    }

    public int resolveSessionPollingIntervalSeconds() {
        return readDialogConfigInt("public_form_session_polling_interval_seconds", 15, 5, 300);
    }

    private PublicFormMetricsAccumulator metrics(Long channelId) {
        return metricsByChannel.computeIfAbsent(channelId, key -> new PublicFormMetricsAccumulator());
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

    private Map<String, String> sanitizeAnswers(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
                result.put(key.trim(), value.trim());
            }
        });
        return result;
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> readDialogConfig() {
        Map<String, Object> settings = sharedConfigService.loadSettings();
        Object dialogConfig = settings.get("dialog_config");
        if (dialogConfig instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private boolean readDialogConfigBoolean(String key, boolean defaultValue) {
        Object raw = readDialogConfig().get(key);
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

    private int readDialogConfigInt(String key, int defaultValue, int minValue, int maxValue) {
        Object raw = readDialogConfig().get(key);
        int value = defaultValue;
        if (raw instanceof Number number) {
            value = number.intValue();
        } else if (raw instanceof String text) {
            try {
                value = Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                value = defaultValue;
            }
        }
        return Math.max(minValue, Math.min(maxValue, value));
    }

    private String generateToken() {
        return UUID.randomUUID().toString();
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
        return value == 410 ? 410 : 404;
    }

    private record IdempotencyEntry(String payloadHash, PublicFormSessionDto session, long createdAtMs) {
    }

    private record ParsedPublicFormSettings(int schemaVersion,
                                            boolean enabled,
                                            boolean captchaEnabled,
                                            int disabledStatus,
                                            List<Map<String, Object>> fields) {
        private static ParsedPublicFormSettings defaults() {
            return new ParsedPublicFormSettings(1, true, false, 404, List.of());
        }
    }

    private static final class PublicFormMetricsAccumulator {
        private final AtomicLong views = new AtomicLong(0);
        private final AtomicLong submits = new AtomicLong(0);
        private final AtomicLong submitErrors = new AtomicLong(0);
        private final AtomicLong captchaFailures = new AtomicLong(0);
        private final AtomicLong rateLimitRejections = new AtomicLong(0);
        private final AtomicLong lastUpdatedAtMs = new AtomicLong(System.currentTimeMillis());

        private void touch() {
            lastUpdatedAtMs.set(System.currentTimeMillis());
        }
    }
}
