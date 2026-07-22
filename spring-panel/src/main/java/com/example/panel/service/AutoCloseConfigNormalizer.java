package com.example.panel.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AutoCloseConfigNormalizer {

    private static final Logger logger = LoggerFactory.getLogger(AutoCloseConfigNormalizer.class);
    private static final int DEFAULT_FALLBACK_HOURS = 24;
    public Map<String, Object> normalize(Object rawConfig) {
        if (!(rawConfig instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> config = new LinkedHashMap<>();
        map.forEach((key, value) -> {
            if (key != null) {
                config.put(key.toString(), value);
            }
        });

        List<Map<String, Object>> templates = normalizeTemplates(config.get("templates"));
        if (templates.isEmpty()) {
            return new LinkedHashMap<>();
        }

        config.put("templates", templates);
        Map<String, Object> activeTemplate = resolveTemplateById(
                templates,
                stringValue(config.get("active_template_id"))
        );
        if (activeTemplate != null) {
            config.put("active_template_id", stringValue(activeTemplate.get("id")));
        } else {
            config.remove("active_template_id");
        }
        return config;
    }

    public int resolveFallbackHours(Object rawConfig) {
        Map<String, Object> normalized = normalize(rawConfig);
        Map<String, Object> activeTemplate = resolveTemplateById(
                normalizeTemplates(normalized.get("templates")),
                stringValue(normalized.get("active_template_id"))
        );
        Integer activeHours = activeTemplate != null ? parseInteger(activeTemplate.get("hours")) : null;
        if (activeHours != null && activeHours > 0) {
            return activeHours;
        }
        return DEFAULT_FALLBACK_HOURS;
    }

    private List<Map<String, Object>> normalizeTemplates(Object rawTemplates) {
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
            Integer hours = canonicalHours(template);
            if (hours == null || hours < 0) {
                continue;
            }
            template.put("hours", hours);
            template.remove("timeout_hours");
            template.remove("auto_close_hours");
            templates.add(template);
        }
        return templates;
    }

    private Integer canonicalHours(Map<String, Object> template) {
        Integer hours = parseInteger(template.get("hours"));
        if (hours != null && hours >= 0) {
            return hours;
        }
        if (template.containsKey("timeout_hours") || template.containsKey("auto_close_hours")) {
            logger.warn("Ignoring deprecated auto-close template hour keys for template {} because canonical key is hours",
                    template.get("id"));
        }
        return null;
    }

    private Map<String, Object> resolveTemplateById(List<Map<String, Object>> templates,
                                                    String templateId) {
        if (templates == null || templates.isEmpty()) {
            return null;
        }
        if (StringUtils.hasText(templateId)) {
            for (Map<String, Object> template : templates) {
                if (Objects.equals(templateId, stringValue(template.get("id")))) {
                    return template;
                }
            }
        }
        return templates.get(0);
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

    private String stringValue(Object rawValue) {
        return rawValue != null ? rawValue.toString().trim() : "";
    }
}
