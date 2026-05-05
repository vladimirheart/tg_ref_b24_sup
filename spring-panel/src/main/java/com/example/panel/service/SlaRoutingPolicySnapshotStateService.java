package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SlaRoutingPolicySnapshotStateService {

    public Map<String, Object> initializeBasePayload(boolean orchestrationEnabled,
                                                     boolean autoAssignEnabled,
                                                     boolean webhookEnabled,
                                                     SlaEscalationWebhookNotifier.SlaOrchestrationMode orchestrationMode,
                                                     Instant evaluatedAt,
                                                     int targetMinutes,
                                                     int criticalMinutes) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enabled", orchestrationEnabled);
        payload.put("mode", orchestrationMode.name().toLowerCase(Locale.ROOT));
        payload.put("evaluated_at_utc", evaluatedAt.toString());
        payload.put("target_minutes", targetMinutes);
        payload.put("critical_minutes", criticalMinutes);
        payload.put("auto_assign_enabled", autoAssignEnabled);
        payload.put("webhook_enabled", webhookEnabled);
        return payload;
    }

    public Map<String, Object> buildTicketMissingPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "attention");
        payload.put("ready", false);
        payload.put("action", "unavailable");
        payload.put("summary", "SLA policy недоступен: не удалось определить тикет.");
        payload.put("issues", List.of("ticket_missing"));
        return payload;
    }

    public Map<String, Object> buildDisabledPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "disabled");
        payload.put("ready", true);
        payload.put("action", "disabled");
        payload.put("summary", "SLA orchestration выключен настройкой: текущий поток не меняется.");
        payload.put("issues", List.of());
        return payload;
    }

    public Map<String, Object> buildInvalidUtcPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "invalid_utc");
        payload.put("ready", false);
        payload.put("action", "attention");
        payload.put("summary", "SLA policy не может быть рассчитан: дата создания пуста или невалидна для UTC.");
        payload.put("issues", List.of("created_at_invalid_utc"));
        return payload;
    }

    public Map<String, Object> buildNotApplicablePayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "ready");
        payload.put("ready", true);
        payload.put("action", "not_applicable");
        payload.put("summary", "SLA policy не применяется: диалог уже не в open lifecycle.");
        payload.put("issues", List.of());
        return payload;
    }

    public Map<String, Object> buildMonitorPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "ready");
        payload.put("ready", true);
        payload.put("action", "monitor");
        payload.put("summary", "Диалог под SLA-наблюдением, но ещё вне критичного окна policy.");
        payload.put("issues", List.of());
        return payload;
    }
}
