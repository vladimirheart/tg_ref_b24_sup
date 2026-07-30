package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConversationProblemTextSupportTest {

    @Test
    void returnsFinalAnswerWhenBootstrapIsBlank() {
        assertThat(ConversationProblemTextSupport.mergeProblemText("   ", "Касса зависла"))
                .isEqualTo("Касса зависла");
    }

    @Test
    void returnsBootstrapWhenFinalAnswerIsBlank() {
        assertThat(ConversationProblemTextSupport.mergeProblemText("Большое первое сообщение", " "))
                .isEqualTo("Большое первое сообщение");
    }

    @Test
    void avoidsDuplicatingSameText() {
        assertThat(ConversationProblemTextSupport.mergeProblemText("Касса зависла", "  касса   зависла "))
                .isEqualTo("касса   зависла");
    }

    @Test
    void appendsFollowUpWhenTextsDiffer() {
        assertThat(ConversationProblemTextSupport.mergeProblemText("Большое первое сообщение", "Не печатает чек"))
                .isEqualTo("Большое первое сообщение\n\nУточнение после ответов на вопросы:\nНе печатает чек");
    }
}
