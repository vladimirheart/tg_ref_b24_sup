package com.example.panel.service;

import com.example.panel.entity.Project;
import com.example.panel.entity.Tag;
import com.example.panel.entity.Task;
import com.example.panel.entity.TaskEvent;
import com.example.panel.entity.TaskProjectMembership;
import com.example.panel.entity.TaskTag;
import com.example.panel.repository.ProjectRepository;
import com.example.panel.repository.TagRepository;
import com.example.panel.repository.TaskEventRepository;
import com.example.panel.repository.TaskProjectMembershipRepository;
import com.example.panel.repository.TaskTagRepository;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
public class TaskDomainFoundationService {

    private static final int MAX_TAGS_PER_TASK = 32;
    private static final int MAX_PROJECTS_PER_TASK = 32;

    private final ProjectRepository projectRepository;
    private final TaskProjectMembershipRepository taskProjectMembershipRepository;
    private final TagRepository tagRepository;
    private final TaskTagRepository taskTagRepository;
    private final TaskEventRepository taskEventRepository;

    public TaskDomainFoundationService(ProjectRepository projectRepository,
                                       TaskProjectMembershipRepository taskProjectMembershipRepository,
                                       TagRepository tagRepository,
                                       TaskTagRepository taskTagRepository,
                                       TaskEventRepository taskEventRepository) {
        this.projectRepository = projectRepository;
        this.taskProjectMembershipRepository = taskProjectMembershipRepository;
        this.tagRepository = tagRepository;
        this.taskTagRepository = taskTagRepository;
        this.taskEventRepository = taskEventRepository;
    }

    public TaskMutationSnapshot snapshot(Task task) {
        if (task == null) {
            return null;
        }
        return new TaskMutationSnapshot(
            task.getTitle(),
            task.getAssignee(),
            task.getStatus(),
            task.getDueAt()
        );
    }

    @Transactional
    public void recordSaved(Task task,
                            TaskMutationSnapshot before,
                            boolean isNew,
                            String actor,
                            String rawTags,
                            String rawProjectIds) {
        if (task == null || task.getId() == null) {
            return;
        }
        String normalizedActor = clean(actor);
        if (isNew) {
            appendEvent(
                task,
                null,
                "TASK_CREATED",
                normalizedActor,
                "status",
                null,
                clean(task.getStatus()),
                "{\"status_timeline\":\"v2\"}"
            );
        } else if (before != null) {
            appendFieldChange(task, normalizedActor, "title", before.title(), task.getTitle());
            appendFieldChange(task, normalizedActor, "assignee", before.assignee(), task.getAssignee());
            appendFieldChange(task, normalizedActor, "status", before.status(), task.getStatus());
            appendFieldChange(task, normalizedActor, "due_at", value(before.dueAt()), value(task.getDueAt()));
        }
        if (rawTags != null) {
            syncTags(task, parseTags(rawTags), normalizedActor);
        }
        if (rawProjectIds != null) {
            syncProjects(task, parseProjectIds(rawProjectIds), normalizedActor);
        }
    }

    @Transactional
    public void recordCreated(Task task, String actor, String rawTags) {
        recordSaved(task, null, true, actor, rawTags, null);
    }

    @Transactional
    public void recordCommentAdded(Task task, String actor, Long commentId) {
        if (task == null || task.getId() == null) {
            return;
        }
        String metadata = commentId == null ? null : "{\"comment_id\":" + commentId + "}";
        appendEvent(task, null, "COMMENT_ADDED", clean(actor), null, null, null, metadata);
    }

    public List<Map<String, Object>> listTags(Long taskId) {
        if (taskId == null) {
            return List.of();
        }
        return taskTagRepository.findByTask_IdOrderByAddedAtAsc(taskId).stream()
            .map(link -> {
                Map<String, Object> item = new LinkedHashMap<>();
                Tag tag = link.getTag();
                item.put("id", tag != null ? tag.getId() : null);
                item.put("name", tag != null ? tag.getName() : null);
                item.put("normalized_name", tag != null ? tag.getNormalizedName() : null);
                item.put("color", tag != null ? tag.getColor() : null);
                return item;
            })
            .toList();
    }

    public List<Map<String, Object>> listProjects(Long taskId) {
        if (taskId == null) {
            return List.of();
        }
        return taskProjectMembershipRepository.findByTask_IdOrderByAddedAtAsc(taskId).stream()
            .map(link -> projectSummary(link.getProject()))
            .toList();
    }

    public List<Map<String, Object>> listEvents(Long taskId) {
        if (taskId == null) {
            return List.of();
        }
        return taskEventRepository.findTop100ByTask_IdOrderByOccurredAtDescIdDesc(taskId).stream()
            .map(event -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", event.getId());
                item.put("event_type", event.getEventType());
                item.put("actor", event.getActor());
                item.put("occurred_at", event.getOccurredAt() != null ? event.getOccurredAt().toString() : null);
                item.put("project_id", event.getProjectId());
                item.put("field_name", event.getFieldName());
                item.put("old_value", event.getOldValue());
                item.put("new_value", event.getNewValue());
                item.put("metadata_json", event.getMetadataJson());
                return item;
            })
            .toList();
    }

    private void syncTags(Task task, LinkedHashMap<String, String> desired, String actor) {
        List<TaskTag> existing = taskTagRepository.findByTask_IdOrderByAddedAtAsc(task.getId());
        Map<String, TaskTag> existingByNormalized = new LinkedHashMap<>();
        for (TaskTag link : existing) {
            if (link.getTag() == null || !StringUtils.hasText(link.getTag().getNormalizedName())) {
                continue;
            }
            existingByNormalized.put(link.getTag().getNormalizedName(), link);
        }

        for (Map.Entry<String, String> desiredTag : desired.entrySet()) {
            if (existingByNormalized.remove(desiredTag.getKey()) != null) {
                continue;
            }
            Tag tag = tagRepository.findByNormalizedName(desiredTag.getKey())
                .orElseGet(() -> {
                    Tag fresh = new Tag();
                    fresh.setName(desiredTag.getValue());
                    fresh.setNormalizedName(desiredTag.getKey());
                    fresh.setCreatedAt(OffsetDateTime.now());
                    return tagRepository.save(fresh);
                });
            TaskTag link = new TaskTag();
            link.setTask(task);
            link.setTag(tag);
            link.setAddedAt(OffsetDateTime.now());
            link.setAddedBy(actor);
            taskTagRepository.save(link);
            appendEvent(task, null, "TAG_ADDED", actor, "tag", null, tag.getName(), null);
        }

        for (TaskTag stale : existingByNormalized.values()) {
            String oldName = stale.getTag() != null ? stale.getTag().getName() : null;
            taskTagRepository.delete(stale);
            appendEvent(task, null, "TAG_REMOVED", actor, "tag", oldName, null, null);
        }
    }

    private void syncProjects(Task task, LinkedHashSet<Long> desiredProjectIds, String actor) {
        List<TaskProjectMembership> existing = taskProjectMembershipRepository.findByTask_IdOrderByAddedAtAsc(task.getId());
        Map<Long, TaskProjectMembership> existingByProject = new LinkedHashMap<>();
        for (TaskProjectMembership membership : existing) {
            Project project = membership.getProject();
            if (project != null && project.getId() != null) {
                existingByProject.put(project.getId(), membership);
            }
        }

        Map<Long, Project> desiredProjects = new LinkedHashMap<>();
        if (!desiredProjectIds.isEmpty()) {
            for (Project project : projectRepository.findAllById(desiredProjectIds)) {
                if (project != null && project.getId() != null && project.getArchivedAt() == null) {
                    desiredProjects.put(project.getId(), project);
                }
            }
            if (desiredProjects.size() != desiredProjectIds.size()) {
                LinkedHashSet<Long> missing = new LinkedHashSet<>(desiredProjectIds);
                missing.removeAll(desiredProjects.keySet());
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unknown or archived project ids: " + missing
                );
            }
        }

        for (Map.Entry<Long, Project> desired : desiredProjects.entrySet()) {
            if (existingByProject.remove(desired.getKey()) != null) {
                continue;
            }
            TaskProjectMembership membership = new TaskProjectMembership();
            membership.setTask(task);
            membership.setProject(desired.getValue());
            membership.setAddedAt(OffsetDateTime.now());
            membership.setAddedBy(actor);
            taskProjectMembershipRepository.save(membership);
            appendEvent(
                task,
                desired.getKey(),
                "PROJECT_ADDED",
                actor,
                "project",
                null,
                projectLabel(desired.getValue()),
                null
            );
        }

        for (TaskProjectMembership stale : existingByProject.values()) {
            Project project = stale.getProject();
            Long projectId = project != null ? project.getId() : null;
            String oldLabel = projectLabel(project);
            taskProjectMembershipRepository.delete(stale);
            appendEvent(task, projectId, "PROJECT_REMOVED", actor, "project", oldLabel, null, null);
        }
    }

    private void appendFieldChange(Task task, String actor, String fieldName, Object oldValue, Object newValue) {
        String oldText = value(oldValue);
        String newText = value(newValue);
        if (Objects.equals(oldText, newText)) {
            return;
        }
        appendEvent(task, null, "FIELD_CHANGED", actor, fieldName, oldText, newText, null);
    }

    private TaskEvent appendEvent(Task task,
                                  Long projectId,
                                  String eventType,
                                  String actor,
                                  String fieldName,
                                  String oldValue,
                                  String newValue,
                                  String metadataJson) {
        TaskEvent event = new TaskEvent();
        event.setTask(task);
        event.setProjectId(projectId);
        event.setEventType(eventType);
        event.setActor(actor);
        event.setOccurredAt(OffsetDateTime.now());
        event.setFieldName(fieldName);
        event.setOldValue(oldValue);
        event.setNewValue(newValue);
        event.setMetadataJson(metadataJson);
        return taskEventRepository.save(event);
    }

    private LinkedHashMap<String, String> parseTags(String raw) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (raw == null) {
            return result;
        }
        for (String chunk : raw.split("[,;\\n]+")) {
            String display = cleanTag(chunk);
            if (display == null) {
                continue;
            }
            String normalized = display.toLowerCase(Locale.ROOT);
            result.putIfAbsent(normalized, display);
            if (result.size() >= MAX_TAGS_PER_TASK) {
                break;
            }
        }
        return result;
    }

    private LinkedHashSet<Long> parseProjectIds(String raw) {
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        if (raw == null) {
            return result;
        }
        for (String chunk : raw.split("[,;\\s]+")) {
            if (!StringUtils.hasText(chunk)) {
                continue;
            }
            try {
                long value = Long.parseLong(chunk.trim());
                if (value > 0) {
                    result.add(value);
                }
            } catch (NumberFormatException ignored) {
                // Invalid client-side values are ignored; the curated project picker sends numeric ids.
            }
            if (result.size() >= MAX_PROJECTS_PER_TASK) {
                break;
            }
        }
        return result;
    }

    private String cleanTag(String value) {
        String cleaned = clean(value);
        if (cleaned == null) {
            return null;
        }
        while (cleaned.startsWith("#")) {
            cleaned = cleaned.substring(1).trim();
        }
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() <= 120 ? cleaned : cleaned.substring(0, 120);
    }

    private String clean(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String projectLabel(Project project) {
        if (project == null) {
            return null;
        }
        if (StringUtils.hasText(project.getProjectKey())) {
            return project.getProjectKey();
        }
        return project.getName();
    }

    private Map<String, Object> projectSummary(Project project) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", project != null ? project.getId() : null);
        item.put("project_key", project != null ? project.getProjectKey() : null);
        item.put("name", project != null ? project.getName() : null);
        item.put("status", project != null ? project.getStatus() : null);
        item.put("lead_identity", project != null ? project.getLeadIdentity() : null);
        return item;
    }

    private String value(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public record TaskMutationSnapshot(String title,
                                       String assignee,
                                       String status,
                                       OffsetDateTime dueAt) {
    }
}
