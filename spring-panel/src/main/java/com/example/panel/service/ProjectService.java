package com.example.panel.service;

import com.example.panel.entity.Project;
import com.example.panel.repository.ProjectRepository;
import com.example.panel.repository.TaskProjectMembershipRepository;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ProjectService {

    private static final Set<String> STATUSES = Set.of("active", "paused", "done", "archived");

    private final ProjectRepository projectRepository;
    private final TaskProjectMembershipRepository taskProjectMembershipRepository;
    private final com.example.panel.service.SharedWorkAccessService sharedAccess;

    public ProjectService(ProjectRepository projectRepository,
                          TaskProjectMembershipRepository taskProjectMembershipRepository, com.example.panel.service.SharedWorkAccessService sharedAccess) {
        this.projectRepository = projectRepository;
        this.taskProjectMembershipRepository = taskProjectMembershipRepository;
        this.sharedAccess = sharedAccess;
    }

    public Map<String, Object> list(boolean includeArchived) {
        List<Project> projects = projectRepository.findAllById(sharedAccess.visibleLongIds(com.example.panel.service.SharedWorkAccessService.Kind.PROJECT)).stream()
            .filter(project->includeArchived||project.getArchivedAt()==null)
            .sorted(java.util.Comparator.comparing(Project::getName,java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))).toList();
        return Map.of(
            "items", projects.stream().map(this::toDto).toList(),
            "total", projects.size()
        );
    }

    public Map<String, Object> get(Long id) {
        return toDto(requireProject(id));
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> payload, String actor) {
        OffsetDateTime now = OffsetDateTime.now();
        long owner=sharedAccess.requireCreation(com.example.panel.service.SharedWorkAccessService.Kind.PROJECT,com.example.panel.service.SharedWorkAccessService.current());
        Project project = new Project();
        project.setSharedOwnerUserId(owner);
        project.setName(requiredText(payload.get("name"), "Укажите название проекта."));
        project.setDescription(clean(payload.get("description")));
        project.setStatus(normalizeStatus(payload.get("status"), "active"));
        if ("archived".equals(project.getStatus())) {
            project.setArchivedAt(now);
        }
        project.setLeadIdentity(clean(payload.get("lead_identity")));
        project.setProjectKey(normalizeProjectKey(payload.get("project_key"), null));
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        project = projectRepository.saveAndFlush(project);
        if (!StringUtils.hasText(project.getProjectKey())) {
            project.setProjectKey("PRJ_" + project.getId());
            project = projectRepository.saveAndFlush(project);
        }
        sharedAccess.registerCreated(com.example.panel.service.SharedWorkAccessService.Kind.PROJECT,project.getId(),com.example.panel.service.SharedWorkAccessService.current());
        return toDto(project);
    }

    @Transactional
    public Map<String, Object> update(Long id, Map<String, Object> payload, String actor) {
        sharedAccess.require(com.example.panel.service.SharedWorkAccessService.Kind.PROJECT,id,com.example.panel.service.SharedWorkAccessService.Access.WRITE,com.example.panel.service.SharedWorkAccessService.current());
        Project project = requireProject(id);
        if (payload.containsKey("name")) {
            project.setName(requiredText(payload.get("name"), "Укажите название проекта."));
        }
        if (payload.containsKey("description")) {
            project.setDescription(clean(payload.get("description")));
        }
        if (payload.containsKey("status")) {
            String status = normalizeStatus(payload.get("status"), project.getStatus());
            project.setStatus(status);
            if ("archived".equals(status)) {
                if (project.getArchivedAt() == null) {
                    project.setArchivedAt(OffsetDateTime.now());
                }
            } else {
                project.setArchivedAt(null);
            }
        }
        if (payload.containsKey("lead_identity")) {
            project.setLeadIdentity(clean(payload.get("lead_identity")));
        }
        if (payload.containsKey("project_key")) {
            String key = normalizeProjectKey(payload.get("project_key"), project.getId());
            if (!StringUtils.hasText(key)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Код проекта не может быть пустым.");
            }
            project.setProjectKey(key);
        }
        project.setUpdatedAt(OffsetDateTime.now());
        return toDto(projectRepository.save(project));
    }

    @Transactional
    public Map<String, Object> archive(Long id, String actor) {
        sharedAccess.require(com.example.panel.service.SharedWorkAccessService.Kind.PROJECT,id,com.example.panel.service.SharedWorkAccessService.Access.WRITE,com.example.panel.service.SharedWorkAccessService.current());
        Project project = requireProject(id);
        OffsetDateTime now = OffsetDateTime.now();
        project.setStatus("archived");
        project.setArchivedAt(now);
        project.setUpdatedAt(now);
        return toDto(projectRepository.save(project));
    }

    private Project requireProject(Long id) {
        sharedAccess.require(com.example.panel.service.SharedWorkAccessService.Kind.PROJECT,id,com.example.panel.service.SharedWorkAccessService.Access.READ,com.example.panel.service.SharedWorkAccessService.current());
        return projectRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private String normalizeProjectKey(Object raw, Long currentProjectId) {
        String value = clean(raw);
        if (value == null) {
            return null;
        }
        String normalized = value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_-]+", "-");
        while (normalized.startsWith("-")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("-")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > 80) {
            normalized = normalized.substring(0, 80);
        }
        String candidate = normalized;
        projectRepository.findByProjectKeyIgnoreCase(candidate).ifPresent(existing -> {
            if (!Objects.equals(existing.getId(), currentProjectId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Код проекта уже используется: " + candidate);
            }
        });
        return candidate;
    }

    private String normalizeStatus(Object raw, String fallback) {
        String value = clean(raw);
        if (value == null) {
            return fallback;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported project status: " + value);
        }
        return normalized;
    }

    private String requiredText(Object raw, String message) {
        String value = clean(raw);
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value;
    }

    private String clean(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        return value.isEmpty() ? null : value;
    }

    private Map<String, Object> toDto(Project project) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", project.getId());
        dto.put("project_key", project.getProjectKey());
        dto.put("name", project.getName());
        dto.put("description", project.getDescription());
        dto.put("status", project.getStatus());
        dto.put("lead_identity", project.getLeadIdentity());
        dto.put("created_at", project.getCreatedAt() != null ? project.getCreatedAt().toString() : null);
        dto.put("updated_at", project.getUpdatedAt() != null ? project.getUpdatedAt().toString() : null);
        dto.put("archived_at", project.getArchivedAt() != null ? project.getArchivedAt().toString() : null);
        var readableTasks=new java.util.HashSet<>(sharedAccess.visibleLongIds(com.example.panel.service.SharedWorkAccessService.Kind.TASK));
        dto.put("task_count",project.getId()==null?0L:taskProjectMembershipRepository.findByProject_IdOrderByTask_IdAsc(project.getId()).stream()
            .filter(link->link.getTask()!=null&&readableTasks.contains(link.getTask().getId())).count());
        return dto;
    }
}
