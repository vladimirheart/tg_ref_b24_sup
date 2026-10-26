package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class SlaRoutingIdentityBoundaryTest {
    private static final String TICKET = " SLA/?=identity ";

    static Stream<String> literalKeys() {
        return Stream.of(" reader", "reader ", " reader ", "\treader\t", "\nreader\r", " null ");
    }

    static Stream<Arguments> ruleTargets() {
        return literalKeys().flatMap(key -> Stream.of("single", "pool").map(mode -> Arguments.of(key, mode)));
    }

    private DialogListItem dialog(String responsible) {
        return new DialogListItem(TICKET, "REQ-1", 901L, "client", "Client", "Same label", 7L,
            "Telegram", null, null, "Help", Instant.now().minusSeconds(7200).toString(), "open", false,
            null, null, responsible, null, null, null, null, null, 0, null, null, null, null);
    }

    private Map<String, Object> config() {
        var config = new LinkedHashMap<String, Object>();
        config.put("sla_target_minutes", 60);
        config.put("sla_critical_minutes", 30);
        config.put("sla_critical_orchestration_mode", "assist");
        config.put("sla_critical_auto_assign_enabled", true);
        config.put("sla_critical_auto_assign_include_assigned", true);
        config.put("sla_critical_escalation_include_assigned", true);
        config.put("sla_critical_escalation_webhook_enabled", false);
        return config;
    }

    private SlaRoutingPolicyService policy() {
        return new SlaRoutingPolicyService(new SlaEscalationCandidateService(),
            new SlaEscalationAutoAssignService(null), new SlaRoutingRuleAuditService());
    }

    @ParameterizedTest @MethodSource("ruleTargets")
    @SuppressWarnings("unchecked")
    void governanceAuditDescribesTheSameLiteralTargetAsTheRuntime(String target, String mode) {
        var config = config();
        config.put("sla_critical_auto_assign_rules", List.of(Map.of("rule_id", "r", "match_channel", " Telegram ",
            mode.equals("single") ? "assign_to" : "assign_to_pool", mode.equals("single") ? target : List.of(target))));
        var settings = Map.<String, Object>of("dialog_config", config);
        var snapshot = policy().buildRoutingPolicySnapshot(dialog(null), settings);
        assertThat(snapshot).containsEntry("recommended_assignee", target).containsEntry("action", "assign");
        var audit = policy().buildRoutingGovernanceAudit(List.of(dialog(null)), settings);
        assertThat((List<Map<String, Object>>) audit.get("rules")).singleElement().satisfies(rule ->
            assertThat(rule).containsEntry("rule_id", "r").containsEntry("assignee_target", target)
                .containsEntry("matched_candidates", 1).containsEntry("selected_candidates", 1));
    }

    @ParameterizedTest @MethodSource("literalKeys")
    void snapshotDoesNotConfuseLiteralResponsibleWithThePlainAccount(String previous) {
        var config = config(); config.put("sla_critical_auto_assign_to", "reader");
        var snapshot = policy().buildRoutingPolicySnapshot(dialog(previous), Map.of("dialog_config", config));
        assertThat(snapshot).containsEntry("current_responsible", previous).containsEntry("recommended_assignee", "reader")
            .containsEntry("candidate_scope", "assigned").containsEntry("action", "reassign");
    }

    @ParameterizedTest @MethodSource("literalKeys")
    void candidatePayloadKeepsTheLiteralResponsibleAndExistingIncludeAssignedGate(String responsible) {
        var service = new SlaEscalationCandidateService(); var row = dialog(responsible);
        assertThat(service.findEscalationCandidates(List.of(row), 60, 30, true)).singleElement().satisfies(candidate ->
            assertThat(candidate).containsEntry("ticket_id", TICKET).containsEntry("responsible", responsible)
                .containsEntry("escalation_scope", "assigned"));
        assertThat(service.findEscalationCandidates(List.of(row), 60, 30, false)).isEmpty();
    }

    @ParameterizedTest @MethodSource("literalKeys")
    void backgroundPassesExactActorAndFrozenIdentityToScopedWriter(String actor) {
        var config = config(); config.put("sla_critical_auto_assign_actor", actor);
        assertBackground(config, actor, true);
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"", " \t\r\n", "null", "NULL"})
    void missingActorRetainsSystemFallback(String actor) {
        var config = config(); config.put("sla_critical_auto_assign_actor", actor);
        assertBackground(config, "sla_orchestrator", true);
    }

    @ParameterizedTest @ValueSource(strings = {"monitor", "disabled", "missing-writer"})
    void backgroundKeepsModeAndWriterGates(String mode) {
        var config = config();
        if (mode.equals("monitor")) config.put("sla_critical_orchestration_mode", "monitor");
        if (mode.equals("disabled")) config.put("sla_critical_auto_assign_enabled", false);
        assertBackground(config, "sla_orchestrator", false, mode.equals("missing-writer"));
    }

    private void assertBackground(Map<String, Object> config, String actor, boolean assigns) {
        assertBackground(config, actor, assigns, false);
    }

    private void assertBackground(Map<String, Object> config, String actor, boolean assigns, boolean missingWriter) {
        var settings = mock(SharedConfigService.class); var reads = mock(DialogLookupReadService.class);
        var candidates = mock(ScopedAssignmentCandidateService.class); var writer = mock(ScopedSlaAssignmentService.class);
        var legacy = mock(DialogResponsibilityService.class); var rawAudit = mock(DialogAuditService.class);
        var delivery = mock(SlaEscalationWebhookDeliveryService.class);
        var scope = new UiEventTicketScope(901L, TICKET, "a".repeat(32), "BUSINESS", 1L);
        var frozen = new DialogLookupReadService.ScopedSlaDialog(dialog(" previous\t"), scope, 7L, 5, " previous\t");
        config.put("sla_critical_auto_assign_to", "reader");
        when(settings.loadSettings()).thenReturn(Map.of("dialog_config", config));
        when(reads.loadScopedSlaDialogs()).thenReturn(List.of(frozen));
        when(candidates.capture(any(), anyLong())).thenReturn(Optional.of(
            new ScopedAssignmentCandidateService.Snapshot(false, Map.of("reader", 0L))));
        var routing = new SlaEscalationAutoAssignService(reads, null, candidates);
        var candidateService = new SlaEscalationCandidateService();
        var policy = new SlaRoutingPolicyService(candidateService, routing, new SlaRoutingRuleAuditService());
        new SlaEscalationWebhookNotifier(settings, reads, legacy, rawAudit, new ObjectMapper(), candidateService,
            routing, policy, delivery, null, missingWriter ? null : writer).notifyCriticalUnassignedDialogs();
        if (assigns) verify(writer).assign(frozen, "reader", actor, "fallback", "fallback_default");
        else verifyNoInteractions(writer);
        verify(reads).loadScopedSlaDialogs(); verify(reads, never()).loadDialogs(any());
        verifyNoInteractions(legacy, rawAudit, delivery);
    }

    @Test void typedPoolKeepsDistinctKeysCaseOrderAndDelimiterCharacters() {
        var parser = new SlaRoutingRuleMatchNormalizerService();
        assertThat(parser.parseAssigneePool(Arrays.asList("Alice", "Alice", "alice", " Alice ", "member,one", "member\nline", null, "", " \t", "NULL")))
            .containsExactly("Alice", "alice", " Alice ", "member,one", "member\nline");
    }

    @Test void literalPaddedNullFallbackIsPresentInManualReviewDiagnostics() {
        var config = config(); config.put("sla_critical_auto_assign_enabled", false);
        config.put("sla_critical_auto_assign_to", " null ");
        var snapshot = policy().buildRoutingPolicySnapshot(dialog(null), Map.of("dialog_config", config));
        assertThat(snapshot.get("issues")).isEqualTo(List.of("auto_assign_disabled"));
    }

    @Test void legacyCsvAndRuleLabelsKeepTheirPreviousNormalization() {
        var rule = new SlaRoutingRuleDefinitionFactoryService().buildDefinition(Map.of(
            "rule_id", " r ", "owner", " Support ", "match_channel", " Telegram ", "assign_to_pool", " Alice, Bob\nAlice "), 1);
        assertThat(rule.rule().assigneePool()).containsExactly("Alice", "Bob");
        assertThat(rule.rule().channels()).containsExactly("telegram");
        assertThat(rule.ruleId()).isEqualTo("r"); assertThat(rule.owner()).isEqualTo("Support");
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"", " \t\r\n", "null", "NULL"})
    void missingTargetsAndResponsibleKeepExistingAbsenceSemantics(String absent) {
        var raw = new LinkedHashMap<String, Object>(); raw.put("match_channel", "telegram"); raw.put("assign_to", absent);
        raw.put("assign_to_pool", Arrays.asList(absent));
        assertThat(new SlaRoutingRuleDefinitionFactoryService().buildDefinition(raw, 1)).isNull();
        assertThat(new SlaEscalationCandidateService().findEscalationCandidates(List.of(dialog(absent)), 60, 30, false))
            .singleElement().satisfies(candidate -> assertThat(candidate).containsEntry("responsible", null)
                .containsEntry("escalation_scope", "unassigned"));
        var config = config(); config.put("sla_critical_auto_assign_to", "reader");
        assertThat(policy().buildRoutingPolicySnapshot(dialog(absent), Map.of("dialog_config", config)))
            .containsEntry("current_responsible", null).containsEntry("action", "assign");
    }

    @ParameterizedTest @ValueSource(strings = {"Display label", "reader"})
    void snapshotAndCandidateReadRawIdentityInsteadOfDisplayName(String label) {
        var row = presentationDialog(" reader ", label, null);
        var config = config(); config.put("sla_critical_auto_assign_to", "reader");
        assertThat(policy().buildRoutingPolicySnapshot(row, Map.of("dialog_config", config)))
            .containsEntry("current_responsible", " reader ").containsEntry("recommended_assignee", "reader")
            .containsEntry("action", "reassign");
        assertThat(new SlaEscalationCandidateService().findEscalationCandidates(List.of(row), 60, 30, true))
            .singleElement().satisfies(candidate -> assertThat(candidate).containsEntry("responsible", " reader "));
    }

    @ParameterizedTest @ValueSource(strings = {"display", "resolved"})
    void displayAndPastResolverCannotCreateAnAssignedCandidate(String source) {
        var row = presentationDialog(null, source.equals("display") ? "Display label" : null,
            source.equals("resolved") ? "past_operator" : null);
        var config = config(); config.put("sla_critical_auto_assign_to", "reader");
        assertThat(policy().buildRoutingPolicySnapshot(row, Map.of("dialog_config", config)))
            .containsEntry("current_responsible", null).containsEntry("action", "assign");
        assertThat(new SlaEscalationCandidateService().findEscalationCandidates(List.of(row), 60, 30, false))
            .singleElement().satisfies(candidate -> assertThat(candidate).containsEntry("responsible", null)
                .containsEntry("escalation_scope", "unassigned"));
    }

    private DialogListItem presentationDialog(String raw, String display, String resolvedBy) {
        var base = dialog(raw);
        return new DialogListItem(base.ticketId(), base.requestNumber(), base.userId(), base.username(), base.clientName(),
            base.business(), base.channelId(), base.channelName(), base.city(), base.locationName(), base.problem(),
            base.createdAt(), base.status(), base.aiProcessing(), resolvedBy, base.resolvedAt(), raw, base.createdDate(),
            base.createdTime(), base.clientStatus(), base.lastMessageSender(), base.lastMessageTimestamp(),
            base.unreadCount(), base.rating(), base.categories(), display, null);
    }
}
