package com.example.panel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class SettingsMacroTemplateService {

    private static final Logger log = LoggerFactory.getLogger(SettingsMacroTemplateService.class);

    private final PermissionService permissionService;

    public SettingsMacroTemplateService(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public MacroNormalizationResult normalizeForSettingsUpdate(Authentication authentication,
                                                               Map<String, Object> dialogConfig,
                                                               Object existingTemplates,
                                                               Object incomingTemplates) {
        boolean canPublishMacros = canPublishDialogMacros(authentication, dialogConfig);
        boolean requireIndependentReview = resolveMacroIndependentReviewRequired(dialogConfig);
        return normalizeMacroTemplates(
                existingTemplates,
                incomingTemplates,
                authentication != null ? authentication.getName() : "system",
                canPublishMacros,
                requireIndependentReview
        );
    }

    private boolean canPublishDialogMacros(Authentication authentication, Map<String, Object> dialogConfig) {
        if (!permissionService.hasAuthority(authentication, "DIALOG_MACRO_PUBLISH")) {
            return false;
        }
        Set<String> allowedRoles = resolveMacroPublishAllowedRoles(dialogConfig);
        return permissionService.hasAnyRole(authentication, allowedRoles);
    }

    private Set<String> resolveMacroPublishAllowedRoles(Map<String, Object> dialogConfig) {
        if (dialogConfig == null) {
            return Set.of();
        }
        Object raw = dialogConfig.get("macro_publish_allowed_roles");
        if (!(raw instanceof List<?> roles)) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (Object roleRaw : roles) {
            String role = String.valueOf(roleRaw).trim();
            if (!role.isBlank()) {
                normalized.add(role);
            }
        }
        return normalized;
    }

    private MacroNormalizationResult normalizeMacroTemplates(Object existingRaw,
                                                             Object incomingRaw,
                                                             String actor,
                                                             boolean canPublishMacros,
                                                             boolean requireIndependentReview) {
        List<Map<String, Object>> existingTemplates = castTemplateList(existingRaw);
        Map<String, Map<String, Object>> existingById = new LinkedHashMap<>();
        for (Map<String, Object> template : existingTemplates) {
            String id = stringValue(template.get("id"));
            if (StringUtils.hasText(id)) {
                existingById.put(id, template);
            }
        }

        List<Map<String, Object>> normalized = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (!(incomingRaw instanceof List<?> incomingTemplates)) {
            return new MacroNormalizationResult(normalized, warnings);
        }

        String normalizedActor = StringUtils.hasText(actor) ? actor : "system";
        String now = Instant.now().toString();
        for (Object candidate : incomingTemplates) {
            if (!(candidate instanceof Map<?, ?> sourceMap)) {
                continue;
            }

            String name = stringValue(sourceMap.get("name"));
            String message = stringValue(sourceMap.get("message"));
            if (!StringUtils.hasText(message)) {
                message = stringValue(sourceMap.get("text"));
            }
            if (!StringUtils.hasText(name) || !StringUtils.hasText(message)) {
                continue;
            }

            String id = stringValue(sourceMap.get("id"));
            if (!StringUtils.hasText(id)) {
                id = "macro_" + UUID.randomUUID();
            }
            Map<String, Object> previous = existingById.get(id);

            List<String> tags = normalizeTemplateTags(sourceMap.get("tags"));
            Map<String, Object> workflow = normalizeMacroWorkflow(sourceMap.get("workflow"), sourceMap);
            String owner = normalizeMacroTemplateOwner(sourceMap.containsKey("owner")
                    ? sourceMap.get("owner")
                    : (previous != null ? previous.get("owner") : null));
            String namespace = normalizeMacroTemplateNamespace(sourceMap.containsKey("namespace")
                    ? sourceMap.get("namespace")
                    : (previous != null ? previous.get("namespace") : null));
            int version = resolveTemplateVersion(previous);
            boolean changedMeaningfully = templateMeaningfullyChanged(previous, name, message, tags)
                    || !Objects.equals(normalizeWorkflowForComparison(previous != null ? previous.get("workflow") : null), workflow);

            boolean previouslyPublished = previous != null && asBoolean(previous.get("published"));
            if (!canPublishMacros && previouslyPublished && changedMeaningfully) {
                warnings.add("Недостаточно прав для изменения опубликованного макроса «"
                        + name
                        + "»: правки сохранены только для пользователей с правом DIALOG_MACRO_PUBLISH.");
                name = stringValue(previous.get("name"));
                message = stringValue(previous.get("message"));
                tags = normalizeTemplateTags(previous.get("tags"));
                workflow = normalizeMacroWorkflow(previous.get("workflow"), previous);
                changedMeaningfully = false;
            }

            if (changedMeaningfully) {
                version += 1;
            }

            String previousUpdatedBy = previous != null ? stringValue(previous.get("updated_by")) : "";
            boolean approvedForPublish = resolveMacroApproval(previous);
            boolean approvalRequested = false;
            if (sourceMap.containsKey("approved_for_publish")) {
                approvalRequested = asBoolean(sourceMap.get("approved_for_publish"));
                approvedForPublish = approvalRequested;
            }
            if (!canPublishMacros) {
                if (sourceMap.containsKey("published") || sourceMap.containsKey("approved_for_publish")) {
                    warnings.add("Недостаточно прав для публикации макросов: изменения статуса публикации для «"
                            + name + "» проигнорированы.");
                }
                approvedForPublish = resolveMacroApproval(previous);
            }
            if (changedMeaningfully) {
                approvedForPublish = false;
            }
            if (requireIndependentReview && changedMeaningfully) {
                if (sourceMap.containsKey("published") || sourceMap.containsKey("approved_for_publish")) {
                    warnings.add("Макрос «" + name + "» требует независимого ревью после изменений: публикация отклонена до подтверждения другим сотрудником.");
                }
                approvedForPublish = false;
            }
            boolean requiresIndependentReview = requireIndependentReview && !changedMeaningfully
                    && StringUtils.hasText(previousUpdatedBy)
                    && previousUpdatedBy.equalsIgnoreCase(normalizedActor);
            if (approvalRequested && requiresIndependentReview) {
                approvedForPublish = false;
                warnings.add("Макрос «" + name + "» требует независимого ревью: подтверждение тем же автором отклонено.");
                log.info("Dialog macro template '{}' approval requires independent reviewer: actor='{}', previous_updated_by='{}'",
                        id,
                        normalizedActor,
                        previousUpdatedBy);
            }

            boolean published = previous != null
                    ? asBoolean(previous.get("published"))
                    : true;
            if (sourceMap.containsKey("published")) {
                published = asBoolean(sourceMap.get("published"));
            }
            if (!canPublishMacros) {
                published = previous != null ? asBoolean(previous.get("published")) : false;
            }
            if (!approvedForPublish) {
                published = false;
            }
            boolean wasPublished = previouslyPublished;
            String previousPublishedAt = previous != null ? stringValue(previous.get("published_at")) : "";
            String previousPublishedBy = previous != null ? stringValue(previous.get("published_by")) : "";
            String publishedAt = published
                    ? (StringUtils.hasText(previousPublishedAt) ? previousPublishedAt : now)
                    : "";
            String publishedBy = published
                    ? (StringUtils.hasText(previousPublishedBy) ? previousPublishedBy : normalizedActor)
                    : "";
            if (!wasPublished && published) {
                publishedAt = now;
                publishedBy = normalizedActor;
            }

            boolean wasApproved = resolveMacroApproval(previous);
            String previousReviewedAt = previous != null ? stringValue(previous.get("reviewed_at")) : "";
            String previousReviewedBy = previous != null ? stringValue(previous.get("reviewed_by")) : "";
            String reviewedAt = approvedForPublish
                    ? (StringUtils.hasText(previousReviewedAt) ? previousReviewedAt : now)
                    : "";
            String reviewedBy = approvedForPublish
                    ? (StringUtils.hasText(previousReviewedBy) ? previousReviewedBy : normalizedActor)
                    : "";
            if (!wasApproved && approvedForPublish) {
                reviewedAt = now;
                reviewedBy = normalizedActor;
            }
            if (changedMeaningfully) {
                reviewedAt = "";
                reviewedBy = "";
            }

            boolean deprecated = sourceMap.containsKey("deprecated")
                    ? asBoolean(sourceMap.get("deprecated"))
                    : (previous != null && asBoolean(previous.get("deprecated")));
            String deprecationReason = deprecated
                    ? normalizeMacroDeprecationReason(sourceMap.containsKey("deprecation_reason")
                    ? sourceMap.get("deprecation_reason")
                    : (previous != null ? previous.get("deprecation_reason") : null))
                    : "";
            boolean wasDeprecated = previous != null && asBoolean(previous.get("deprecated"));
            String previousDeprecatedAt = previous != null ? stringValue(previous.get("deprecated_at")) : "";
            String previousDeprecatedBy = previous != null ? stringValue(previous.get("deprecated_by")) : "";
            String deprecatedAt = deprecated
                    ? (wasDeprecated && StringUtils.hasText(previousDeprecatedAt) ? previousDeprecatedAt : now)
                    : "";
            String deprecatedBy = deprecated
                    ? (wasDeprecated && StringUtils.hasText(previousDeprecatedBy) ? previousDeprecatedBy : normalizedActor)
                    : "";

            Map<String, Object> normalizedTemplate = new LinkedHashMap<>();
            normalizedTemplate.put("id", id);
            normalizedTemplate.put("name", name);
            normalizedTemplate.put("message", message);
            normalizedTemplate.put("text", message);
            normalizedTemplate.put("tags", tags);
            normalizedTemplate.put("owner", StringUtils.hasText(owner) ? owner : null);
            normalizedTemplate.put("namespace", StringUtils.hasText(namespace) ? namespace : null);
            normalizedTemplate.put("workflow", workflow);
            normalizedTemplate.put("assign_to_me", asBoolean(workflow.get("assign_to_me")));
            Object snoozeMinutes = workflow.get("snooze_minutes");
            normalizedTemplate.put("snooze_minutes", snoozeMinutes instanceof Number n ? n.intValue() : null);
            normalizedTemplate.put("close_ticket", asBoolean(workflow.get("close_ticket")));
            normalizedTemplate.put("published", published);
            normalizedTemplate.put("approved_for_publish", approvedForPublish);
            String reviewState = approvedForPublish
                    ? "approved"
                    : ((requireIndependentReview && changedMeaningfully) || requiresIndependentReview
                    ? "pending_peer_review"
                    : "pending_review");
            normalizedTemplate.put("review_state", reviewState);
            normalizedTemplate.put("version", Math.max(1, version));
            normalizedTemplate.put("created_at", previous != null
                    ? stringValue(previous.get("created_at"))
                    : now);
            normalizedTemplate.put("updated_at", now);
            normalizedTemplate.put("updated_by", normalizedActor);
            normalizedTemplate.put("reviewed_at", StringUtils.hasText(reviewedAt) ? reviewedAt : null);
            normalizedTemplate.put("reviewed_by", StringUtils.hasText(reviewedBy) ? reviewedBy : null);
            normalizedTemplate.put("published_at", StringUtils.hasText(publishedAt) ? publishedAt : null);
            normalizedTemplate.put("published_by", StringUtils.hasText(publishedBy) ? publishedBy : null);
            normalizedTemplate.put("deprecated", deprecated);
            normalizedTemplate.put("deprecation_reason", StringUtils.hasText(deprecationReason) ? deprecationReason : null);
            normalizedTemplate.put("deprecated_at", StringUtils.hasText(deprecatedAt) ? deprecatedAt : null);
            normalizedTemplate.put("deprecated_by", StringUtils.hasText(deprecatedBy) ? deprecatedBy : null);

            normalized.add(normalizedTemplate);
        }
        log.info("Dialog macro templates normalized: actor='{}', incoming={}, stored={}, can_publish={}",
                normalizedActor,
                incomingTemplates.size(),
                normalized.size(),
                canPublishMacros);
        return new MacroNormalizationResult(normalized, warnings.stream().distinct().toList());
    }

    private boolean resolveMacroApproval(Map<String, Object> template) {
        if (template == null) {
            return false;
        }
        if (template.containsKey("approved_for_publish")) {
            return asBoolean(template.get("approved_for_publish"));
        }
        return asBoolean(template.get("published"));
    }

    private boolean resolveMacroIndependentReviewRequired(Map<String, Object> dialogConfig) {
        if (dialogConfig == null || !dialogConfig.containsKey("macro_require_independent_review")) {
            return true;
        }
        return asBoolean(dialogConfig.get("macro_require_independent_review"));
    }

    private List<Map<String, Object>> castTemplateList(Object raw) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return result;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                map.forEach((key, value) -> normalized.put(String.valueOf(key), value));
                result.add(normalized);
            }
        }
        return result;
    }

    private List<String> normalizeTemplateTags(Object rawTags) {
        List<String> tags = new ArrayList<>();
        if (!(rawTags instanceof List<?> list)) {
            return tags;
        }
        for (Object tagRaw : list) {
            String tag = stringValue(tagRaw);
            if (StringUtils.hasText(tag) && !tags.contains(tag)) {
                tags.add(tag);
            }
        }
        return tags;
    }

    private String normalizeMacroTemplateOwner(Object rawOwner) {
        String owner = stringValue(rawOwner);
        return StringUtils.hasText(owner) ? owner : "";
    }

    private String normalizeMacroTemplateNamespace(Object rawNamespace) {
        String namespace = stringValue(rawNamespace)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("[-]{2,}", "-")
                .replaceAll("^[.-]+|[.-]+$", "");
        return StringUtils.hasText(namespace) ? namespace : "";
    }

    private String normalizeMacroDeprecationReason(Object rawReason) {
        String reason = stringValue(rawReason);
        return StringUtils.hasText(reason) ? reason : "";
    }

    private Map<String, Object> normalizeWorkflowForComparison(Object rawWorkflow) {
        return normalizeMacroWorkflow(rawWorkflow, Collections.emptyMap());
    }

    private Map<String, Object> normalizeMacroWorkflow(Object rawWorkflow, Map<?, ?> sourceMap) {
        Map<String, Object> workflow = new LinkedHashMap<>();
        boolean assignToMe = false;
        int snoozeMinutes = 0;
        boolean closeTicket = false;

        if (rawWorkflow instanceof Map<?, ?> workflowMap) {
            assignToMe = asBoolean(workflowMap.get("assign_to_me"));
            snoozeMinutes = normalizeWorkflowSnoozeMinutes(workflowMap.get("snooze_minutes"));
            closeTicket = asBoolean(workflowMap.get("close_ticket"));
        }

        assignToMe = assignToMe || asBoolean(sourceMap.get("assign_to_me"));
        closeTicket = closeTicket || asBoolean(sourceMap.get("close_ticket"));
        int fallbackSnooze = normalizeWorkflowSnoozeMinutes(sourceMap.get("snooze_minutes"));
        if (snoozeMinutes <= 0) {
            snoozeMinutes = fallbackSnooze;
        }

        workflow.put("assign_to_me", assignToMe);
        workflow.put("snooze_minutes", snoozeMinutes > 0 ? snoozeMinutes : null);
        workflow.put("close_ticket", closeTicket);
        return workflow;
    }

    private int normalizeWorkflowSnoozeMinutes(Object rawValue) {
        if (rawValue == null) {
            return 0;
        }
        int minutes;
        if (rawValue instanceof Number number) {
            minutes = number.intValue();
        } else {
            String text = stringValue(rawValue);
            if (!StringUtils.hasText(text)) {
                return 0;
            }
            try {
                minutes = Integer.parseInt(text);
            } catch (NumberFormatException ex) {
                return 0;
            }
        }
        return (minutes >= 1 && minutes <= 1440) ? minutes : 0;
    }

    private int resolveTemplateVersion(Map<String, Object> template) {
        if (template == null) {
            return 0;
        }
        Object raw = template.get("version");
        if (raw instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        try {
            return Math.max(1, Integer.parseInt(stringValue(raw)));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private boolean templateMeaningfullyChanged(Map<String, Object> previous,
                                                String name,
                                                String message,
                                                List<String> tags) {
        if (previous == null) {
            return true;
        }
        String previousName = stringValue(previous.get("name"));
        String previousMessage = stringValue(previous.get("message"));
        if (!StringUtils.hasText(previousMessage)) {
            previousMessage = stringValue(previous.get("text"));
        }
        List<String> previousTags = normalizeTemplateTags(previous.get("tags"));
        return !previousName.equals(name)
                || !previousMessage.equals(message)
                || !previousTags.equals(tags);
    }

    private String stringValue(Object rawValue) {
        return rawValue == null ? "" : String.valueOf(rawValue).trim();
    }

    private boolean asBoolean(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw instanceof Number n) {
            return n.intValue() != 0;
        }
        if (raw == null) {
            return false;
        }
        String normalized = String.valueOf(raw).trim();
        return "true".equalsIgnoreCase(normalized) || "1".equals(normalized);
    }

    public record MacroNormalizationResult(List<Map<String, Object>> templates,
                                           List<String> warnings) {
    }
}
