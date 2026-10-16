package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.supportbot.settings.dto.QuestionFlowItemDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeneralQuestionnaireFlowTest {

    @Test
    void preservesArbitraryQuestionSetInConfiguredOrderWithoutBusinessOrProblemDefaults() {
        var second = new QuestionFlowItemDto("contact", "text", "Контакт", 2, null, List.of());
        var first = new QuestionFlowItemDto("topic", "select", "Тема", 1, null, List.of());

        assertThat(GeneralQuestionnaireFlow.from(List.of(second, first)))
            .extracting(QuestionFlowItemDto::getId).containsExactly("topic", "contact");
    }

    @Test
    void emptyGeneralQuestionnaireFailsClosed() {
        assertThatThrownBy(() -> GeneralQuestionnaireFlow.from(List.of()))
            .isInstanceOf(IllegalStateException.class);
    }
}
