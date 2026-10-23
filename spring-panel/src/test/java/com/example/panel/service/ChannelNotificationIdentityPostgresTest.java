package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class ChannelNotificationIdentityPostgresTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private JdbcTemplate users;
    private NotificationService notices;
    private ChannelAssignmentRoutingService assignments;
    private LegacyTicketIdJdbcGuard guard;
    private AlertQueueService queue;

    @BeforeEach void prepare() {
        users=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("channel_notice_identity");
        users.execute("CREATE TABLE users(username TEXT UNIQUE,enabled BOOLEAN,is_blocked BOOLEAN,department TEXT,last_portal_activity_at TEXT)");
        notices=mock(NotificationService.class);assignments=mock(ChannelAssignmentRoutingService.class);guard=mock(LegacyTicketIdJdbcGuard.class);
        when(guard.hasUniqueNonGeneralTicket(any())).thenReturn(true);
        when(notices.buildDialogUrl(any())).thenReturn("/dialogs?ticketId=T");
        when(notices.notifyDialogUsers(any(),anySet(),any(),any())).thenAnswer(call->call.getArgument(1));
        when(notices.notifyScopedDialogUsers(any(),anySet(),any(),any())).thenAnswer(call->call.getArgument(1));
        when(assignments.resolveRecipients(any(),any(),any())).thenReturn(ChannelAssignmentRoutingService.ResolvedAssignmentRouting.disabled());
        queue=new AlertQueueService(users,notices,mapper,assignments,guard);
    }

    @ParameterizedTest @ValueSource(strings={" reader","reader "," reader ","\treader\t","\nreader\r"})
    void departmentRecipientsKeepTheirDatabaseIdentity(String identity) throws Exception {
        account("reader","Elsewhere",null);account(identity,"Support",null);
        assertThat(send(channel(route("department_all","all",List.of(),List.of()),"nested"))).containsExactly(identity);
    }

    @ParameterizedTest @ValueSource(strings={" reader","reader "," reader ","\treader\t","\nreader\r"})
    void typedSelectorCannotBorrowTheTrimmedAccountsDepartment(String identity) throws Exception {
        account("reader","Support",null);account(identity,"Elsewhere",null);
        assertThat(send(channel(route("employees_only","all",List.of(identity.toUpperCase(Locale.ROOT)),List.of()),"nested"))).isEmpty();
        verify(notices,never()).notifyDialogUsers(any(),anySet(),any(),any());
    }

    @ParameterizedTest @ValueSource(strings={"reader"," reader "})
    void exclusionsRemoveOnlyTheExactAccount(String excluded) throws Exception {
        account("reader","Support",null);account(" reader ","Support",null);
        assertThat(send(channel(route("department_except","all",List.of(),List.of(excluded.toUpperCase(Locale.ROOT))),"nested")))
            .containsExactly(excluded.equals("reader")?" reader ":"reader");
    }

    @ParameterizedTest @ValueSource(strings={"reader"," reader "})
    void onlineFilterDoesNotTransferActivityBetweenAccounts(String online) throws Exception {
        String other=online.equals("reader")?" reader ":"reader";
        account(other,"Support",OffsetDateTime.now().minusHours(2).toString());account(online,"Support",OffsetDateTime.now().minusMinutes(1).toString());
        for(String mode:List.of("department_all","all_operators")) {
            when(notices.findAllOperatorRecipients()).thenReturn(Set.of("reader"," reader "));
            assertThat(send(channel(route(mode,"online_only_fallback_all",List.of(),List.of()),"nested"))).containsExactly(online);
        }
    }

    @Test void noOnlineUsersKeepBothExactFallbackNamesAndCaseDeduplication() throws Exception {
        account("reader","Support",null);account(" reader ","Support",null);
        when(notices.findAllOperatorRecipients()).thenReturn(Set.of("reader","READER"," reader "," READER "));
        assertThat(send(channel(route("all_operators","online_only_fallback_all",List.of(),List.of()),"nested")))
            .containsExactlyInAnyOrder("reader"," reader ");
    }

    @ParameterizedTest @ValueSource(strings={" reader","reader "," reader ","\treader\t","\nreader\r"})
    void assignmentRecipientHandoffKeepsTheResolvedLiteralKey(String identity) throws Exception {
        when(assignments.resolveRecipients(any(),any(),any())).thenReturn(new ChannelAssignmentRoutingService.ResolvedAssignmentRouting(
            true,List.of(identity.toUpperCase(Locale.ROOT),identity,"","  "),null,"single_operator","round_robin"));
        assertThat(send(channel(route("department_all","all",List.of(),List.of()),"nested"))).containsExactly(identity);
        // This verifies the queue handoff only; assignment resolver and writer have their own audit gate.
    }

    @ParameterizedTest @ValueSource(strings={" reader","reader "," reader ","\treader\t","\nreader\r"})
    void channelSaveAndReloadPreserveTypedNotificationArrays(String identity) throws Exception {
        for(String shape:List.of("nested","direct","legacy")) {
            Channel channel=channel(route("employees_only","all",List.of(),List.of()),shape);
            var repository=mock(ChannelRepository.class);when(repository.findById(7L)).thenReturn(Optional.of(channel));
            when(repository.save(any())).thenAnswer(call->call.getArgument(0));
            var transport=new ChannelTransportService(repository,mapper,mock(SharedConfigService.class),mock(IntegrationNetworkService.class));
            var routing=route("employees_only","all",List.of(identity.toUpperCase(Locale.ROOT),identity,"reader","READER","","  "),List.of(identity));
            var payload=mapper.readValue(channel(routing,shape).getQuestionsCfg(),Map.class);
            assertThat(transport.updateChannel(7,Map.of("questions_cfg",payload)).getStatusCode().value()).isEqualTo(200);
            var root=mapper.readTree(channel.getQuestionsCfg());
            var saved=shape.equals("legacy")?root.path("alertQueue"):root.path("panelNotifications").path("routing");
            assertThat(mapper.convertValue(saved.path("employeeUsernames"),List.class)).containsExactly(identity,"reader");
            assertThat(mapper.convertValue(saved.path("excludeUsernames"),List.class)).containsExactly(identity);
            verify(repository).save(channel);
        }
    }

    @ParameterizedTest @ValueSource(strings={"nested","direct","legacy"})
    void everySupportedNotificationShapeUsesExactKeys(String shape) throws Exception {
        account("reader","Support",null);account(" reader ","Support",null);
        assertThat(send(channel(route("employees_only","all",List.of(" READER "," reader ",""," "),List.of()),shape))).containsExactly(" reader ");
    }

    @ParameterizedTest @ValueSource(strings={"disabled","blocked","moved","deleted"})
    void currentDepartmentDirectoryIsRecheckedAfterEveryDispatch(String mutation) throws Exception {
        account("reader","Support",null);account(" reader ","Support",null);
        Channel channel=channel(route("employees_only","all",List.of(" READER "),List.of()),"nested");
        assertThat(send(channel)).containsExactly(" reader ");clearInvocations(notices);
        switch(mutation) {
            case "disabled"->users.update("UPDATE users SET enabled=FALSE WHERE username=' reader '");
            case "blocked"->users.update("UPDATE users SET is_blocked=TRUE WHERE username=' reader '");
            case "moved"->users.update("UPDATE users SET department='Other' WHERE username=' reader '");
            case "deleted"->users.update("DELETE FROM users WHERE username=' reader '");
        }
        assertThat(send(channel)).isEmpty();verify(notices,never()).notifyDialogUsers(any(),anySet(),any(),any());
    }

    @Test void scopedDeliveryCarriesTheFrozenOwnerAndUnchangedRecipient() throws Exception {
        account(" reader ","Support",null);
        var scope=new UiEventTicketScope(10L,"T","a".repeat(32),"BUSINESS",1L);
        var result=queue.notifyScopedQueueForNewPublicAppealRecipients(channel(route("department_all","all",List.of(),List.of()),"nested"),scope," private ");
        assertThat(result).containsExactly(" reader ");
        verify(notices).notifyScopedDialogUsers(eq(scope),eq(Set.of(" reader ")),any(),eq("/dialogs?ticketId=T"));
        verify(notices,never()).notifyDialogUsers(any(),anySet(),any(),any());
    }

    @Test void eventDisableAndRootGuardStillPreventDelivery() throws Exception {
        account(" reader ","Support",null);
        Channel channel=channel(route("department_all","all",List.of(),List.of()),"nested");
        channel.setQuestionsCfg(channel.getQuestionsCfg().replace("\"newPublicAppeal\":true","\"newPublicAppeal\":false"));
        assertThat(send(channel)).isEmpty();
        when(guard.hasUniqueNonGeneralTicket(any())).thenReturn(false);
        assertThat(send(channel(route("department_all","all",List.of(),List.of()),"nested"))).isEmpty();
        verify(notices,never()).notifyDialogUsers(any(),anySet(),any(),any());
    }

    private void account(String username,String department,String activity) {
        users.update("INSERT INTO users VALUES(?,TRUE,FALSE,?,?)",username,department,activity);
    }
    private Map<String,Object> route(String mode,String delivery,List<String> employees,List<String> exclusions) {
        return Map.of("department"," Support ","targetMode",mode,"deliveryMode",delivery,"employeeUsernames",employees,"excludeUsernames",exclusions);
    }
    private Channel channel(Map<String,Object> route,String shape) throws Exception {
        var channel=new Channel();channel.setId(7L);channel.setChannelName("Identity test");channel.setPlatform("web");
        var value=new LinkedHashMap<>(route);value.put("enabled",true);
        Object payload=shape.equals("legacy")?Map.of("alertQueue",value):Map.of("panelNotifications",shape.equals("direct")?value:
            Map.of("routing",route,"events",Map.of("newPublicAppeal",true)));
        channel.setQuestionsCfg(mapper.writeValueAsString(payload));return channel;
    }
    private Set<String> send(Channel channel) {return queue.notifyQueueForNewPublicAppealRecipients(channel,"T"," private ");}
}
