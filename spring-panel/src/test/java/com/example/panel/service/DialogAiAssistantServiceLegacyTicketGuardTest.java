package com.example.panel.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

class DialogAiAssistantServiceLegacyTicketGuardTest {

    @Test
    void incomingMessageSkipsAmbiguousLegacyTicketIdBeforeAiFlow() {
        DialogAiAssistantMessageFlowService messageFlowService = mock(DialogAiAssistantMessageFlowService.class);
        LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard = mock(LegacyTicketIdJdbcGuard.class);
        DialogAiAssistantService service = service(messageFlowService, legacyTicketIdJdbcGuard);

        service.processIncomingClientMessage("T-DUPLICATE", "message", "text", null);

        verify(legacyTicketIdJdbcGuard).hasUniqueTicket("T-DUPLICATE");
        verifyNoInteractions(messageFlowService);
    }

    @ParameterizedTest
    @ValueSource(strings = {" T-EXACT ", " T-EXACT", "T-EXACT ", " null "})
    void preservesExactIdThroughRealGuardBeforeDelegating(String ticketId) {
        GuardFixture fixture = guardedFixture();
        addTicket(fixture, 1L, ticketId.trim());
        addTicket(fixture, 2L, ticketId);
        Map<String, Object> control = Map.of("ticket_id", ticketId, "ai_disabled", true);
        List<Map<String, Object>> events = List.of(Map.of("ticket_id", ticketId));
        when(fixture.state().loadDialogControlState(ticketId)).thenReturn(control);
        when(fixture.state().isProcessing(ticketId)).thenReturn(true);
        when(fixture.config().resolveAgentMode()).thenReturn("assist_only");
        when(fixture.state().updateDialogControlState(
            ticketId, true, false, " reason ", " actor ", "assist_only")).thenReturn(true);
        when(fixture.monitoring().loadMonitoringEvents(7, 10, ticketId, " event ", " actor ")).thenReturn(events);

        assertThat(fixture.service().loadDialogControlState(ticketId)).isEqualTo(control);
        assertThat(fixture.service().updateDialogControlState(
            ticketId, true, false, " reason ", " actor ")).isTrue();
        assertThat(fixture.service().isProcessing(ticketId)).isTrue();
        assertThat(fixture.service().loadMonitoringEvents(
            7, 10, ticketId, " event ", " actor ")).isEqualTo(events);

        verify(fixture.state()).loadDialogControlState(ticketId);
        verify(fixture.state()).updateDialogControlState(
            ticketId, true, false, " reason ", " actor ", "assist_only");
        verify(fixture.monitoring()).loadMonitoringEvents(7, 10, ticketId, " event ", " actor ");
    }

    @ParameterizedTest
    @ValueSource(strings = {" T-EXACT ", " T-EXACT", "T-EXACT ", " null "})
    void absentExactIdDoesNotFallBackToExistingTrimmedSibling(String ticketId) {
        GuardFixture fixture = guardedFixture();
        addTicket(fixture, 1L, ticketId.trim());

        assertDeniedBeforeDelegates(fixture, ticketId);
    }

    @Test
    void duplicateExactIdDoesNotFallBackToUniqueTrimmedSibling() {
        GuardFixture fixture = guardedFixture();
        addTicket(fixture, 1L, "T-EXACT");
        addTicket(fixture, 2L, " T-EXACT ");
        addTicket(fixture, 3L, " T-EXACT ");

        assertDeniedBeforeDelegates(fixture, " T-EXACT ");
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void blankTicketScopedRequestsDoNotReachDelegates(String ticketId) {
        assertDeniedBeforeDelegates(guardedFixture(), ticketId);
    }

    @Test
    void absentMonitoringTicketFilterKeepsGlobalMonitoringBehavior() {
        GuardFixture fixture = guardedFixture();
        List<Map<String, Object>> events = List.of(Map.of("event_type", "global"));
        when(fixture.monitoring().loadMonitoringEvents(7, 10, null, " event ", " actor ")).thenReturn(events);

        for (String ticketId : new String[]{null, "", " ", "\t", "\n"}) {
            assertThat(fixture.service().loadMonitoringEvents(
                7, 10, ticketId, " event ", " actor ")).isEqualTo(events);
        }
        verifyNoInteractions(fixture.state(), fixture.config());
    }

    private void assertDeniedBeforeDelegates(GuardFixture fixture, String ticketId) {
        DialogAiAssistantService service = fixture.service();
        service.processIncomingClientMessage(ticketId, "message", "text", null);
        service.registerOperatorReply(ticketId, "reply", "operator");
        assertThat(service.submitOperatorLearningMapping(ticketId, "problem", "solution", "operator")).isFalse();
        assertThat(service.loadOperatorSuggestions(ticketId, 3)).isEmpty();
        assertThat(service.loadDialogControlState(ticketId)).isEmpty();
        assertThat(service.updateDialogControlState(ticketId, true, false, "reason", "operator")).isFalse();
        service.recordSuggestionFeedback(ticketId, "accepted", "source", "title", "snippet", "reply", "key", "operator");
        assertThat(service.loadPendingReview(ticketId)).isEmpty();
        assertThat(service.approvePendingReview(ticketId, "operator")).isFalse();
        assertThat(service.approvePendingReview(ticketId, "operator", 1L, 2L)).isFalse();
        assertThat(service.rejectPendingReview(ticketId, "operator")).isFalse();
        assertThat(service.isProcessing(ticketId)).isFalse();
        service.clearProcessing(ticketId, "action", "error");
        if (ticketId != null && !ticketId.isBlank()) {
            assertThat(service.loadMonitoringEvents(7, 10, ticketId, null, null)).isEmpty();
        }
        verifyNoInteractions(fixture.protectedDelegates());
    }

    private GuardFixture guardedFixture() {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate("ai_facade_exact_id_r31");
        jdbc.execute("""
            CREATE TABLE tickets (
                user_id BIGINT,
                ticket_id TEXT,
                PRIMARY KEY (user_id, ticket_id)
            )
            """);
        AiMonitoringService monitoring = mock(AiMonitoringService.class);
        AiInputNormalizerService input = mock(AiInputNormalizerService.class);
        AiRetrievalService retrieval = mock(AiRetrievalService.class);
        DialogAiAssistantReviewService review = mock(DialogAiAssistantReviewService.class);
        DialogAiSolutionMemoryService memory = mock(DialogAiSolutionMemoryService.class);
        DialogAiAssistantStateService state = mock(DialogAiAssistantStateService.class);
        DialogAiAssistantConfigService config = mock(DialogAiAssistantConfigService.class);
        DialogAiAssistantOperatorFeedbackService feedback = mock(DialogAiAssistantOperatorFeedbackService.class);
        DialogAiAssistantSuggestionService suggestions = mock(DialogAiAssistantSuggestionService.class);
        DialogAiAssistantMessageFlowService flow = mock(DialogAiAssistantMessageFlowService.class);
        DialogAiAssistantService service = new DialogAiAssistantService(
            monitoring, input, retrieval, review, memory, state, config, feedback, suggestions, flow,
            new LegacyTicketIdJdbcGuard(jdbc));
        return new GuardFixture(jdbc, service, state, config, monitoring,
            new Object[]{monitoring, input, retrieval, review, memory, state, config, feedback, suggestions, flow});
    }

    private void addTicket(GuardFixture fixture, long userId, String ticketId) {
        fixture.jdbc().update("INSERT INTO tickets(user_id, ticket_id) VALUES (?, ?)", userId, ticketId);
    }

    private record GuardFixture(JdbcTemplate jdbc,
                                DialogAiAssistantService service,
                                DialogAiAssistantStateService state,
                                DialogAiAssistantConfigService config,
                                AiMonitoringService monitoring,
                                Object[] protectedDelegates) {
    }

    private DialogAiAssistantService service(DialogAiAssistantMessageFlowService messageFlowService,
                                             LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard) {
        return new DialogAiAssistantService(
                mock(AiMonitoringService.class),
                mock(AiInputNormalizerService.class),
                mock(AiRetrievalService.class),
                mock(DialogAiAssistantReviewService.class),
                mock(DialogAiSolutionMemoryService.class),
                mock(DialogAiAssistantStateService.class),
                mock(DialogAiAssistantConfigService.class),
                mock(DialogAiAssistantOperatorFeedbackService.class),
                mock(DialogAiAssistantSuggestionService.class),
                messageFlowService,
                legacyTicketIdJdbcGuard
        );
    }
}
