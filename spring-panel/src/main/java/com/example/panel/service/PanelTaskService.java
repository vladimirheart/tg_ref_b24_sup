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

    @org.springframework.beans.factory.annotation.Autowired private SharedWorkAccessService sharedAccess;
    @org.springframework.beans.factory.annotation.Autowired private UiEventTicketAccessService ticketAccess;
    private static final int SEQUENCE_ROW_ID = 1;
    private static final String DEFAULT_STATUS = "Новая";

    private final TaskRepository taskRepository;
    private final TaskSequenceRepository taskSequenceRepository;
    private final TaskPersonRepository taskPersonRepository;
    private final TaskHistoryRepository taskHistoryRepository;
    private final TaskLinkRepository taskLinkRepository;
    private final NotificationRepository notificationRepository;
    private final TicketRepository ticketRepository;
    private final TaskDomainFoundationService taskDomainFoundationService;

    public PanelTaskService(TaskRepository taskRepository,
                            TaskSequenceRepository taskSequenceRepository,
                            TaskPersonRepository taskPersonRepository,
                            TaskHistoryRepository taskHistoryRepository,
                            TaskLinkRepository taskLinkRepository,
                            NotificationRepository notificationRepository,
                            TicketRepository ticketRepository,
                            TaskDomainFoundationService taskDomainFoundationService) {
        this.taskRepository = taskRepository;
        this.taskSequenceRepository = taskSequenceRepository;
        this.taskPersonRepository = taskPersonRepository;
        this.taskHistoryRepository = taskHistoryRepository;
        this.taskLinkRepository = taskLinkRepository;
        this.notificationRepository = notificationRepository;
        this.ticketRepository = ticketRepository;
        this.taskDomainFoundationService = taskDomainFoundationService;
    }

    /** Unowned legacy entry points are closed; callers must provide a current canonical source scope. */
    public Task createTask(TaskPayload payload) { throw new IllegalStateException("A current ticket scope is required"); }
    public Task createTask(TaskPayload payload,boolean notifyParticipants) { return createTask(payload); }

    @Transactional
    public Task createFollowUpTask(TaskPayload payload,UiEventTicketScope scope) {
        if(sharedAccess==null||ticketAccess==null)throw new IllegalStateException("Shared-work services are required");
        sharedAccess.beginMutation();
        if(scope==null||!scope.isValid()||payload==null||payload.ticketIds()==null||payload.ticketIds().size()!=1
            ||!scope.ticketId().equals(payload.ticketIds().get(0))||!"dialog_auto_close".equals(payload.source()))
            throw new IllegalArgumentException("Invalid follow-up source");
        var owner=sharedAccess.eligibleReader(payload.assignee());
        if(owner==null)throw new IllegalArgumentException("Follow-up owner has no current shared-work access");
        long ownerId=Long.parseLong(owner.userId());
        if(!ticketAccess.canReadTicketForRecipient(new UiEventRecipient(ownerId,owner.username()),scope))
            throw new IllegalArgumentException("Follow-up owner cannot read the source ticket");
        OffsetDateTime now=OffsetDateTime.now();
        Task task=new Task();task.setSeq(sharedAccess.nextTaskSequence());applyPayload(task,payload,now);
        task.setCreator("auto_close");task.setAssignee(owner.username());task.setSharedOwnerUserId(ownerId);
        taskRepository.saveAndFlush(task);sharedAccess.registerSystemCreated(task.getId(),ownerId,owner.username());
        taskDomainFoundationService.recordSystemCreated(task,"auto_close",payload.tag(),payload.projectIds(),ownerId,owner.username());
        var accepted=new ArrayList<String>();
        for(String candidate:sanitizeValues(payload.coExecutors())) {
            var person=sharedAccess.eligibleReader(candidate);
            if(person==null)continue;
            long userId=Long.parseLong(person.userId());
            if(userId==ownerId||!ticketAccess.canReadTicketForRecipient(new UiEventRecipient(userId,person.username()),scope))continue;
            sharedAccess.registerSystemParticipant(task.getId(),userId);accepted.add(person.username());
        }
        savePeople(task,"co",accepted);appendHistory(task,"Задача создана автоматически");
        TaskLink link=new TaskLink();TaskLinkId key=new TaskLinkId();
        key.setTaskId(task.getId());key.setUserId(scope.ticketUserId());key.setTicketId(scope.ticketId());
        link.setId(key);link.setTask(task);taskLinkRepository.save(link);
        return task;
    }

    private void applyPayload(Task task, TaskPayload payload, OffsetDateTime now) {
        task.setSource(clean(payload.source()));
        task.setTitle(clean(payload.title()));
        task.setBodyHtml(clean(payload.bodyHtml()));

        String creator = payload.creator();
        String assignee = payload.assignee();
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
        if (ticketIds == null) {
            return;
        }
        for (String ticketId : new LinkedHashSet<>(ticketIds)) {
            if (!StringUtils.hasText(ticketId)) {
                continue;
            }
            Optional<Ticket> ticketOpt = ticketRepository.findUniqueByLegacyTicketId(ticketId);
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

    private String serializeProjectIds(List<Long> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return null;
        }
        Set<String> values = new LinkedHashSet<>();
        for (Long projectId : projectIds) {
            if (projectId != null && projectId > 0L) {
                values.add(Long.toString(projectId));
            }
        }
        return values.isEmpty() ? null : String.join(",", values);
    }

    private Set<String> sanitizeValues(List<String> people) {
        if (people == null) {
            return Set.of();
        }
        Set<String> sanitized = new LinkedHashSet<>();
        for (String value : people) {
            String normalized = StringUtils.hasText(value)?value:null;
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
                              List<String> ticketIds,
                              List<Long> projectIds) {
    }
}
