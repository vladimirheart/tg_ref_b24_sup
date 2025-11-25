package com.example.supportbot.service;

import com.example.supportbot.entity.Notification;
import com.example.supportbot.entity.Task;
import com.example.supportbot.entity.TaskComment;
import com.example.supportbot.entity.TaskHistory;
import com.example.supportbot.entity.TaskLink;
import com.example.supportbot.entity.TaskLinkId;
import com.example.supportbot.entity.TaskPerson;
import com.example.supportbot.entity.TaskSequence;
import com.example.supportbot.repository.NotificationRepository;
import com.example.supportbot.repository.TaskCommentRepository;
import com.example.supportbot.repository.TaskHistoryRepository;
import com.example.supportbot.repository.TaskLinkRepository;
import com.example.supportbot.repository.TaskPersonRepository;
import com.example.supportbot.repository.TaskRepository;
import com.example.supportbot.repository.TaskSequenceRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

    private static final int SEQUENCE_ROW_ID = 1;
    private static final String DEFAULT_STATUS = "Новая";

    private final TaskRepository taskRepository;
    private final TaskSequenceRepository taskSequenceRepository;
    private final TaskPersonRepository taskPersonRepository;
    private final TaskCommentRepository taskCommentRepository;
    private final TaskHistoryRepository taskHistoryRepository;
    private final TaskLinkRepository taskLinkRepository;
    private final NotificationRepository notificationRepository;

    public TaskService(TaskRepository taskRepository,
                       TaskSequenceRepository taskSequenceRepository,
                       TaskPersonRepository taskPersonRepository,
                       TaskCommentRepository taskCommentRepository,
                       TaskHistoryRepository taskHistoryRepository,
                       TaskLinkRepository taskLinkRepository,
                       NotificationRepository notificationRepository) {
        this.taskRepository = taskRepository;
        this.taskSequenceRepository = taskSequenceRepository;
        this.taskPersonRepository = taskPersonRepository;
        this.taskCommentRepository = taskCommentRepository;
        this.taskHistoryRepository = taskHistoryRepository;
        this.taskLinkRepository = taskLinkRepository;
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public Task createTask(TaskPayload payload) {
        OffsetDateTime now = OffsetDateTime.now();

        Task task = new Task();
        task.setSeq(nextSequenceValue());
        applyPayload(task, payload, now, true);
        taskRepository.save(task);

        replacePeople(task.getId(), "co", payload.coExecutors());
        replacePeople(task.getId(), "watcher", payload.watchers());
        appendHistory(task.getId(), "Задача создана");
        linkTickets(task.getId(), payload.ticketIds());
        notifyParticipants(task, "Новая задача «" + displayTitle(task) + "»", "/tasks");
        return task;
    }

    @Transactional
    public Optional<Task> updateTask(Long taskId, TaskPayload payload) {
        return taskRepository.findById(taskId).map(task -> {
            OffsetDateTime now = OffsetDateTime.now();
            applyPayload(task, payload, now, false);
            taskRepository.save(task);

            replacePeople(task.getId(), "co", payload.coExecutors());
            replacePeople(task.getId(), "watcher", payload.watchers());
            linkTickets(task.getId(), payload.ticketIds());
            appendHistory(task.getId(), "Задача изменена (" + task.getStatus() + ")");
            notifyParticipants(task, "Обновление задачи «" + displayTitle(task) + "»", "/tasks");
            return task;
        });
    }

    @Transactional
    public Optional<TaskComment> addComment(Long taskId, String author, String html) {
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }
        return taskRepository.findById(taskId).map(task -> {
            OffsetDateTime now = OffsetDateTime.now();
            TaskComment comment = new TaskComment();
            comment.setTaskId(taskId);
            comment.setAuthor(author);
            comment.setHtml(html);
            comment.setCreatedAt(now);
            taskCommentRepository.save(comment);

            appendHistory(taskId, "Комментарий от " + Optional.ofNullable(author).orElse("оператор"));
            touchActivity(task, now);
            taskRepository.save(task);
            notifyParticipants(task, "Комментарий в задаче «" + displayTitle(task) + "»", "/tasks");
            return comment;
        });
    }

    @Transactional
    public Optional<TaskLink> linkTaskToTicket(Long taskId, String ticketId) {
        if (ticketId == null || ticketId.isBlank()) {
            return Optional.empty();
        }
        return taskRepository.findById(taskId).map(task -> {
            TaskLinkId id = new TaskLinkId(taskId, ticketId);
            Optional<TaskLink> existingLink = taskLinkRepository.findById(id);
            if (existingLink.isPresent()) {
                touchActivity(task, OffsetDateTime.now());
                taskRepository.save(task);
                return existingLink.get();
            }
            TaskLink link = new TaskLink(id);
            taskLinkRepository.save(link);
            appendHistory(taskId, "Добавлена связь с тикетом " + ticketId);
            touchActivity(task, OffsetDateTime.now());
            taskRepository.save(task);
            return link;
        });
    }

    private void applyPayload(Task task, TaskPayload payload, OffsetDateTime now, boolean isNew) {
        task.setSource(clean(payload.source()));
        task.setTitle(clean(payload.title()));
        task.setBodyHtml(clean(payload.bodyHtml()));

        String creator = clean(payload.creator());
        String assignee = clean(payload.assignee());
        if (assignee == null || assignee.isBlank()) {
            assignee = creator;
        }
        task.setCreator(creator);
        task.setAssignee(assignee);
        task.setTag(clean(payload.tag()));

        String status = clean(payload.status());
        if (status == null || status.isBlank()) {
            status = DEFAULT_STATUS;
        }
        task.setStatus(status);
        task.setDueAt(payload.dueAt());

        if (isNew && task.getCreatedAt() == null) {
            task.setCreatedAt(now);
        }
        if ("Завершена".equalsIgnoreCase(status)) {
            if (task.getClosedAt() == null) {
                task.setClosedAt(now);
            }
        } else {
            task.setClosedAt(null);
        }
        touchActivity(task, now);
    }

    private void replacePeople(Long taskId, String role, List<String> people) {
        Set<String> desired = sanitizePeople(people);
        List<TaskPerson> existing = taskPersonRepository.findByTaskId(taskId);

        for (TaskPerson person : existing) {
            if (role.equalsIgnoreCase(person.getRole()) && !desired.contains(person.getIdentity())) {
                taskPersonRepository.delete(person);
            }
        }

        Set<String> existingMatches = existing.stream()
                .filter(p -> role.equalsIgnoreCase(p.getRole()))
                .map(TaskPerson::getIdentity)
                .collect(Collectors.toSet());

        for (String identity : desired) {
            if (!existingMatches.contains(identity)) {
                TaskPerson person = new TaskPerson();
                person.setTaskId(taskId);
                person.setRole(role);
                person.setIdentity(identity);
                taskPersonRepository.save(person);
            }
        }
    }

    private void appendHistory(Long taskId, String text) {
        TaskHistory history = new TaskHistory();
        history.setTaskId(taskId);
        history.setText(text);
        history.setAt(OffsetDateTime.now());
        taskHistoryRepository.save(history);
    }

    private void linkTickets(Long taskId, List<String> ticketIds) {
        Set<String> sanitized = sanitizePeople(ticketIds);
        if (sanitized.isEmpty()) {
            return;
        }
        List<TaskLink> existing = taskLinkRepository.findByIdTaskId(taskId);
        Set<String> alreadyLinked = existing.stream()
                .map(link -> Optional.ofNullable(link.getId()).map(TaskLinkId::getTicketId).orElse(null))
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
        for (String ticketId : sanitized) {
            if (!alreadyLinked.contains(ticketId)) {
                taskLinkRepository.save(new TaskLink(taskId, ticketId));
                appendHistory(taskId, "Добавлена связь с тикетом " + ticketId);
            }
        }
    }

    private void notifyParticipants(Task task, String text, String url) {
        Set<String> targets = new HashSet<>();
        if (task.getAssignee() != null && !task.getAssignee().isBlank()) {
            targets.add(task.getAssignee());
        }
        targets.addAll(loadPeople(task.getId()));
        if (targets.isEmpty()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        List<Notification> notifications = new ArrayList<>();
        for (String target : targets) {
            Notification notification = new Notification();
            notification.setUser(target);
            notification.setText(text);
            notification.setUrl(url);
            notification.setCreatedAt(now);
            notifications.add(notification);
        }
        notificationRepository.saveAll(notifications);
    }

    private Set<String> loadPeople(Long taskId) {
        return taskPersonRepository.findByTaskId(taskId).stream()
                .map(TaskPerson::getIdentity)
                .filter(identity -> identity != null && !identity.isBlank())
                .collect(Collectors.toSet());
    }

    private void touchActivity(Task task, OffsetDateTime at) {
        task.setLastActivityAt(at);
    }

    private int nextSequenceValue() {
        TaskSequence sequence = taskSequenceRepository.findById(SEQUENCE_ROW_ID)
                .orElseGet(() -> {
                    TaskSequence seed = new TaskSequence();
                    seed.setId(SEQUENCE_ROW_ID);
                    seed.setVal(0);
                    return taskSequenceRepository.save(seed);
                });
        int current = Optional.ofNullable(sequence.getVal()).orElse(0);
        sequence.setVal(current + 1);
        taskSequenceRepository.save(sequence);
        return current + 1;
    }

    private Set<String> sanitizePeople(List<String> people) {
        if (people == null) {
            return Set.of();
        }
        return people.stream()
                .map(this::clean)
                .filter(item -> item != null && !item.isBlank())
                .collect(Collectors.toSet());
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String displayTitle(Task task) {
        return Optional.ofNullable(task.getTitle()).filter(title -> !title.isBlank()).orElse("без названия");
    }

    public record TaskPayload(String title,
                              String bodyHtml,
                              String creator,
                              String assignee,
                              String tag,
                              String status,
                              OffsetDateTime dueAt,
                              String source,
                              List<String> coExecutors,
                              List<String> watchers,
                              List<String> ticketIds) {
    }
}
