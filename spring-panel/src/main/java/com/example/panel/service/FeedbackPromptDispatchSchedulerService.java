package com.example.panel.service;

import com.example.panel.runtime.RuntimeWorkload;
import com.example.panel.runtime.RuntimeRole;
import com.example.panel.runtime.RuntimeReplicaPolicy;
import com.example.panel.config.PanelIntegrationTransportMode;
import com.example.panel.entity.Channel;
import com.example.panel.entity.PendingFeedbackRequest;
import com.example.panel.repository.PendingFeedbackRequestRepository;
import com.example.panel.service.integration.OutboundFeedbackPromptPublisher;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@RuntimeWorkload(
    id = "feedback-prompt-dispatch-scheduler-service",
    roles = {RuntimeRole.WORKER},
    replicaPolicy = RuntimeReplicaPolicy.LEASED
)@Component
public class FeedbackPromptDispatchSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackPromptDispatchSchedulerService.class);

    private final PendingFeedbackRequestRepository pendingFeedbackRequestRepository;
    private final BotRuntimeTicketReadService ticketReadService;
    private final PanelBotSettingsService panelBotSettingsService;
    private final OutboundFeedbackPromptPublisher outboundFeedbackPromptPublisher;
    private final PanelIntegrationTransportMode integrationTransportMode;
    private final RuntimeCoordinationService runtimeCoordinationService;

    public FeedbackPromptDispatchSchedulerService(PendingFeedbackRequestRepository pendingFeedbackRequestRepository,
                                                  BotRuntimeTicketReadService ticketReadService,
                                                  PanelBotSettingsService panelBotSettingsService,
                                                  OutboundFeedbackPromptPublisher outboundFeedbackPromptPublisher,
                                                  PanelIntegrationTransportMode integrationTransportMode,
                                                  RuntimeCoordinationService runtimeCoordinationService) {
        this.pendingFeedbackRequestRepository = pendingFeedbackRequestRepository;
        this.ticketReadService = ticketReadService;
        this.panelBotSettingsService = panelBotSettingsService;
        this.outboundFeedbackPromptPublisher = outboundFeedbackPromptPublisher;
        this.integrationTransportMode = integrationTransportMode;
        this.runtimeCoordinationService = runtimeCoordinationService;
    }

    @Scheduled(cron = "0 */2 * * * *")
    @Transactional
    public void dispatchPendingFeedbackRequests() {
        runtimeCoordinationService.runWithLease("feedback-prompt-dispatch", java.time.Duration.ofMinutes(3), this::dispatchPendingFeedbackRequestsInternal);
    }

    void dispatchPendingFeedbackRequestsInternal() {
        if (!integrationTransportMode.isRabbitMqMode()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        List<PendingFeedbackRequest> pending = pendingFeedbackRequestRepository
            .findTop50BySentAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(now);
        if (pending.isEmpty()) {
            return;
        }
        for (PendingFeedbackRequest request : pending) {
            Channel channel = request.getChannel();
            Long userId = request.getUserId();
            if (channel == null || channel.getId() == null || userId == null || !StringUtils.hasText(request.getTicketId())) {
                continue;
            }
            String prompt = buildRatingPrompt(channel, request.getTicketId());
            try {
                outboundFeedbackPromptPublisher.publish(request.getId(), channel, userId, request.getTicketId(), prompt);
                request.setSentAt(now);
                pendingFeedbackRequestRepository.save(request);
            } catch (RuntimeException ex) {
                log.warn("Failed to dispatch feedback prompt for request {}: {}", request.getId(), ex.getMessage());
            }
        }
    }

    String buildRatingPrompt(Channel channel, String ticketId) {
        PanelBotSettingsService.RatingPromptTemplate template = panelBotSettingsService.resolveRatingPromptTemplate(channel);
        String requestNumber = Optional.ofNullable(ticketId)
            .flatMap(ticketReadService::resolveRequestNumber)
            .map(BotRuntimeTicketReadService.RequestNumberLookup::requestNumber)
            .filter(StringUtils::hasText)
            .orElse(ticketId);
        String fallbackTicketLabel = StringUtils.hasText(requestNumber) ? requestNumber : "заявку";
        return template.prompt()
            .replace("{ticket_id}", fallbackTicketLabel)
            .replace("{scale}", Integer.toString(template.scale()));
    }
}
