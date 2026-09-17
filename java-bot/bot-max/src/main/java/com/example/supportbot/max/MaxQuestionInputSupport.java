package com.example.supportbot.max;

import com.example.supportbot.settings.dto.QuestionFlowItemDto;
import java.util.List;
import java.util.Optional;

final class MaxQuestionInputSupport {

    static final String SKIP_BUTTON = "Пропустить";
    static final String BACK_BUTTON = "Назад";

    private MaxQuestionInputSupport() {
    }

    static boolean isPresetQuestion(QuestionFlowItemDto current) {
        if (current == null) {
            return false;
        }
        if ("preset".equalsIgnoreCase(current.getType())) {
            return true;
        }
        return current.getPreset() != null && current.getPreset().field() != null;
    }

    static boolean isSelectQuestion(QuestionFlowItemDto current) {
        return current != null
                && "select".equalsIgnoreCase(Optional.ofNullable(current.getType()).orElse(""))
                && current.getOptions() != null
                && !current.getOptions().isEmpty();
    }

    static boolean isChoiceQuestion(QuestionFlowItemDto current) {
        return isPresetQuestion(current) || isSelectQuestion(current);
    }

    static boolean isOptionalFreeQuestion(QuestionFlowItemDto current) {
        return current != null && !isChoiceQuestion(current) && !current.isRequiredAnswer();
    }

    static String buildQuestionPromptText(QuestionFlowItemDto current, List<String> options, boolean includeBack) {
        StringBuilder text = new StringBuilder(Optional.ofNullable(current.getText()).orElse(""));
        if (options != null && !options.isEmpty()) {
            text.append("\n\nВарианты:");
            for (int i = 0; i < options.size(); i++) {
                text.append("\n").append(i + 1).append(". ").append(options.get(i));
            }
            text.append("\nМожно ответить номером (1, 2, ...) или текстом варианта.");
        }
        if (isOptionalFreeQuestion(current)) {
            text.append("\n\nМожно пропустить вопрос: отправьте \"").append(SKIP_BUTTON).append("\".");
        }
        if (includeBack) {
            text.append("\n\nЧтобы вернуться к предыдущему вопросу, отправьте \"").append(BACK_BUTTON).append("\".");
        }
        return text.toString();
    }

    static String resolveChoiceAnswer(String rawAnswer, List<String> options) {
        if (rawAnswer == null) {
            return "";
        }
        String trimmed = rawAnswer.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        try {
            int numeric = Integer.parseInt(trimmed);
            if (numeric >= 1 && numeric <= options.size()) {
                return options.get(numeric - 1);
            }
        } catch (NumberFormatException ignored) {
            // fallback to text matching
        }
        for (String option : options) {
            if (option.equalsIgnoreCase(trimmed)) {
                return option;
            }
        }
        return trimmed;
    }
}
