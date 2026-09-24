package com.example.panel.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.panel.entity.Project;
import com.example.panel.entity.Tag;
import com.example.panel.entity.Task;
import com.example.panel.entity.TaskEvent;
import com.example.panel.repository.ProjectRepository;
import com.example.panel.repository.TagRepository;
import com.example.panel.repository.TaskEventRepository;
import com.example.panel.repository.TaskProjectMembershipRepository;
import com.example.panel.repository.TaskTagRepository;
import com.example.panel.service.TaskDomainFoundationService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TaskDomainFoundationServiceTest {

    @Test
    void newTaskWritesCreateTagAndProjectEvents() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        TaskProjectMembershipRepository membershipRepository = mock(TaskProjectMembershipRepository.class);
        TagRepository tagRepository = mock(TagRepository.class);
        TaskTagRepository taskTagRepository = mock(TaskTagRepository.class);
        TaskEventRepository taskEventRepository = mock(TaskEventRepository.class);

        when(membershipRepository.findByTask_IdOrderByAddedAtAsc(42L)).thenReturn(List.of());
        when(taskTagRepository.findByTask_IdOrderByAddedAtAsc(42L)).thenReturn(List.of());
        when(tagRepository.findByNormalizedName(any())).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskEventRepository.save(any(TaskEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Project project = new Project();
        project.setId(12L);
        project.setProjectKey("PRJ_12");
        project.setName("iiko");
        when(projectRepository.findAllById(any())).thenReturn(List.of(project));

        TaskDomainFoundationService service = new TaskDomainFoundationService(
            projectRepository,
            membershipRepository,
            tagRepository,
            taskTagRepository,
            taskEventRepository
        );

        Task task = new Task();
        task.setId(42L);
        task.setTitle("Foundation");
        task.setStatus("Новая");
        task.setCreatedAt(OffsetDateTime.now());

        service.recordSaved(task, null, true, "operator", "#Backend, ui", "12");

        verify(taskTagRepository, org.mockito.Mockito.times(2)).save(any());
        verify(membershipRepository).save(any());

        ArgumentCaptor<TaskEvent> eventCaptor = ArgumentCaptor.forClass(TaskEvent.class);
        verify(taskEventRepository, org.mockito.Mockito.atLeast(4)).save(eventCaptor.capture());
        List<String> eventTypes = new ArrayList<>();
        for (TaskEvent event : eventCaptor.getAllValues()) {
            eventTypes.add(event.getEventType());
        }
        assertThat(eventTypes).contains("TASK_CREATED", "TAG_ADDED", "PROJECT_ADDED");
    }
}
