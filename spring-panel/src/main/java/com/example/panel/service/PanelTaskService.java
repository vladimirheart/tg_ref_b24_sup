package com.example.panel.service;

import com.example.panel.entity.Notification;
import com.example.panel.entity.Task;
import com.example.panel.entity.TaskHistory;
import com.example.panel.entity.TaskLink;
import com.example.panel.entity.TaskLinkId;
import com.example.panel.entity.TaskPerson;
import com.example.panel.entity.TaskSequence;
import com.example.panel.entity.Ticket;
import com.example.panel.repository.NotificationRepository;
import com.example.panel.repository.TaskHistoryRepository;
import com.example.panel.repository.TaskLinkRepository;
import com.example.panel.repository.TaskPersonRepository;
import com.example.panel.repository.TaskRepository;
import com.example.panel.repository.TaskSequenceRepository;
import com.example.panel.repository.TicketRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PanelTaskService {

    private static final int SEQUENCE_ROW_ID = 1;
    private static final String DEFAULT_STATUS = "Новая";

    private final TaskRepository taskRepository;
    private final TaskSequenceRepository taskSequenceRepository;
    private final TaskPersonRepository taskPersonRepository;
    private final TaskHistoryRepository taskHistoryRepository;
    private final TaskLinkRepository taskLinkRepository;
    private final NotificationRepository notificationRepository;
    private final TicketRepository ticketRepository;

    public PanelTaskService(TaskRepository taskRepository,
                            TaskSequenceRepository taskSequenceRepository,
                            TaskPersonRepository taskPersonRepository,
                            TaskHistoryRepository taskHistoryRepository,
                            TaskLinkRepository taskLinkRepository,
                            NotificationRepository notificationRepository,
                            TicketRepository ticketRepository) {
        this.taskRepository = taskRepository;
        this.taskSequenceRepository = taskSequenceRepository;
        this.taskPersonRepository = taskPersonRepository;
        this.taskHistoryRepository = taskHistoryRepository;
        this.taskLinkRepository = taskLinkRepository;
        this.notificationRepository = notificationRepository;
        this.ticketRepository = ticketRepository;
    }

    @Transactional
    public Task createTask(TaskPayload payload) {
        OffsetDateTime now = OffsetDateTime.now();

        Task task = new Task();
        task.setSeq(nextSequenceValue());
        applyPayload(task, payload, now);
        taskRepository.save(task);

        savePeople(task, "co", payload.coExecutors());
        savePeople(task, "watcher", payload.watchers());
        appendHistory(task, "Задача создана");
        linkTickets(task, payload.ticketIds());
        notifyParticipants(task, payload.coExecutors(), payload.watchers(),
            "Новая задача «" + displayTitle(task) + "»", "/tasks", now);
        return task;
    }

    private void applyPayload(Task task, TaskPayload payload, OffsetDateTime now) {
        task.setSource(clean(payload.source()));
        task.setTitle(clean(payload.title()));
        task.setBodyHtml(clean(payload.bodyHtml()));

        String creator = clean(payload.creator());
        String assignee = clean(payload.assignee());
        if (!StringUtils.hasText(assignee)) {
            assignee = creator;
        }
        task.setCreator(creator);
        task.setAssignee(assignee);
        task.setTag(clean(payload.tag()));
        task.setStatus(StringUtils.hasText(payload.status()) ? clean(payload.status()) : DEFAULT_STATUS);
        task.setDueAt(payload.dueAt());
        task.setCreatedAt(now);
        task.setClosedAt(null);
        task.setLastActivityAt(now);
    }

    private void savePeople(Task task, String role, List<String> people) {
        for (String identity : sanitizeValues(people)) {
            TaskPerson person = new TaskPerson();
            person.setTask(task);
            person.setRole(role);
            person.setIdentity(identity);
            taskPersonRepository.save(person);
        }
    }

    private void appendHistory(Task task, String text) {
        TaskHistory history = new TaskHistory();
        history.setTask(task);
        history.setAt(OffsetDateTime.now());
        history.setText(text);
        taskHistoryRepository.save(history);
    }

    private void linkTickets(Task task, List<String> ticketIds) {
        for (String ticketId : sanitizeValues(ticketIds)) {
            Optional<Ticket> ticketOpt = ticketRepository.findByIdTicketId(ticketId);
            if (ticketOpt.isEmpty() || ticketOpt.get().getUserId() == null) {
                continue;
            }
            TaskLink link = new TaskLink();
            TaskLinkId id = new TaskLinkId();
            id.setTaskId(task.getId());
            id.setUserId(ticketOpt.get().getUserId());
            id.setTicketId(ticketId);
            link.setId(id);
            link.setTask(task);
            taskLinkRepository.save(link);
        }
    }

    private void notifyParticipants(Task task,
                                    List<String> coExecutors,
                                    List<String> watchers,
                                    String text,
                                    String url,
                                    OffsetDateTime now) {
        Set<String> targets = new LinkedHashSet<>();
        if (StringUtils.hasText(task.getAssignee())) {
            targets.add(normalizeRecipient(task.getAssignee()));
        }
        for (String coExecutor : sanitizeValues(coExecutors)) {
            targets.add(normalizeRecipient(coExecutor));
        }
        for (String watcher : sanitizeValues(watchers)) {
            targets.add(normalizeRecipient(watcher));
        }
        targets.remove(null);
        if (targets.isEmpty()) {
            return;
        }
        List<Notification> notifications = new ArrayList<>();
        for (String target : targets) {
            Notification notification = new Notification();
            notification.setUserIdentity(target);
            notification.setText(text);
            notification.setUrl(url);
            notification.setIsRead(Boolean.FALSE);
            notification.setCreatedAt(now);
            notifications.add(notification);
        }
        notificationRepository.saveAll(notifications);
    }

    private long nextSequenceValue() {
        TaskSequence sequence = taskSequenceRepository.findById(SEQUENCE_ROW_ID)
            .orElseGet(() -> {
                TaskSequence seed = new TaskSequence();
                seed.setId(SEQUENCE_ROW_ID);
                seed.setVal(0L);
                return taskSequenceRepository.save(seed);
            });
        long current = Optional.ofNullable(sequence.getVal()).orElse(0L);
        sequence.setVal(current + 1);
        taskSequenceRepository.save(sequence);
        return current + 1;
    }

    private Set<String> sanitizeValues(List<String> people) {
        if (people == null) {
            return Set.of();
        }
        Set<String> sanitized = new LinkedHashSet<>();
        for (String value : people) {
            String normalized = clean(value);
            if (normalized != null) {
                sanitized.add(normalized);
            }
        }
        return sanitized;
    }

    private String normalizeRecipient(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String clean(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String displayTitle(Task task) {
        return Optional.ofNullable(task.getTitle())
            .filter(StringUtils::hasText)
            .orElse("без названия");
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
