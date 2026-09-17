package com.example.supportbot.max;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.supportbot.settings.dto.QuestionFlowItemDto;
import com.example.supportbot.settings.dto.QuestionOptionDto;
import com.example.supportbot.settings.dto.QuestionRouteDto;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MaxConversationSessionTest {

    @Test
    void routesByChoiceValueIdAndStepBackRestoresPreviousQuestion() {
        QuestionFlowItemDto first = question("q1", "select", "First");
        first.setOptions(List.of(
                new QuestionOptionDto("alpha-id", "Alpha"),
                new QuestionOptionDto("beta-id", "Beta")
        ));
        first.setRoutes(List.of(new QuestionRouteDto("alpha-id", "q3")));
        QuestionFlowItemDto second = question("q2", "text", "Second");
        QuestionFlowItemDto third = question("q3", "text", "Third");

        MaxConversationSession session = new MaxConversationSession(1001L, 2002L, "client", "Client",
                List.of(first, second, third), null);

        assertThat(session.currentQuestion().getId()).isEqualTo("q1");
        session.recordAnswer("Alpha");

        assertThat(session.currentQuestion().getId()).isEqualTo("q3");
        assertThat(session.answers()).containsEntry("q1", "Alpha");
        assertThat(session.canGoBack()).isTrue();

        assertThat(session.stepBack()).isTrue();
        assertThat(session.currentQuestion().getId()).isEqualTo("q1");
        assertThat(session.answers()).doesNotContainKey("q1");
    }

    @Test
    void reuseDecisionAppliesCachedAnswersUntilFirstMissingQuestion() {
        QuestionFlowItemDto business = question("business", "text", "Business");
        QuestionFlowItemDto problem = question("problem", "text", "Problem");
        MaxConversationSession session = new MaxConversationSession(1001L, 2002L, null, null,
                List.of(business, problem), null);

        session.enableReusePrompt(Map.of("business", "Alpha"));

        assertThat(session.awaitingReuseDecision()).isTrue();
        assertThat(session.consumeReuseDecision("да")).isTrue();
        assertThat(session.awaitingReuseDecision()).isFalse();
        assertThat(session.answers()).containsEntry("business", "Alpha");
        assertThat(session.currentQuestion().getId()).isEqualTo("problem");
        assertThat(session.reusePrompt()).contains("Бизнес: Alpha");
    }

    @Test
    void snapshotRoundTripPreservesMergedProblemHistoryAndState() {
        QuestionFlowItemDto problem = question("problem", "text", "Problem");
        MaxConversationSession session = new MaxConversationSession(1001L, 2002L, "client", "Client",
                List.of(problem), null);
        session.captureBootstrapClientText("Initial problem");
        session.recordAnswer("More details");
        session.restorePersistedRawPayload("raw-v1");

        MaxConversationSession.State state = session.snapshot();
        MaxConversationSession restored = new MaxConversationSession(state);

        assertThat(restored.answers().get("problem"))
                .contains("Initial problem")
                .contains("More details");
        assertThat(restored.history()).hasSize(2);
        assertThat(restored.isComplete()).isTrue();
        assertThat(restored.userId()).isEqualTo(1001L);
        assertThat(restored.chatId()).isEqualTo(2002L);
        assertThat(restored.username()).isEqualTo("client");
        assertThat(restored.clientName()).isEqualTo("Client");
        assertThat(restored.persistedRawPayload()).isNull();
    }

    @Test
    void firstResponseTimeoutStopsExpiringAfterClientResponse() {
        MaxConversationSession session = new MaxConversationSession(1001L, 2002L, null, null,
                List.of(question("q1", "text", "Question")), null);
        OffsetDateTime afterDeadline = session.startedAt().plusMinutes(11);

        assertThat(session.shouldExpireDueToMissingFirstResponse(afterDeadline, 10)).isTrue();
        session.markClientResponseReceived();
        assertThat(session.shouldExpireDueToMissingFirstResponse(afterDeadline, 10)).isFalse();
    }

    private QuestionFlowItemDto question(String id, String type, String text) {
        QuestionFlowItemDto item = new QuestionFlowItemDto();
        item.setId(id);
        item.setType(type);
        item.setText(text);
        return item;
    }
}
