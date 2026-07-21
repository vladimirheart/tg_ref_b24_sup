package com.example.panel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class BotSettingsPayloadNormalizer {

    private static final Logger logger = LoggerFactory.getLogger(BotSettingsPayloadNormalizer.class);

    private static final String IMPORTED_QUESTION_TEMPLATE_ID = "template-imported";
    private static final String IMPORTED_QUESTION_TEMPLATE_NAME = "Импортированный сценарий";
    private static final String IMPORTED_RATING_TEMPLATE_ID = "rating-template-imported";
    private static final String IMPORTED_RATING_TEMPLATE_NAME = "Импортированный шаблон оценок";

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
        if (questionTemplates.isEmpty() && botSettings.get("question_flow") instanceof List<?>) {
            Map<String, Object> importedTemplate = buildImportedQuestionTemplate(botSettings);
            if (!importedTemplate.isEmpty()) {
                questionTemplates.add(importedTemplate);
                botSettings.put("question_templates", questionTemplates);
                logger.info("Imported deprecated bot_settings.question_flow into canonical bot_settings.question_templates for panel/runtime bootstrap");
            }
        } else if (!questionTemplates.isEmpty()) {
            botSettings.put("question_templates", questionTemplates);
        }

        Map<String, Object> activeQuestionTemplate = resolveActiveTemplate(
                questionTemplates,
                stringValue(botSettings.get("active_template_id"))
        );
        if (activeQuestionTemplate != null) {
            botSettings.put("active_template_id", stringValue(activeQuestionTemplate.get("id")));
            if (botSettings.containsKey("question_flow")) {
                Object derivedQuestionFlow = activeQuestionTemplate.get("question_flow");
                if (!Objects.equals(botSettings.get("question_flow"), derivedQuestionFlow)) {
                    logger.warn(
                            "Dropping deprecated bot_settings.question_flow payload because active template {} is canonical source of truth",
                            botSettings.get("active_template_id")
                    );
                } else {
                    logger.info("Dropping deprecated bot_settings.question_flow mirror in favor of canonical question_templates");
                }
            }
        }
        botSettings.remove("question_flow");

        List<Map<String, Object>> ratingTemplates = normalizeRatingTemplateList(botSettings.get("rating_templates"));
        if (ratingTemplates.isEmpty() && botSettings.get("rating_system") instanceof Map<?, ?>) {
            Map<String, Object> importedTemplate = buildImportedRatingTemplate(botSettings);
            if (!importedTemplate.isEmpty()) {
                ratingTemplates.add(importedTemplate);
                botSettings.put("rating_templates", ratingTemplates);
                logger.info("Imported deprecated bot_settings.rating_system into canonical bot_settings.rating_templates for panel/runtime bootstrap");
            }
        } else if (!ratingTemplates.isEmpty()) {
            botSettings.put("rating_templates", ratingTemplates);
        }

        Map<String, Object> activeRatingTemplate = resolveActiveTemplate(
                ratingTemplates,
                stringValue(botSettings.get("active_rating_template_id"))
        );
        if (activeRatingTemplate != null) {
            botSettings.put("active_rating_template_id", stringValue(activeRatingTemplate.get("id")));
            Map<String, Object> derivedRatingSystem = new LinkedHashMap<>();
            copyFieldIfPresent(activeRatingTemplate, derivedRatingSystem, "prompt_text");
            copyFieldIfPresent(activeRatingTemplate, derivedRatingSystem, "scale_size");
            copyFieldIfPresent(activeRatingTemplate, derivedRatingSystem, "responses");
            if (botSettings.containsKey("rating_system")) {
                if (!derivedRatingSystem.isEmpty()
                        && !Objects.equals(botSettings.get("rating_system"), derivedRatingSystem)) {
                    logger.warn(
                            "Dropping deprecated bot_settings.rating_system payload because active rating template {} is canonical source of truth",
                            botSettings.get("active_rating_template_id")
                    );
                } else {
                    logger.info("Dropping deprecated bot_settings.rating_system mirror in favor of canonical rating_templates");
                }
            }
        }
        botSettings.remove("rating_system");

        return botSettings;
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

    private Map<String, Object> buildImportedQuestionTemplate(Map<String, Object> botSettings) {
        if (!(botSettings.get("question_flow") instanceof List<?>)) {
            return Map.of();
        }
        Map<String, Object> template = new LinkedHashMap<>();
        String requestedId = stringValue(botSettings.get("active_template_id"));
        template.put("id", StringUtils.hasText(requestedId) ? requestedId : IMPORTED_QUESTION_TEMPLATE_ID);
        template.put("name", IMPORTED_QUESTION_TEMPLATE_NAME);
        template.put("question_flow", botSettings.get("question_flow"));
        String startMessage = stringValue(botSettings.get("start_message"));
        if (!StringUtils.hasText(startMessage)) {
            startMessage = stringValue(botSettings.get("startMessage"));
        }
        if (StringUtils.hasText(startMessage)) {
            template.put("start_message", startMessage);
        }
        return template;
    }

    private Map<String, Object> buildImportedRatingTemplate(Map<String, Object> botSettings) {
        if (!(botSettings.get("rating_system") instanceof Map<?, ?> rawRatingSystem)) {
            return Map.of();
        }
        Map<String, Object> ratingSystem = new LinkedHashMap<>();
        rawRatingSystem.forEach((key, value) -> {
            if (key != null) {
                ratingSystem.put(key.toString(), value);
            }
        });
        Map<String, Object> template = new LinkedHashMap<>();
        String requestedId = stringValue(botSettings.get("active_rating_template_id"));
        template.put("id", StringUtils.hasText(requestedId) ? requestedId : IMPORTED_RATING_TEMPLATE_ID);
        template.put("name", IMPORTED_RATING_TEMPLATE_NAME);
        copyFirstPresent(ratingSystem, template, "prompt_text", "promptText", "prompt");
        copyFirstPresent(ratingSystem, template, "scale_size", "scaleSize", "scale");
        copyFieldIfPresent(ratingSystem, template, "responses");
        return template;
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

    private void copyFieldIfPresent(Map<String, Object> source,
                                    Map<String, Object> target,
                                    String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    private void copyFirstPresent(Map<String, Object> source,
                                  Map<String, Object> target,
                                  String targetKey,
                                  String... candidateKeys) {
        for (String candidateKey : candidateKeys) {
            if (source.containsKey(candidateKey)) {
                target.put(targetKey, source.get(candidateKey));
                return;
            }
        }
    }

    private String stringValue(Object rawValue) {
        return rawValue != null ? rawValue.toString().trim() : "";
    }
}
