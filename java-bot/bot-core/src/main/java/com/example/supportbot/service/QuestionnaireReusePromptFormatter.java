package com.example.supportbot.service;

import java.util.Map;

/** Shared plain-text reuse question for Telegram, VK and MAX. */
public final class QuestionnaireReusePromptFormatter {
    private QuestionnaireReusePromptFormatter() { }

    public static String format(Map<String, String> answers) {
        Map<String, String> values = answers == null ? Map.of() : answers;
        return "Использовать прошлые значения?\n"
                + String.format("Бизнес: %s\nТип: %s\nГород: %s\nЛокация: %s.\nОтветьте 'да' или 'нет'.",
                values.getOrDefault("business", "—"),
                values.getOrDefault("location_type", "—"),
                values.getOrDefault("city", "—"),
                values.getOrDefault("location_name", "—"));
    }
}
