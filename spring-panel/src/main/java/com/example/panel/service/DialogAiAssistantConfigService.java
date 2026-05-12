package com.example.panel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class DialogAiAssistantConfigService {

    private static final Logger log = LoggerFactory.getLogger(DialogAiAssistantConfigService.class);
    private static final double AUTO_REPLY_THRESHOLD_DEFAULT = 0.62d;
    private static final double SUGGEST_THRESHOLD_DEFAULT = 0.46d;
    private static final double DIFFERENCE_THRESHOLD_DEFAULT = 0.42d;
    private static final int MAX_AUTO_REPLIES_PER_DIALOG_DEFAULT = 3;
    private static final int AUTO_REPLY_WINDOW_MINUTES_DEFAULT = 60;
    private static final int AUTO_REPLY_COOLDOWN_SECONDS_DEFAULT = 90;
    private static final String MODE_AUTO_REPLY = "auto_reply";
    private static final String MODE_ASSIST_ONLY = "assist_only";
    private static final String MODE_ESCALATE_ONLY = "escalate_only";

    private final JdbcTemplate jdbcTemplate;
    private final SharedConfigService sharedConfigService;
    private final DialogAiAssistantPersistenceService persistenceService;

    public DialogAiAssistantConfigService(JdbcTemplate jdbcTemplate,
                                          SharedConfigService sharedConfigService,
                                          DialogAiAssistantPersistenceService persistenceService) {
        this.jdbcTemplate = jdbcTemplate;
        this.sharedConfigService = sharedConfigService;
        this.persistenceService = persistenceService;
    }

    public boolean isAgentEnabled() {
        try {
            Object dialogConfig = sharedConfigService.loadSettings().get("dialog_config");
            if (dialogConfig instanceof Map<?, ?> map) {
                Object enabled = map.get("ai_agent_enabled");
                if (enabled instanceof Boolean bool) {
                    return bool;
                }
                String normalized = String.valueOf(enabled).trim().toLowerCase(Locale.ROOT);
                return !"false".equals(normalized) && !"0".equals(normalized) && !"off".equals(normalized);
            }
        } catch (Exception ex) {
            log.debug("ai_agent_enabled read failed: {}", ex.getMessage());
        }
        return true;
    }

    public double resolveAutoReplyThreshold() {
        return resolveDialogConfigDouble("ai_agent_auto_reply_threshold", AUTO_REPLY_THRESHOLD_DEFAULT, 0.2d, 0.95d);
    }

    public double resolveSuggestThreshold() {
        return resolveDialogConfigDouble("ai_agent_suggest_threshold", SUGGEST_THRESHOLD_DEFAULT, 0.1d, 0.95d);
    }

    public double resolveDifferenceThreshold() {
        return resolveDialogConfigDouble("ai_agent_difference_threshold", DIFFERENCE_THRESHOLD_DEFAULT, 0.1d, 0.9d);
    }

    public String resolveAgentMode() {
        return resolveDialogConfigString(
                "ai_agent_mode",
                MODE_AUTO_REPLY,
                Set.of(MODE_AUTO_REPLY, MODE_ASSIST_ONLY, MODE_ESCALATE_ONLY)
        );
    }

    public AutoReplyGuard evaluateAutoReplyGuard(String ticketId) {
        int maxReplies = resolveDialogConfigInt("ai_agent_max_auto_replies_per_dialog", MAX_AUTO_REPLIES_PER_DIALOG_DEFAULT, 1, 20);
        int windowMinutes = resolveDialogConfigInt("ai_agent_auto_reply_window_minutes", AUTO_REPLY_WINDOW_MINUTES_DEFAULT, 1, 1440);
        int cooldownSeconds = resolveDialogConfigInt("ai_agent_auto_reply_cooldown_seconds", AUTO_REPLY_COOLDOWN_SECONDS_DEFAULT, 0, 3600);
        String windowExpr = "-" + windowMinutes + " minutes";
        Integer replyCount = null;
        try {
            replyCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM chat_history WHERE ticket_id = ? AND lower(COALESCE(sender,'')) = 'ai_agent' AND datetime(substr(COALESCE(timestamp,''),1,19)) >= datetime('now', ?)",
                    Integer.class,
                    ticketId,
                    windowExpr
            );
        } catch (Exception ignored) {
        }
        if (replyCount != null && replyCount >= maxReplies) {
            return new AutoReplyGuard(false, "limit_reached:" + replyCount + "/" + maxReplies);
        }
        if (cooldownSeconds <= 0) {
            return new AutoReplyGuard(true, null);
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT last_action, updated_at FROM ticket_ai_agent_state WHERE ticket_id = ? LIMIT 1",
                    ticketId
            );
            if (!rows.isEmpty()) {
                String lastAction = persistenceService.trim(persistenceService.safe(rows.get(0).get("last_action")));
                Instant updatedAt = persistenceService.parseInstant(rows.get(0).get("updated_at"));
                if ("auto_reply".equalsIgnoreCase(lastAction) && updatedAt != null) {
                    long elapsed = Duration.between(updatedAt, Instant.now()).getSeconds();
                    if (elapsed >= 0 && elapsed < cooldownSeconds) {
                        return new AutoReplyGuard(false, "cooldown:" + elapsed + "/" + cooldownSeconds + "s");
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return new AutoReplyGuard(true, null);
    }

    private int resolveDialogConfigInt(String key, int fallback, int min, int max) {
        try {
            Object raw = sharedConfigService.loadSettings().get("dialog_config");
            if (!(raw instanceof Map<?, ?> map)) {
                return fallback;
            }
            Object value = map.get(key);
            if (value == null) {
                return fallback;
            }
            int parsed = Integer.parseInt(String.valueOf(value).trim());
            return Math.max(min, Math.min(max, parsed));
        } catch (Exception ex) {
            return fallback;
        }
    }

    private String resolveDialogConfigString(String key, String fallback, Set<String> allowedValues) {
        try {
            Object raw = sharedConfigService.loadSettings().get("dialog_config");
            if (!(raw instanceof Map<?, ?> map)) {
                return fallback;
            }
            Object rawValue = map.get(key);
            if (rawValue == null) {
                return fallback;
            }
            String value = persistenceService.trim(String.valueOf(rawValue));
            if (value == null) {
                return fallback;
            }
            String normalized = value.toLowerCase(Locale.ROOT);
            return allowedValues.contains(normalized) ? normalized : fallback;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private double resolveDialogConfigDouble(String key, double fallback, double min, double max) {
        try {
            Object raw = sharedConfigService.loadSettings().get("dialog_config");
            if (!(raw instanceof Map<?, ?> map)) {
                return fallback;
            }
            Object value = map.get(key);
            if (value == null) {
                return fallback;
            }
            double parsed = Double.parseDouble(String.valueOf(value).trim());
            if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
                return fallback;
            }
            return Math.max(min, Math.min(max, parsed));
        } catch (Exception ex) {
            return fallback;
        }
    }

    public record AutoReplyGuard(boolean allowed, String reason) {
    }
}
