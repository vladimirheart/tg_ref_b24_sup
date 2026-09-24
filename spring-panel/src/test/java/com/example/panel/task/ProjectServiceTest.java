package com.example.panel.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.panel.entity.Project;
import com.example.panel.repository.ProjectRepository;
import com.example.panel.service.ProjectService;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProjectServiceTest {

    @Test
    void createGeneratesStableProjectKeyWhenClientDoesNotProvideOne() {
        ProjectRepository repository = mock(ProjectRepository.class);
        when(repository.saveAndFlush(any(Project.class))).thenAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            if (project.getId() == null) {
                project.setId(7L);
            }
            return project;
        });

        ProjectService service = new ProjectService(repository);
        Map<String, Object> created = service.create(Map.of("name", "Operations"), "operator");

        assertThat(created.get("id")).isEqualTo(7L);
        assertThat(created.get("project_key")).isEqualTo("PRJ_7");
        assertThat(created.get("status")).isEqualTo("active");
    }

    @Test
    void explicitProjectKeyIsNormalizedAndCheckedForCollision() {
        ProjectRepository repository = mock(ProjectRepository.class);
        when(repository.findByProjectKeyIgnoreCase("SUPPORT-OPS")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(Project.class))).thenAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            project.setId(9L);
            return project;
        });

        ProjectService service = new ProjectService(repository);
        Map<String, Object> created = service.create(
            Map.of("name", "Support", "project_key", "support ops"),
            "operator"
        );

        assertThat(created.get("project_key")).isEqualTo("SUPPORT-OPS");
    }
}
