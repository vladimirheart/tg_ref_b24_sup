package com.example.panel.model.dialog;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Bounds apply to every caller, including legacy GETs without pagination parameters. */
public record DialogListQuery(int page, int pageSize, String search, String status,
                              String view, Integer slaWindowMinutes, String sortMode,
                              List<String> snoozedTicketIds, List<String> pinnedTicketIds) {
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;
    private static final Set<String> VIEWS = Set.of("all", "active", "new", "unassigned", "overdue",
            "sla_critical", "escalation_required");

    public DialogListQuery {
        page = Math.max(1, page);
        pageSize = pageSize <= 0 ? DEFAULT_SIZE : Math.min(MAX_SIZE, pageSize);
        search = normalize(search);
        status = normalize(status);
        view = normalize(view);
        if (!VIEWS.contains(view)) view = "all";
        sortMode = "sla_priority".equals(sortMode) ? "sla_priority" : "default";
        slaWindowMinutes = slaWindowMinutes != null && slaWindowMinutes > 0
                ? Math.min(slaWindowMinutes, 525600) : null;
        snoozedTicketIds = snoozedTicketIds == null ? List.of() : snoozedTicketIds;
        pinnedTicketIds = pinnedTicketIds == null ? List.of() : pinnedTicketIds;
        if (search.length() > 500 || status.length() > 100 || snoozedTicketIds.size() > 1000
                || pinnedTicketIds.size() > 100
                || java.util.stream.Stream.concat(snoozedTicketIds.stream(), pinnedTicketIds.stream())
                    .anyMatch(id -> id == null || id.length() > 255)) {
            throw new IllegalArgumentException("Слишком большой запрос фильтра диалогов");
        }
        snoozedTicketIds = List.copyOf(snoozedTicketIds);
        pinnedTicketIds = List.copyOf(pinnedTicketIds);
    }

    public DialogListQuery(int page, int pageSize, String search, String status, String view,
                           Integer slaWindowMinutes, String sortMode, List<String> snoozedTicketIds) {
        this(page, pageSize, search, status, view, slaWindowMinutes, sortMode, snoozedTicketIds, List.of());
    }

    public static DialogListQuery defaults() {
        return new DialogListQuery(1, DEFAULT_SIZE, "", "", "all", null, "default", List.of());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
