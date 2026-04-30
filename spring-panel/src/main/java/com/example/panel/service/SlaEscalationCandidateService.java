package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SlaEscalationCandidateService {

    public List<Map<String, Object>> findEscalationCandidates(List<DialogListItem> dialogs,
                                                              int targetMinutes,
                                                              int criticalMinutes) {
        return findEscalationCandidates(dialogs, targetMinutes, criticalMinutes, false);
    }

    public List<Map<String, Object>> findEscalationCandidates(List<DialogListItem> dialogs,
                                                              int targetMinutes,
                                                              int criticalMinutes,
                                                              boolean includeAssigned) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (dialogs == null || dialogs.isEmpty()) {
            return result;
        }

        long nowMs = System.currentTimeMillis();
        for (DialogListItem dialog : dialogs) {
            if (dialog == null || dialog.ticketId() == null || dialog.ticketId().isBlank()) {
                continue;
            }
            if (!"open".equals(normalizeLifecycleState(dialog.statusKey()))) {
                continue;
            }
            String responsible = trimToNull(dialog.responsible());
            if (responsible != null && !includeAssigned) {
                continue;
            }
            Long minutesLeft = resolveMinutesLeft(dialog.createdAt(), targetMinutes, nowMs);
            if (minutesLeft == null || minutesLeft > criticalMinutes) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ticket_id", dialog.ticketId());
            row.put("request_number", dialog.requestNumber());
            row.put("client", dialog.displayClientName());
            row.put("minutes_left", minutesLeft);
            row.put("status", dialog.statusLabel());
            row.put("channel", dialog.channelLabel());
            row.put("business", dialog.businessLabel());
            row.put("location", dialog.location());
            row.put("categories", dialog.categories());
            row.put("client_status", dialog.clientStatus());
            row.put("responsible", responsible);
            row.put("unread_count", dialog.unreadCount());
            row.put("rating", dialog.rating());
            row.put("sla_state", minutesLeft < 0 ? "breached" : "at_risk");
            row.put("escalation_scope", responsible == null ? "unassigned" : "assigned");
            result.add(row);
        }
        return result;
    }

    public Long resolveMinutesLeft(String createdAt, int targetMinutes, long nowMs) {
        Instant created = parseInstant(createdAt);
        if (created == null) {
            return null;
        }
        long deadlineMs = created.toEpochMilli() + targetMinutes * 60_000L;
        long diffMs = deadlineMs - nowMs;
        return Math.floorDiv(diffMs, 60_000L);
    }

    public String normalizeUtcTimestamp(String value) {
        Instant parsed = parseInstant(value);
        return parsed != null ? parsed.toString() : null;
    }

    public String normalizeLifecycleState(String statusKey) {
        if (statusKey == null || statusKey.isBlank()) {
            return "open";
        }
        String normalized = statusKey.trim().toLowerCase();
        if (normalized.contains("closed") || normalized.contains("resolved")) {
            return "closed";
        }
        return "open";
    }

    public Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return Instant.parse(trimmed);
        } catch (Exception ignored) {
        }
        try {
            return Instant.parse(trimmed.replace(' ', 'T') + "Z");
        } catch (Exception ignored) {
        }
        try {
            return OffsetDateTime.parse(trimmed).withOffsetSameInstant(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed) ? null : trimmed;
    }
}
