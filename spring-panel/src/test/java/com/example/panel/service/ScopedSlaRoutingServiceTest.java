package com.example.panel.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.example.panel.model.dialog.DialogListItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ScopedSlaRoutingServiceTest {
    private final DialogLookupReadService reads=mock(DialogLookupReadService.class);
    private final ScopedAssignmentCandidateService candidates=mock(ScopedAssignmentCandidateService.class);
    private final RuntimeCoordinationService counters=mock(RuntimeCoordinationService.class);
    private SlaEscalationAutoAssignService routing;
    private DialogLookupReadService.ScopedSlaDialog frozen;

    @BeforeEach void setup() {
        routing=new SlaEscalationAutoAssignService(reads,counters,candidates);
        frozen=source(1,7,null);
        when(candidates.capture(any(),anyLong())).thenReturn(Optional.of(
            new ScopedAssignmentCandidateService.Snapshot(false,Map.of("alice",2L,"bob",0L))));
    }
    private DialogLookupReadService.ScopedSlaDialog source(long business,long channel,String previous) {
        String raw=" SLA/?=root ";
        var scope=new UiEventTicketScope(901L,raw,"a".repeat(32),"BUSINESS",business);
        var dialog=new DialogListItem(raw,"REQ-1",901L,"client","Client","Same label",channel,
            "Telegram",null,null,"Help","2026-01-01T00:00:00Z","open",false,null,null,
            previous,null,null,null,null,null,0,null,null,null,null);
        return new DialogLookupReadService.ScopedSlaDialog(dialog,scope,channel,5,previous);
    }
    private Map<String,Object> config(String strategy) {
        var config=new LinkedHashMap<String,Object>();
        config.put("sla_critical_auto_assign_enabled",true);
        config.put("sla_critical_auto_assign_rules",List.of(Map.of("rule_id","r","match_business","Same label",
            "assign_to_pool",List.of("foreign","alice","bob"),"assign_to_pool_strategy",strategy)));
        return config;
    }
    private List<Map<String,Object>> payload() {
        return List.of(Map.of("ticket_id",frozen.scope().ticketId(),"business","Same label"));
    }
    private List<SlaEscalationWebhookNotifier.AutoAssignDecision> resolve(Map<String,Object> config) {
        return routing.resolveScopedAutoAssignDecisions(payload(),config,List.of(frozen));
    }
    @ParameterizedTest @ValueSource(strings={"hash_by_ticket","round_robin","least_loaded"})
    void filtersBusinessPoolBeforeEveryStrategyWithoutConsultingGlobalLoads(String strategy) {
        var result=resolve(config(strategy));
        String expected=strategy.equals("least_loaded")?"bob":strategy.equals("round_robin")?"alice":
            List.of("alice","bob").get(Math.floorMod(frozen.scope().ticketId().hashCode(),2));
        assertThat(result).hasSize(1);assertThat(result.get(0).assignee()).isEqualTo(expected);
        assertThat(result.get(0).context()).isSameAs(frozen);
        verify(candidates).capture(frozen.scope(),7L);verifyNoInteractions(reads);
    }
    @ParameterizedTest @ValueSource(strings={"single","fallback","empty","unavailable","missing","duplicate","provider"})
    void missingProofOrForeignAssigneeNeverFallsBackToGlobalAssignment(String kind) {
        var config=config("round_robin");
        List<DialogLookupReadService.ScopedSlaDialog> source=List.of(frozen);
        if(kind.equals("single"))config.put("sla_critical_auto_assign_rules",List.of(Map.of("match_business","Same label","assign_to","foreign")));
        if(kind.equals("fallback")) {config.remove("sla_critical_auto_assign_rules");config.put("sla_critical_auto_assign_to","foreign");}
        if(kind.equals("empty"))when(candidates.capture(any(),anyLong())).thenReturn(Optional.of(new ScopedAssignmentCandidateService.Snapshot(false,Map.of())));
        if(kind.equals("unavailable"))when(candidates.capture(any(),anyLong())).thenReturn(Optional.empty());
        if(kind.equals("missing"))source=List.of();
        if(kind.equals("duplicate"))source=List.of(frozen,frozen);
        if(kind.equals("provider"))routing=new SlaEscalationAutoAssignService(reads);
        assertThat(routing.resolveScopedAutoAssignDecisions(payload(),config,source)).isEmpty();
        verifyNoInteractions(reads,counters);
    }
    @Test void skillsQueuesAndCapacityFilterBeforeSelectionUsingOnlyBusinessLoads() {
        var config=config("least_loaded");
        config.put("sla_critical_auto_assign_rules",List.of(Map.of("match_business","Same label","assign_to_pool",List.of("foreign","alice","bob"),
            "assign_to_pool_strategy","least_loaded","required_assignee_skills",List.of("support"),"required_assignee_queues",List.of("help"))));
        config.put("sla_critical_operator_skills",Map.of("foreign",List.of("support"),"alice",List.of("support")));
        config.put("sla_critical_operator_queues",Map.of("foreign",List.of("help"),"alice",List.of("help"),"bob",List.of("help")));
        config.put("sla_critical_auto_assign_max_open_per_operator",3);
        assertThat(resolve(config)).singleElement().satisfies(d->assertThat(d.assignee()).isEqualTo("alice"));
        config.put("sla_critical_auto_assign_max_open_per_operator",2);
        assertThat(resolve(config)).isEmpty();verifyNoInteractions(reads);
    }
    @Test void cursorUsesFullLongAndSeparatesCanonicalBusinessAndChannelDespiteIdenticalLabels() {
        when(counters.nextCounterValue(anyString())).thenReturn(2147483648L);
        when(candidates.capture(any(),anyLong())).thenReturn(Optional.of(new ScopedAssignmentCandidateService.Snapshot(false,
            Map.of("foreign",0L,"alice",0L,"bob",0L))));
        for(var row:List.of(source(1,7,null),source(2,7,null),source(1,8,null))) {
            frozen=row;
            assertThat(resolve(config("round_robin"))).singleElement().satisfies(d->assertThat(d.assignee()).isEqualTo("bob"));
            verify(counters).nextCounterValue("sla-auto-assign:business:"+row.scope().businessId()+":channel:"+row.channelId()+":r:foreign,alice,bob");
        }
        verifyNoInteractions(reads);
    }
    @Test void frozenResponsibleOverridesMutablePayloadAndIsPreservedForCompareAndSet() {
        frozen=source(1,7," Original Owner ");
        var config=config("least_loaded");
        assertThat(resolve(config)).isEmpty();
        config.put("sla_critical_auto_assign_include_assigned",true);
        assertThat(resolve(config)).singleElement().satisfies(d->{
            assertThat(d.previousResponsible()).isEqualTo("Original Owner");
            assertThat(d.context().expectedResponsible()).isEqualTo(" Original Owner ");
        });
    }
    @Test void internalFrozenIdentityIsNotSerializedIntoExternalDecisionPayload() throws Exception {
        var decision=resolve(config("least_loaded")).get(0);
        var json=new ObjectMapper().readTree(new ObjectMapper().writeValueAsBytes(decision));
        assertThat(json.has("context")).isFalse();
        assertThat(json.fieldNames()).toIterable().containsExactlyInAnyOrder("ticketId","assignee","source","route","previousResponsible");
    }
    @ParameterizedTest @ValueSource(strings={"success","rejected","monitor","missing-writer","empty-source"})
    void backgroundUsesFrozenReaderAndScopedWriterOnly(String mode) {
        var settings=mock(SharedConfigService.class);
        var legacyWriter=mock(DialogResponsibilityService.class);var rawAudit=mock(DialogAuditService.class);
        var writer=mock(ScopedSlaAssignmentService.class);var delivery=mock(SlaEscalationWebhookDeliveryService.class);
        var candidateService=new SlaEscalationCandidateService();
        var config=config("least_loaded");
        config.put("sla_critical_orchestration_mode",mode.equals("monitor")?"monitor":"assist");
        config.put("sla_critical_escalation_webhook_enabled",false);
        when(settings.loadSettings()).thenReturn(Map.of("dialog_config",config));
        when(reads.loadScopedSlaDialogs()).thenReturn(mode.equals("empty-source")?List.of():List.of(frozen));
        when(writer.assign(any(),anyString(),anyString(),anyString(),anyString())).thenReturn(mode.equals("success"));
        var policy=new SlaRoutingPolicyService(candidateService,routing,new SlaRoutingRuleAuditService());
        var notifier=new SlaEscalationWebhookNotifier(settings,reads,legacyWriter,rawAudit,new ObjectMapper(),
            candidateService,routing,policy,delivery,null,mode.equals("missing-writer")?null:writer);
        notifier.notifyCriticalUnassignedDialogs();
        verify(reads).loadScopedSlaDialogs();verify(reads,never()).loadDialogs(any());
        if(mode.equals("success")||mode.equals("rejected"))verify(writer).assign(frozen,"bob","sla_orchestrator","rules","r");
        else verifyNoInteractions(writer);
        verifyNoInteractions(legacyWriter,rawAudit,delivery);
    }
}
