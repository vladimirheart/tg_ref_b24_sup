package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.model.publicform.PublicFormConfig;
import com.example.panel.model.publicform.PublicFormSessionDto;
import com.example.panel.model.publicform.PublicFormSubmission;
import com.example.panel.repository.ChannelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class PublicFormService {

    private static final Logger log = LoggerFactory.getLogger(PublicFormService.class);
    private final ChannelRepository channelRepository;
    private final PublicFormDefinitionService publicFormDefinitionService;
    private final PublicFormRuntimeConfigService publicFormRuntimeConfigService;
    private final PublicFormMetricsService publicFormMetricsService;
    private final PublicFormSessionService publicFormSessionService;
    private final PublicFormAntiAbuseService publicFormAntiAbuseService;
    private final PublicFormSubmissionPolicyService publicFormSubmissionPolicyService;
    private final PublicFormSubmissionPersistenceService publicFormSubmissionPersistenceService;

    public PublicFormService(ChannelRepository channelRepository,
                             PublicFormDefinitionService publicFormDefinitionService,
                             PublicFormRuntimeConfigService publicFormRuntimeConfigService,
                             PublicFormMetricsService publicFormMetricsService,
                             PublicFormSessionService publicFormSessionService,
                             PublicFormAntiAbuseService publicFormAntiAbuseService,
                             PublicFormSubmissionPolicyService publicFormSubmissionPolicyService,
                             PublicFormSubmissionPersistenceService publicFormSubmissionPersistenceService) {
        this.channelRepository = channelRepository;
        this.publicFormDefinitionService = publicFormDefinitionService;
        this.publicFormRuntimeConfigService = publicFormRuntimeConfigService;
        this.publicFormMetricsService = publicFormMetricsService;
        this.publicFormSessionService = publicFormSessionService;
        this.publicFormAntiAbuseService = publicFormAntiAbuseService;
        this.publicFormSubmissionPolicyService = publicFormSubmissionPolicyService;
        this.publicFormSubmissionPersistenceService = publicFormSubmissionPersistenceService;
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
        PublicFormSessionDto result =
                publicFormSubmissionPersistenceService.persistSubmission(channel, preparedSubmission, requesterKey);
        publicFormAntiAbuseService.cacheIdempotentSession(channel, requesterKey, fingerprint, result);
        return result;
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

}
