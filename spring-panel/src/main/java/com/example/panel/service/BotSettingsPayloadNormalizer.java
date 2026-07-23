package com.example.panel.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BotSettingsPayloadNormalizer {

    public Map<String, Object> normalize(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> botSettings = new LinkedHashMap<>();
        map.forEach((key, value) -> {
            if (key != null) {
                botSettings.put(key.toString(), value);
            }
        });

        List<Map<String, Object>> questionTemplates = normalizeQuestionTemplateList(botSettings.get("question_templates"));
        if (!questionTemplates.isEmpty()) {
            botSettings.put("question_templates", questionTemplates);
        }

        Map<String, Object> activeQuestionTemplate = resolveActiveTemplate(
                questionTemplates,
                stringValue(botSettings.get("active_template_id"))
        );
        if (activeQuestionTemplate != null) {
            botSettings.put("active_template_id", stringValue(activeQuestionTemplate.get("id")));
        }
        botSettings.remove("question_flow");

        List<Map<String, Object>> ratingTemplates = normalizeRatingTemplateList(botSettings.get("rating_templates"));
        if (!ratingTemplates.isEmpty()) {
            botSettings.put("rating_templates", ratingTemplates);
        }

        Map<String, Object> activeRatingTemplate = resolveActiveTemplate(
                ratingTemplates,
                stringValue(botSettings.get("active_rating_template_id"))
        );
        if (activeRatingTemplate != null) {
            botSettings.put("active_rating_template_id", stringValue(activeRatingTemplate.get("id")));
        }
        botSettings.remove("rating_system");
        normalizeCooldown(botSettings);

        return botSettings;
    }

    private void normalizeCooldown(Map<String, Object> botSettings) {
        Integer cooldown = parseInteger(botSettings.get("unblock_request_cooldown_minutes"));
        if (cooldown == null) {
            cooldown = parseInteger(botSettings.get("unblockRequestCooldownMinutes"));
        }

        if (cooldown != null) {
            cooldown = Math.max(0, cooldown);
            botSettings.put("unblock_request_cooldown_minutes", cooldown);
            botSettings.remove("unblockRequestCooldownMinutes");
            return;
        }

        botSettings.remove("unblockRequestCooldownMinutes");
    }

    private List<Map<String, Object>> normalizeQuestionTemplateList(Object rawTemplates) {
        List<Map<String, Object>> templates = normalizeTemplateList(rawTemplates);
        for (Map<String, Object> template : templates) {
            if (!template.containsKey("question_flow") && template.get("questions") instanceof List<?>) {
                template.put("question_flow", template.get("questions"));
            }
        }
        return templates;
    }

    private List<Map<String, Object>> normalizeRatingTemplateList(Object rawTemplates) {
        return normalizeTemplateList(rawTemplates);
    }

    private List<Map<String, Object>> normalizeTemplateList(Object rawTemplates) {
        List<Map<String, Object>> templates = new ArrayList<>();
        if (!(rawTemplates instanceof List<?> list)) {
            return templates;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> template = new LinkedHashMap<>();
            map.forEach((key, value) -> {
                if (key != null) {
                    template.put(key.toString(), value);
                }
            });
            templates.add(template);
        }
        return templates;
    }

    private Map<String, Object> resolveActiveTemplate(List<Map<String, Object>> templates,
                                                      String requestedId) {
        if (templates.isEmpty()) {
            return null;
        }
        if (StringUtils.hasText(requestedId)) {
            for (Map<String, Object> template : templates) {
                if (requestedId.equals(stringValue(template.get("id")))) {
                    return template;
                }
            }
        }
        return templates.get(0);
    }

    private String stringValue(Object rawValue) {
        return rawValue != null ? rawValue.toString().trim() : "";
    }

    private Integer parseInteger(Object rawValue) {
        if (rawValue instanceof Number number) {
            return number.intValue();
        }
        String value = stringValue(rawValue);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
