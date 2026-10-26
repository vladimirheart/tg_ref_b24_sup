package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.model.dialog.DialogMyDialogs;
import com.example.panel.model.dialog.DialogSummary;
import com.example.panel.model.dialog.DialogListQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DialogListReadService {

    private static final Logger log = LoggerFactory.getLogger(DialogListReadService.class);

    private static final int DEFAULT_SLA_TARGET_MINUTES = 24 * 60;
    private static final int DEFAULT_SLA_WARNING_MINUTES = 4 * 60;

    private final DialogLookupReadService dialogLookupReadService;
    private final SharedConfigService sharedConfigService;
    private final DialogSlaRuntimeService dialogSlaRuntimeService;

    public DialogListReadService(DialogLookupReadService dialogLookupReadService,
                                 SharedConfigService sharedConfigService,
                                 DialogSlaRuntimeService dialogSlaRuntimeService) {
        this.dialogLookupReadService = dialogLookupReadService;
        this.sharedConfigService = sharedConfigService;
        this.dialogSlaRuntimeService = dialogSlaRuntimeService;
    }

    public Map<String, Object> loadListPayload(String operator) {
        return loadListPayload(operator, DialogListQuery.defaults(), true);
    }

    public Map<String, Object> loadListPayload(String operator, DialogListQuery query, boolean includeOverview) {
        Map<String, Object> settings = sharedConfigService.loadSettings();
        var policy = resolvePolicy(settings);
        var page = dialogLookupReadService.loadDialogsPage(operator, query, policy);
        List<DialogListItem> dialogs = page.dialogs();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dialogs", dialogs);
        payload.put("pagination", Map.of("page", page.page(), "page_size", page.pageSize(),
                "total_items", page.totalItems(), "total_pages", page.totalPages()));
        if (includeOverview) {
            DialogSummary summary = dialogLookupReadService.loadSummary();
            var preview = dialogLookupReadService.loadMyDialogsPreview(operator, policy, query);
            DialogMyDialogs myDialogs = preview.dialogs();
            payload.put("summary", summary);
            payload.put("my_dialogs", Map.of("new", myDialogs.newUnassigned(),
                    "unanswered", myDialogs.unanswered(), "in_work", myDialogs.inWork(),
                    "counts", preview.counts(), "limit", 20));
        }
        payload.put("sla_orchestration", buildSlaOrchestration(dialogs, settings, policy.nowMs()));
        payload.put("success", true);

        log.info("Loaded dialogs list payload: {} dialogs, summary stats loaded", dialogs.size());
        return payload;
    }

    private DialogListPageSql.Policy resolvePolicy(Map<String, Object> settings) {
        int target = dialogSlaRuntimeService.resolveDialogConfigMinutes(settings, "sla_target_minutes", DEFAULT_SLA_TARGET_MINUTES);
        return new DialogListPageSql.Policy(target,
                Math.min(dialogSlaRuntimeService.resolveDialogConfigMinutes(settings, "sla_warning_minutes", DEFAULT_SLA_WARNING_MINUTES), target),
                Math.min(dialogSlaRuntimeService.resolveDialogConfigMinutes(settings, "sla_critical_minutes", 30), target),
                Math.min(dialogSlaRuntimeService.resolveDialogConfigMinutes(settings, "overdue_threshold_hours", 24), 168),
                dialogSlaRuntimeService.resolveDialogConfigBoolean(settings, "sla_critical_escalation_enabled", true),
                dialogSlaRuntimeService.resolveDialogConfigBoolean(settings, "sla_critical_pin_unassigned_only", false),
                dialogSlaRuntimeService.resolveDialogConfigBoolean(settings, "sla_critical_view_unassigned_only", false),
                System.currentTimeMillis());
    }

    private Map<String, Object> buildSlaOrchestration(List<DialogListItem> dialogs, Map<String, Object> settings, long nowMs) {
        int targetMinutes = dialogSlaRuntimeService.resolveDialogConfigMinutes(settings, "sla_target_minutes", DEFAULT_SLA_TARGET_MINUTES);
        int warningMinutes = Math.min(dialogSlaRuntimeService.resolveDialogConfigMinutes(settings, "sla_warning_minutes", DEFAULT_SLA_WARNING_MINUTES), targetMinutes);
        int criticalMinutes = Math.min(dialogSlaRuntimeService.resolveDialogConfigMinutes(settings, "sla_critical_minutes", 30), targetMinutes);
        boolean escalationEnabled = dialogSlaRuntimeService.resolveDialogConfigBoolean(settings, "sla_critical_escalation_enabled", true);

        Map<String, Object> ticketSignals = new LinkedHashMap<>();
        for (DialogListItem dialog : dialogs) {
            String ticketId = dialog.ticketId();
            if (ticketId == null || ticketId.isBlank()) {
                continue;
            }
            String statusKey = dialog.statusKey();
            String state = dialogSlaRuntimeService.resolveSlaState(dialog.createdAt(), targetMinutes, warningMinutes, statusKey, nowMs);
            Long minutesLeft = dialogSlaRuntimeService.resolveSlaMinutesLeft(dialog.createdAt(), targetMinutes, statusKey, nowMs);
            boolean critical = escalationEnabled && "open".equals(dialogSlaRuntimeService.normalizeSlaLifecycleState(statusKey))
                    && minutesLeft != null && minutesLeft <= criticalMinutes;
            boolean assigned = dialog.responsible() != null && !dialog.responsible().isBlank();

            Map<String, Object> signal = new LinkedHashMap<>();
            signal.put("state", state);
            signal.put("minutes_left", minutesLeft);
            signal.put("is_critical", critical);
            signal.put("auto_pin", critical);
            signal.put("escalation_required", critical && !assigned);
            signal.put("escalation_reason", critical && !assigned ? "critical_sla_unassigned" : null);
            ticketSignals.put(ticketId, signal);
        }

        return Map.of(
                "enabled", escalationEnabled,
                "target_minutes", targetMinutes,
                "warning_minutes", warningMinutes,
                "critical_minutes", criticalMinutes,
                "generated_at", Instant.ofEpochMilli(nowMs).toString(),
                "tickets", ticketSignals
        );
    }

}
