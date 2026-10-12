package com.example.panel.service;

import com.example.panel.support.PanelTimestampSqlSupport;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DialogAiExactTicketIdPersistenceIntegrationTest {

    private static final List<String> IDS = List.of(" T-EXACT ", " T-EXACT", "T-EXACT ", " null ");

    @ParameterizedTest
    @ValueSource(strings = {" T-EXACT ", " T-EXACT", "T-EXACT ", " null "})
    void facadeControlAndProcessingRoundTripDoNotMutatePlainSibling(String id) {
        Fixture f = fixture(true, true);
        seedRoots(f, id, true);
        f.jdbc().update("INSERT INTO ticket_ai_agent_dialog_control(ticket_id, ai_disabled, auto_reply_blocked) VALUES (?, 1, 1)", id.trim());
        f.jdbc().update("INSERT INTO ticket_ai_agent_state(ticket_id, is_processing) VALUES (?, 1)", id.trim());

        assertThat(f.facade().loadDialogControlState(id)).containsEntry("ticket_id", id).containsEntry("ai_disabled", false);
        assertThat(f.facade().updateDialogControlState(id, true, false, " reason ", " actor ")).isTrue();
        assertThat(f.facade().loadDialogControlState(id)).containsEntry("ticket_id", id)
            .containsEntry("ai_disabled", true).containsEntry("auto_reply_blocked", false).containsEntry("reason", "reason");
        assertThat(f.jdbc().queryForMap("SELECT * FROM ticket_ai_agent_dialog_control WHERE ticket_id = ?", id.trim()))
            .containsEntry("auto_reply_blocked", 1);
        assertThat(f.facade().isProcessing(id)).isFalse();
        f.state().markProcessing(id, " action ", null, " source ", 0.9, " reply ", " decision ", " reason ", "[]", " assist_only ");
        assertThat(f.facade().isProcessing(id)).isTrue();
        f.facade().clearProcessing(id, " done ", null);
        assertThat(f.facade().isProcessing(id)).isFalse();
        assertThat(f.jdbc().queryForObject("SELECT is_processing FROM ticket_ai_agent_state WHERE ticket_id = ?", Integer.class, id.trim())).isEqualTo(1);
        assertThat(f.jdbc().queryForObject("SELECT ticket_id FROM ai_agent_event_log", String.class)).isEqualTo(id);

        f.feedback().recordSuggestionFeedback(id, " accepted ", " source ", " title ", " snippet ", " reply ", null, " actor ");
        assertThat(f.jdbc().queryForMap("SELECT * FROM ai_agent_suggestion_feedback"))
            .containsEntry("ticket_id", id).containsEntry("decision", "accepted").containsEntry("actor", "actor");
    }

    @ParameterizedTest
    @MethodSource("eventCases")
    void eventWritersAndMonitoringRetainExactIdsInBothSchemas(String id, boolean extended, boolean separateWriter) {
        Fixture f = fixture(true, extended);
        seedRoots(f, id, true);
        writeEvent(f, separateWriter, id.trim());
        writeEvent(f, separateWriter, id);
        writeEvent(f, separateWriter, null);

        List<Map<String, Object>> events = f.facade().loadMonitoringEvents(7, 20, id, " test_event ", " actor ");
        assertThat(events).singleElement().satisfies(row ->
            assertThat(row).containsEntry("ticket_id", id).containsEntry("event_type", "TEST_EVENT").containsEntry("actor", "Actor"));
        assertThat(f.facade().loadMonitoringEvents(7, 20, null, null, null)).hasSize(3);
        assertThat(f.facade().loadMonitoringEvents(7, 20, " ", null, null)).hasSize(3);
        assertThat(f.jdbc().queryForObject("SELECT COUNT(*) FROM ai_agent_event_log WHERE ticket_id IS NULL", Integer.class)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {" T-EXACT ", " T-EXACT", "T-EXACT ", " null "})
    void historyReviewRetrievalAndDebugUseExactRows(String id) {
        Fixture f = fixture(true, true);
        seedRoots(f, id, true);
        f.jdbc().update("INSERT INTO channels(id, platform) VALUES (1, 'telegram')");
        f.jdbc().update("INSERT INTO messages(ticket_id, channel_id, business, location_name) VALUES (?, 1, 'plain_scope', 'plain_location'), (?, 1, 'exact_scope', 'exact_location')", id.trim(), id);
        f.jdbc().update("INSERT INTO chat_history(ticket_id, sender, message, message_type, attachment) VALUES (?, 'client', ' Plain problem ', 'text', ' ref '), (?, 'client', ' Exact problem ', 'text', ' ref ')", id.trim(), id);
        f.jdbc().update("INSERT INTO ai_agent_solution_memory(query_key, last_ticket_id, query_text, pending_solution_text, review_required) VALUES ('plain', ?, 'Plain problem', 'Plain solution', 1), ('exact', ?, 'Exact problem', 'Exact solution', 1)", id.trim(), id);
        f.state().markProcessing(id, "exact_action", null, "source", 0.9, "reply", "decision", "reason", "[]", "assist_only");
        writeEvent(f, false, id.trim());
        writeEvent(f, false, id);

        assertThat(f.input().loadLastClientPayload(id).message()).isEqualTo("Exact problem");
        assertThat(f.persistence().loadLastClientMessage(id)).isEqualTo("Exact problem");
        assertThat(f.review().loadPendingReview(id)).containsEntry("pending", true).containsEntry("ticket_id", id).containsEntry("query_key", "exact");
        AiRetrievalService.RetrievalResult retrieval = f.retrieval().retrieve(id, "vpn reset", 3);
        assertThat(retrieval.context().ticketId()).isEqualTo(id);
        assertThat(retrieval.context().business()).isEqualTo("exact_scope");

        Map<String, Object> trace = f.runtime().loadDecisionTrace(id, 10);
        assertThat(trace).containsEntry("ticket_id", id);
        assertThat(((Map<?, ?>) trace.get("state")).get("ticket_id")).isEqualTo(id);
        assertThat(((Map<?, ?>) trace.get("last_client")).get("message")).isEqualTo("Exact problem");
        assertThat((List<?>) trace.get("events")).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stateSchemaFallbackAndClearPreserveExactKey(boolean extendedState) {
        Fixture f = fixture(extendedState, true);
        String id = " T-EXACT ";
        f.jdbc().update("INSERT INTO ticket_ai_agent_state(ticket_id, is_processing) VALUES ('T-EXACT', 1)");
        f.state().markProcessing(id, "processing", null, "source", 0.8, "reply", "review", "reason", "[]", "assist_only");
        assertThat(f.state().isProcessing(id)).isTrue();
        f.state().clearProcessing(id, "done", null, "review", "reason", "[]", "assist_only");
        assertThat(f.state().isProcessing(id)).isFalse();
        assertThat(f.jdbc().queryForObject("SELECT is_processing FROM ticket_ai_agent_state WHERE ticket_id = 'T-EXACT'", Integer.class)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void facadeDeniesMissingOrDuplicateExactRootBeforeRealWrites(boolean duplicate) {
        Fixture f = fixture(true, true);
        String id = " T-EXACT ";
        seedRoots(f, id, duplicate);
        if (duplicate) {
            f.jdbc().update("INSERT INTO tickets(user_id, ticket_id) VALUES (3, ?)", id);
        }
        f.jdbc().update("INSERT INTO ticket_ai_agent_dialog_control(ticket_id, ai_disabled, auto_reply_blocked) VALUES ('T-EXACT', 1, 1)");
        writeEvent(f, false, "T-EXACT");

        assertThat(f.facade().loadDialogControlState(id)).isEmpty();
        assertThat(f.facade().updateDialogControlState(id, false, false, "change", "actor")).isFalse();
        f.facade().clearProcessing(id, "clear", null);
        assertThat(f.facade().loadMonitoringEvents(7, 20, id, null, null)).isEmpty();
        assertThat(f.jdbc().queryForObject("SELECT COUNT(*) FROM ticket_ai_agent_dialog_control", Integer.class)).isEqualTo(1);
        assertThat(f.jdbc().queryForObject("SELECT COUNT(*) FROM ticket_ai_agent_state", Integer.class)).isZero();
        assertThat(f.jdbc().queryForObject("SELECT COUNT(*) FROM ai_agent_event_log", Integer.class)).isEqualTo(1);
    }

    private static Stream<Arguments> eventCases() {
        return IDS.stream().flatMap(id -> Stream.of(false, true).flatMap(extended ->
            Stream.of(false, true).map(writer -> Arguments.of(id, extended, writer))));
    }

    private void seedRoots(Fixture f, String id, boolean exactRoot) {
        f.jdbc().update("INSERT INTO tickets(user_id, ticket_id) VALUES (1, ?)", id.trim());
        if (exactRoot) {
            f.jdbc().update("INSERT INTO tickets(user_id, ticket_id) VALUES (2, ?)", id);
        }
    }

    private void writeEvent(Fixture f, boolean separateWriter, String id) {
        Map<String, Object> payload = Map.of("policy_stage", " stage ", "policy_outcome", " outcome ");
        if (separateWriter) {
            new DialogAiAssistantEventService(f.jdbc(), new ObjectMapper()).recordAiEvent(
                id, " TEST_EVENT ", " Actor ", " decision ", " reason ", " source ", 0.9, " detail ", payload);
        } else {
            f.persistence().recordAiEvent(id, " TEST_EVENT ", " Actor ", " decision ", " reason ", " source ", 0.9, " detail ", payload);
        }
    }

    private Fixture fixture(boolean extendedState, boolean extendedEvents) {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate("ai_exact_roundtrip_r32");
        jdbc.execute("CREATE TABLE tickets(user_id BIGINT, ticket_id TEXT, channel_id BIGINT, PRIMARY KEY(user_id, ticket_id))");
        jdbc.execute("CREATE TABLE ticket_ai_agent_dialog_control(ticket_id TEXT PRIMARY KEY, ai_disabled INTEGER, auto_reply_blocked INTEGER, reason TEXT, updated_by TEXT, updated_at TIMESTAMPTZ)");
        jdbc.execute("""
            CREATE TABLE ticket_ai_agent_state(
                ticket_id TEXT PRIMARY KEY, is_processing INTEGER, mode TEXT, last_action TEXT,
                last_error TEXT, last_source TEXT, last_score DOUBLE PRECISION, last_suggested_reply TEXT,
                updated_at TIMESTAMPTZ
            """ + (extendedState ? ", decision_type TEXT, decision_reason TEXT, source_hits TEXT" : "") + ")");
        jdbc.execute("""
            CREATE TABLE ai_agent_event_log(
                id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, ticket_id TEXT, event_type TEXT,
                actor TEXT, decision_type TEXT, decision_reason TEXT, source TEXT, score DOUBLE PRECISION,
                detail TEXT, payload_json TEXT, created_at TIMESTAMPTZ
            """ + (extendedEvents ? ", policy_stage TEXT, policy_outcome TEXT, intent_key TEXT, sensitive_topic INTEGER, top_candidate_trust TEXT, top_candidate_source_type TEXT" : "") + ")");
        jdbc.execute("CREATE TABLE ai_agent_suggestion_feedback(ticket_id TEXT, decision TEXT, source TEXT, title TEXT, snippet TEXT, suggested_reply TEXT, actor TEXT, created_at TIMESTAMPTZ)");
        jdbc.execute("CREATE TABLE chat_history(id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, ticket_id TEXT, sender TEXT, message TEXT, message_type TEXT, attachment TEXT, timestamp TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE channels(id BIGINT PRIMARY KEY, platform TEXT)");
        jdbc.execute("CREATE TABLE messages(ticket_id TEXT, channel_id BIGINT, business TEXT, location_name TEXT)");
        jdbc.execute("CREATE TABLE ai_agent_solution_memory(query_key TEXT PRIMARY KEY, query_text TEXT, solution_text TEXT, pending_solution_text TEXT, last_ticket_id TEXT, review_required INTEGER, updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP, created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP)");

        SharedConfigService shared = mock(SharedConfigService.class);
        when(shared.loadSettings()).thenReturn(Map.of("dialog_config", Map.of("ai_agent_mode", "assist_only")));
        DialogAiAssistantPersistenceService persistence = new DialogAiAssistantPersistenceService(
            jdbc, shared, mock(AiPolicyService.class), mock(AiKnowledgeService.class), new ObjectMapper());
        DialogAiAssistantStateService state = new DialogAiAssistantStateService(jdbc, persistence);
        DialogAiAssistantReviewService review = new DialogAiAssistantReviewService(jdbc, persistence);
        DialogAiAssistantConfigService config = mock(DialogAiAssistantConfigService.class);
        when(config.resolveAgentMode()).thenReturn("assist_only");
        AiMonitoringService monitoring = new AiMonitoringService(jdbc, new ObjectMapper(),
            mock(AiOfflineEvaluationService.class), new PanelTimestampSqlSupport());
        AiInputNormalizerService input = new AiInputNormalizerService(jdbc);
        AiIntentService intent = mock(AiIntentService.class);
        when(intent.extract("vpn reset")).thenReturn(new AiIntentService.IntentMatch(
            "general_support", Map.of(), "{}", null, false, List.of(), 0.0));
        when(intent.resolvePolicy("general_support")).thenReturn(new AiIntentService.IntentPolicy(
            "general_support", false, true, true, "normal", null));
        AiRetrievalService retrieval = new AiRetrievalService(jdbc, intent, new PanelTimestampSqlSupport());
        DialogAiAssistantOperatorFeedbackService feedback = new DialogAiAssistantOperatorFeedbackService(
            mock(AiLearningService.class), mock(NotificationService.class), persistence, state, config);
        DialogAiAssistantService facade = new DialogAiAssistantService(
            monitoring, input, retrieval, review, mock(DialogAiSolutionMemoryService.class),
            state, config, feedback, mock(DialogAiAssistantSuggestionService.class),
            mock(DialogAiAssistantMessageFlowService.class), new LegacyTicketIdJdbcGuard(jdbc));
        AiOpsRuntimeService runtime = new AiOpsRuntimeService(
            jdbc, facade, intent, retrieval, mock(AiPolicyService.class), mock(AiDecisionService.class),
            config, state, mock(DialogAiAssistantPolicyService.class), input, mock(AiControlledLlmService.class),
            mock(AiOfflineEvaluationService.class), new ObjectMapper(), shared, new PanelTimestampSqlSupport());
        return new Fixture(jdbc, persistence, state, facade, feedback, input, review, retrieval, runtime);
    }

    private record Fixture(JdbcTemplate jdbc, DialogAiAssistantPersistenceService persistence,
                           DialogAiAssistantStateService state, DialogAiAssistantService facade,
                           DialogAiAssistantOperatorFeedbackService feedback, AiInputNormalizerService input,
                           DialogAiAssistantReviewService review, AiRetrievalService retrieval,
                           AiOpsRuntimeService runtime) {
    }
}
