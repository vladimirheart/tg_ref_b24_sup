package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class DialogRealtimeEventService {

    private final NotificationService notificationService;
    private final DialogAiAssistantService dialogAiAssistantService;
    private final AlertQueueService alertQueueService;
    private final ChannelAssignmentRoutingService channelAssignmentRoutingService;
    private final ChannelRepository channelRepository;
    private final DialogResponsibilityService dialogResponsibilityService;
    private final DialogNotificationService dialogNotificationService;
    private final UiEventStreamService uiEventStreamService;
    private final LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard;
    private final UiEventTicketAccessService ticketAccess;

    public DialogRealtimeEventService(NotificationService notificationService,
                                      DialogAiAssistantService dialogAiAssistantService,
                                      AlertQueueService alertQueueService,
                                      ChannelAssignmentRoutingService channelAssignmentRoutingService,
                                      ChannelRepository channelRepository,
                                      DialogResponsibilityService dialogResponsibilityService,
                                      DialogNotificationService dialogNotificationService,
                                      UiEventStreamService uiEventStreamService,
                                      LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard,
                                      UiEventTicketAccessService ticketAccess) {
        this.notificationService = notificationService;
        this.dialogAiAssistantService = dialogAiAssistantService;
        this.alertQueueService = alertQueueService;
        this.channelAssignmentRoutingService = channelAssignmentRoutingService;
        this.channelRepository = channelRepository;
        this.dialogResponsibilityService = dialogResponsibilityService;
        this.dialogNotificationService = dialogNotificationService;
        this.uiEventStreamService = uiEventStreamService;
        this.legacyTicketIdJdbcGuard = legacyTicketIdJdbcGuard;
        this.ticketAccess = ticketAccess;
    }

    public void handleTicketCreated(String ticketId, Long channelId, String previewText) {
        handleTicketCreated(ticketId, channelId, previewText, null);
    }

    private void handleTicketCreated(String ticketId, Long channelId, String previewText, UiEventTicketScope scope) {
        String normalizedTicketId = resolveUniqueTicketId(ticketId, scope);
        if (normalizedTicketId == null) {
            return;
        }
        Channel channel = resolveChannel(channelId);
        if (!isCurrent(scope)) {
            return;
        }
        if (channel != null) {
            ChannelAssignmentRoutingService.ResolvedAssignmentRouting routing =
                    channelAssignmentRoutingService.resolve(
                            channel,
                            ChannelAssignmentRoutingService.RoutingEvent.NEW_PUBLIC_APPEAL,
                            normalizedTicketId
                    );
            if (!isCurrent(scope)) {
                return;
            }
            if (StringUtils.hasText(routing.assignee())) {
                if (scope == null) {
                    dialogResponsibilityService.assignResponsibleIfMissing(
                            normalizedTicketId, routing.assignee(), "channel_assignment_routing");
                } else {
                    dialogResponsibilityService.assignScopedResponsibleIfMissing(
                            scope, routing.assignee(), "channel_assignment_routing");
                }
            }
        }
        String text = notificationService.formatNewAppealText(normalizedTicketId);
        String url = notificationService.buildDialogUrl(normalizedTicketId);
        Set<String> routedRecipients = Set.of();
        if (channel != null) {
            routedRecipients = scope == null
                ? alertQueueService.notifyQueueForNewPublicAppealRecipients(channel, normalizedTicketId, previewText)
                : alertQueueService.notifyScopedQueueForNewPublicAppealRecipients(channel, scope, previewText);
        }
        Set<String> remainingOperators = new LinkedHashSet<>(notificationService.findAllOperatorRecipients());
        remainingOperators.removeAll(routedRecipients);
        if (!remainingOperators.isEmpty()) {
            notifyUsers(normalizedTicketId, scope, remainingOperators, text, url);
        }
        publishDialogsChanged("ticket_created", normalizedTicketId, scope);
    }

    public void handleIncomingClientMessage(String ticketId,
                                            Long channelId,
                                            String message,
                                            String messageType,
                                            String attachment) {
        handleIncomingClientMessage(ticketId, channelId, message, messageType, attachment, true);
    }

    public void handleIncomingClientMessage(String ticketId,
                                            Long channelId,
                                            String message,
                                            String messageType,
                                            String attachment,
                                            boolean notifyBell) {
        handleIncomingClientMessage(ticketId, channelId, message, messageType, attachment, notifyBell, null);
    }

    private void handleIncomingClientMessage(String ticketId, Long channelId, String message, String messageType,
                                             String attachment, boolean notifyBell, UiEventTicketScope scope) {
        String normalizedTicketId = resolveUniqueTicketId(ticketId, scope);
        if (normalizedTicketId == null) {
            return;
        }
        Channel channel = resolveChannel(channelId);
        if (!isCurrent(scope)) {
            return;
        }
        if (notifyBell) {
            boolean handledByQueue = channel != null
                    && (scope == null ? alertQueueService.notifyIncomingClientMessage(channel, normalizedTicketId, message)
                        : alertQueueService.notifyScopedIncomingClientMessage(channel, scope, message));
            if (!handledByQueue) {
                notifyUsers(
                        normalizedTicketId,
                        scope,
                        notificationService.findAllOperatorRecipients(),
                        notificationService.formatIncomingClientMessageText(normalizedTicketId),
                        notificationService.buildDialogUrl(normalizedTicketId)
                );
            }
        }
        publishDialogsChanged("incoming_client_message", normalizedTicketId, scope);
        publishHistoryChanged(normalizedTicketId, channelId, "incoming_client_message", scope);
        if (isCurrent(scope)) {
            if (scope == null) {
                dialogAiAssistantService.processIncomingClientMessage(normalizedTicketId, message, messageType, attachment);
            } else {
                dialogAiAssistantService.processScopedIncomingClientMessage(scope, message, messageType, attachment);
            }
        }
    }

    public void handleClientMessageEdited(String ticketId, Long channelId) {
        String normalizedTicketId = resolveUniqueTicketId(ticketId);
        if (normalizedTicketId == null) {
            return;
        }
        uiEventStreamService.publishDialogsChanged("client_message_edited", normalizedTicketId);
        uiEventStreamService.publishDialogHistoryChanged(normalizedTicketId, channelId, "client_message_edited");
    }

    public void handleOperatorMessageEdited(String ticketId, Long channelId) {
        String normalizedTicketId = resolveUniqueTicketId(ticketId);
        if (normalizedTicketId == null) {
            return;
        }
        uiEventStreamService.publishDialogsChanged("operator_message_edited", normalizedTicketId);
        uiEventStreamService.publishDialogHistoryChanged(normalizedTicketId, channelId, "operator_message_edited");
    }

    public void handleFeedbackCreated(String ticketId, Integer rating) {
        handleFeedbackCreated(ticketId, rating, null);
    }

    private void handleFeedbackCreated(String ticketId, Integer rating, UiEventTicketScope scope) {
        String normalizedTicketId = resolveUniqueTicketId(ticketId, scope);
        if (normalizedTicketId == null || rating == null) {
            return;
        }
        String text = "Новая оценка по обращению " + normalizedTicketId + ": " + rating + "/5";
        String url = notificationService.buildDialogUrl(normalizedTicketId);
        if (scope == null) {
            notificationService.notifyDialogParticipants(normalizedTicketId, text, url, null);
        } else {
            notificationService.notifyScopedDialogParticipants(scope, text, url, null);
        }
        publishDialogsChanged("dialog_feedback_created", normalizedTicketId, scope);
    }

    public void handleTicketAutoClosed(String ticketId, Long channelId, String text) {
        handleTicketAutoClosed(ticketId, channelId, text, null);
    }

    private void handleTicketAutoClosed(String ticketId, Long channelId, String text, UiEventTicketScope scope) {
        String normalizedTicketId = resolveUniqueTicketId(ticketId, scope);
        if (normalizedTicketId == null) {
            return;
        }
        Channel channel = resolveChannel(channelId);
        String message = notificationService.formatAutoCloseText(normalizedTicketId);
        Set<String> recipients = scope == null ? notificationService.findDialogRecipients(normalizedTicketId)
            : notificationService.findScopedDialogRecipients(scope);
        if (recipients.isEmpty()) {
            recipients = notificationService.findAllOperatorRecipients();
        }
        notifyUsers(normalizedTicketId, scope, recipients, message,
                notificationService.buildDialogUrl(normalizedTicketId));
        if (channel != null && legacyTicketIdJdbcGuard.hasUniqueNonGeneralTicket(normalizedTicketId) && isCurrent(scope)) {
            if (scope == null) {
                dialogNotificationService.notifySupportChat(channel, message);
            } else {
                dialogNotificationService.notifyScopedSupportChat(scope, channelId, message);
            }
        }
        publishDialogsChanged("dialog_auto_closed", normalizedTicketId, scope);
    }

    public void handleTicketClosed(String ticketId, Long channelId) {
        publishTicketLifecycleChange(ticketId, channelId, "dialog_closed", null);
    }

    public void handleTicketReopened(String ticketId, Long channelId) {
        publishTicketLifecycleChange(ticketId, channelId, "dialog_reopened", null);
    }

    private void publishTicketLifecycleChange(String ticketId, Long channelId, String reason, UiEventTicketScope scope) {
        String normalizedTicketId = resolveUniqueTicketId(ticketId, scope);
        if (normalizedTicketId == null) {
            return;
        }
        publishDialogsChanged(reason, normalizedTicketId, scope);
        publishHistoryChanged(normalizedTicketId, channelId, reason, scope);
    }

    public void handleScopedTicketCreated(UiEventTicketScope scope, Long channelId, String previewText) {
        if (scope != null && scope.isValid()) {
            handleTicketCreated(scope.ticketId(), channelId, previewText, scope);
        }
    }

    public void handleScopedIncomingClientMessage(UiEventTicketScope scope, Long channelId, String message,
                                                  String messageType, String attachment, boolean notifyBell) {
        if (scope != null && scope.isValid()) {
            handleIncomingClientMessage(scope.ticketId(), channelId, message, messageType, attachment, notifyBell, scope);
        }
    }

    public void handleScopedClientMessageEdited(UiEventTicketScope scope, Long channelId) {
        if (scope != null && scope.isValid()) {
            publishTicketLifecycleChange(scope.ticketId(), channelId, "client_message_edited", scope);
        }
    }

    public void handleScopedOperatorMessageEdited(UiEventTicketScope scope, Long channelId) {
        if (scope != null && scope.isValid()) {
            publishTicketLifecycleChange(scope.ticketId(), channelId, "operator_message_edited", scope);
        }
    }

    public void handleScopedFeedbackCreated(UiEventTicketScope scope, Integer rating) {
        if (scope != null && scope.isValid()) {
            handleFeedbackCreated(scope.ticketId(), rating, scope);
        }
    }

    public void handleScopedTicketAutoClosed(UiEventTicketScope scope, Long channelId, String text) {
        if (scope != null && scope.isValid()) {
            handleTicketAutoClosed(scope.ticketId(), channelId, text, scope);
        }
    }

    public void handleScopedTicketClosed(UiEventTicketScope scope, Long channelId) {
        if (scope != null && scope.isValid()) {
            publishTicketLifecycleChange(scope.ticketId(), channelId, "dialog_closed", scope);
        }
    }

    public void handleScopedTicketReopened(UiEventTicketScope scope, Long channelId) {
        if (scope != null && scope.isValid()) {
            publishTicketLifecycleChange(scope.ticketId(), channelId, "dialog_reopened", scope);
        }
    }

    private void notifyUsers(String ticketId, UiEventTicketScope scope, Set<String> recipients, String text, String url) {
        if (scope == null) {
            notificationService.notifyDialogUsers(ticketId, recipients, text, url);
        } else {
            notificationService.notifyScopedDialogUsers(scope, recipients, text, url);
        }
    }

    private void publishDialogsChanged(String reason, String ticketId, UiEventTicketScope scope) {
        if (scope == null) {
            uiEventStreamService.publishDialogsChanged(reason, ticketId);
        } else {
            uiEventStreamService.publishScopedDialogsChanged(reason, scope);
        }
    }

    private void publishHistoryChanged(String ticketId, Long channelId, String reason, UiEventTicketScope scope) {
        if (scope == null) {
            uiEventStreamService.publishDialogHistoryChanged(ticketId, channelId, reason);
        } else {
            uiEventStreamService.publishScopedDialogHistoryChanged(scope, channelId, reason);
        }
    }

    private boolean isCurrent(UiEventTicketScope scope) {
        return scope == null || ticketAccess.isCurrentScope(scope);
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

    private String resolveUniqueTicketId(String ticketId) {
        return resolveUniqueTicketId(ticketId, null);
    }

    private String resolveUniqueTicketId(String ticketId, UiEventTicketScope scope) {
        return StringUtils.hasText(ticketId) && legacyTicketIdJdbcGuard.hasUniqueNonGeneralTicket(ticketId) && isCurrent(scope)
                ? ticketId
                : null;
    }
}
