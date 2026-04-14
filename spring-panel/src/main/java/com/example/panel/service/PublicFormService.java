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
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("(?i)<\\/?[a-z][^>]*>");
    private static final Pattern CONTROL_CHARS_PATTERN = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]");
    private static final int DEFAULT_MESSAGE_MAX_LENGTH = 4000;
    private static final int DEFAULT_ANSWERS_TOTAL_MAX_LENGTH = 6000;
    private static final int DEFAULT_ALERT_MIN_VIEWS = 20;
    private static final double DEFAULT_ALERT_ERROR_RATE = 0.35d;
    private static final double DEFAULT_ALERT_CAPTCHA_FAILURE_RATE = 0.20d;
    private static final double DEFAULT_ALERT_RATE_LIMIT_REJECTION_RATE = 0.20d;
    private static final double DEFAULT_ALERT_SESSION_LOOKUP_MISS_RATE = 0.30d;

    private final ChannelRepository channelRepository;
    private final WebFormSessionRepository sessionRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private final TicketRepository ticketRepository;
    private final MessageRepository messageRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final SharedConfigService sharedConfigService;
    private final DialogService dialogService;
    private final AlertQueueService alertQueueService;
    private final Map<String, Deque<Long>> rateLimitBuckets = new ConcurrentHashMap<>();
    private final Map<String, IdempotencyEntry> idempotencyCache = new ConcurrentHashMap<>();
    private final Map<Long, PublicFormMetricsAccumulator> metricsByChannel = new ConcurrentHashMap<>();
    private final AtomicLong syntheticMessageId = new AtomicLong(System.currentTimeMillis());
    private final HttpClient httpClient = HttpClient.newBuilder().build();

    public PublicFormService(ChannelRepository channelRepository,
                             WebFormSessionRepository sessionRepository,
                             ChatHistoryRepository chatHistoryRepository,
                             TicketRepository ticketRepository,
                             MessageRepository messageRepository,
                             JdbcTemplate jdbcTemplate,
                             ObjectMapper objectMapper,
                             SharedConfigService sharedConfigService,
                             DialogService dialogService,
                             AlertQueueService alertQueueService) {
        this.channelRepository = channelRepository;
        this.sessionRepository = sessionRepository;
        this.chatHistoryRepository = chatHistoryRepository;
        this.ticketRepository = ticketRepository;
        this.messageRepository = messageRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.sharedConfigService = sharedConfigService;
        this.dialogService = dialogService;
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
        String requestId = normalizeRequestId(submission.requestId());
        String payloadHash = buildPayloadHash(normalizedSubmission, answers);
        Optional<PublicFormSessionDto> duplicate = findIdempotentSession(channel, requesterKey, requestId, payloadHash);
        if (duplicate.isPresent()) {
            log.info("Public form idempotency hit for channel {} requesterHash {} requestId {}",
                    channel.getId(), summarizeRequester(requesterKey), requestId);
            return duplicate.get();
        }

        enforceRateLimit(channel, requesterKey);
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
        dialogService.logDialogActionAudit(
                saved.getTicketId(),
                saved.getUsername(),
                "public_form_submit",
                "success",
                "channel=" + channel.getId() + ", source=web_form"
        );
        alertQueueService.notifyQueueForNewPublicAppeal(channel, saved.getTicketId(), combinedMessage);
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
        if (normalizedReason.contains("слишком много запросов")
                || normalizedReason.contains("too many requests")
                || normalizedReason.contains("rate limit")) {
            accumulator.rateLimitRejections.incrementAndGet();
        }
        accumulator.touch();
    }

    public void recordSessionLookup(Long channelId, boolean found) {
        if (!isMetricsEnabled() || channelId == null) {
            return;
        }
        PublicFormMetricsAccumulator accumulator = metrics(channelId);
        accumulator.sessionLookups.incrementAndGet();
        if (!found) {
            accumulator.sessionLookupMisses.incrementAndGet();
        }
        accumulator.touch();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> loadMetricsSnapshot(Long channelId) {
        boolean alertsEnabled = readDialogConfigBoolean("public_form_alerts_enabled", true);
        int minViews = readDialogConfigInt("public_form_alert_min_views", DEFAULT_ALERT_MIN_VIEWS, 1, 10_000);
        double errorRateThreshold = readDialogConfigDouble("public_form_alert_error_rate_threshold", DEFAULT_ALERT_ERROR_RATE, 0.01d, 1d);
        double captchaFailureRateThreshold = readDialogConfigDouble("public_form_alert_captcha_failure_rate_threshold", DEFAULT_ALERT_CAPTCHA_FAILURE_RATE, 0.01d, 1d);
        double rateLimitRejectionRateThreshold = readDialogConfigDouble("public_form_alert_rate_limit_rejection_rate_threshold", DEFAULT_ALERT_RATE_LIMIT_REJECTION_RATE, 0.01d, 1d);
        double sessionLookupMissRateThreshold = readDialogConfigDouble("public_form_alert_session_lookup_miss_rate_threshold", DEFAULT_ALERT_SESSION_LOOKUP_MISS_RATE, 0.01d, 1d);
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
                    long sessionLookups = metric.sessionLookups.get();
                    long sessionLookupMisses = metric.sessionLookupMisses.get();
                    long submitAttempts = submits + submitErrors;
                    double submitErrorRateByAttempts = submitAttempts > 0 ? (double) submitErrors / submitAttempts : 0.0d;
                    double captchaFailureRateByAttempts = submitAttempts > 0 ? (double) captchaFailures / submitAttempts : 0.0d;
                    double rateLimitRejectionRateByAttempts = submitAttempts > 0 ? (double) rateLimitRejections / submitAttempts : 0.0d;
                    double sessionLookupMissRate = sessionLookups > 0 ? (double) sessionLookupMisses / sessionLookups : 0.0d;
                    List<String> alerts = buildMetricAlerts(alertsEnabled,
                            views,
                            minViews,
                            submitErrorRateByAttempts,
                            errorRateThreshold,
                            captchaFailureRateByAttempts,
                            captchaFailureRateThreshold,
                            rateLimitRejectionRateByAttempts,
                            rateLimitRejectionRateThreshold,
                            sessionLookupMissRate,
                            sessionLookupMissRateThreshold);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("channelId", entry.getKey());
                    row.put("views", views);
                    row.put("submits", submits);
                    row.put("submitErrors", submitErrors);
                    row.put("captchaFailures", captchaFailures);
                    row.put("rateLimitRejections", rateLimitRejections);
                    row.put("sessionLookups", sessionLookups);
                    row.put("sessionLookupMisses", sessionLookupMisses);
                    row.put("sessionLookupMissRate", sessionLookupMissRate);
                    row.put("conversion", views > 0 ? (double) submits / views : 0.0d);
                    row.put("errorRate", submits > 0 ? (double) submitErrors / submits : 0.0d);
                    row.put("submitErrorRateByAttempts", submitErrorRateByAttempts);
                    row.put("captchaFailureRateByAttempts", captchaFailureRateByAttempts);
                    row.put("rateLimitRejectionRateByAttempts", rateLimitRejectionRateByAttempts);
                    row.put("alerts", alerts);
                    row.put("updatedAt", OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(metric.lastUpdatedAtMs.get()), java.time.ZoneOffset.UTC));
                    return row;
                })
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enabled", isMetricsEnabled());
        payload.put("alertsEnabled", alertsEnabled);
        payload.put("alertThresholds", Map.of(
                "minViews", minViews,
                "errorRate", errorRateThreshold,
                "captchaFailureRate", captchaFailureRateThreshold,
                "rateLimitRejectionRate", rateLimitRejectionRateThreshold,
                "sessionLookupMissRate", sessionLookupMissRateThreshold));
        payload.put("channelsWithAlerts", channels.stream()
                .filter(channel -> channel.get("alerts") instanceof List<?> alertList && !alertList.isEmpty())
                .count());
        payload.put("channels", channels);
        return payload;
    }

    private List<String> buildMetricAlerts(boolean alertsEnabled,
                                           long views,
                                           int minViews,
                                           double submitErrorRate,
                                           double submitErrorThreshold,
                                           double captchaFailureRate,
                                           double captchaFailureThreshold,
                                           double rateLimitRejectionRate,
                                           double rateLimitRejectionThreshold,
                                           double sessionLookupMissRate,
                                           double sessionLookupMissRateThreshold) {
        if (!alertsEnabled || views < minViews) {
            return List.of();
        }
        List<String> alerts = new java.util.ArrayList<>();
        if (submitErrorRate >= submitErrorThreshold) {
            alerts.add("high_submit_error_rate");
        }
        if (captchaFailureRate >= captchaFailureThreshold) {
            alerts.add("high_captcha_failure_rate");
        }
        if (rateLimitRejectionRate >= rateLimitRejectionThreshold) {
            alerts.add("high_rate_limit_rejection_rate");
        }
        if (sessionLookupMissRate >= sessionLookupMissRateThreshold) {
            alerts.add("high_session_lookup_miss_rate");
        }
        return alerts;
    }

    public Optional<PublicFormSessionDto> findSession(String channelRef, String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        return resolveChannel(channelRef)
                .flatMap(channel -> loadSessionRow(token, channel.getId())
                        .filter(this::isSessionActive)
                        .map(session -> {
                            PublicSessionRow persistedSession = maybeRotateSessionToken(session);
                            return new PublicFormSessionDto(
                                persistedSession.token(),
                                persistedSession.ticketId(),
                                channel.getId(),
                                channel.getPublicId(),
                                persistedSession.clientName(),
                                persistedSession.clientContact(),
                                persistedSession.username(),
                                persistedSession.createdAt()
                            );
                        }));
    }

    @Transactional(readOnly = true)
    public Optional<Long> resolveChannelId(String channelRef) {
        return resolveChannel(channelRef).map(Channel::getId);
    }

    private PublicSessionRow maybeRotateSessionToken(PublicSessionRow session) {
        if (!readDialogConfigBoolean("public_form_session_token_rotate_on_read", false)) {
            return session;
        }
        String nextToken = generateToken();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update("""
                UPDATE web_form_sessions
                   SET token = ?, last_active_at = ?
                 WHERE id = ?
                """,
                nextToken,
                Timestamp.from(now.toInstant()),
                session.id()
        );
        return new PublicSessionRow(
                session.id(),
                nextToken,
                session.ticketId(),
                session.channelId(),
                session.userId(),
                session.clientName(),
                session.clientContact(),
                session.username(),
                session.createdAt(),
                now
        );
    }

    private boolean isSessionActive(PublicSessionRow session) {
        int ttlHours = readDialogConfigInt("public_form_session_ttl_hours", 72, 1, 24 * 30);
        OffsetDateTime createdAt = session.createdAt();
        if (createdAt == null) {
            return true;
        }
        return createdAt.plusHours(ttlHours).isAfter(OffsetDateTime.now(ZoneOffset.UTC));
    }

    private Optional<PublicSessionRow> loadSessionRow(String token, Long channelId) {
        if (!StringUtils.hasText(token) || channelId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                        SELECT id, token, ticket_id, channel_id, user_id, client_name, client_contact, username, created_at, last_active_at
                          FROM web_form_sessions
                         WHERE token = ? AND channel_id = ?
                         LIMIT 1
                        """,
                (rs, rowNum) -> new PublicSessionRow(
                        rs.getLong("id"),
                        rs.getString("token"),
                        rs.getString("ticket_id"),
                        rs.getLong("channel_id"),
                        rs.getLong("user_id"),
                        rs.getString("client_name"),
                        rs.getString("client_contact"),
                        rs.getString("username"),
                        parseOffsetDateTimeValue(rs.getObject("created_at")),
                        parseOffsetDateTimeValue(rs.getObject("last_active_at"))
                ),
                token,
                channelId
        ).stream().findFirst();
    }

    private OffsetDateTime parseOffsetDateTimeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.withOffsetSameInstant(ZoneOffset.UTC);
        }
        if (value instanceof Timestamp timestamp) {
            return OffsetDateTime.ofInstant(timestamp.toInstant(), ZoneOffset.UTC);
        }
        if (value instanceof java.util.Date date) {
            return OffsetDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC);
        }
        if (value instanceof Number number) {
            return OffsetDateTime.ofInstant(Instant.ofEpochMilli(number.longValue()), ZoneOffset.UTC);
        }
        String raw = value.toString().trim();
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw).withOffsetSameInstant(ZoneOffset.UTC);
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(raw.replace(' ', 'T')).atOffset(ZoneOffset.UTC);
        } catch (Exception ignored) {
        }
        try {
            return OffsetDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(raw)), ZoneOffset.UTC);
        } catch (Exception ignored) {
        }
        return null;
    }

    private record PublicSessionRow(Long id,
                                    String token,
                                    String ticketId,
                                    Long channelId,
                                    Long userId,
                                    String clientName,
                                    String clientContact,
                                    String username,
                                    OffsetDateTime createdAt,
                                    OffsetDateTime lastActiveAt) {
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

    private String summarizeRequester(String requesterKey) {
        if (!StringUtils.hasText(requesterKey)) {
            return "unknown";
        }
        String normalized = requesterKey.trim();
        if (normalized.length() <= 12) {
            return hashValue(normalized).substring(0, 12);
        }
        return normalized.substring(0, 6) + "…" + hashValue(normalized).substring(0, 6);
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
        ParsedPublicFormSettings settings = parseSettings(channel);
        boolean enabled = settings.rateLimitEnabled() != null
                ? settings.rateLimitEnabled()
                : readDialogConfigBoolean("public_form_rate_limit_enabled", true);
        if (!enabled) {
            return;
        }
        int windowSeconds = settings.rateLimitWindowSeconds() != null
                ? settings.rateLimitWindowSeconds()
                : readDialogConfigInt("public_form_rate_limit_window_seconds", 60, 10, 3600);
        int maxRequests = settings.rateLimitMaxRequests() != null
                ? settings.rateLimitMaxRequests()
                : readDialogConfigInt("public_form_rate_limit_max_requests", 5, 1, 500);
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
        String expected = value(readDialogConfig().get("public_form_captcha_shared_secret"));
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
        String configured = readDialogConfigString("public_form_default_locale", "auto");
        String normalized = configured.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "ru", "en", "auto" -> normalized;
            default -> "auto";
        };
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

    private double readDialogConfigDouble(String key, double defaultValue, double minValue, double maxValue) {
        Object value = readDialogConfig().get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            double parsed = value instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(value).trim());
            if (!Double.isFinite(parsed) || parsed < minValue || parsed > maxValue) {
                return defaultValue;
            }
            return parsed;
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private String readDialogConfigString(String key, String defaultValue) {
        Object raw = readDialogConfig().get(key);
        if (raw == null) {
            return defaultValue;
        }
        String value = raw.toString().trim();
        return StringUtils.hasText(value) ? value : defaultValue;
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
        return value == 410 ? 410 : 404;
    }

    private int normalizeRange(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record IdempotencyEntry(String payloadHash, PublicFormSessionDto session, long createdAtMs) {
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

    private static final class PublicFormMetricsAccumulator {
        private final AtomicLong views = new AtomicLong(0);
        private final AtomicLong submits = new AtomicLong(0);
        private final AtomicLong submitErrors = new AtomicLong(0);
        private final AtomicLong captchaFailures = new AtomicLong(0);
        private final AtomicLong rateLimitRejections = new AtomicLong(0);
        private final AtomicLong sessionLookups = new AtomicLong(0);
        private final AtomicLong sessionLookupMisses = new AtomicLong(0);
        private final AtomicLong lastUpdatedAtMs = new AtomicLong(System.currentTimeMillis());

        private void touch() {
            lastUpdatedAtMs.set(System.currentTimeMillis());
        }
    }
}
