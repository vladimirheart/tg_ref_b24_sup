package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.supportbot.entity.Task;
import com.example.supportbot.entity.TaskLink;
import com.example.supportbot.repository.NotificationRepository;
import com.example.supportbot.repository.TaskCommentRepository;
import com.example.supportbot.repository.TaskHistoryRepository;
import com.example.supportbot.repository.TaskLinkRepository;
import com.example.supportbot.repository.TaskPersonRepository;
import com.example.supportbot.repository.TaskRepository;
import com.example.supportbot.repository.TaskSequenceRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TaskServiceExactTicketLinkTest {

    @Test
    void payloadLinksKeepOpaqueTicketIdsDistinctAndIgnoreBlankValues() {
        TaskRepository taskRepository = mock(TaskRepository.class);
        TaskPersonRepository taskPersonRepository = mock(TaskPersonRepository.class);
        TaskLinkRepository taskLinkRepository = mock(TaskLinkRepository.class);
        Task task = new Task();
        task.setId(77L);
        when(taskRepository.findById(77L)).thenReturn(Optional.of(task));
        when(taskPersonRepository.findByTaskId(77L)).thenReturn(List.of());
        when(taskLinkRepository.findByIdTaskId(77L)).thenReturn(List.of());
        TaskService service = new TaskService(
            taskRepository,
            mock(TaskSequenceRepository.class),
            taskPersonRepository,
            mock(TaskCommentRepository.class),
            mock(TaskHistoryRepository.class),
            taskLinkRepository,
            mock(NotificationRepository.class)
        );

        var payload = new TaskService.TaskPayload(
            "Проверить диалог", "", "owner", "owner", "dialogs", null,
            null, "dialog_auto_close", List.of(), List.of(),
            List.of("T-EXACT", " T-EXACT ", " T-EXACT ", "null", " null ", " \t ")
        );
        assertThat(service.updateTask(77L, payload)).contains(task);

        ArgumentCaptor<TaskLink> links = ArgumentCaptor.forClass(TaskLink.class);
        verify(taskLinkRepository, times(4)).save(links.capture());
        assertThat(links.getAllValues())
            .extracting(link -> link.getId().getTicketId())
            .containsExactlyInAnyOrder("T-EXACT", " T-EXACT ", "null", " null ");
    }
}
