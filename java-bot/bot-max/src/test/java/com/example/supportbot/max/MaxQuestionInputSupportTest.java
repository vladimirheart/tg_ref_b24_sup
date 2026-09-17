package com.example.supportbot.max;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.supportbot.settings.dto.QuestionFlowItemDto;
import com.example.supportbot.settings.dto.QuestionOptionDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class MaxQuestionInputSupportTest {

    @Test
    void resolvesChoiceAnswerByNumberAndCaseInsensitiveText() {
        List<String> options = List.of("Alpha", "Beta", "Gamma");

        assertThat(MaxQuestionInputSupport.resolveChoiceAnswer(" 2 ", options)).isEqualTo("Beta");
        assertThat(MaxQuestionInputSupport.resolveChoiceAnswer(" beta ", options)).isEqualTo("Beta");
        assertThat(MaxQuestionInputSupport.resolveChoiceAnswer(" custom ", options)).isEqualTo("custom");
        assertThat(MaxQuestionInputSupport.resolveChoiceAnswer(null, options)).isEmpty();
    }

    @Test
    void classifiesSelectPresetAndOptionalFreeQuestions() {
        QuestionFlowItemDto select = new QuestionFlowItemDto();
        select.setType("select");
        select.setOptions(List.of(new QuestionOptionDto("a", "Alpha")));

        QuestionFlowItemDto preset = new QuestionFlowItemDto();
        preset.setType("preset");

        QuestionFlowItemDto optionalFree = new QuestionFlowItemDto(
                "note", "text", "Комментарий", 1, null, List.of(), false
        );

        assertThat(MaxQuestionInputSupport.isSelectQuestion(select)).isTrue();
        assertThat(MaxQuestionInputSupport.isChoiceQuestion(select)).isTrue();
        assertThat(MaxQuestionInputSupport.isPresetQuestion(preset)).isTrue();
        assertThat(MaxQuestionInputSupport.isChoiceQuestion(preset)).isTrue();
        assertThat(MaxQuestionInputSupport.isOptionalFreeQuestion(optionalFree)).isTrue();
        assertThat(MaxQuestionInputSupport.isOptionalFreeQuestion(select)).isFalse();
    }

    @Test
    void promptTextPreservesChoiceSkipAndBackGuidance() {
        QuestionFlowItemDto select = new QuestionFlowItemDto();
        select.setType("select");
        select.setText("Выберите вариант");
        select.setOptions(List.of(new QuestionOptionDto("a", "Alpha"), new QuestionOptionDto("b", "Beta")));

        String choicePrompt = MaxQuestionInputSupport.buildQuestionPromptText(
                select, List.of("Alpha", "Beta"), true
        );
        assertThat(choicePrompt)
                .contains("Выберите вариант")
                .contains("1. Alpha")
                .contains("2. Beta")
                .contains("Можно ответить номером")
                .contains("Назад")
                .doesNotContain("Пропустить");

        QuestionFlowItemDto optionalFree = new QuestionFlowItemDto(
                "note", "text", "Комментарий", 1, null, List.of(), false
        );
        assertThat(MaxQuestionInputSupport.buildQuestionPromptText(optionalFree, List.of(), true))
                .contains("Комментарий")
                .contains("Пропустить")
                .contains("Назад");
    }
}
