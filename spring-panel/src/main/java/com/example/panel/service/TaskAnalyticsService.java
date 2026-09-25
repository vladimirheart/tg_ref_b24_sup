package com.example.panel.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TaskAnalyticsService {

    private static final String STATUS_DONE = "Завершена";
    private static final String STATUS_CANCELLED = "Отменена";
    private static final String STATUS_IN_PROGRESS = "В работе";

    private final JdbcTemplate jdbcTemplate;

    public TaskAnalyticsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> summary(String rawFrom,
                                       String rawTo,
                                       Long projectId,
                                       String tag,
                                       String status,
                                       String assignee,
                                       String source,
                                       String eventType) {
        Period period = resolvePeriod(rawFrom, rawTo);
        Filter filter = buildFilter(projectId, tag, status, assignee, source, eventType, period);
        List<TaskRow> tasks = loadTasks(filter);
        List<EventRow> events = loadEvents(filter);

        Map<Long, TaskRow> taskById = new LinkedHashMap<>();
        for (TaskRow task : tasks) {
            taskById.put(task.id(), task);
        }

        Map<Long, List<EventRow>> eventsByTask = new HashMap<>();
        Map<String, Long> eventBreakdown = new HashMap<>();
        Set<Long> throughputTasks = new HashSet<>();
        Set<Long> reopenedTasks = new HashSet<>();
        long statusChangeEvents = 0L;

        for (EventRow event : events) {
            eventsByTask.computeIfAbsent(event.taskId(), ignored -> new ArrayList<>()).add(event);
            if (!inPeriod(event.occurredAt(), period)) {
                continue;
            }
            String eventKey = clean(event.eventType());
            if (eventKey != null) {
                eventBreakdown.merge(eventKey, 1L, Long::sum);
            }
            if (isStatusChange(event)) {
                statusChangeEvents++;
                if (isCompletedStatus(event.newValue())) {
                    throughputTasks.add(event.taskId());
                }
                if (isFinalStatus(event.oldValue()) && !isFinalStatus(event.newValue())) {
                    reopenedTasks.add(event.taskId());
                }
            }
        }

        long openTasks = 0L;
        long overdueOpen = 0L;
        long createdInPeriod = 0L;
        Map<String, Long> statusBreakdown = new HashMap<>();
        Map<String, Long> assigneeBreakdown = new HashMap<>();
        Map<String, Long> sourceBreakdown = new HashMap<>();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        List<Double> leadHours = new ArrayList<>();
        List<Double> cycleHours = new ArrayList<>();

        for (TaskRow task : tasks) {
            String currentStatus = label(task.status(), "Без статуса");
            statusBreakdown.merge(currentStatus, 1L, Long::sum);
            assigneeBreakdown.merge(label(task.assignee(), "Не назначено"), 1L, Long::sum);
            sourceBreakdown.merge(label(task.source(), "Не указан"), 1L, Long::sum);

            if (!isFinalStatus(task.status())) {
                openTasks++;
                if (task.dueAt() != null && task.dueAt().isBefore(now)) {
                    overdueOpen++;
                }
            }
            if (inPeriod(task.createdAt(), period)) {
                createdInPeriod++;
            }

            List<EventRow> history = eventsByTask.getOrDefault(task.id(), List.of()).stream()
                .sorted(Comparator.comparing(EventRow::occurredAt, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(EventRow::id, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
            OffsetDateTime firstStarted = null;
            OffsetDateTime firstCompleted = null;
            for (EventRow event : history) {
                if (!isStatusChange(event) || event.occurredAt() == null) {
                    continue;
                }
                if (firstStarted == null && isInProgressStatus(event.newValue())) {
                    firstStarted = event.occurredAt();
                }
                if (isCompletedStatus(event.newValue())) {
                    firstCompleted = event.occurredAt();
                    break;
                }
            }
            if (firstCompleted != null && inPeriod(firstCompleted, period)) {
                if (task.createdAt() != null && !firstCompleted.isBefore(task.createdAt())) {
                    leadHours.add(hoursBetween(task.createdAt(), firstCompleted));
                }
                if (firstStarted != null && !firstCompleted.isBefore(firstStarted)) {
                    cycleHours.add(hoursBetween(firstStarted, firstCompleted));
                }
            }
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("tasks_total", tasks.size());
        metrics.put("open_tasks", openTasks);
        metrics.put("overdue_open", overdueOpen);
        metrics.put("created_in_period", createdInPeriod);
        metrics.put("throughput", throughputTasks.size());
        metrics.put("reopened", reopenedTasks.size());
        metrics.put("status_change_events", statusChangeEvents);
        metrics.put("avg_lead_hours", average(leadHours));
        metrics.put("avg_cycle_hours", average(cycleHours));
        metrics.put("lead_samples", leadHours.size());
        metrics.put("cycle_samples", cycleHours.size());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("period", Map.of(
            "from", period.fromDate().toString(),
            "to", period.toDate().toString()
        ));
        response.put("filters", filterEcho(projectId, tag, status, assignee, source, eventType));
        response.put("metrics", metrics);
        response.put("status_breakdown", ranked(statusBreakdown));
        response.put("assignee_breakdown", ranked(assigneeBreakdown));
        response.put("source_breakdown", ranked(sourceBreakdown));
        response.put("event_breakdown", ranked(eventBreakdown));
        response.put("notes", List.of(
            "Lead time считается от created_at до первого перехода в статус «Завершена».",
            "Cycle time считается от первого перехода в «В работе» до первого перехода в «Завершена».",
            "Time-in-status не рассчитывается в этом slice: старые TASK_CREATED events не фиксировали начальный статус."
        ));
        return response;
    }

    private List<TaskRow> loadTasks(Filter filter) {
        String sql = """
            SELECT t.id, t.status, t.assignee, t.source, t.created_at, t.due_at
              FROM tasks t
             WHERE 1 = 1
            """ + filter.sql() + " ORDER BY t.id";
        return jdbcTemplate.query(sql, this::mapTask, filter.params().toArray());
    }

    private List<EventRow> loadEvents(Filter filter) {
        String sql = """
            SELECT e.id, e.task_id, e.event_type, e.field_name, e.old_value, e.new_value, e.occurred_at
              FROM task_events e
              JOIN tasks t ON t.id = e.task_id
             WHERE 1 = 1
            """ + filter.sql() + " ORDER BY e.task_id, e.occurred_at, e.id";
        return jdbcTemplate.query(sql, this::mapEvent, filter.params().toArray());
    }

    private TaskRow mapTask(ResultSet rs, int rowNum) throws SQLException {
        return new TaskRow(
            rs.getLong("id"),
            rs.getString("status"),
            rs.getString("assignee"),
            rs.getString("source"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("due_at", OffsetDateTime.class)
        );
    }

    private EventRow mapEvent(ResultSet rs, int rowNum) throws SQLException {
        return new EventRow(
            rs.getLong("id"),
            rs.getLong("task_id"),
            rs.getString("event_type"),
            rs.getString("field_name"),
            rs.getString("old_value"),
            rs.getString("new_value"),
            rs.getObject("occurred_at", OffsetDateTime.class)
        );
    }

    private Filter buildFilter(Long projectId,
                               String tag,
                               String status,
                               String assignee,
                               String source,
                               String eventType,
                               Period period) {
        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();

        if (projectId != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM task_project_memberships tpm WHERE tpm.task_id = t.id AND tpm.project_id = ?)");
            params.add(projectId);
        }
        if (StringUtils.hasText(tag)) {
            String normalized = tag.trim().replaceFirst("^#+", "").toLowerCase(Locale.ROOT);
            sql.append(" AND EXISTS (SELECT 1 FROM task_tags tt JOIN tags tg ON tg.id = tt.tag_id WHERE tt.task_id = t.id AND LOWER(tg.normalized_name) = ?)");
            params.add(normalized);
        }
        if (StringUtils.hasText(status)) {
            sql.append(" AND LOWER(COALESCE(t.status, '')) = LOWER(?)");
            params.add(status.trim());
        }
        if (StringUtils.hasText(assignee)) {
            sql.append(" AND LOWER(COALESCE(t.assignee, '')) = LOWER(?)");
            params.add(assignee.trim());
        }
        if (StringUtils.hasText(source)) {
            sql.append(" AND LOWER(COALESCE(t.source, '')) = LOWER(?)");
            params.add(source.trim());
        }
        if (StringUtils.hasText(eventType)) {
            sql.append(" AND EXISTS (SELECT 1 FROM task_events ef WHERE ef.task_id = t.id AND UPPER(COALESCE(ef.event_type, '')) = UPPER(?) AND ef.occurred_at >= ? AND ef.occurred_at < ?)");
            params.add(eventType.trim());
            params.add(period.start());
            params.add(period.endExclusive());
        }
        return new Filter(sql.toString(), params);
    }

    private Period resolvePeriod(String rawFrom, String rawTo) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate from = parseDate(rawFrom, "from", today.minusDays(29));
        LocalDate to = parseDate(rawTo, "to", today);
        if (to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Дата 'to' не может быть раньше 'from'.");
        }
        if (Duration.between(from.atStartOfDay().toInstant(ZoneOffset.UTC), to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)).toDays() > 3660) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Период аналитики слишком большой.");
        }
        return new Period(
            from,
            to,
            from.atStartOfDay().atOffset(ZoneOffset.UTC),
            to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC)
        );
    }

    private LocalDate parseDate(String raw, String field, LocalDate fallback) {
        if (!StringUtils.hasText(raw)) {
            return fallback;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректная дата '" + field + "'.");
        }
    }

    private Map<String, Object> filterEcho(Long projectId,
                                           String tag,
                                           String status,
                                           String assignee,
                                           String source,
                                           String eventType) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("project_id", projectId);
        result.put("tag", clean(tag));
        result.put("status", clean(status));
        result.put("assignee", clean(assignee));
        result.put("source", clean(source));
        result.put("event_type", clean(eventType));
        return result;
    }

    private List<Map<String, Object>> ranked(Map<String, Long> counts) {
        return counts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
            .map(entry -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("key", entry.getKey());
                item.put("count", entry.getValue());
                return item;
            })
            .toList();
    }

    private boolean inPeriod(OffsetDateTime value, Period period) {
        return value != null && !value.isBefore(period.start()) && value.isBefore(period.endExclusive());
    }

    private boolean isStatusChange(EventRow event) {
        return "FIELD_CHANGED".equalsIgnoreCase(clean(event.eventType()))
            && "status".equalsIgnoreCase(clean(event.fieldName()));
    }

    private boolean isFinalStatus(String value) {
        return STATUS_DONE.equalsIgnoreCase(clean(value)) || STATUS_CANCELLED.equalsIgnoreCase(clean(value));
    }

    private boolean isCompletedStatus(String value) {
        return STATUS_DONE.equalsIgnoreCase(clean(value));
    }

    private boolean isInProgressStatus(String value) {
        return STATUS_IN_PROGRESS.equalsIgnoreCase(clean(value));
    }

    private double hoursBetween(OffsetDateTime from, OffsetDateTime to) {
        return Duration.between(from, to).toMillis() / 3_600_000.0d;
    }

    private Double average(List<Double> values) {
        if (values.isEmpty()) {
            return null;
        }
        double sum = values.stream().mapToDouble(Double::doubleValue).sum();
        return Math.round((sum / values.size()) * 10.0d) / 10.0d;
    }

    private String clean(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String label(String value, String fallback) {
        String cleaned = clean(value);
        return cleaned != null ? cleaned : fallback;
    }

    private record Period(LocalDate fromDate,
                          LocalDate toDate,
                          OffsetDateTime start,
                          OffsetDateTime endExclusive) {
    }

    private record Filter(String sql, List<Object> params) {
    }

    private record TaskRow(Long id,
                           String status,
                           String assignee,
                           String source,
                           OffsetDateTime createdAt,
                           OffsetDateTime dueAt) {
    }

    private record EventRow(Long id,
                            Long taskId,
                            String eventType,
                            String fieldName,
                            String oldValue,
                            String newValue,
                            OffsetDateTime occurredAt) {
    }
}
