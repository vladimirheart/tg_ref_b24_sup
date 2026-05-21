package com.example.panel.service;

import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DialogAiAssistantEscalationServiceTest {

    @Test
    void notifyOperatorsEscalationIncludesTicketLinkAndPreview() {
        NotificationService notificationService = mock(NotificationService.class);
        when(notificationService.buildDialogUrl("T-77")).thenReturn("/dialogs/T-77");
        DialogAiAssistantEscalationService service = new DialogAiAssistantEscalationService(notificationService);

        service.notifyOperatorsEscalation("T-77", "  Клиент просит срочно проверить недоступность VPN после смены пароля  ", "Нужен оператор");

        verify(notificationService).notifyAllOperators(
                eq("AI-агент эскалировал обращение T-77. Нужен оператор Вопрос клиента: Клиент просит срочно проверить недоступность VPN после смены пароля"),
                eq("/dialogs/T-77"),
                isNull()
        );
    }
}
