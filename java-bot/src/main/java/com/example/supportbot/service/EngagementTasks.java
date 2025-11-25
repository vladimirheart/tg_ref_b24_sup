package com.example.supportbot.service;

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
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class EngagementTasks {

    private static final Logger log = LoggerFactory.getLogger(EngagementTasks.class);

    private final PendingFeedbackRequestRepository pendingFeedbackRequestRepository;
    private final NotificationRepository notificationRepository;
    private final ChannelRepository channelRepository;
    private final BotSettingsService botSettingsService;
    private final MessagingService messagingService;

    public EngagementTasks(PendingFeedbackRequestRepository pendingFeedbackRequestRepository,
                           NotificationRepository notificationRepository,
                           ChannelRepository channelRepository,
                           BotSettingsService botSettingsService,
                           MessagingService messagingService) {
        this.pendingFeedbackRequestRepository = pendingFeedbackRequestRepository;
        this.notificationRepository = notificationRepository;
        this.channelRepository = channelRepository;
        this.botSettingsService = botSettingsService;
        this.messagingService = messagingService;
    }

    @Scheduled(cron = "0 */2 * * * *")
    @Transactional
    public void dispatchPendingFeedbackRequests() {
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
                request.setSentAt(OffsetDateTime.now());
                pendingFeedbackRequestRepository.save(request);
            }
        }
    }

    private String buildRatingPrompt(Channel channel, PendingFeedbackRequest request) {
        BotSettingsDto settings = botSettingsService.loadFromChannel(channel);
        int scale = botSettingsService.ratingScale(settings, 5);
        String template = botSettingsService.ratingPrompt(settings, "Оцените заявку {ticket_id} по шкале 1-{scale}");
        return template
                .replace("{ticket_id}", Optional.ofNullable(request.getTicketId()).orElse("заявку"))
                .replace("{scale}", Integer.toString(scale));
    }

    @Scheduled(cron = "30 */2 * * * *")
    @Transactional
    public void dispatchOperatorNotifications() {
        List<Notification> pending = notificationRepository.findByReadIsNullOrReadFalseOrderByCreatedAtAsc();
        if (pending.isEmpty()) {
            return;
        }
        List<Channel> supportChannels = channelRepository.findAll().stream()
                .filter(ch -> ch.getSupportChatId() != null && !ch.getSupportChatId().isBlank())
                .collect(Collectors.toList());
        if (supportChannels.isEmpty()) {
            log.debug("No support chats configured; skipping notifications");
            return;
        }
        for (Notification notification : pending) {
            String text = buildNotificationText(notification);
            boolean delivered = false;
            for (Channel channel : supportChannels) {
                if (messagingService.sendToSupportChat(channel, text)) {
                    delivered = true;
                }
            }
            if (delivered) {
                notification.setRead(true);
                notificationRepository.save(notification);
            }
        }
    }

    private String buildNotificationText(Notification notification) {
        StringBuilder builder = new StringBuilder();
        if (notification.getUser() != null && !notification.getUser().isBlank()) {
            builder.append(notification.getUser()).append(": ");
        }
        builder.append(Optional.ofNullable(notification.getText()).orElse(""));
        if (notification.getUrl() != null && !notification.getUrl().isBlank()) {
            builder.append("\n").append(notification.getUrl());
        }
        return builder.toString().trim();
    }
}
