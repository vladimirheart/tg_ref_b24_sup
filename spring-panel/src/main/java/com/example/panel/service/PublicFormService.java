package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.model.publicform.PublicFormConfig;
import com.example.panel.model.publicform.PublicFormSessionDto;
import com.example.panel.model.publicform.PublicFormSubmission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class PublicFormService {

    private static final Logger log = LoggerFactory.getLogger(PublicFormService.class);
    private final PublicFormChannelService publicFormChannelService;
    private final PublicFormRuntimeConfigService publicFormRuntimeConfigService;
    private final PublicFormMetricsService publicFormMetricsService;
    private final PublicFormAntiAbuseService publicFormAntiAbuseService;
    private final PublicFormSubmissionPolicyService publicFormSubmissionPolicyService;
    private final PublicFormSubmissionPersistenceService publicFormSubmissionPersistenceService;

    public PublicFormService(PublicFormChannelService publicFormChannelService,
                             PublicFormRuntimeConfigService publicFormRuntimeConfigService,
                             PublicFormMetricsService publicFormMetricsService,
                             PublicFormAntiAbuseService publicFormAntiAbuseService,
                             PublicFormSubmissionPolicyService publicFormSubmissionPolicyService,
                             PublicFormSubmissionPersistenceService publicFormSubmissionPersistenceService) {
        this.publicFormChannelService = publicFormChannelService;
        this.publicFormRuntimeConfigService = publicFormRuntimeConfigService;
        this.publicFormMetricsService = publicFormMetricsService;
        this.publicFormAntiAbuseService = publicFormAntiAbuseService;
        this.publicFormSubmissionPolicyService = publicFormSubmissionPolicyService;
        this.publicFormSubmissionPersistenceService = publicFormSubmissionPersistenceService;
    }

    @Transactional(readOnly = true)
    public Optional<PublicFormConfig> loadConfig(String channelRef) {
        return publicFormChannelService.loadConfig(channelRef);
    }

    @Transactional(readOnly = true)
    public Optional<PublicFormConfig> loadConfigRaw(String channelRef) {
        return publicFormChannelService.loadConfigRaw(channelRef);
    }

    public PublicFormSessionDto createSession(String channelRef, PublicFormSubmission submission, String requesterKey) {
        Channel channel = publicFormChannelService.resolveChannel(channelRef)
                .orElseThrow(() -> new IllegalArgumentException("Канал не найден"));
        if (!Boolean.TRUE.equals(channel.getActive())) {
            throw new IllegalArgumentException("Форма канала временно отключена");
        }

        var config = publicFormChannelService.loadConfigRaw(channelRef)
                .orElseThrow(() -> new IllegalArgumentException("Канал не найден"));
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
        return publicFormChannelService.findSession(channelRef, token);
    }

    @Transactional(readOnly = true)
    public Optional<Long> resolveChannelId(String channelRef) {
        return publicFormChannelService.resolveChannelId(channelRef);
    }

    public String buildRequesterKey(String requesterIp, String fingerprint) {
        return publicFormAntiAbuseService.buildRequesterKey(requesterIp, fingerprint);
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
        return publicFormChannelService.buildContinuationOptions(channelRef, sessionToken);
    }

    public String resolveUiLocale() {
        return publicFormRuntimeConfigService.resolveUiLocale();
    }

}
