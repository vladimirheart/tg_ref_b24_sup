package com.example.panel.service;

import com.example.panel.model.dialog.ChatMessageDto;
import com.example.panel.model.dialog.DialogListItem;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DialogWorkspaceParityService {

    public Map<String, Object> buildComposerMeta(DialogListItem summary,
                                                 List<ChatMessageDto> history,
                                                 Map<String, Object> permissions) {
        boolean canReply = permissions != null && Boolean.TRUE.equals(permissions.get("can_reply"));
        boolean hasReplyTargets = history != null
                && history.stream().anyMatch(message -> message != null && message.telegramMessageId() != null);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reply_supported", canReply);
        payload.put("media_supported", canReply);
        payload.put("reply_target_supported", canReply && hasReplyTargets);
        payload.put("draft_supported", true);
        payload.put("channel_id", summary != null ? summary.channelId() : null);
        payload.put("channel_label", summary != null ? summary.channelLabel() : null);
        payload.put("timezone", "UTC");
        return payload;
    }

    public Map<String, Object> buildParityMeta(Set<String> includeSections,
                                               Map<String, Object> workspaceClient,
                                               List<Map<String, Object>> clientHistory,
                                               List<Map<String, Object>> relatedEvents,
                                               Map<String, Object> profileHealth,
                                               Map<String, Object> contextBlocksHealth,
                                               Map<String, Object> permissions,
                                               Map<String, Object> composer,
                                               String slaState,
                                               DialogListItem summary,
                                               Map<String, Object> workspaceRollout) {
        List<Map<String, Object>> checks = new ArrayList<>();
        Instant checkedAt = Instant.now();

        boolean messagesReady = includeSections.contains("messages");
        checks.add(buildParityCheck(
                "messages_timeline",
                "Лента сообщений загружена в workspace",
                messagesReady ? "ok" : "attention",
                messagesReady ? "Основная лента доступна без перехода в legacy modal." : "Контракт workspace запрошен без секции messages.",
                checkedAt
        ));

        boolean customerContextReady = workspaceClient != null && !workspaceClient.isEmpty();
        checks.add(buildParityCheck(
                "customer_context",
                "Контекст клиента доступен в workspace",
                customerContextReady ? "ok" : "attention",
                customerContextReady ? "Карточка клиента доступна в основном workspace-потоке." : "Контекст клиента не загрузился.",
                checkedAt
        ));

        boolean profileReady = !toBoolean(profileHealth != null ? profileHealth.get("enabled") : null)
                || toBoolean(profileHealth.get("ready"));
        checks.add(buildParityCheck(
                "customer_profile_minimum",
                "Минимальный customer profile готов",
                profileReady ? "ok" : "attention",
                profileReady ? "Контекст достаточен для решения без переключения в сторонние экраны." : "Есть обязательные profile gaps — нужен дозаполняющий контекст.",
                checkedAt
        ));

        boolean contextBlocksReady = !toBoolean(contextBlocksHealth != null ? contextBlocksHealth.get("enabled") : null)
                || toBoolean(contextBlocksHealth.get("ready"));
        checks.add(buildParityCheck(
                "customer_context_blocks",
                "Контекстные блоки стандартизированы по приоритету",
                contextBlocksReady ? "ok" : "attention",
                contextBlocksReady
                        ? "Приоритетные блоки customer context готовы и не требуют переключения между экранами."
                        : "Есть обязательные context-block gaps — приоритетные блоки ещё не готовы.",
                checkedAt
        ));

        boolean historyReady = includeSections.contains("context") && clientHistory != null;
        checks.add(buildParityCheck(
                "history_context",
                "История клиента доступна",
                historyReady ? "ok" : "attention",
                historyReady ? "Оператор видит историю клиента в workspace." : "Не удалось загрузить клиентскую историю.",
                checkedAt
        ));

        boolean relatedEventsReady = includeSections.contains("context") && relatedEvents != null;
        checks.add(buildParityCheck(
                "related_events",
                "Связанные события доступны",
                relatedEventsReady ? "ok" : "attention",
                relatedEventsReady ? "Связанные события доступны в правой колонке workspace." : "Связанные события недоступны.",
                checkedAt
        ));

        boolean slaReady = includeSections.contains("sla") && StringUtils.hasText(slaState) && !"unknown".equalsIgnoreCase(slaState);
        checks.add(buildParityCheck(
                "sla_visibility",
                "SLA-контекст доступен",
                slaReady ? "ok" : "attention",
                slaReady ? "SLA состояние и дедлайн доступны в workspace." : "SLA-контекст неполный или недоступен.",
                checkedAt
        ));

        boolean actionControlsReady = permissions != null
                && permissions.get("can_reply") instanceof Boolean
                && permissions.get("can_assign") instanceof Boolean
                && permissions.get("can_close") instanceof Boolean
                && permissions.get("can_snooze") instanceof Boolean;
        checks.add(buildParityCheck(
                "operator_actions",
                "Операторские действия доступны по контракту",
                actionControlsReady ? "ok" : "blocked",
                actionControlsReady ? "Workspace знает, какие действия можно выполнять." : "Права оператора невалидны — parity с legacy неполный.",
                checkedAt
        ));

        boolean replyThreadingReady = composer != null
                && Boolean.TRUE.equals(composer.get("reply_target_supported"))
                && Boolean.TRUE.equals(composer.get("reply_supported"));
        checks.add(buildParityCheck(
                "reply_threading",
                "Ответ на конкретное сообщение доступен в workspace",
                replyThreadingReady ? "ok" : "attention",
                replyThreadingReady ? "Оператор может отвечать на конкретное сообщение без перехода в legacy modal."
                        : "Reply-threading в workspace недоступен или контракт композера неполный.",
                checkedAt
        ));

        boolean mediaReplyReady = composer != null
                && Boolean.TRUE.equals(composer.get("media_supported"))
                && Boolean.TRUE.equals(composer.get("reply_supported"));
        checks.add(buildParityCheck(
                "media_reply",
                "Отправка медиа доступна в workspace",
                mediaReplyReady ? "ok" : "attention",
                mediaReplyReady ? "Медиа-ответы доступны напрямую из workspace composer."
                        : "Медиа-ответы в workspace недоступны по текущему контракту.",
                checkedAt
        ));

        String rolloutMode = workspaceRollout != null ? String.valueOf(workspaceRollout.getOrDefault("mode", "")) : "";
        boolean workspacePrimary = StringUtils.hasText(rolloutMode) && !"legacy_primary".equalsIgnoreCase(rolloutMode);
        checks.add(buildParityCheck(
                "workspace_primary_flow",
                "Workspace остаётся основным рабочим потоком",
                workspacePrimary ? "ok" : "attention",
                workspacePrimary ? "Legacy modal рассматривается как rollback-механизм." : "Legacy modal всё ещё основной режим.",
                checkedAt
        ));

        long okChecks = checks.stream().filter(item -> "ok".equals(item.get("status"))).count();
        long blockedChecks = checks.stream().filter(item -> "blocked".equals(item.get("status"))).count();
        int scorePct = checks.isEmpty() ? 100 : (int) Math.round((okChecks * 100d) / checks.size());
        List<String> missingKeys = checks.stream()
                .filter(item -> !"ok".equals(item.get("status")))
                .map(item -> String.valueOf(item.get("key")))
                .toList();
        List<String> missingLabels = checks.stream()
                .filter(item -> !"ok".equals(item.get("status")))
                .map(item -> String.valueOf(item.get("label")))
                .toList();

        String status;
        if (blockedChecks > 0 || scorePct < 60) {
            status = "blocked";
        } else if (!missingKeys.isEmpty()) {
            status = "attention";
        } else {
            status = "ok";
        }

        String summaryText;
        if ("ok".equals(status)) {
            summaryText = "Workspace покрывает ключевые ежедневные операторские сценарии без видимого parity-gap.";
        } else if ("blocked".equals(status)) {
            summaryText = "Есть критичный parity-gap: часть operator-flow ещё не может считаться production-grade без legacy fallback.";
        } else {
            summaryText = "Есть неполный parity с legacy: workspace покрывает основной поток, но требует дозакрытия нескольких сценариев.";
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("score_pct", Math.max(0, Math.min(100, scorePct)));
        payload.put("checked_at", checkedAt.toString());
        payload.put("summary", summaryText);
        payload.put("ticket_id", summary != null ? summary.ticketId() : null);
        payload.put("missing_capabilities", missingKeys);
        payload.put("missing_labels", missingLabels);
        payload.put("checks", checks);
        return payload;
    }

    private Map<String, Object> buildParityCheck(String key,
                                                 String label,
                                                 String status,
                                                 String detail,
                                                 Instant checkedAt) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("label", label);
        item.put("status", normalizeParityStatus(status));
        item.put("detail", StringUtils.hasText(detail) ? detail.trim() : null);
        item.put("checked_at", checkedAt != null ? checkedAt.toString() : Instant.now().toString());
        return item;
    }

    private String normalizeParityStatus(String status) {
        String normalized = status != null ? status.trim().toLowerCase() : "";
        return switch (normalized) {
            case "ok", "attention", "blocked" -> normalized;
            default -> "attention";
        };
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String normalized = value != null ? String.valueOf(value).trim().toLowerCase() : "";
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        return switch (normalized) {
            case "1", "true", "yes", "on", "ok", "ready" -> true;
            default -> false;
        };
    }
}
