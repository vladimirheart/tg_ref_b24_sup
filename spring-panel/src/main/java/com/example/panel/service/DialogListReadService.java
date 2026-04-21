package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.model.dialog.DialogSummary;
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
        DialogSummary summary = dialogLookupReadService.loadSummary();
        List<DialogListItem> dialogs = dialogLookupReadService.loadDialogs(operator);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", summary);
        payload.put("dialogs", dialogs);
        payload.put("sla_orchestration", buildSlaOrchestration(dialogs));
        payload.put("success", true);

        log.info("Loaded dialogs list payload: {} dialogs, summary stats loaded", dialogs.size());
        return payload;
    }

    private Map<String, Object> buildSlaOrchestration(List<DialogListItem> dialogs) {
        Map<String, Object> settings = sharedConfigService.loadSettings();
        int targetMinutes = dialogSlaRuntimeService.resolveDialogConfigMinutes(settings, "sla_target_minutes", DEFAULT_SLA_TARGET_MINUTES);
        int warningMinutes = Math.min(dialogSlaRuntimeService.resolveDialogConfigMinutes(settings, "sla_warning_minutes", DEFAULT_SLA_WARNING_MINUTES), targetMinutes);
        int criticalMinutes = dialogSlaRuntimeService.resolveDialogConfigMinutes(settings, "sla_critical_minutes", 30);
        boolean escalationEnabled = dialogSlaRuntimeService.resolveDialogConfigBoolean(settings, "sla_critical_escalation_enabled", true);

        Map<String, Object> ticketSignals = new LinkedHashMap<>();
        long nowMs = System.currentTimeMillis();
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
