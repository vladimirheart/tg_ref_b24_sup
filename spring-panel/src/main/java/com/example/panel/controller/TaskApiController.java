package com.example.panel.controller;

import com.example.panel.entity.Task;
import com.example.panel.entity.TaskComment;
import com.example.panel.entity.TaskHistory;
import com.example.panel.entity.TaskPerson;
import com.example.panel.repository.TaskCommentRepository;
import com.example.panel.repository.TaskHistoryRepository;
import com.example.panel.repository.TaskPersonRepository;
import com.example.panel.repository.TaskRepository;
import com.example.panel.service.NotificationService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.util.StringUtils;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/tasks")
@PreAuthorize("hasAuthority('PAGE_TASKS')")
public class TaskApiController {

    private static final Logger log = LoggerFactory.getLogger(TaskApiController.class);
    private static final DateTimeFormatter LOCAL_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final TaskRepository taskRepository;
    private final TaskCommentRepository commentRepository;
    private final TaskHistoryRepository historyRepository;
    private final TaskPersonRepository taskPersonRepository;
    private final NotificationService notificationService;

    public TaskApiController(TaskRepository taskRepository,
                             TaskCommentRepository commentRepository,
                             TaskHistoryRepository historyRepository,
                             TaskPersonRepository taskPersonRepository,
                             NotificationService notificationService) {
        this.taskRepository = taskRepository;
        this.commentRepository = commentRepository;
        this.historyRepository = historyRepository;
        this.taskPersonRepository = taskPersonRepository;
        this.notificationService = notificationService;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(name = "page", defaultValue = "1") int page,
                                    @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
                                    @RequestParam(name = "sort_by", defaultValue = "last_activity_at") String sortBy,
                                    @RequestParam(name = "sort_dir", defaultValue = "desc") String sortDir) {
        int safePage = Math.max(page, 1) - 1;
        int safeSize = Math.min(Math.max(pageSize, 1), 200);

        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(resolveSortField(sortBy)).ascending()
                : Sort.by(resolveSortField(sortBy)).descending();
        Pageable pageable = PageRequest.of(safePage, safeSize, sort);

        Page<Task> result = taskRepository.findAll(pageable);
        List<Map<String, Object>> items = result.stream().map(this::toSummaryDto).toList();

        Map<String, Object> response = new HashMap<>();
        response.put("items", items);
        response.put("total", result.getTotalElements());
        response.put("page", result.getNumber() + 1);
        response.put("page_size", result.getSize());
        log.info("Tasks list requested: page={}, size={}, sort={} {}, returned {} items of {}", page, pageSize,
                sortBy, sortDir, items.size(), result.getTotalElements());
        return response;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable Long id) {
        Optional<Task> taskOpt = taskRepository.findById(id);
        if (taskOpt.isEmpty()) {
            log.warn("Task {} not found when requesting details", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Task not found"));
        }
        Task task = taskOpt.get();
        Map<String, Object> dto = toDetailedDto(task);
        int commentsCount = dto.getOrDefault("comments", List.of()) instanceof List<?> comments
                ? comments.size() : 0;
        int historyCount = dto.getOrDefault("history", List.of()) instanceof List<?> history
                ? history.size() : 0;
        log.info("Task {} details loaded with {} comments and {} history records", id, commentsCount, historyCount);
        return ResponseEntity.ok(dto);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public Map<String, Object> save(@RequestParam(name = "id", required = false) Long id,
                                    @RequestParam String title,
                                    @RequestParam(name = "body_html", required = false) String bodyHtml,
                                    @RequestParam(required = false) String creator,
                                    @RequestParam(required = false) String assignee,
                                    @RequestParam(required = false) String co,
                                    @RequestParam(required = false) String watchers,
                                    @RequestParam(required = false) String tag,
                                    @RequestParam(name = "due_at", required = false) String dueAt,
                                    @RequestParam(required = false) String status,
                                    Authentication authentication) {
        boolean isNew = id == null || taskRepository.findById(id).isEmpty();
        Task task = id != null ? taskRepository.findById(id).orElse(new Task()) : new Task();
        task.setTitle(title);
        task.setBodyHtml(bodyHtml);
        task.setCreator(creator);
        task.setAssignee(assignee);
        task.setTag(tag);
        task.setStatus(StringUtils.hasText(status) ? status : task.getStatus());
        task.setDueAt(parseClientDateTime(dueAt));
        if (task.getCreatedAt() == null) {
            task.setCreatedAt(OffsetDateTime.now());
        }
        task.setLastActivityAt(OffsetDateTime.now());

        Task saved = taskRepository.save(task);
        if (saved.getSeq() == null) {
            saved.setSeq(saved.getId());
            saved = taskRepository.save(saved);
        }
        syncTaskPeople(saved, co, watchers);
        String actor = resolveActor(authentication, saved.getCreator());
        Set<String> recipients = resolveTaskRecipients(saved, co, watchers);
        String displayNo = saved.getSeq() != null ? "DL_" + saved.getSeq() : "DL_" + saved.getId();
        String safeTitle = StringUtils.hasText(saved.getTitle()) ? saved.getTitle().trim() : "без названия";
        String text = isNew
                ? "Новая задача " + displayNo + ": " + safeTitle
                : "Обновлена задача " + displayNo + ": " + safeTitle;
        notificationService.notifyUsersExcluding(recipients, actor, text, "/tasks#task=" + saved.getId());
        log.info("Task {} saved (id={}, creator={}, assignee={}, status={})",
                saved.getSeq(), saved.getId(), saved.getCreator(), saved.getAssignee(), saved.getStatus());
        return Map.of("ok", true, "id", saved.getId());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        if (!taskRepository.existsById(id)) {
            log.warn("Attempt to delete missing task {}", id);
            return Map.of("ok", false, "error", "Task not found");
        }
        commentRepository.deleteAll(commentRepository.findByTaskIdOrderByCreatedAtAsc(id));
        historyRepository.deleteAll(historyRepository.findByTaskIdOrderByAtDesc(id));
        taskRepository.deleteById(id);
        log.info("Task {} deleted along with related comments and history", id);
        return Map.of("ok", true);
    }

    @PostMapping(value = "/{id}/comments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> addComment(@PathVariable Long id,
                                          @RequestParam String html,
                                          @RequestParam(name = "author", required = false) String author,
                                          Authentication authentication) {
        Task task = taskRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        TaskComment comment = new TaskComment();
        comment.setTask(task);
        comment.setAuthor(StringUtils.hasText(author) ? author : "");
        comment.setHtml(html);
        comment.setCreatedAt(OffsetDateTime.now());
        TaskComment saved = commentRepository.save(comment);
        Map<String, Object> dto = Map.of(
                "id", saved.getId(),
                "author", saved.getAuthor(),
                "html", saved.getHtml(),
                "created_at", formatDate(saved.getCreatedAt())
        );
        String actor = resolveActor(authentication, saved.getAuthor());
        Set<String> recipients = resolveTaskRecipients(task, null, null);
        String displayNo = task.getSeq() != null ? "DL_" + task.getSeq() : "DL_" + task.getId();
        String safeAuthor = StringUtils.hasText(saved.getAuthor()) ? saved.getAuthor().trim() : "оператор";
        notificationService.notifyUsersExcluding(
                recipients,
                actor,
                "Новый комментарий в задаче " + displayNo + " от " + safeAuthor,
                "/tasks#task=" + task.getId()
        );
        log.info("Comment {} added to task {} by {}", saved.getId(), id, saved.getAuthor());
        return Map.of("ok", true, "item", dto);
    }

    private Map<String, Object> toSummaryDto(Task task) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", task.getId());
        dto.put("display_no", task.getSeq() != null ? "DL_" + task.getSeq() : "DL_" + task.getId());
        dto.put("title", task.getTitle());
        dto.put("assignee", task.getAssignee());
        dto.put("tag", task.getTag());
        dto.put("status", task.getStatus());
        dto.put("due_at", formatDate(task.getDueAt()));
        dto.put("created_at", formatDate(task.getCreatedAt()));
        dto.put("closed_at", formatDate(task.getClosedAt()));
        dto.put("last_activity_at", formatDate(task.getLastActivityAt()));
        return dto;
    }

    private Map<String, Object> toDetailedDto(Task task) {
        Map<String, Object> dto = toSummaryDto(task);
        dto.put("body_html", task.getBodyHtml());
        dto.put("creator", task.getCreator());

        List<Map<String, Object>> comments = commentRepository.findByTaskIdOrderByCreatedAtAsc(task.getId()).stream()
                .map(c -> {
                    Map<String, Object> commentDto = new HashMap<>();
                    commentDto.put("id", c.getId());
                    commentDto.put("author", c.getAuthor());
                    commentDto.put("html", c.getHtml());
                    commentDto.put("created_at", formatDate(c.getCreatedAt()));
                    return commentDto;
                })
                .toList();
        dto.put("comments", comments);
        List<TaskPerson> people = taskPersonRepository.findByTaskId(task.getId());
        dto.put("co", people.stream()
                .filter(person -> "co".equalsIgnoreCase(person.getRole()))
                .map(TaskPerson::getIdentity)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList());
        dto.put("watchers", people.stream()
                .filter(person -> "watcher".equalsIgnoreCase(person.getRole()))
                .map(TaskPerson::getIdentity)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList());

        List<Map<String, Object>> history = historyRepository.findByTaskIdOrderByAtDesc(task.getId()).stream()
                .map(h -> {
                    Map<String, Object> historyDto = new HashMap<>();
                    historyDto.put("id", h.getId());
                    historyDto.put("text", h.getText());
                    historyDto.put("at", formatDate(h.getAt()));
                    return historyDto;
                })
                .toList();
        dto.put("history", history);
        return dto;
    }

    private String resolveSortField(String sortBy) {
        return switch (sortBy) {
            case "created_at" -> "createdAt";
            case "due_at" -> "dueAt";
            case "status" -> "status";
            case "assignee" -> "assignee";
            case "last_activity_at" -> "lastActivityAt";
            default -> "lastActivityAt";
        };
    }

    private OffsetDateTime parseClientDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            if (value.length() == 16) {
                return LocalDateTime.parse(value, LOCAL_DT).atOffset(ZoneOffset.UTC);
            }
            return OffsetDateTime.parse(value);
        } catch (Exception ex) {
            log.warn("Failed to parse client datetime '{}': {}", value, ex.getMessage());
            return null;
        }
    }

    private String formatDate(OffsetDateTime value) {
        return value != null ? value.toString() : null;
    }

    private void syncTaskPeople(Task task, String co, String watchers) {
        if (task == null || task.getId() == null) {
            return;
        }
        List<TaskPerson> existing = taskPersonRepository.findByTaskId(task.getId());
        if (!existing.isEmpty()) {
            taskPersonRepository.deleteAll(existing);
        }
        List<TaskPerson> fresh = new java.util.ArrayList<>();
        addTaskPeopleRole(fresh, task, "assignee", task.getAssignee());
        addTaskPeopleRole(fresh, task, "creator", task.getCreator());
        for (String identity : parseIdentityList(co)) {
            addTaskPeopleRole(fresh, task, "co", identity);
        }
        for (String identity : parseIdentityList(watchers)) {
            addTaskPeopleRole(fresh, task, "watcher", identity);
        }
        if (!fresh.isEmpty()) {
            taskPersonRepository.saveAll(fresh);
        }
    }

    private void addTaskPeopleRole(List<TaskPerson> target, Task task, String role, String identity) {
        if (target == null || task == null || !StringUtils.hasText(identity) || !StringUtils.hasText(role)) {
            return;
        }
        String trimmedIdentity = identity.trim();
        if (trimmedIdentity.isEmpty()) {
            return;
        }
        boolean exists = target.stream().anyMatch(person ->
                role.equalsIgnoreCase(person.getRole())
                        && trimmedIdentity.equalsIgnoreCase(person.getIdentity()));
        if (exists) {
            return;
        }
        TaskPerson person = new TaskPerson();
        person.setTask(task);
        person.setRole(role);
        person.setIdentity(trimmedIdentity);
        target.add(person);
    }

    private Set<String> resolveTaskRecipients(Task task, String co, String watchers) {
        Set<String> recipients = new LinkedHashSet<>();
        if (task != null) {
            if (StringUtils.hasText(task.getAssignee())) {
                recipients.add(task.getAssignee().trim());
            }
            if (StringUtils.hasText(task.getCreator())) {
                recipients.add(task.getCreator().trim());
            }
            if (task.getId() != null) {
                taskPersonRepository.findByTaskId(task.getId()).stream()
                        .map(TaskPerson::getIdentity)
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .forEach(recipients::add);
            }
        }
        parseIdentityList(co).forEach(recipients::add);
        parseIdentityList(watchers).forEach(recipients::add);
        return recipients;
    }

    private List<String> parseIdentityList(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        Set<String> values = new LinkedHashSet<>();
        String[] chunks = raw.split("[,;\\n]+");
        for (String chunk : chunks) {
            if (!StringUtils.hasText(chunk)) {
                continue;
            }
            String value = chunk.trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values.stream().toList();
    }

    private String resolveActor(Authentication authentication, String fallback) {
        if (authentication != null && StringUtils.hasText(authentication.getName())) {
            return authentication.getName().trim();
        }
        if (StringUtils.hasText(fallback)) {
            return fallback.trim();
        }
        return null;
    }
}
