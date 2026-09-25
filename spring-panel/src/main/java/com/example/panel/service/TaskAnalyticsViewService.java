package com.example.panel.service;

import com.example.panel.entity.TaskAnalyticsView;
import com.example.panel.repository.TaskAnalyticsViewRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TaskAnalyticsViewService {

    private static final Set<String> ALLOWED_FILTERS = Set.of(
        "from",
        "to",
        "project_id",
        "tag",
        "status",
        "assignee",
        "source",
        "event_type"
    );

    private final TaskAnalyticsViewRepository repository;
    private final ObjectMapper objectMapper;

    public TaskAnalyticsViewService(TaskAnalyticsViewRepository repository,
                                    ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String ownerIdentity) {
        String owner = normalizeOwner(ownerIdentity);
        return repository.findByOwnerIdentityOrderByUpdatedAtDescIdDesc(owner).stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public Map<String, Object> save(String ownerIdentity,
                                    Long id,
                                    String rawName,
                                    Map<String, Object> rawFilters) {
        String owner = normalizeOwner(ownerIdentity);
        String name = normalizeName(rawName);
        Map<String, Object> filters = normalizeFilters(rawFilters);

        TaskAnalyticsView view = id == null
            ? new TaskAnalyticsView()
            : repository.findByIdAndOwnerIdentity(id, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Analytical view not found"));

        repository.findByOwnerIdentityAndNameIgnoreCase(owner, name)
            .filter(existing -> view.getId() == null || !existing.getId().equals(view.getId()))
            .ifPresent(existing -> {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Analytical view with this name already exists");
            });

        OffsetDateTime now = OffsetDateTime.now();
        if (view.getId() == null) {
            view.setOwnerIdentity(owner);
            view.setCreatedAt(now);
        }
        view.setName(name);
        view.setFiltersJson(writeFilters(filters));
        view.setUpdatedAt(now);
        return toDto(repository.save(view));
    }

    @Transactional
    public void delete(String ownerIdentity, Long id) {
        String owner = normalizeOwner(ownerIdentity);
        TaskAnalyticsView view = repository.findByIdAndOwnerIdentity(id, owner)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Analytical view not found"));
        repository.delete(view);
    }

    private Map<String, Object> toDto(TaskAnalyticsView view) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", view.getId());
        dto.put("name", view.getName());
        dto.put("filters", readFilters(view.getFiltersJson()));
        dto.put("created_at", view.getCreatedAt() != null ? view.getCreatedAt().toString() : null);
        dto.put("updated_at", view.getUpdatedAt() != null ? view.getUpdatedAt().toString() : null);
        return dto;
    }

    private Map<String, Object> normalizeFilters(Map<String, Object> rawFilters) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (rawFilters == null) {
            return normalized;
        }
        for (String key : ALLOWED_FILTERS) {
            Object rawValue = rawFilters.get(key);
            if (rawValue == null) {
                continue;
            }
            String value = String.valueOf(rawValue).trim();
            if (value.isEmpty()) {
                continue;
            }
            if (value.length() > 255) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Analytics filter value is too long: " + key);
            }
            normalized.put(key, value);
        }
        return normalized;
    }

    private String writeFilters(Map<String, Object> filters) {
        try {
            return objectMapper.writeValueAsString(filters);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize analytical view filters", ex);
        }
    }

    private Map<String, Object> readFilters(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
            return normalizeFilters(parsed);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to read analytical view filters", ex);
        }
    }

    private String normalizeOwner(String ownerIdentity) {
        if (!StringUtils.hasText(ownerIdentity)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user is required");
        }
        return ownerIdentity.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeName(String rawName) {
        if (!StringUtils.hasText(rawName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "View name is required");
        }
        String name = rawName.trim();
        if (name.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "View name is too long");
        }
        return name;
    }
}
