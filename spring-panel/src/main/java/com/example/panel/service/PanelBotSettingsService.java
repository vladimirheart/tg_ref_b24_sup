package com.example.panel.service;

import com.example.panel.entity.Channel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PanelBotSettingsService {

    private static final String DEFAULT_RATING_PROMPT = "Оцените заявку {ticket_id} по шкале 1-{scale}";

    private final SharedConfigService sharedConfigService;
    private final BotSettingsPayloadNormalizer botSettingsPayloadNormalizer;

    public PanelBotSettingsService(SharedConfigService sharedConfigService,
                                   BotSettingsPayloadNormalizer botSettingsPayloadNormalizer) {
        this.sharedConfigService = sharedConfigService;
        this.botSettingsPayloadNormalizer = botSettingsPayloadNormalizer;
    }

    public RatingPromptTemplate resolveRatingPromptTemplate(Channel channel) {
        Map<String, Object> settings = new LinkedHashMap<>(sharedConfigService.loadSettings());
        Map<String, Object> botSettings = botSettingsPayloadNormalizer.normalize(settings.get("bot_settings"));
        List<Map<String, Object>> templates = castTemplateList(botSettings.get("rating_templates"));
        if (templates.isEmpty()) {
            return new RatingPromptTemplate(DEFAULT_RATING_PROMPT, 5);
        }

        Map<String, Object> selectedTemplate = null;
        String channelTemplateId = channel != null ? stringValue(channel.getRatingTemplateId()) : "";
        if (StringUtils.hasText(channelTemplateId)) {
            selectedTemplate = findTemplateById(templates, channelTemplateId);
        }
        if (selectedTemplate == null) {
            selectedTemplate = findTemplateById(templates, stringValue(botSettings.get("active_rating_template_id")));
        }
        if (selectedTemplate == null) {
            selectedTemplate = templates.get(0);
        }

        int scale = resolveRatingScale(selectedTemplate);
        String prompt = firstText(
            selectedTemplate.get("prompt_text"),
            selectedTemplate.get("prompt"),
            selectedTemplate.get("promptText")
        );
        if (!StringUtils.hasText(prompt)) {
            prompt = DEFAULT_RATING_PROMPT;
        }
        return new RatingPromptTemplate(prompt.trim(), scale);
    }

    private List<Map<String, Object>> castTemplateList(Object rawTemplates) {
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

    private Map<String, Object> findTemplateById(List<Map<String, Object>> templates, String templateId) {
        if (!StringUtils.hasText(templateId)) {
            return null;
        }
        for (Map<String, Object> template : templates) {
            if (templateId.equals(stringValue(template.get("id")))) {
                return template;
            }
        }
        return null;
    }

    private int resolveRatingScale(Map<String, Object> template) {
        Integer scale = parseInteger(template != null ? template.get("scale_size") : null);
        if (scale == null) {
            scale = parseInteger(template != null ? template.get("scale") : null);
        }
        if (scale == null && template != null && template.get("responses") instanceof List<?> responses) {
            scale = responses.size();
        }
        return scale != null && scale > 0 ? scale : 5;
    }

    private String firstText(Object... values) {
        if (values == null) {
            return "";
        }
        for (Object value : values) {
            String text = stringValue(value);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return "";
    }

    private Integer parseInteger(Object rawValue) {
        if (rawValue instanceof Number number) {
            return number.intValue();
        }
        String normalized = stringValue(rawValue);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String stringValue(Object rawValue) {
        return rawValue != null ? rawValue.toString().trim() : "";
    }

    public record RatingPromptTemplate(String prompt, int scale) {
    }
}
