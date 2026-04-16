package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AiDecisionService {

    public Decision evaluateCandidateDecision(String mode,
                                              double topScore,
                                              double suggestThreshold,
                                              double autoReplyThreshold,
                                              boolean autoReplyBlocked,
                                              boolean sourceEligibleForAutoReply) {
        String normalizedMode = normalizeMode(mode);
        if ("escalate_only".equals(normalizedMode)) {
            return new Decision(
                    DecisionAction.ESCALATE,
                    "escalate",
                    "mode_escalate_only",
                    "escalated",
                    "Mode escalate_only is enabled",
                    "7_auto_reply_allowed",
                    "blocked_by_mode"
            );
        }
        if (topScore < suggestThreshold) {
            return new Decision(
                    DecisionAction.ESCALATE,
                    "escalate",
                    "below_suggest_threshold",
                    "low_confidence",
                    "Low confidence (" + formatScore(topScore) + ").",
                    "5_confidence",
                    "below_suggest_threshold"
            );
        }
        if ("assist_only".equals(normalizedMode) || topScore < autoReplyThreshold || autoReplyBlocked || !sourceEligibleForAutoReply) {
            String decisionReason = autoReplyBlocked
                    ? "dialog_override_auto_reply_blocked"
                    : ("assist_only".equals(normalizedMode)
                    ? "mode_assist_only"
                    : (!sourceEligibleForAutoReply ? "untrusted_source_for_auto_reply" : "below_auto_reply_threshold"));
            return new Decision(
                    DecisionAction.SUGGEST_ONLY,
                    "suggest_only",
                    decisionReason,
                    "suggest_only",
                    null,
                    "7_auto_reply_allowed",
                    "suggest_only"
            );
        }
        return new Decision(
                DecisionAction.AUTO_REPLY,
                "auto_reply",
                "score_above_threshold",
                "auto_replied",
                null,
                "7_auto_reply_allowed",
                "auto_reply"
        );
    }

    private String normalizeMode(String mode) {
        String normalized = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "auto_reply", "assist_only", "escalate_only" -> normalized;
            default -> "auto_reply";
        };
    }

    private String formatScore(double score) {
        return String.format(Locale.ROOT, "%.2f", Math.max(0d, Math.min(1d, score)));
    }

    public enum DecisionAction {
        AUTO_REPLY,
        SUGGEST_ONLY,
        ESCALATE
    }

    public record Decision(DecisionAction action,
                           String decisionType,
                           String decisionReason,
                           String processingAction,
                           String detail,
                           String policyStage,
                           String policyOutcome) {
    }
}
