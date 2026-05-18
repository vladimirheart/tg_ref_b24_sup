package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.entity.ChatHistory;
import com.example.panel.entity.Message;
import com.example.panel.entity.Ticket;
import com.example.panel.entity.WebFormSession;
import com.example.panel.model.publicform.PublicFormSessionDto;
import com.example.panel.model.publicform.PublicFormSubmission;
import com.example.panel.repository.ChatHistoryRepository;
import com.example.panel.repository.MessageRepository;
import com.example.panel.repository.TicketRepository;
import com.example.panel.repository.WebFormSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicFormSubmissionPersistenceServiceTest {

    @Test
    void persistSubmissionCreatesSessionTicketMessageHistoryAndAlerts() throws Exception {
        WebFormSessionRepository sessionRepository = mock(WebFormSessionRepository.class);
        ChatHistoryRepository chatHistoryRepository = mock(ChatHistoryRepository.class);
        TicketRepository ticketRepository = mock(TicketRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        DialogAuditService dialogAuditService = mock(DialogAuditService.class);
        AlertQueueService alertQueueService = mock(AlertQueueService.class);

        when(objectMapper.writeValueAsString(Map.of(
                "business", "БлинБери",
                "city", "Пенза",
                "location_name", "Коллаж ФК"
        ))).thenReturn("""
                {"business":"БлинБери","city":"Пенза","location_name":"Коллаж ФК"}
                """.trim());
        when(sessionRepository.save(any(WebFormSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PublicFormSubmissionPersistenceService service = new PublicFormSubmissionPersistenceService(
                sessionRepository,
                chatHistoryRepository,
                ticketRepository,
                messageRepository,
                objectMapper,
                dialogAuditService,
                alertQueueService
        );

        Channel channel = new Channel();
        channel.setId(18L);
        channel.setPublicId("web-submit");
        channel.setChannelName("Support Web");

        PublicFormSubmission submission = new PublicFormSubmission(
                "Нужна помощь",
                "Анна",
                "+79991234567",
                "anna",
                null,
                Map.of(
                        "business", "БлинБери",
                        "city", "Пенза",
                        "location_name", "Коллаж ФК"
                ),
                "req-88"
        );
        PublicFormSubmissionPolicyService.PreparedSubmission preparedSubmission =
                new PublicFormSubmissionPolicyService.PreparedSubmission(
                        submission,
                        submission.answers(),
                        "Ответы формы:\nБизнес: БлинБери\n\nНужна помощь",
                        "Анна"
                );

        PublicFormSessionDto result = service.persistSubmission(channel, preparedSubmission, "203.0.113.10|fp-42");

        ArgumentCaptor<WebFormSession> sessionCaptor = ArgumentCaptor.forClass(WebFormSession.class);
        ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        ArgumentCaptor<ChatHistory> historyCaptor = ArgumentCaptor.forClass(ChatHistory.class);

        verify(sessionRepository).save(sessionCaptor.capture());
        verify(ticketRepository).save(ticketCaptor.capture());
        verify(messageRepository).save(messageCaptor.capture());
        verify(chatHistoryRepository).save(historyCaptor.capture());
        verify(dialogAuditService).logDialogActionAudit(
                eq(result.ticketId()),
                eq("anna"),
                eq("public_form_submit"),
                eq("success"),
                eq("channel=18, source=web_form")
        );
        verify(alertQueueService, times(1)).notifyQueueForNewPublicAppeal(
                eq(channel),
                eq(result.ticketId()),
                eq("Ответы формы:\nБизнес: БлинБери\n\nНужна помощь")
        );

        WebFormSession savedSession = sessionCaptor.getValue();
        assertThat(savedSession.getTicketId()).startsWith("web-");
        assertThat(savedSession.getToken()).isNotBlank();
        assertThat(savedSession.getUserId()).isNotNull();
        assertThat(savedSession.getClientName()).isEqualTo("Анна");
        assertThat(savedSession.getClientContact()).isEqualTo("+79991234567");
        assertThat(savedSession.getUsername()).isEqualTo("anna");
        assertThat(savedSession.getAnswersJson()).contains("location_name");
        assertThat(result.ticketId()).isEqualTo(savedSession.getTicketId());
        assertThat(result.token()).isEqualTo(savedSession.getToken());

        Ticket ticket = ticketCaptor.getValue();
        assertThat(ticket.getTicketId()).isEqualTo(savedSession.getTicketId());
        assertThat(ticket.getUserId()).isEqualTo(savedSession.getUserId());
        assertThat(ticket.getStatus()).isEqualTo("open");
        assertThat(ticket.getChannel()).isEqualTo(channel);

        Message message = messageCaptor.getValue();
        assertThat(message.getTicketId()).isEqualTo(savedSession.getTicketId());
        assertThat(message.getUserId()).isEqualTo(savedSession.getUserId());
        assertThat(message.getBusiness()).isEqualTo("БлинБери");
        assertThat(message.getCity()).isEqualTo("Пенза");
        assertThat(message.getLocationName()).isEqualTo("Коллаж ФК");
        assertThat(message.getProblem()).isEqualTo("Нужна помощь");
        assertThat(message.getClientName()).isEqualTo("Анна");
        assertThat(message.getUpdatedBy()).isEqualTo("public_form");

        ChatHistory history = historyCaptor.getValue();
        assertThat(history.getTicketId()).isEqualTo(savedSession.getTicketId());
        assertThat(history.getSender()).isEqualTo("user");
        assertThat(history.getMessageType()).isEqualTo("text");
        assertThat(history.getMessage()).isEqualTo("Ответы формы:\nБизнес: БлинБери\n\nНужна помощь");
    }
}
