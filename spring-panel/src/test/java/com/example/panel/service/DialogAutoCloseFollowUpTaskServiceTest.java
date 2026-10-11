package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.panel.entity.Channel;
import com.example.panel.entity.Message;
import com.example.panel.entity.Project;
import com.example.panel.entity.TicketResponsible;
import com.example.panel.repository.MessageRepository;
import com.example.panel.repository.ProjectRepository;
import com.example.panel.repository.TicketResponsibleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
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
        ProjectRepository projectRepository = mock(ProjectRepository.class);
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
        Project project = new Project();
        project.setId(42L);
        project.setName("Auto-close follow-up");
        when(projectRepository.findById(42L)).thenReturn(Optional.of(project));

        DialogLookupReadService dialogLookupReadService = mock(DialogLookupReadService.class);
        when(dialogLookupReadService.resolveRequestNumber("T-100")).thenReturn("20260928-007");
        DialogTaskDisplayService dialogTaskDisplayService = new DialogTaskDisplayService(dialogLookupReadService);

        DialogAutoCloseFollowUpTaskService service = new DialogAutoCloseFollowUpTaskService(
            panelTaskService,
            responsibleRepository,
            messageRepository,
            projectRepository,
            dialogTaskDisplayService,
            new ObjectMapper(),
            jdbcTemplate,
            transactionManager(),
            uniqueTicketGuard("T-100")
        );

        Channel channel = new Channel();
        channel.setId(7L);
        channel.setDeliverySettings("{\"auto_close_follow_up_project_id\":42}");
        service.createTaskForAutoClosedDialog("T-100", channel);

        ArgumentCaptor<PanelTaskService.TaskPayload> payloadCaptor = ArgumentCaptor.forClass(PanelTaskService.TaskPayload.class);
        verify(panelTaskService).createTask(payloadCaptor.capture(), eq(false));
        PanelTaskService.TaskPayload payload = payloadCaptor.getValue();

        assertThat(payload.assignee()).isEqualTo("owner");
        assertThat(payload.creator()).isEqualTo("auto_close");
        assertThat(payload.source()).isEqualTo("dialog_auto_close");
        assertThat(payload.ticketIds()).containsExactly("T-100");
        assertThat(payload.coExecutors()).containsExactly("helper", "reviewer");
        assertThat(payload.projectIds()).containsExactly(42L);
        assertThat(payload.bodyHtml()).contains("/dialogs/T-100");
        assertThat(payload.bodyHtml()).contains("Открыть обращение №20260928-007");
        assertThat(payload.bodyHtml()).doesNotContain("Открыть диалог #T-100");
        assertThat(payload.bodyHtml()).contains("Иван");
        assertThat(payload.title()).contains("№20260928-007").doesNotContain("T-100");

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
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(responsibleRepository.findById("T-101")).thenReturn(Optional.empty());

        DialogAutoCloseFollowUpTaskService service = new DialogAutoCloseFollowUpTaskService(
            panelTaskService,
            responsibleRepository,
            messageRepository,
            projectRepository,
            new DialogTaskDisplayService(mock(DialogLookupReadService.class)),
            new ObjectMapper(),
            jdbcTemplate,
            transactionManager(),
            uniqueTicketGuard("T-101")
        );

        service.createTaskForAutoClosedDialog("T-101");

        verifyNoInteractions(panelTaskService, messageRepository, projectRepository, jdbcTemplate);
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {" ", "T-MISSING", "T-DUPLICATE", " T-100 "})
    void skipsUnresolvedTicketBeforeReadingContextOrCreatingTask(String ticketId) {
        PanelTaskService panelTaskService = mock(PanelTaskService.class);
        TicketResponsibleRepository responsibleRepository = mock(TicketResponsibleRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        DialogTaskDisplayService displayService = mock(DialogTaskDisplayService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        LegacyTicketIdJdbcGuard guard = mock(LegacyTicketIdJdbcGuard.class);
        DialogAutoCloseFollowUpTaskService service = new DialogAutoCloseFollowUpTaskService(
            panelTaskService, responsibleRepository, messageRepository, projectRepository, displayService,
            new ObjectMapper(), jdbcTemplate, transactionManager(), guard
        );

        service.createTaskForAutoClosedDialog(ticketId);

        verifyNoInteractions(panelTaskService, responsibleRepository, messageRepository, projectRepository,
            displayService, jdbcTemplate);
        if (ticketId != null && !ticketId.isBlank()) {
            verify(guard).hasUniqueTicket(ticketId);
        }
    }

    @Test
    void preservesExactStoredLegacyTicketIdForGuardAndResponsibleLookup() {
        TicketResponsibleRepository responsibleRepository = mock(TicketResponsibleRepository.class);
        LegacyTicketIdJdbcGuard guard = uniqueTicketGuard(" T-100 ");
        DialogAutoCloseFollowUpTaskService service = new DialogAutoCloseFollowUpTaskService(
            mock(PanelTaskService.class), responsibleRepository, mock(MessageRepository.class),
            mock(ProjectRepository.class), mock(DialogTaskDisplayService.class), new ObjectMapper(),
            mock(JdbcTemplate.class), transactionManager(), guard
        );

        service.createTaskForAutoClosedDialog(" T-100 ");

        verify(guard).hasUniqueTicket(" T-100 ");
        verify(responsibleRepository).findById(" T-100 ");
        verify(responsibleRepository, never()).findById("T-100");
    }

    private LegacyTicketIdJdbcGuard uniqueTicketGuard(String ticketId) {
        LegacyTicketIdJdbcGuard guard = mock(LegacyTicketIdJdbcGuard.class);
        when(guard.hasUniqueTicket(ticketId)).thenReturn(true);
        return guard;
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
