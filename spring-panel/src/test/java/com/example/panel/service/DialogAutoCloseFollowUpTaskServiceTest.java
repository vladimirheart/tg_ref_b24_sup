package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.panel.entity.Message;
import com.example.panel.entity.TicketResponsible;
import com.example.panel.repository.MessageRepository;
import com.example.panel.repository.TicketResponsibleRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class DialogAutoCloseFollowUpTaskServiceTest {

    @Test
    void createsTaskForAutoClosedDialogWithResponsibleParticipantsAndLink() {
        PanelTaskService panelTaskService = mock(PanelTaskService.class);
        TicketResponsibleRepository responsibleRepository = mock(TicketResponsibleRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        TicketResponsible responsible = new TicketResponsible();
        responsible.setTicketId("T-100");
        responsible.setResponsible("owner");
        when(responsibleRepository.findById("T-100")).thenReturn(Optional.of(responsible));

        Message message = new Message();
        message.setTicketId("T-100");
        message.setProblem("Клиент ждёт обратную связь по оборудованию");
        message.setClientName("Иван");
        message.setBusiness("Iguana");
        message.setCity("Москва");
        message.setLocationName("Пушкинская");
        when(messageRepository.findByTicketId("T-100")).thenReturn(Optional.of(message));

        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq("T-100")))
            .thenReturn(List.of("owner", "helper", "HELPER", "reviewer"));

        DialogAutoCloseFollowUpTaskService service = new DialogAutoCloseFollowUpTaskService(
            panelTaskService,
            responsibleRepository,
            messageRepository,
            jdbcTemplate,
            transactionManager()
        );

        service.createTaskForAutoClosedDialog("T-100");

        ArgumentCaptor<PanelTaskService.TaskPayload> payloadCaptor = ArgumentCaptor.forClass(PanelTaskService.TaskPayload.class);
        verify(panelTaskService).createTask(payloadCaptor.capture());
        PanelTaskService.TaskPayload payload = payloadCaptor.getValue();

        assertThat(payload.assignee()).isEqualTo("owner");
        assertThat(payload.creator()).isEqualTo("auto_close");
        assertThat(payload.source()).isEqualTo("dialog_auto_close");
        assertThat(payload.ticketIds()).containsExactly("T-100");
        assertThat(payload.coExecutors()).containsExactly("helper", "reviewer");
        assertThat(payload.bodyHtml()).contains("/dialogs/T-100");
        assertThat(payload.bodyHtml()).contains("Иван");
        assertThat(payload.title()).contains("T-100");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), eq("T-100"));
        assertThat(sqlCaptor.getValue())
            .contains("CASE WHEN added_at IS NULL")
            .doesNotContain("COALESCE(added_at, '')");
    }

    @Test
    void skipsTaskCreationWhenDialogHasNoResponsible() {
        PanelTaskService panelTaskService = mock(PanelTaskService.class);
        TicketResponsibleRepository responsibleRepository = mock(TicketResponsibleRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(responsibleRepository.findById("T-101")).thenReturn(Optional.empty());

        DialogAutoCloseFollowUpTaskService service = new DialogAutoCloseFollowUpTaskService(
            panelTaskService,
            responsibleRepository,
            messageRepository,
            jdbcTemplate,
            transactionManager()
        );

        service.createTaskForAutoClosedDialog("T-101");

        verify(panelTaskService, never()).createTask(any());
    }

    private PlatformTransactionManager transactionManager() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) throws TransactionException {
            }

            @Override
            public void rollback(TransactionStatus status) throws TransactionException {
            }
        };
    }
}
