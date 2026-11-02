package com.example.supportbot.max;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.supportbot.service.QuestionnaireScope;
import com.example.supportbot.service.PanelRuntimeConfigClient.Choice;
import com.example.supportbot.service.PanelRuntimeConfigClient.ChoiceCatalog;
import com.example.supportbot.service.PanelRuntimeConfigClient.SelectedQuestionnaire;
import com.example.supportbot.settings.dto.QuestionFlowItemDto;
import com.example.supportbot.settings.dto.QuestionOptionDto;
import com.example.supportbot.settings.dto.QuestionRouteDto;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MaxConversationSessionTest {

    @Test
    void snapshotKeepsSelectedBusinessVersionAndFreeQuestionOrderAcrossJson() throws Exception {
        var scope=new QuestionnaireScope("BUSINESS","USER_SELECTION","q-a","a".repeat(64),7L,"c".repeat(64));
        var catalog=new ChoiceCatalog(1,55L,"c".repeat(64),
            List.of(new Choice("BUSINESS",7L,"a","Business A","q-a","a".repeat(64))));
        var selected=new SelectedQuestionnaire(1,55L,"BUSINESS","USER_SELECTION",7L,"q-a",
            "a".repeat(64),"c".repeat(64),Map.of(),Map.of(),Map.of());
        var session=MaxConversationSession.pending(1001L,2002L,null,null,catalog,"start")
            .choose(selected,null,List.of(question("free-first","text","First"),question("free-second","text","Second")));
        session.recordAnswer("First answer");
        var mapper=org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build();
        var restored=new MaxConversationSession(mapper.readValue(mapper.writeValueAsString(session.snapshot()),MaxConversationSession.State.class));
        assertThat(restored.scope()).isEqualTo(scope);
        assertThat(restored.selection()).isEqualTo(session.selection());
        assertThat(restored.currentQuestion().getId()).isEqualTo("free-second");
        assertThat(restored.answers()).containsEntry("free-first","First answer");
    }

    @Test
    void snapshotKeepsGeneralScopeChosenBeforeFirstQuestion() {
        QuestionnaireScope scope = new QuestionnaireScope("GENERAL", "GENERAL_CHANNEL", "q-general", "hash");
        MaxConversationSession session = new MaxConversationSession(1001L, 2002L, null, null,
            List.of(question("problem", "text", "Problem")), null, scope);

        assertThat(new MaxConversationSession(session.snapshot()).scope()).isEqualTo(scope);
    }

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

    private ChoiceCatalog replayCatalog() {
        return new ChoiceCatalog(1,55L,"c".repeat(64),List.of(new Choice("BUSINESS",7L,"a","Business A","q-a","a".repeat(64))));
    }
    private MaxConversationSession replaySession() {
        var selected=new SelectedQuestionnaire(1,55L,"BUSINESS","USER_SELECTION",7L,"q-a","a".repeat(64),"c".repeat(64),Map.of(),Map.of(),Map.of());
        return MaxConversationSession.pending(1001L,2002L,null,null,replayCatalog(),"start")
            .choose(selected,null,List.of(question("free-first","text","First"),question("free-second","text","Second")));
    }
    @Test void journalSurvivesJsonAndAllSelectionTransitionsWithoutMutableAliases() throws Exception {
        var pending=MaxConversationSession.pending(1001L,2002L,null,null,replayCatalog(),"start");
        pending.acceptEvent("page");pending.questionnairePage(0);
        var selected=new SelectedQuestionnaire(1,55L,"BUSINESS","USER_SELECTION",7L,"q-a","a".repeat(64),"c".repeat(64),Map.of(),Map.of(),Map.of());
        var session=pending.choose(selected,null,List.of(question("q1","text","First"),question("q2","text","Second")));
        session.acceptEvent("choose");session.recordAnswer("One");session.acceptEvent("answer-1");
        session.recordAnswer("Two");session.acceptEvent("answer-2");session.prepareSubmission("answer-2");session.markSubmitted("T-MAX");
        var mapper=org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build();
        var restored=new MaxConversationSession(mapper.readValue(mapper.writeValueAsString(session.snapshot()),MaxConversationSession.State.class));
        assertThat(restored.selection().acceptedEventKeys()).containsExactly("page","choose","answer-1","answer-2");
        assertThat(restored.lastAcceptedEvent()).isEqualTo("answer-2");
        org.assertj.core.api.Assertions.assertThatThrownBy(()->restored.selection().acceptedEventKeys().add("foreign"))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThat(restored.scope()).isEqualTo(session.scope());assertThat(restored.answers()).isEqualTo(session.answers());
    }
    @Test void oldJsonWithoutJournalRetainsScopeAndProgressWithoutInventingEventIds() throws Exception {
        var session=replaySession();session.recordAnswer("One");session.acceptEvent("not-in-old-format");
        var mapper=org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build();
        var old=mapper.valueToTree(session.snapshot());((com.fasterxml.jackson.databind.node.ObjectNode)old.get("selection")).remove("acceptedEventKeys");
        var restored=new MaxConversationSession(mapper.treeToValue(old,MaxConversationSession.State.class));
        assertThat(restored.selection().acceptedEventKeys()).isEmpty();assertThat(restored.lastAcceptedEvent()).isNull();
        assertThat(restored.currentQuestion().getId()).isEqualTo("free-second");
        assertThat(restored.answers()).containsExactlyEntriesOf(Map.of("free-first","One"));
        assertThat(restored.scope()).isEqualTo(session.scope());
        assertThat(mapper.valueToTree(restored.snapshot()).at("/selection/acceptedEventKeys").isMissingNode()).isTrue();
    }
    @Test void duplicateEventIsIdempotentAndInputListIsDefensivelyCopied() {
        var session=replaySession();session.acceptEvent("first");session.acceptEvent("first");
        assertThat(session.selection().acceptedEventKeys()).containsExactly("first");
        var keys=new java.util.ArrayList<>(List.of("saved"));
        var selection=new MaxConversationSession.Selection(replayCatalog(),"0123456789ab","start",null,null,null,0,keys);
        keys.add("changed");assertThat(selection.acceptedEventKeys()).containsExactly("saved");
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"null-key","blank-key","duplicate","overflow","missing-submission","nonterminal-submission"})
    void malformedJournalCannotBeRestored(String kind) {
        List<String> keys=switch(kind) {
            case "null-key"->java.util.Arrays.asList("a",null);
            case "blank-key"->List.of(" ");case "duplicate"->List.of("a","a");
            case "overflow"->java.util.stream.IntStream.range(0,4097).mapToObj(i->"event-"+i).toList();
            case "missing-submission"->List.of("other");default->List.of("final","later");
        };
        String submission=kind.endsWith("submission")?"final":null;
        org.assertj.core.api.Assertions.assertThatThrownBy(()->new MaxConversationSession.Selection(
            replayCatalog(),"0123456789ab","start",submission,null,null,0,keys)).isInstanceOf(IllegalStateException.class);
    }
    @Test void journalBudgetFailsBeforeReplacingTheLastValidSelection() {
        var keys=java.util.stream.IntStream.range(0,4096).mapToObj(i->"event-"+i).toList();
        var pending=MaxConversationSession.pending(1001L,2002L,null,null,replayCatalog(),"start");
        var state=pending.snapshot();
        var tree=org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build().valueToTree(state);
        var mapper=org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build();
        ((com.fasterxml.jackson.databind.node.ObjectNode)tree.get("selection")).set("acceptedEventKeys",mapper.valueToTree(keys));
        var session=new MaxConversationSession(mapper.convertValue(tree,MaxConversationSession.State.class));
        org.assertj.core.api.Assertions.assertThatThrownBy(()->session.acceptEvent("overflow")).isInstanceOf(IllegalStateException.class);
        assertThat(session.selection().acceptedEventKeys()).containsExactlyElementsOf(keys);
    }
    @Test void directLegacySessionDoesNotAcquireASelectionOrJournal() {
        var legacy=new MaxConversationSession(1001L,2002L,null,null,List.of(question("q1","text","First")),null);
        legacy.acceptEvent("legacy");assertThat(legacy.selection()).isNull();assertThat(legacy.acceptedEvent("legacy")).isFalse();
    }
}
