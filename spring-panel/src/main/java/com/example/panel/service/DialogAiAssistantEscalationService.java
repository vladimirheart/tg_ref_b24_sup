package com.example.panel.service;

import static com.example.panel.support.LegacyTicketIdSupport.exactTicketIdOrNull;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DialogAiAssistantEscalationService {

    private final NotificationService notificationService;

    public DialogAiAssistantEscalationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public void notifyOperatorsEscalation(String ticketId, String message, String reason) {
        String normalizedTicketId = exactTicketIdOrNull(ticketId);
        if (normalizedTicketId == null) {
            return;
        }
        String text = "AI-агент эскалировал обращение " + normalizedTicketId + ". " + reason;
        if (StringUtils.hasText(message)) {
            text += " Вопрос клиента: " + cut(message, 140);
        }
        notificationService.notifyDialogUsers(normalizedTicketId, notificationService.findAllOperatorRecipients(),
                text, notificationService.buildDialogUrl(normalizedTicketId));
    }

    public void notifyScopedOperatorsEscalation(UiEventTicketScope scope, String message, String reason) {
        if (scope == null || !scope.isValid()) {
            return;
        }
        String text = "AI-агент эскалировал обращение " + scope.ticketId() + ". " + reason;
        if (StringUtils.hasText(message)) {
            text += " Вопрос клиента: " + cut(message, 140);
        }
        notificationService.notifyScopedAiDialogUsers(scope, notificationService.findAllOperatorRecipients(),
            text, notificationService.buildDialogUrl(scope.ticketId()));
    }

    private String cut(String text, int len) {
        String normalized = trim(text);
        if (normalized == null) {
            return "";
        }
        String compact = normalized.replaceAll("\\s+", " ").trim();
        return compact.length() <= len ? compact : compact.substring(0, Math.max(0, len - 3)) + "...";
    }

    private String trim(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
