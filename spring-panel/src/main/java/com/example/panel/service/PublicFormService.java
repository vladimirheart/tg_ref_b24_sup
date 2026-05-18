package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.entity.ChatHistory;
import com.example.panel.entity.Message;
import com.example.panel.entity.Ticket;
import com.example.panel.entity.TicketId;
import com.example.panel.entity.WebFormSession;
import com.example.panel.model.publicform.PublicFormConfig;
import com.example.panel.model.publicform.PublicFormSessionDto;
import com.example.panel.model.publicform.PublicFormSubmission;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.repository.ChatHistoryRepository;
import com.example.panel.repository.MessageRepository;
import com.example.panel.repository.TicketRepository;
import com.example.panel.repository.WebFormSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Transactional
public class PublicFormService {

    private static final Logger log = LoggerFactory.getLogger(PublicFormService.class);
    private final ChannelRepository channelRepository;
    private final WebFormSessionRepository sessionRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private final TicketRepository ticketRepository;
    private final MessageRepository messageRepository;
    private final ObjectMapper objectMapper;
    private final PublicFormDefinitionService publicFormDefinitionService;
    private final PublicFormRuntimeConfigService publicFormRuntimeConfigService;
    private final PublicFormMetricsService publicFormMetricsService;
    private final PublicFormSessionService publicFormSessionService;
    private final PublicFormAntiAbuseService publicFormAntiAbuseService;
    private final PublicFormSubmissionPolicyService publicFormSubmissionPolicyService;
    private final DialogAuditService dialogAuditService;
    private final AlertQueueService alertQueueService;
    private final AtomicLong syntheticMessageId = new AtomicLong(System.currentTimeMillis());

    public PublicFormService(ChannelRepository channelRepository,
                             WebFormSessionRepository sessionRepository,
                             ChatHistoryRepository chatHistoryRepository,
                             TicketRepository ticketRepository,
                             MessageRepository messageRepository,
                             ObjectMapper objectMapper,
                             PublicFormDefinitionService publicFormDefinitionService,
                             PublicFormRuntimeConfigService publicFormRuntimeConfigService,
                             PublicFormMetricsService publicFormMetricsService,
                             PublicFormSessionService publicFormSessionService,
                             PublicFormAntiAbuseService publicFormAntiAbuseService,
                             PublicFormSubmissionPolicyService publicFormSubmissionPolicyService,
                             DialogAuditService dialogAuditService,
                             AlertQueueService alertQueueService) {
        this.channelRepository = channelRepository;
        this.sessionRepository = sessionRepository;
        this.chatHistoryRepository = chatHistoryRepository;
        this.ticketRepository = ticketRepository;
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
        this.publicFormDefinitionService = publicFormDefinitionService;
        this.publicFormRuntimeConfigService = publicFormRuntimeConfigService;
        this.publicFormMetricsService = publicFormMetricsService;
        this.publicFormSessionService = publicFormSessionService;
        this.publicFormAntiAbuseService = publicFormAntiAbuseService;
        this.publicFormSubmissionPolicyService = publicFormSubmissionPolicyService;
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
            return Optional.of(publicFormDefinitionService.buildDemoConfig());
        }
        return resolveChannel(channelRef).map(publicFormDefinitionService::buildConfig);
    }

    public PublicFormSessionDto createSession(String channelRef, PublicFormSubmission submission, String requesterKey) {
        Channel channel = resolveChannel(channelRef)
                .orElseThrow(() -> new IllegalArgumentException("Канал не найден"));
        if (!Boolean.TRUE.equals(channel.getActive())) {
            throw new IllegalArgumentException("Форма канала временно отключена");
        }

        PublicFormConfig config = publicFormDefinitionService.buildConfig(channel);
        if (!config.enabled()) {
            throw new IllegalArgumentException("Форма канала временно отключена");
        }
        PublicFormSubmissionPolicyService.PreparedSubmission preparedSubmission =
                publicFormSubmissionPolicyService.prepareSubmission(config, submission);
        PublicFormSubmission normalizedSubmission = preparedSubmission.submission();
        Map<String, String> answers = preparedSubmission.answers();
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
        String combinedMessage = preparedSubmission.combinedMessage();

        WebFormSession session = new WebFormSession();
        session.setChannel(channel);
        session.setToken(generateToken());
        session.setTicketId(generateTicketId());
        session.setUserId(generateSyntheticUserId(channel.getId(), requesterKey));
        session.setAnswersJson(writeJson(answers));
        session.setClientName(preparedSubmission.clientName());
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

    private String writeJson(Map<String, String> answers) {
        try {
            return objectMapper.writeValueAsString(answers);
        } catch (Exception ex) {
            log.warn("Failed to serialize answers: {}", ex.getMessage());
            return "{}";
        }
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

    private String urlEncode(String value) {
        return URLEncoder.encode(Optional.ofNullable(value).orElse(""), StandardCharsets.UTF_8);
    }

    private String generateTicketId() {
        return "web-" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8).toLowerCase(Locale.ROOT);
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

}
