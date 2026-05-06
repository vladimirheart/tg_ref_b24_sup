package com.example.panel.service;

import com.example.panel.service.SlaRoutingPolicySnapshotDialogStateService.DialogState;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SlaRoutingPolicySnapshotContextService {

    public SlaRoutingPolicySnapshotRuntimeService.SnapshotRuntimeContext missingTicketContext(
            SlaRoutingPolicySnapshotSettingsService.SnapshotSettingsContext settingsContext) {
        return new SlaRoutingPolicySnapshotRuntimeService.SnapshotRuntimeContext(
                settingsContext.dialogConfig(),
                new LinkedHashMap<>(settingsContext.payload()),
                settingsContext.orchestrationEnabled(),
                settingsContext.autoAssignEnabled(),
                settingsContext.webhookEnabled(),
                settingsContext.orchestrationMode(),
                settingsContext.targetMinutes(),
                settingsContext.criticalMinutes(),
                null,
                null,
                null,
                null,
                false,
                false,
                false,
                true
        );
    }

    public SlaRoutingPolicySnapshotRuntimeService.SnapshotRuntimeContext buildDialogContext(
            SlaRoutingPolicySnapshotSettingsService.SnapshotSettingsContext settingsContext,
            String ticketId,
            DialogState dialogState) {
        Map<String, Object> payload = new LinkedHashMap<>(settingsContext.payload());
        payload.put("ticket_id", ticketId);
        if (dialogState.createdAtUtc() != null) {
            payload.put("created_at_utc", dialogState.createdAtUtc());
        }
        if (dialogState.minutesLeft() != null) {
            payload.put("minutes_left", dialogState.minutesLeft());
        }
        payload.put("current_responsible", dialogState.currentResponsible());
        payload.put("effective_include_assigned", dialogState.effectiveIncludeAssigned());
        payload.put("critical", dialogState.critical());

        return new SlaRoutingPolicySnapshotRuntimeService.SnapshotRuntimeContext(
                settingsContext.dialogConfig(),
                payload,
                settingsContext.orchestrationEnabled(),
                settingsContext.autoAssignEnabled(),
                settingsContext.webhookEnabled(),
                settingsContext.orchestrationMode(),
                settingsContext.targetMinutes(),
                settingsContext.criticalMinutes(),
                dialogState.lifecycleState(),
                dialogState.createdAtUtc(),
                dialogState.minutesLeft(),
                dialogState.currentResponsible(),
                dialogState.effectiveIncludeAssigned(),
                dialogState.openLifecycle(),
                dialogState.critical(),
                false
        );
    }
}
