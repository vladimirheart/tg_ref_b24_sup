package com.example.supportbot.max;

import com.example.supportbot.settings.dto.PresetReference;
import com.example.supportbot.settings.dto.QuestionFlowItemDto;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MaxIncidentFlowSupport {

    private static final List<String> CORE_LOCATION_FIELDS =
            List.of("business", "location_type", "city", "location_name");

    private MaxIncidentFlowSupport() {
    }

    static List<QuestionFlowItemDto> normalize(List<QuestionFlowItemDto> configuredFlow) {
        List<QuestionFlowItemDto> source = new ArrayList<>(configuredFlow);
        source.sort(Comparator.comparingInt(QuestionFlowItemDto::getOrder));

        Map<String, QuestionFlowItemDto> byField = new LinkedHashMap<>();
        for (QuestionFlowItemDto item : source) {
            if (item == null || item.getPreset() == null) {
                continue;
            }
            String field = item.getPreset().field();
            String group = item.getPreset().group();
            if (!"locations".equalsIgnoreCase(group) || field == null || field.isBlank()) {
                continue;
            }
            if (CORE_LOCATION_FIELDS.contains(field) && !byField.containsKey(field)) {
                byField.put(field, item);
            }
        }

        List<QuestionFlowItemDto> normalized = new ArrayList<>();
        int order = 1;
        for (String field : CORE_LOCATION_FIELDS) {
            QuestionFlowItemDto existing = byField.get(field);
            String text = existing != null ? existing.getText() : defaultPrompt(field);
            List<String> excluded = existing != null && existing.getExcludedOptions() != null
                    ? existing.getExcludedOptions()
                    : List.of();
            QuestionFlowItemDto question = new QuestionFlowItemDto(
                    field,
                    "preset",
                    (text == null || text.isBlank()) ? defaultPrompt(field) : text,
                    order++,
                    new PresetReference("locations", field),
                    excluded
            );
            if (existing != null) {
                question.setBindingKey(existing.getBindingKey());
                question.setIncludeInDashboard(existing.getIncludeInDashboard());
                question.setRoutes(existing.getRoutes());
            }
            normalized.add(question);
        }

        normalized.add(new QuestionFlowItemDto("problem", "text", "Опишите проблему", order, null, List.of()));
        return normalized;
    }

    private static String defaultPrompt(String field) {
        return switch (field) {
            case "business" -> "Бизнес";
            case "location_type" -> "Тип бизнеса";
            case "city" -> "Город";
            case "location_name" -> "Локация";
            default -> field;
        };
    }
}
