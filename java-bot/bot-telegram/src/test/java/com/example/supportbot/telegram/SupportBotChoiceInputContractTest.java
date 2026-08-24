package com.example.supportbot.telegram;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SupportBotChoiceInputContractTest {

    @Test
    void choiceQuestionMediaGuidanceIsReadableAndListsAllowedOptions() {
        String message = SupportBot.choiceQuestionMediaGuidance(List.of("1", "2"));

        assertEquals(
                "\u0414\u043b\u044f \u044d\u0442\u043e\u0433\u043e \u0432\u043e\u043f\u0440\u043e\u0441\u0430 \u0432\u044b\u0431\u0435\u0440\u0438\u0442\u0435 \u043e\u0434\u0438\u043d \u0438\u0437 \u0432\u0430\u0440\u0438\u0430\u043d\u0442\u043e\u0432: 1, 2.",
                message
        );
    }
}
