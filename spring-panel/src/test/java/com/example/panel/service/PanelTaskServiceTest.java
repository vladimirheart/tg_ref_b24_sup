package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.panel.entity.Task;
import com.example.panel.entity.TaskLink;
import com.example.panel.entity.TaskSequence;
import com.example.panel.entity.Ticket;
import com.example.panel.entity.TicketId;
import com.example.panel.repository.NotificationRepository;
import com.example.panel.repository.TaskHistoryRepository;
import com.example.panel.repository.TaskLinkRepository;
import com.example.panel.repository.TaskPersonRepository;
import com.example.panel.repository.TaskRepository;
import com.example.panel.repository.TaskSequenceRepository;
import com.example.panel.repository.TicketRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PanelTaskServiceTest {

    @Test
    void linksOnlyTicketResolvedUniquelyByLegacyId() {
        TaskRepository taskRepository = mock(TaskRepository.class);
        TaskSequenceRepository taskSequenceRepository = mock(TaskSequenceRepository.class);
        TaskLinkRepository taskLinkRepository = mock(TaskLinkRepository.class);
        TicketRepository ticketRepository = mock(TicketRepository.class);
        TaskSequence sequence = new TaskSequence();
        sequence.setId(1);
        sequence.setVal(41L);
        when(taskSequenceRepository.findById(1)).thenReturn(Optional.of(sequence));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task saved = invocation.getArgument(0);
            saved.setId(77L);
            return saved;
        });
        Ticket uniqueTicket = new Ticket();
        TicketId ticketId = new TicketId();
        ticketId.setUserId(901L);
        ticketId.setTicketId("T-UNIQUE");
        uniqueTicket.setId(ticketId);
        when(ticketRepository.findUniqueByLegacyTicketId("T-UNIQUE")).thenReturn(Optional.of(uniqueTicket));
        when(ticketRepository.findUniqueByLegacyTicketId("T-AMBIGUOUS")).thenReturn(Optional.empty());

        PanelTaskService service = new PanelTaskService(
            taskRepository,
            taskSequenceRepository,
            mock(TaskPersonRepository.class),
            mock(TaskHistoryRepository.class),
            taskLinkRepository,
            mock(NotificationRepository.class),
            ticketRepository,
            mock(TaskDomainFoundationService.class)
        );

        service.createTask(new PanelTaskService.TaskPayload(
            "Follow up", null, "operator", "operator", null, null, null, "panel",
            List.of(), List.of(), List.of("T-UNIQUE", "T-AMBIGUOUS"), List.of()
        ), false);

        ArgumentCaptor<TaskLink> linkCaptor = ArgumentCaptor.forClass(TaskLink.class);
        verify(taskLinkRepository).save(linkCaptor.capture());
        assertThat(linkCaptor.getValue().getId().getTaskId()).isEqualTo(77L);
        assertThat(linkCaptor.getValue().getId().getUserId()).isEqualTo(901L);
        assertThat(linkCaptor.getValue().getId().getTicketId()).isEqualTo("T-UNIQUE");
        verify(ticketRepository).findUniqueByLegacyTicketId("T-UNIQUE");
        verify(ticketRepository).findUniqueByLegacyTicketId("T-AMBIGUOUS");
        verify(taskLinkRepository, times(1)).save(any(TaskLink.class));
    }
}
