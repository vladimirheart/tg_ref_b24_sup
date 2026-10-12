package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

class AutoCloseFollowUpExactTicketIdTest {

    private static final String PADDED = " T-EXACT ";
    private static final String PLAIN = "T-EXACT";
    private static final TransactionOperations NOOP_NEW_TRANSACTION = new TransactionOperations() {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(new SimpleTransactionStatus());
        }
    };

    private AutoCloseFollowUpTaskService build(TaskService tasks,
                                               TicketResponsibleRepository responsible,
                                               TicketMessageRepository messages,
                                               JdbcTemplate jdbc) {
        return new AutoCloseFollowUpTaskService(tasks, responsible, messages, jdbc, NOOP_NEW_TRANSACTION);
    }

    @Test
    @SuppressWarnings("unchecked")
    void paddedIdReachesExactResponsibleMessageParticipantAndTaskLink() {
        TaskService tasks = mock(TaskService.class);
        TicketResponsibleRepository responsible = mock(TicketResponsibleRepository.class);
        TicketMessageRepository messages = mock(TicketMessageRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TicketResponsible owner = new TicketResponsible();
        owner.setTicketId(PADDED);
        owner.setResponsible("owner");
        when(responsible.findById(PADDED)).thenReturn(Optional.of(owner));
        TicketMessage message = new TicketMessage();
        message.setTicketId(PADDED);
        message.setProblem("Broken printer");
        when(messages.findByTicketId(PADDED)).thenReturn(Optional.of(message));
        when(jdbc.query(any(String.class), any(RowMapper.class), eq(PADDED)))
                .thenReturn(List.of("owner", "helper", "HELPER"));
        Task saved = new Task();
        saved.setId(38L);
        when(tasks.createTask(any())).thenReturn(saved);

        build(tasks, responsible, messages, jdbc).createTaskForAutoClosedDialog(PADDED);

        ArgumentCaptor<TaskService.TaskPayload> captured = ArgumentCaptor.forClass(TaskService.TaskPayload.class);
        verify(tasks).createTask(captured.capture());
        TaskService.TaskPayload payload = captured.getValue();
        assertThat(payload.ticketIds()).containsExactly(PADDED);
        assertThat(payload.coExecutors()).containsExactly("helper");
        assertThat(payload.assignee()).isEqualTo("owner");
        assertThat(payload.title()).contains(PADDED);
        assertThat(payload.bodyHtml()).contains("href=\"/dialogs?ticketId=%20T-EXACT%20\"");
        verify(responsible).findById(PADDED);
        verify(responsible, never()).findById(PLAIN);
        verify(messages).findByTicketId(PADDED);
        verify(messages, never()).findByTicketId(PLAIN);
        verify(jdbc).query(any(String.class), any(RowMapper.class), eq(PADDED));
        verify(jdbc, never()).query(any(String.class), any(RowMapper.class), eq(PLAIN));
    }

    @Test
    @SuppressWarnings("unchecked")
    void literalNullAndPathMetacharactersStayOpaqueInTaskLinkAndEncodedUrl() {
        String exact = " null#%&/ ";
        TaskService tasks = mock(TaskService.class);
        TicketResponsibleRepository responsible = mock(TicketResponsibleRepository.class);
        TicketMessageRepository messages = mock(TicketMessageRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TicketResponsible owner = new TicketResponsible();
        owner.setTicketId(exact);
        owner.setResponsible("owner");
        when(responsible.findById(exact)).thenReturn(Optional.of(owner));
        when(messages.findByTicketId(exact)).thenReturn(Optional.empty());
        when(jdbc.query(any(String.class), any(RowMapper.class), eq(exact)))
                .thenReturn(List.of());
        Task saved = new Task();
        saved.setId(39L);
        when(tasks.createTask(any())).thenReturn(saved);

        build(tasks, responsible, messages, jdbc).createTaskForAutoClosedDialog(exact);

        ArgumentCaptor<TaskService.TaskPayload> captured = ArgumentCaptor.forClass(TaskService.TaskPayload.class);
        verify(tasks).createTask(captured.capture());
        assertThat(captured.getValue().ticketIds()).containsExactly(exact);
        assertThat(captured.getValue().bodyHtml())
                .contains("href=\"/dialogs?ticketId=%20null%23%25%26%2F%20\"");
        verify(responsible).findById(exact);
        verify(messages).findByTicketId(exact);
        verify(jdbc).query(any(String.class), any(RowMapper.class), eq(exact));
    }

    @Test
    void missingPaddedResponsibleNeverBorrowsThePlainSibling() {
        TaskService tasks = mock(TaskService.class);
        TicketResponsibleRepository responsible = mock(TicketResponsibleRepository.class);
        TicketMessageRepository messages = mock(TicketMessageRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TicketResponsible plainOwner = new TicketResponsible();
        plainOwner.setTicketId(PLAIN);
        plainOwner.setResponsible("owner");
        when(responsible.findById(PLAIN)).thenReturn(Optional.of(plainOwner));

        build(tasks, responsible, messages, jdbc).createTaskForAutoClosedDialog(PADDED);

        verify(responsible).findById(PADDED);
        verify(responsible, never()).findById(PLAIN);
        verifyNoInteractions(tasks, messages, jdbc);
    }

    @Test
    void nullEmptyAndWhitespaceOnlyIdsNeverStartFollowUpCreation() {
        TaskService tasks = mock(TaskService.class);
        TicketResponsibleRepository responsible = mock(TicketResponsibleRepository.class);
        TicketMessageRepository messages = mock(TicketMessageRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AutoCloseFollowUpTaskService service = build(tasks, responsible, messages, jdbc);

        service.createTaskForAutoClosedDialog(null);
        service.createTaskForAutoClosedDialog("");
        service.createTaskForAutoClosedDialog(" \t ");

        verifyNoInteractions(responsible, messages, jdbc, tasks);
    }
}
