package com.example.supportbot.service;

import com.example.supportbot.config.BotIntegrationTransportMode;
import com.example.supportbot.entity.Channel;
import com.example.supportbot.entity.Notification;
import com.example.supportbot.entity.PendingFeedbackRequest;
import com.example.supportbot.repository.ChannelRepository;
import com.example.supportbot.repository.NotificationRepository;
import com.example.supportbot.repository.PendingFeedbackRequestRepository;
import com.example.supportbot.settings.BotSettingsService;
import com.example.supportbot.settings.dto.BotSettingsDto;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EngagementTasks {

    private static final Logger log = LoggerFactory.getLogger(EngagementTasks.class);

    private final PendingFeedbackRequestRepository pendingFeedbackRequestRepository;
    private final NotificationRepository notificationRepository;
    private final ChannelRepository channelRepository;
    private final BotSettingsService botSettingsService;
    private final MessagingService messagingService;
    private final TicketService ticketService;
    private final BotIntegrationTransportMode integrationTransportMode;

    public EngagementTasks(PendingFeedbackRequestRepository pendingFeedbackRequestRepository,
                           NotificationRepository notificationRepository,
                           ChannelRepository channelRepository,
                           BotSettingsService botSettingsService,
                           MessagingService messagingService,
                           TicketService ticketService,
                           BotIntegrationTransportMode integrationTransportMode) {
        this.pendingFeedbackRequestRepository = pendingFeedbackRequestRepository;
        this.notificationRepository = notificationRepository;
        this.channelRepository = channelRepository;
        this.botSettingsService = botSettingsService;
        this.messagingService = messagingService;
        this.ticketService = ticketService;
        this.integrationTransportMode = integrationTransportMode;
    }

    @Scheduled(cron = "0 */2 * * * *")
    public void dispatchPendingFeedbackRequests() {
        if (integrationTransportMode.isRabbitMqMode()) {
            log.debug("Skipping bot-side feedback prompt scheduler because rabbitmq transport delegates ownership to spring-panel");
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
            if (channel == null || userId == null) {
                continue;
            }
            String prompt = buildRatingPrompt(channel, request);
            if (messagingService.sendToUser(channel, userId, prompt)) {
                markFeedbackRequestSent(request, OffsetDateTime.now());
            }
        }
    }

    private String buildRatingPrompt(Channel channel, PendingFeedbackRequest request) {
        BotSettingsDto settings = botSettingsService.loadFromChannel(channel);
        int scale = botSettingsService.ratingScale(settings, 5);
        String template = botSettingsService.ratingPrompt(settings, "Оцените заявку {ticket_id} по шкале 1-{scale}");
        String requestNumber = Optional.ofNullable(ticketService.resolveClientTicketNumber(request.getTicketId()))
                .orElse(Optional.ofNullable(request.getTicketId()).orElse("заявку"));
        return template
                .replace("{ticket_id}", requestNumber)
                .replace("{scale}", Integer.toString(scale));
    }

    @Scheduled(cron = "30 */2 * * * *")
    public void dispatchOperatorNotifications() {
        if (integrationTransportMode.isRabbitMqMode()) {
            log.debug("Skipping legacy bot-side operator notification probe because rabbitmq transport delegates business storage to spring-panel");
            return;
        }
        if (notificationRepository.count() > 0) {
            log.debug("Legacy operator-notification bridge to support chats is disabled");
        }
    }

    private void markFeedbackRequestSent(PendingFeedbackRequest request, OffsetDateTime sentAt) {
        if (request == null) {
            return;
        }
        request.setSentAt(sentAt);
        try {
            pendingFeedbackRequestRepository.save(request);
        } catch (RuntimeException ex) {
            log.warn("Feedback prompt was sent for ticket {}, but sent_at was not persisted for request {}: {}",
                    request.getTicketId(),
                    request.getId(),
                    ex.getMessage());
        }
    }

    private String buildNotificationText(Notification notification) {
        StringBuilder builder = new StringBuilder();
        builder.append(Optional.ofNullable(notification.getText()).orElse(""));
        if (notification.getUrl() != null && !notification.getUrl().isBlank()) {
            builder.append("\n").append(notification.getUrl());
        }
        return builder.toString().trim();
    }
}
