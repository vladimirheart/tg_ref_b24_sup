package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.supportbot.entity.Task;
import com.example.supportbot.entity.TicketMessage;
import com.example.supportbot.entity.TicketResponsible;
import com.example.supportbot.repository.TicketMessageRepository;
import com.example.supportbot.repository.TicketResponsibleRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

class AutoCloseFollowUpTaskServiceTest {

    private static final TransactionOperations NOOP_NEW_TRANSACTION = new TransactionOperations() {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(new SimpleTransactionStatus());
        }
    };

    @Test
    void createsTaskForAutoClosedDialogWithResponsibleParticipantsAndLink() {
        TaskService taskService = mock(TaskService.class);
        TicketResponsibleRepository responsibleRepository = mock(TicketResponsibleRepository.class);
        TicketMessageRepository messageRepository = mock(TicketMessageRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        TicketResponsible responsible = new TicketResponsible();
        responsible.setTicketId("T-100");
        responsible.setResponsible("owner");
        when(responsibleRepository.findById("T-100")).thenReturn(Optional.of(responsible));

        TicketMessage message = new TicketMessage();
        message.setTicketId("T-100");
        message.setProblem("Клиент ждёт обратную связь по оборудованию");
        message.setClientName("Иван");
        message.setBusiness("Iguana");
        message.setCity("Москва");
        message.setLocationName("Пушкинская");
        when(messageRepository.findByTicketId("T-100")).thenReturn(Optional.of(message));

        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq("T-100")))
                .thenReturn(List.of("owner", "helper", "HELPER", "reviewer"));

        Task savedTask = new Task();
        savedTask.setId(42L);
        when(taskService.createTask(any())).thenReturn(savedTask);

        AutoCloseFollowUpTaskService service = new AutoCloseFollowUpTaskService(
                taskService, responsibleRepository, messageRepository, jdbcTemplate, NOOP_NEW_TRANSACTION);

        service.createTaskForAutoClosedDialog("T-100");

        ArgumentCaptor<TaskService.TaskPayload> payloadCaptor = ArgumentCaptor.forClass(TaskService.TaskPayload.class);
        verify(taskService).createTask(payloadCaptor.capture());
        TaskService.TaskPayload payload = payloadCaptor.getValue();

        assertThat(payload.assignee()).isEqualTo("owner");
        assertThat(payload.creator()).isEqualTo("auto_close");
        assertThat(payload.source()).isEqualTo("dialog_auto_close");
        assertThat(payload.ticketIds()).containsExactly("T-100");
        assertThat(payload.coExecutors()).containsExactly("helper", "reviewer");
        assertThat(payload.bodyHtml()).contains("/dialogs/T-100");
        assertThat(payload.bodyHtml()).contains("Иван");
        assertThat(payload.title()).contains("T-100");
    }

    @Test
    void skipsTaskCreationWhenDialogHasNoResponsible() {
        TaskService taskService = mock(TaskService.class);
        TicketResponsibleRepository responsibleRepository = mock(TicketResponsibleRepository.class);
        TicketMessageRepository messageRepository = mock(TicketMessageRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        when(responsibleRepository.findById("T-101")).thenReturn(Optional.empty());

        AutoCloseFollowUpTaskService service = new AutoCloseFollowUpTaskService(
                taskService, responsibleRepository, messageRepository, jdbcTemplate, NOOP_NEW_TRANSACTION);

        service.createTaskForAutoClosedDialog("T-101");

        verify(taskService, never()).createTask(any());
    }

    @Test
    void swallowsTaskCreationFailureInsideIsolatedTransaction() {
        TaskService taskService = mock(TaskService.class);
        TicketResponsibleRepository responsibleRepository = mock(TicketResponsibleRepository.class);
        TicketMessageRepository messageRepository = mock(TicketMessageRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        TicketResponsible responsible = new TicketResponsible();
        responsible.setTicketId("T-102");
        responsible.setResponsible("owner");
        when(responsibleRepository.findById("T-102")).thenReturn(Optional.of(responsible));
        when(messageRepository.findByTicketId("T-102")).thenReturn(Optional.empty());
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq("T-102"))).thenReturn(List.of());
        when(taskService.createTask(any())).thenThrow(new RuntimeException("task_links.user_id is null"));

        AutoCloseFollowUpTaskService service = new AutoCloseFollowUpTaskService(
                taskService, responsibleRepository, messageRepository, jdbcTemplate, NOOP_NEW_TRANSACTION);

        service.createTaskForAutoClosedDialog("T-102");

        verify(taskService).createTask(any());
    }
}
