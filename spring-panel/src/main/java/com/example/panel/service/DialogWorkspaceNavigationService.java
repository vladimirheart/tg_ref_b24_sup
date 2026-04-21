package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DialogWorkspaceNavigationService {

    private final DialogLookupReadService dialogLookupReadService;

    public DialogWorkspaceNavigationService(DialogLookupReadService dialogLookupReadService) {
        this.dialogLookupReadService = dialogLookupReadService;
    }

    public Map<String, Object> buildNavigationMeta(Map<String, Object> settings,
                                                   String operator,
                                                   String currentTicketId) {
        boolean enabled = true;
        if (settings != null && settings.get("dialog_config") instanceof Map<?, ?> dialogConfig) {
            enabled = resolveBooleanDialogConfig(dialogConfig, "workspace_inline_navigation", true);
        }
        List<DialogListItem> dialogs = dialogLookupReadService.loadDialogs(operator);
        List<DialogListItem> navigationItems = dialogs == null
                ? List.of()
                : dialogs.stream()
                .filter(item -> item != null && StringUtils.hasText(item.ticketId()))
                .toList();
        int currentIndex = -1;
        for (int i = 0; i < navigationItems.size(); i++) {
            if (String.valueOf(navigationItems.get(i).ticketId()).equals(currentTicketId)) {
                currentIndex = i;
                break;
            }
        }
        DialogListItem previous = currentIndex > 0 ? navigationItems.get(currentIndex - 1) : null;
        DialogListItem next = currentIndex >= 0 && currentIndex + 1 < navigationItems.size()
                ? navigationItems.get(currentIndex + 1)
                : null;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enabled", enabled);
        payload.put("current_ticket_id", currentTicketId);
        payload.put("found_in_queue", currentIndex >= 0);
        payload.put("position", currentIndex >= 0 ? currentIndex + 1 : null);
        payload.put("total", navigationItems.size());
        payload.put("has_previous", previous != null);
        payload.put("has_next", next != null);
        payload.put("previous", buildNavigationItem(previous));
        payload.put("next", buildNavigationItem(next));
        payload.put("queue_generated_at_utc", Instant.now().toString());
        payload.put("summary", buildNavigationSummary(enabled, currentIndex, navigationItems.size(), previous, next));
        return payload;
    }

    private Map<String, Object> buildNavigationItem(DialogListItem item) {
        if (item == null || !StringUtils.hasText(item.ticketId())) {
            return Map.of();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ticket_id", item.ticketId());
        payload.put("channel_id", item.channelId());
        payload.put("client_name", item.displayClientName());
        payload.put("status", item.statusLabel());
        payload.put("last_message_at_utc", normalizeUtcTimestamp(item.lastMessageTimestamp()));
        return payload;
    }

    private String buildNavigationSummary(boolean enabled,
                                          int currentIndex,
                                          int total,
                                          DialogListItem previous,
                                          DialogListItem next) {
        if (!enabled) {
            return "Inline navigation отключена текущей настройкой rollout.";
        }
        if (currentIndex < 0 || total <= 0) {
            return "Текущий диалог открыт вне активной очереди — inline navigation недоступна.";
        }
        if (previous == null && next == null) {
            return "В очереди только текущий диалог.";
        }
        return "Позиция %d из %d. %s%s".formatted(
                currentIndex + 1,
                total,
                previous != null ? "Есть предыдущий диалог. " : "",
                next != null ? "Есть следующий диалог." : ""
        ).trim();
    }

    private boolean resolveBooleanDialogConfig(Map<?, ?> dialogConfig, String key, boolean fallbackValue) {
        if (dialogConfig == null || key == null) {
            return fallbackValue;
        }
        Object value = dialogConfig.get(key);
        if (value == null) {
            return fallbackValue;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        String normalized = String.valueOf(value).trim().toLowerCase();
        if (!StringUtils.hasText(normalized)) {
            return fallbackValue;
        }
        return switch (normalized) {
            case "1", "true", "yes", "on" -> true;
            case "0", "false", "no", "off" -> false;
            default -> fallbackValue;
        };
    }

    private OffsetDateTime parseUtcTimestamp(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(rawValue).withOffsetSameInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            // fallback to legacy datetime-local without explicit offset
        }
        try {
            return LocalDateTime.parse(rawValue).atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String normalizeUtcTimestamp(Object rawValue) {
        String normalized = rawValue != null ? String.valueOf(rawValue).trim() : null;
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        OffsetDateTime parsed = parseUtcTimestamp(normalized);
        return parsed != null ? parsed.toString() : null;
    }
}
