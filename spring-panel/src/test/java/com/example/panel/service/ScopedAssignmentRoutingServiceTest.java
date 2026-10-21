package com.example.panel.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.example.panel.entity.Channel;
import com.example.panel.model.dialog.DialogOperatorOption;
import com.example.panel.repository.ChannelRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class ScopedAssignmentRoutingServiceTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private final DialogParticipantService directory=mock(DialogParticipantService.class);
    private final DialogLookupReadService globalLoads=mock(DialogLookupReadService.class);
    private final ScopedAssignmentCandidateService capture=mock(ScopedAssignmentCandidateService.class);
    private final SharedConfigService settings=mock(SharedConfigService.class);
    private final UiEventTicketScope scope=new UiEventTicketScope(10L," T/?=root ","a".repeat(32),"BUSINESS",1L);
    private ChannelAssignmentRoutingService routing;

    @BeforeEach void setup() {
        routing=new ChannelAssignmentRoutingService(mapper,settings,directory,globalLoads,null,capture);
        when(capture.capture(any(),anyLong())).thenReturn(Optional.of(new ScopedAssignmentCandidateService.Snapshot(false,Map.of("alice",2L,"bob",0L))));
        when(directory.loadAssignableOperators(anySet())).thenReturn(List.of(
            new DialogOperatorOption("foreign","Foreign",null,"Support","operator"),
            new DialogOperatorOption("Alice","Alice",null,"Support","operator"),new DialogOperatorOption("bob","Bob",null,"Support","operator")));
    }
    private Channel channel(long id,String mode,String strategy,String operator,boolean assign) {
        var channel=new Channel();channel.setId(id);channel.setActive(true);
        try {channel.setQuestionsCfg(mapper.writeValueAsString(Map.of("assignmentRouting",Map.of(
            "enabled",true,"mode",mode,"strategy",strategy,"operatorUsername",operator,"operatorUsernames",List.of("foreign","alice","bob"),"department","Support","assignResponsibleOnCreate",assign))));}
        catch(Exception ex){throw new AssertionError(ex);}
        return channel;
    }
    @ParameterizedTest @CsvSource({"single_operator,round_robin","single_operator,least_loaded","single_operator,hash_by_ticket",
        "operator_pool,round_robin","operator_pool,least_loaded","operator_pool,hash_by_ticket",
        "department_queue,round_robin","department_queue,least_loaded","department_queue,hash_by_ticket"})
    void businessFilteringPrecedesEveryModeAndStrategyWithoutGlobalLoadReads(String mode,String strategy) {
        var channel=channel(7,mode,strategy,"foreign",true);
        var result=routing.resolveScoped(channel,ChannelAssignmentRoutingService.RoutingEvent.NEW_PUBLIC_APPEAL,scope);
        if(mode.equals("single_operator")) {assertThat(result.recipients()).isEmpty();assertThat(result.assignee()).isNull();}
        else {
            assertThat(result.recipients()).containsExactly("alice","bob");
            String expected=strategy.equals("least_loaded")?"bob":strategy.equals("hash_by_ticket")?List.of("alice","bob").get(Math.floorMod(scope.ticketId().hashCode(),2)):"alice";
            assertThat(result.assignee()).isEqualTo(expected);
        }
        verify(directory).loadAssignableOperators(Set.of("alice","bob"));
        verify(directory,never()).loadAssignableOperators();verifyNoInteractions(globalLoads);
    }
    @Test void singleEligibleOperatorStillUsesTheConfiguredIdentity() {
        var result=routing.resolveScoped(channel(7,"single_operator","round_robin"," ALICE ",true),ChannelAssignmentRoutingService.RoutingEvent.NEW_PUBLIC_APPEAL,scope);
        assertThat(result.recipients()).containsExactly("alice");assertThat(result.assignee()).isEqualTo("alice");
    }
    @ParameterizedTest @ValueSource(strings={"least_loaded","round_robin","hash_by_ticket"})
    void notificationsRetainTheirIndependentRecipientsWithoutReadingGlobalLoadsOrAdvancingAnyCursor(String strategy) {
        var coordination=mock(RuntimeCoordinationService.class);
        routing=new ChannelAssignmentRoutingService(mapper,settings,directory,globalLoads,coordination,capture);
        when(directory.loadAssignableOperators()).thenReturn(List.of(new DialogOperatorOption("viewer","Viewer",null,"Support","viewer"),
            new DialogOperatorOption("foreign","Foreign",null,"Support","operator")));
        var result=routing.resolveRecipients(channel(7,"department_queue",strategy,"",true),ChannelAssignmentRoutingService.RoutingEvent.NEW_PUBLIC_APPEAL,scope.ticketId());
        assertThat(result.recipients()).containsExactly("foreign","viewer");assertThat(result.assignee()).isNull();
        verifyNoInteractions(globalLoads,coordination,capture);
    }
    @Test void roundRobinSeparatesBusinessesAndChannelsAndDoesNotUseNotificationGlobalCounter() {
        var a=channel(7,"operator_pool","round_robin","",true);var b=channel(8,"operator_pool","round_robin","",true);
        var other=new UiEventTicketScope(10L,scope.ticketId(),scope.ticketPublicId(),"BUSINESS",2L);
        assertThat(routing.resolveScoped(a,ChannelAssignmentRoutingService.RoutingEvent.NEW_PUBLIC_APPEAL,scope).assignee()).isEqualTo("alice");
        assertThat(routing.resolveScoped(a,ChannelAssignmentRoutingService.RoutingEvent.NEW_PUBLIC_APPEAL,scope).assignee()).isEqualTo("bob");
        assertThat(routing.resolveScoped(a,ChannelAssignmentRoutingService.RoutingEvent.NEW_PUBLIC_APPEAL,other).assignee()).isEqualTo("alice");
        assertThat(routing.resolveScoped(b,ChannelAssignmentRoutingService.RoutingEvent.NEW_PUBLIC_APPEAL,scope).assignee()).isEqualTo("alice");
    }
    @Test void sharedCounterKeyCarriesCanonicalBusinessAndChannelAndUsesTheFullLongCursor() {
        var coordination=mock(RuntimeCoordinationService.class);when(coordination.nextCounterValue(anyString())).thenReturn(2147483648L);
        routing=new ChannelAssignmentRoutingService(mapper,settings,directory,globalLoads,coordination,capture);
        var three=new LinkedHashMap<String,Long>();three.put("alice",0L);three.put("bob",0L);three.put("foreign",0L);
        when(capture.capture(scope,7L)).thenReturn(Optional.of(new ScopedAssignmentCandidateService.Snapshot(false,three)));
        var result=routing.resolveScoped(channel(7,"operator_pool","round_robin","",true),ChannelAssignmentRoutingService.RoutingEvent.NEW_PUBLIC_APPEAL,scope);
        assertThat(result.assignee()).isEqualTo(List.of("foreign","alice","bob").get((int)(2147483648L%3)));
        verify(coordination).nextCounterValue(startsWith("channel-assignment-routing:business:1:channel:7::"));
    }
    @ParameterizedTest @ValueSource(strings={"empty","unavailable","missing-provider"})
    void unavailableOrEmptyScopedPoolNeverFallsBackToGlobalAssignment(String state) {
        if(state.equals("missing-provider"))routing=new ChannelAssignmentRoutingService(mapper,settings,directory,globalLoads);
        else when(capture.capture(any(),anyLong())).thenReturn(state.equals("empty")?Optional.of(new ScopedAssignmentCandidateService.Snapshot(false,Map.of())):Optional.empty());
        var result=routing.resolveScoped(channel(7,"operator_pool","round_robin","",true),ChannelAssignmentRoutingService.RoutingEvent.NEW_PUBLIC_APPEAL,scope);
        assertThat(result.recipients()).isEmpty();assertThat(result.assignee()).isNull();verifyNoInteractions(directory,globalLoads);
    }
    @ParameterizedTest @ValueSource(strings={"no-auto","incoming"})
    void nonAssignmentEventsDoNotAdvanceTheScopedCursor(String kind) {
        var coordination=mock(RuntimeCoordinationService.class);
        routing=new ChannelAssignmentRoutingService(mapper,settings,directory,globalLoads,coordination,capture);
        var result=routing.resolveScoped(channel(7,"operator_pool","round_robin","",!kind.equals("no-auto")),
            kind.equals("incoming")?ChannelAssignmentRoutingService.RoutingEvent.INCOMING_CLIENT_MESSAGE:ChannelAssignmentRoutingService.RoutingEvent.NEW_PUBLIC_APPEAL,scope);
        assertThat(result.recipients()).containsExactly("alice","bob");assertThat(result.assignee()).isNull();verifyNoInteractions(coordination,globalLoads);
    }
    @Test void provedUnresolvedCompatibilityAloneMayUseTheLegacyDirectory() {
        var unresolved=new UiEventTicketScope(10L,scope.ticketId(),scope.ticketPublicId(),"UNRESOLVED",null);
        when(capture.capture(unresolved,7L)).thenReturn(Optional.of(new ScopedAssignmentCandidateService.Snapshot(true,Map.of())));
        when(directory.loadAssignableOperators()).thenReturn(List.of(new DialogOperatorOption("legacy","Legacy",null,"Support","operator")));
        var result=routing.resolveScoped(channel(7,"single_operator","round_robin","legacy",true),ChannelAssignmentRoutingService.RoutingEvent.NEW_PUBLIC_APPEAL,unresolved);
        assertThat(result.assignee()).isEqualTo("legacy");verify(directory).loadAssignableOperators();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void callbackCarriesFrozenScopeToPoolAndFinalWriterWhileReadRecipientsRemainIndependent(boolean lateChange) {
        var notices=mock(NotificationService.class);var responsibility=mock(DialogResponsibilityService.class);
        var channels=mock(ChannelRepository.class);var guard=mock(LegacyTicketIdJdbcGuard.class);var access=mock(UiEventTicketAccessService.class);
        var queue=mock(AlertQueueService.class);var stream=mock(UiEventStreamService.class);var channel=channel(7,"operator_pool","least_loaded","",true);
        when(channels.findById(7L)).thenReturn(Optional.of(channel));when(guard.hasUniqueNonGeneralTicket(scope.ticketId())).thenReturn(true);
        when(access.isCurrentScope(scope)).thenReturn(true);
        if(lateChange)when(capture.capture(scope,7L)).thenAnswer(call->{when(access.isCurrentScope(scope)).thenReturn(false);return Optional.of(new ScopedAssignmentCandidateService.Snapshot(false,Map.of("alice",0L)));});
        when(notices.findAllOperatorRecipients()).thenReturn(Set.of("viewer"));
        when(notices.formatNewAppealText(scope.ticketId())).thenReturn("New");when(notices.buildDialogUrl(scope.ticketId())).thenReturn("/dialog");
        var callback=new DialogRealtimeEventService(notices,mock(DialogAiAssistantService.class),queue,routing,channels,responsibility,
            mock(DialogNotificationService.class),stream,guard,access);
        callback.handleScopedTicketCreated(scope,7L,"preview");
        verify(responsibility,never()).assignResponsibleIfMissing(anyString(),anyString(),anyString());
        if(lateChange) {verifyNoInteractions(responsibility);verify(notices,never()).notifyScopedDialogUsers(any(),anySet(),anyString(),anyString());}
        else {
            verify(responsibility).assignScopedResponsibleIfMissing(scope,"bob","channel_assignment_routing");
            verify(notices).notifyScopedDialogUsers(scope,Set.of("viewer"),"New","/dialog");
        }
    }
}
