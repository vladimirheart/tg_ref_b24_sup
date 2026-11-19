package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QuestionnaireReusePromptFormatterTest {
    @Test
    void formatsFourFieldsOnSeparateLinesAndNeverQuotesTheOldProblem() {
        String output=QuestionnaireReusePromptFormatter.format(Map.of(
            "business","СушиВёсла","location_type","Корпоративная сеть",
            "city","Адлер","location_name","Голубые дали","problem","Не повторять"
        ));
        assertThat(output).isEqualTo(String.join("\n",
            "Использовать прошлые значения?",
            "Бизнес: СушиВёсла",
            "Тип: Корпоративная сеть",
            "Город: Адлер",
            "Локация: Голубые дали.",
            "Ответьте 'да' или 'нет'."));
        assertThat(output).doesNotContain("Не повторять");
    }
    @Test
    void missingFieldsShowPreviousPlaceholders() {
        assertThat(QuestionnaireReusePromptFormatter.format(Map.of("business","СушиВёсла")))
            .contains("Бизнес: СушиВёсла\nТип: —\nГород: —\nЛокация: —.");
    }
}
