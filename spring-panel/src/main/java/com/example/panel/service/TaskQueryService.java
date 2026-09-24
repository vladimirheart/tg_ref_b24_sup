package com.example.panel.service;

import com.example.panel.entity.Task;
import com.example.panel.entity.TaskPerson;
import com.example.panel.entity.TaskProjectMembership;
import com.example.panel.entity.TaskTag;
import com.example.panel.repository.TaskRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TaskQueryService {

    private final TaskRepository taskRepository;
    private final TaskDomainFoundationService taskDomainFoundationService;

    public TaskQueryService(TaskRepository taskRepository,
                            TaskDomainFoundationService taskDomainFoundationService) {
        this.taskRepository = taskRepository;
        this.taskDomainFoundationService = taskDomainFoundationService;
    }

    public Map<String, Object> list(int page,
                                    int pageSize,
                                    String sortBy,
                                    String sortDir,
                                    String number,
                                    String title,
                                    String assignee,
                                    String tag,
                                    String status,
                                    String createdFrom,
                                    String createdTo,
                                    String dueFrom,
                                    String dueTo,
                                    Long projectId,
                                    boolean mine,
                                    String currentUser) {
        int safePage = Math.max(page, 1) - 1;
        int safeSize = Math.min(Math.max(pageSize, 1), 200);
        Sort sort = "asc".equalsIgnoreCase(sortDir)
            ? Sort.by(resolveSortField(sortBy)).ascending()
            : Sort.by(resolveSortField(sortBy)).descending();
        Pageable pageable = PageRequest.of(safePage, safeSize, sort);

        Specification<Task> specification = buildSpecification(
            number,
            title,
            assignee,
            tag,
            status,
            createdFrom,
            createdTo,
            dueFrom,
            dueTo,
            projectId,
            mine,
            currentUser
        );

        Page<Task> result = taskRepository.findAll(specification, pageable);
        List<Map<String, Object>> items = result.stream().map(this::toSummaryDto).toList();

        Map<String, Object> response = new HashMap<>();
        response.put("items", items);
        response.put("total", result.getTotalElements());
        response.put("page", result.getNumber() + 1);
        response.put("page_size", result.getSize());
        return response;
    }

    private Specification<Task> buildSpecification(String number,
                                                   String title,
                                                   String assignee,
                                                   String tag,
                                                   String status,
                                                   String createdFrom,
                                                   String createdTo,
                                                   String dueFrom,
                                                   String dueTo,
                                                   Long projectId,
                                                   boolean mine,
                                                   String currentUser) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            String numberValue = clean(number);
            if (numberValue != null) {
                String digits = numberValue.toUpperCase(Locale.ROOT).replaceFirst("^DL_?", "").trim();
                try {
                    long parsed = Long.parseLong(digits);
                    predicates.add(cb.or(
                        cb.equal(root.get("seq"), parsed),
                        cb.equal(root.get("id"), parsed)
                    ));
                } catch (NumberFormatException ex) {
                    predicates.add(cb.disjunction());
                }
            }

            addContainsIgnoreCase(predicates, cb, root.get("title"), title);
            addContainsIgnoreCase(predicates, cb, root.get("assignee"), assignee);

            String statusValue = clean(status);
            if (statusValue != null) {
                predicates.add(cb.equal(cb.lower(root.<String>get("status")), statusValue.toLowerCase(Locale.ROOT)));
            }

            OffsetDateTime createdFromValue = startOfDay(createdFrom);
            OffsetDateTime createdToValue = endExclusive(createdTo);
            OffsetDateTime dueFromValue = startOfDay(dueFrom);
            OffsetDateTime dueToValue = endExclusive(dueTo);
            if (createdFromValue != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdFromValue));
            if (createdToValue != null) predicates.add(cb.lessThan(root.get("createdAt"), createdToValue));
            if (dueFromValue != null) predicates.add(cb.greaterThanOrEqualTo(root.get("dueAt"), dueFromValue));
            if (dueToValue != null) predicates.add(cb.lessThan(root.get("dueAt"), dueToValue));

            if (projectId != null) {
                Subquery<Long> membershipSubquery = query.subquery(Long.class);
                var membership = membershipSubquery.from(TaskProjectMembership.class);
                membershipSubquery.select(membership.get("task").get("id").as(Long.class));
                membershipSubquery.where(cb.equal(membership.get("project").get("id"), projectId));
                predicates.add(root.get("id").in(membershipSubquery));
            }

            String tagValue = clean(tag);
            if (tagValue != null) {
                String normalizedTag = stripTagPrefix(tagValue).toLowerCase(Locale.ROOT);
                Subquery<Long> tagSubquery = query.subquery(Long.class);
                var taskTag = tagSubquery.from(TaskTag.class);
                tagSubquery.select(taskTag.get("task").get("id").as(Long.class));
                tagSubquery.where(cb.like(
                    cb.lower(taskTag.get("tag").<String>get("normalizedName")),
                    "%" + normalizedTag + "%"
                ));
                predicates.add(cb.or(
                    root.get("id").in(tagSubquery),
                    cb.like(cb.lower(root.<String>get("tag")), "%" + normalizedTag + "%")
                ));
            }

            String user = clean(currentUser);
            if (mine && user != null) {
                Subquery<Long> peopleSubquery = query.subquery(Long.class);
                var person = peopleSubquery.from(TaskPerson.class);
                peopleSubquery.select(person.get("task").get("id").as(Long.class));
                peopleSubquery.where(
                    cb.and(
                        cb.equal(cb.lower(person.<String>get("identity")), user.toLowerCase(Locale.ROOT)),
                        person.get("role").in("assignee", "co")
                    )
                );
                predicates.add(cb.or(
                    cb.equal(cb.lower(root.<String>get("assignee")), user.toLowerCase(Locale.ROOT)),
                    root.get("id").in(peopleSubquery)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void addContainsIgnoreCase(List<Predicate> predicates,
                                       jakarta.persistence.criteria.CriteriaBuilder cb,
                                       jakarta.persistence.criteria.Path<String> path,
                                       String raw) {
        String value = clean(raw);
        if (value == null) return;
        predicates.add(cb.like(cb.lower(path), "%" + value.toLowerCase(Locale.ROOT) + "%"));
    }

    private OffsetDateTime startOfDay(String raw) {
        String value = clean(raw);
        if (value == null) return null;
        try {
            return LocalDate.parse(value).atStartOfDay().atOffset(ZoneOffset.UTC);
        } catch (Exception ex) {
            return null;
        }
    }

    private OffsetDateTime endExclusive(String raw) {
        String value = clean(raw);
        if (value == null) return null;
        try {
            return LocalDate.parse(value).plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<String, Object> toSummaryDto(Task task) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", task.getId());
        dto.put("display_no", task.getSeq() != null ? "DL_" + task.getSeq() : "DL_" + task.getId());
        dto.put("title", task.getTitle());
        dto.put("assignee", task.getAssignee());
        dto.put("tag", task.getTag());
        dto.put("tags", taskDomainFoundationService.listTags(task.getId()));
        dto.put("projects", taskDomainFoundationService.listProjects(task.getId()));
        dto.put("status", task.getStatus());
        dto.put("due_at", formatDate(task.getDueAt()));
        dto.put("created_at", formatDate(task.getCreatedAt()));
        dto.put("closed_at", formatDate(task.getClosedAt()));
        dto.put("last_activity_at", formatDate(task.getLastActivityAt()));
        return dto;
    }

    private String resolveSortField(String sortBy) {
        return switch (String.valueOf(sortBy)) {
            case "num" -> "seq";
            case "title" -> "title";
            case "created_at" -> "createdAt";
            case "due_at" -> "dueAt";
            case "status" -> "status";
            case "assignee" -> "assignee";
            case "last_activity_at" -> "lastActivityAt";
            default -> "lastActivityAt";
        };
    }

    private String stripTagPrefix(String value) {
        String result = value;
        while (result.startsWith("#")) result = result.substring(1).trim();
        return result;
    }

    private String clean(String value) {
        if (!StringUtils.hasText(value)) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String formatDate(OffsetDateTime value) {
        return value != null ? value.toString() : null;
    }
}
