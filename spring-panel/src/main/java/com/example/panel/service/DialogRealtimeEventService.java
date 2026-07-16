package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Set;

@Service
public class DialogRealtimeEventService {

    private final NotificationService notificationService;
    private final DialogAiAssistantService dialogAiAssistantService;
    private final AlertQueueService alertQueueService;
    private final ChannelRepository channelRepository;
    private final DialogNotificationService dialogNotificationService;
    private final UiEventStreamService uiEventStreamService;

    public DialogRealtimeEventService(NotificationService notificationService,
                                      DialogAiAssistantService dialogAiAssistantService,
                                      AlertQueueService alertQueueService,
                                      ChannelRepository channelRepository,
                                      DialogNotificationService dialogNotificationService,
                                      UiEventStreamService uiEventStreamService) {
        this.notificationService = notificationService;
        this.dialogAiAssistantService = dialogAiAssistantService;
        this.alertQueueService = alertQueueService;
        this.channelRepository = channelRepository;
        this.dialogNotificationService = dialogNotificationService;
        this.uiEventStreamService = uiEventStreamService;
    }

    public void handleTicketCreated(String ticketId, Long channelId, String previewText) {
        String normalizedTicketId = trimToNull(ticketId);
        if (normalizedTicketId == null) {
            return;
        }
        Channel channel = resolveChannel(channelId);
        boolean handledByQueue = channel != null
                && alertQueueService.notifyQueueForNewPublicAppeal(channel, normalizedTicketId, previewText);
        if (!handledByQueue) {
            String channelLabel = channel != null && StringUtils.hasText(channel.getChannelName())
                    ? channel.getChannelName().trim()
                    : "Канал";
            String text = "Новое обращение (" + channelLabel + "): " + trimPreview(previewText);
            notificationService.notifyAllOperators(text, notificationService.buildDialogUrl(normalizedTicketId), null);
        }
        uiEventStreamService.publishDialogsChanged("ticket_created", normalizedTicketId);
    }

    public void handleIncomingClientMessage(String ticketId,
                                            Long channelId,
                                            String message,
                                            String messageType,
                                            String attachment) {
        String normalizedTicketId = trimToNull(ticketId);
        if (normalizedTicketId == null) {
            return;
        }
        Channel channel = resolveChannel(channelId);
        boolean handledByQueue = channel != null
                && alertQueueService.notifyIncomingClientMessage(channel, normalizedTicketId, message);
        if (!handledByQueue) {
            String text = "Новое сообщение в обращении " + normalizedTicketId;
            if (StringUtils.hasText(message)) {
                text += ": " + trimPreview(message);
            }
            notificationService.notifyAllOperators(text, notificationService.buildDialogUrl(normalizedTicketId), null);
        }
        uiEventStreamService.publishDialogsChanged("incoming_client_message", normalizedTicketId);
        uiEventStreamService.publishDialogHistoryChanged(normalizedTicketId, channelId, "incoming_client_message");
        dialogAiAssistantService.processIncomingClientMessage(normalizedTicketId, message, messageType, attachment);
    }

    public void handleClientMessageEdited(String ticketId, Long channelId) {
        String normalizedTicketId = trimToNull(ticketId);
        if (normalizedTicketId == null) {
            return;
        }
        uiEventStreamService.publishDialogsChanged("client_message_edited", normalizedTicketId);
        uiEventStreamService.publishDialogHistoryChanged(normalizedTicketId, channelId, "client_message_edited");
    }

    public void handleFeedbackCreated(String ticketId, Integer rating) {
        String normalizedTicketId = trimToNull(ticketId);
        if (normalizedTicketId == null || rating == null) {
            return;
        }
        notificationService.notifyDialogParticipants(
                normalizedTicketId,
                "Новая оценка по обращению " + normalizedTicketId + ": " + rating + "/5",
                notificationService.buildDialogUrl(normalizedTicketId),
                null
        );
        uiEventStreamService.publishDialogsChanged("dialog_feedback_created", normalizedTicketId);
    }

    public void handleTicketAutoClosed(String ticketId, Long channelId, String text) {
        String normalizedTicketId = trimToNull(ticketId);
        if (normalizedTicketId == null) {
            return;
        }
        Channel channel = resolveChannel(channelId);
        String message = StringUtils.hasText(text)
                ? text.trim()
                : "Диалог " + normalizedTicketId + " автоматически закрыт из-за отсутствия активности.";
        Set<String> recipients = notificationService.findDialogRecipients(normalizedTicketId);
        if (recipients.isEmpty()) {
            notificationService.notifyAllOperators(message, notificationService.buildDialogUrl(normalizedTicketId), null);
        } else {
            notificationService.notifyUsers(recipients, message, notificationService.buildDialogUrl(normalizedTicketId));
        }
        if (channel != null) {
            dialogNotificationService.notifySupportChat(channel, message);
        }
        uiEventStreamService.publishDialogsChanged("dialog_auto_closed", normalizedTicketId);
    }

    public void handleTicketClosed(String ticketId, Long channelId) {
        publishTicketLifecycleChange(ticketId, channelId, "dialog_closed");
    }

    public void handleTicketReopened(String ticketId, Long channelId) {
        publishTicketLifecycleChange(ticketId, channelId, "dialog_reopened");
    }

    private void publishTicketLifecycleChange(String ticketId, Long channelId, String reason) {
        String normalizedTicketId = trimToNull(ticketId);
        if (normalizedTicketId == null) {
            return;
        }
        uiEventStreamService.publishDialogsChanged(reason, normalizedTicketId);
        uiEventStreamService.publishDialogHistoryChanged(normalizedTicketId, channelId, reason);
    }

    private Channel resolveChannel(Long channelId) {
        if (channelId == null) {
            return null;
        }
        return channelRepository.findById(channelId).orElse(null);
    }

    private String trimPreview(String value) {
        String safe = StringUtils.hasText(value) ? value.trim() : "без текста";
        return safe.length() <= 140 ? safe : safe.substring(0, 140) + "...";
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
