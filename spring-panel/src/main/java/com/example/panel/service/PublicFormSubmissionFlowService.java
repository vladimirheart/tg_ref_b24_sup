package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.model.publicform.PublicFormSessionDto;
import com.example.panel.model.publicform.PublicFormSubmission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
public class PublicFormSubmissionFlowService {

    private static final Logger log = LoggerFactory.getLogger(PublicFormSubmissionFlowService.class);

    private final PublicFormChannelService publicFormChannelService;
    private final PublicFormAntiAbuseService publicFormAntiAbuseService;
    private final PublicFormSubmissionPolicyService publicFormSubmissionPolicyService;
    private final PublicFormSubmissionPersistenceService publicFormSubmissionPersistenceService;

    public PublicFormSubmissionFlowService(PublicFormChannelService publicFormChannelService,
                                           PublicFormAntiAbuseService publicFormAntiAbuseService,
                                           PublicFormSubmissionPolicyService publicFormSubmissionPolicyService,
                                           PublicFormSubmissionPersistenceService publicFormSubmissionPersistenceService) {
        this.publicFormChannelService = publicFormChannelService;
        this.publicFormAntiAbuseService = publicFormAntiAbuseService;
        this.publicFormSubmissionPolicyService = publicFormSubmissionPolicyService;
        this.publicFormSubmissionPersistenceService = publicFormSubmissionPersistenceService;
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
}
