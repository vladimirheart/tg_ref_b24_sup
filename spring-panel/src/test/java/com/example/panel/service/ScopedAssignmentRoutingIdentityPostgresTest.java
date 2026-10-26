package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.entity.Channel;
import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.model.dialog.DialogOperatorOption;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.jdbc.core.JdbcTemplate;

class ScopedAssignmentRoutingIdentityPostgresTest {
    private static JdbcTemplate jdbc;
    private final ObjectMapper mapper=new ObjectMapper();
    @TempDir Path settingsDir;
    private long businessA,businessB,channelId,literalId;
    private String plain,literal;
    private UiEventTicketScope ownA,ownB;
    private DialogParticipantService directory;
    private ScopedAssignmentCandidateService candidates;
    private DialogLookupReadService globalReads;
    private ChannelAssignmentRoutingService channels;
    private SlaEscalationAutoAssignService sla;
    private DialogResponsibilityService channelWriter;
    private ScopedSlaAssignmentService slaWriter;

    @BeforeAll static void schema() {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("assignment_routing_identity");
        Flyway.configure().dataSource(jdbc.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").load().migrate();
    }
    @BeforeEach void prepare() {
        String suffix=UUID.randomUUID().toString();plain="routing-r177-"+suffix;literal=" "+plain+" ";
        businessA=business();businessB=business();
        channelId=jdbc.queryForObject("INSERT INTO channels(token,channel_name,platform) VALUES (?,'R177','web') RETURNING id",Long.class,suffix);
        long plainId=account(plain);literalId=account(literal);
        jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES (?,?,'OPERATOR'),(?,?,'OPERATOR')",plainId,businessA,literalId,businessB);
        ownA=owner(businessA);ownB=owner(businessB);
        var flags=new MultibusinessFeatureFlags();flags.getFoundation().setEnabled(true);flags.setStrictReadScope(true);
        var guard=new LegacyTicketIdJdbcGuard(jdbc);
        directory=new DialogParticipantService(jdbc,jdbc,guard,flags);candidates=new ScopedAssignmentCandidateService(jdbc,flags);
        globalReads=mock(DialogLookupReadService.class);
        channels=new ChannelAssignmentRoutingService(mapper,mock(SharedConfigService.class),directory,globalReads,null,candidates);
        sla=new SlaEscalationAutoAssignService(globalReads,null,candidates);
        channelWriter=new DialogResponsibilityService(jdbc,guard,flags);slaWriter=new ScopedSlaAssignmentService(jdbc,flags);
    }
    static Stream<Arguments> aliases() {
        return Stream.of("channel-single","channel-pool","sla-single","sla-pool","sla-fallback")
            .flatMap(path->Stream.of("prefix","suffix","both","tab","newline").map(variant->Arguments.of(path,variant)));
    }
    @ParameterizedTest @MethodSource("aliases")
    void literalSelectorCannotBorrowTheTrimmedAccountsOperate(String path,String variant) throws Exception {
        changeLiteral(variant);var before=snapshot(ownA);
        assertThat(resolve(path,ownA,literal.toUpperCase(Locale.ROOT),false)).isNull();
        assertThat(snapshot(ownA)).isEqualTo(before);verifyNoInteractions(globalReads);
    }
    @ParameterizedTest @MethodSource("aliases")
    void settingsDirectoryRoutingAndWriterKeepTheRealLiteralAccount(String path,String variant) throws Exception {
        changeLiteral(variant);
        String target=resolve(path,ownB,literal.toUpperCase(Locale.ROOT),true);
        assertThat(target).isEqualToIgnoringCase(literal);
        assertThat(write(path,ownB,target)).isTrue();
        assertThat(jdbc.queryForObject("SELECT responsible FROM ticket_responsibles WHERE ticket_id=?",String.class,ownB.ticketId())).isEqualTo(target);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_responsibles WHERE ticket_id=?",Long.class,ownA.ticketId())).isZero();
        if(path.startsWith("sla"))assertThat(jdbc.queryForObject("SELECT assignee FROM scoped_sla_assignment_events WHERE ticket_id=?",String.class,ownB.ticketId())).isEqualTo(target);
        verifyNoInteractions(globalReads);
    }
    @ParameterizedTest @ValueSource(strings={"prefix","suffix","both","tab","newline"})
    void bothAssignableCatalogsKeepSeparateLiteralKeysAndTrimOnlyLabels(String variant) {
        String decoy="r177_decoy_"+UUID.randomUUID().toString().replace("-","");
        jdbc.execute("CREATE SCHEMA "+decoy);
        jdbc.execute("CREATE TABLE "+decoy+".users(username TEXT,role TEXT)");
        changeLiteral(variant);
        assertThat(directory.loadAssignableOperators(Set.of(literal.toUpperCase(Locale.ROOT))))
            .singleElement().satisfies(option->{assertThat(option.username()).isEqualTo(literal);assertThat(option.department()).isEqualTo("Support");});
        assertThat(directory.loadAssignableOperators()).extracting(DialogOperatorOption::username).contains(plain,literal);
        var payload=channels.loadCatalogPayload();
        assertThat((List<Map<String,Object>>)payload.get("operators")).extracting(row->row.get("username")).contains(plain,literal);
        assertThat(directory.loadAssignableOperators(Set.of())).isEmpty();
        assertThat(directory.loadAssignableOperators(Set.of(" \t"))).isEmpty();
    }
    @Test void channelSaveDeduplicatesCaseWithoutMergingLiteralAndPlainKeys() throws Exception {
        var channel=saveChannel(Map.of("enabled",true,"mode","operator_pool","operatorUsername",literal.toUpperCase(Locale.ROOT),
            "operatorUsernames",List.of(literal.toUpperCase(Locale.ROOT),literal,plain,plain.toUpperCase(Locale.ROOT),""," \t"),"department"," Support "));
        var saved=mapper.readTree(channel.getQuestionsCfg()).path("assignmentRouting");
        assertThat(saved.path("operatorUsername").asText()).isEqualTo(literal);
        assertThat(mapper.convertValue(saved.path("operatorUsernames"),List.class)).containsExactly(literal,plain);
        assertThat(saved.path("department").asText()).isEqualTo("Support");
    }
    @ParameterizedTest @ValueSource(strings={"hash_by_ticket","round_robin","least_loaded"})
    void poolAndDepartmentStrategiesKeepBothAccountsAndTheirExactWorkloads(String strategy) throws Exception {
        jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES (?,?,'MANAGER')",literalId,businessA);
        for(int i=0;i<2;i++) {var loaded=owner(businessA);jdbc.update("INSERT INTO ticket_responsibles(ticket_id,responsible) VALUES (?,?)",loaded.ticketId(),plain);}
        var foreign=owner(businessB);jdbc.update("INSERT INTO ticket_responsibles(ticket_id,responsible) VALUES (?,?)",foreign.ticketId(),literal);
        assertThat(candidates.capture(ownA,channelId).orElseThrow().openCounts()).containsEntry(plain,2L).containsEntry(literal,0L);
        for(String mode:List.of("operator_pool","department_queue")) {
            var channel=saveChannel(channelRule(mode,strategy,List.of(plain,literal),literal));
            var resolved=channels.resolveScoped(channel,ChannelAssignmentRoutingService.RoutingEvent.NEW_PUBLIC_APPEAL,ownA);
            assertThat(resolved.recipients()).containsExactlyInAnyOrder(plain,literal);
            assertThat(resolved.assignee()).isIn(plain,literal);
            if(strategy.equals("least_loaded"))assertThat(resolved.assignee()).isEqualTo(literal);
        }
        var config=slaConfig("sla-pool",literal);
        config.put("sla_critical_auto_assign_rules",List.of(Map.of("match_channel","web","assign_to_pool",List.of(plain,literal),"assign_to_pool_strategy",strategy)));
        var decision=decisions(ownA,config,null,0).get(0);assertThat(decision.assignee()).isIn(plain,literal);
        if(strategy.equals("least_loaded"))assertThat(decision.assignee()).isEqualTo(literal);
        config.put("sla_critical_auto_assign_max_open_per_operator",1);
        assertThat(decisions(ownA,config,null,0)).singleElement().satisfies(d->assertThat(d.assignee()).isEqualTo(literal));
        verifyNoInteractions(globalReads);
    }
    @ParameterizedTest @ValueSource(strings={"skills","queues"})
    void slaOperatorMetadataCannotBeBorrowedFromTheOtherIdentity(String kind) {
        String key="sla_critical_operator_"+kind,required="required_assignee_"+kind;
        var config=slaConfig("sla-single",literal);
        config.put("sla_critical_auto_assign_rules",List.of(Map.of("match_channel","web","assign_to",literal,required,List.of(" Support "))));
        config.put(key,Map.of(plain,List.of("support")));
        assertThat(decisions(ownB,config,null,0)).isEmpty();
        config.put(key,Map.of(literal.toUpperCase(Locale.ROOT),List.of("support")));
        assertThat(decisions(ownB,config,null,0)).singleElement().satisfies(d->assertThat(d.assignee()).isEqualTo(literal));
        config.put("sla_critical_auto_assign_rules",List.of(Map.of("match_channel","web","assign_to",plain,required,List.of("support"))));
        assertThat(decisions(ownA,config,null,0)).isEmpty();verifyNoInteractions(globalReads);
    }
    @Test void slaReassignmentDoesNotMistakeTheOtherLiteralAccountForCurrentResponsible() {
        jdbc.update("INSERT INTO ticket_responsibles(ticket_id,responsible) VALUES (?,?)",ownA.ticketId(),literal);
        var config=slaConfig("sla-fallback",plain);config.put("sla_critical_auto_assign_include_assigned",true);
        var decision=decisions(ownA,config,literal,1).get(0);
        assertThat(decision.previousResponsible()).isEqualTo(literal);
        assertThat(slaWriter.assign(decision.context(),decision.assignee(),"sla_orchestrator",decision.source(),decision.route())).isTrue();
        assertThat(jdbc.queryForMap("SELECT responsible FROM ticket_responsibles WHERE ticket_id=?",ownA.ticketId())).containsEntry("responsible",plain);
        assertThat(jdbc.queryForMap("SELECT previous_responsible,assignee FROM scoped_sla_assignment_events WHERE ticket_id=?",ownA.ticketId()))
            .containsEntry("previous_responsible",literal).containsEntry("assignee",plain);
    }
    @ParameterizedTest @ValueSource(strings={"disabled","blocked","duplicate","viewer","revoked","archived","foreign","channel-disabled","owner-replaced"})
    void currentEligibilityAndOwnerAreRecheckedWithoutFallingBackToPlainAccount(String mutation) throws Exception {
        var channel=saveChannel(channelRule("single_operator","round_robin",List.of(literal),literal));
        assertThat(channels.resolveScoped(channel,ChannelAssignmentRoutingService.RoutingEvent.NEW_PUBLIC_APPEAL,ownB).assignee()).isEqualTo(literal);
        assertThat(decisions(ownB,slaConfig("sla-fallback",literal),null,0)).hasSize(1);
        switch(mutation) {
            case "disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?",literalId);
            case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=?",literalId);
            case "duplicate"->account(literal.toUpperCase(Locale.ROOT));
            case "viewer"->jdbc.update("UPDATE business_memberships SET role_code='VIEWER' WHERE user_id=?",literalId);
            case "revoked"->jdbc.update("DELETE FROM business_memberships WHERE user_id=?",literalId);
            case "archived"->jdbc.update("UPDATE businesses SET status='archived' WHERE id=?",businessB);
            case "foreign"->jdbc.update("UPDATE business_memberships SET business_id=? WHERE user_id=?",businessA,literalId);
            case "channel-disabled"->jdbc.update("UPDATE channels SET is_active=FALSE WHERE id=?",channelId);
            case "owner-replaced"->{
                jdbc.update("DELETE FROM tickets WHERE ticket_id=?",ownB.ticketId());
                jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,channel_id,status) VALUES (?,?,?,?,?,'open')",
                    ownB.ticketUserId(),ownB.ticketId(),UUID.randomUUID().toString().replace("-",""),businessB,channelId);
            }
        }
        var before=snapshot(ownB);
        assertThat(channels.resolveScoped(channel,ChannelAssignmentRoutingService.RoutingEvent.NEW_PUBLIC_APPEAL,ownB).assignee()).isNull();
        assertThat(decisions(ownB,slaConfig("sla-fallback",literal),null,0)).isEmpty();
        // The create callback supplies only a selected assignee; channel admission belongs to the resolver.
        String createTarget=mutation.equals("channel-disabled")
            ? channels.resolveScoped(channel,ChannelAssignmentRoutingService.RoutingEvent.NEW_PUBLIC_APPEAL,ownB).assignee() : literal;
        assertThat(channelWriter.assignScopedResponsibleIfMissing(ownB,createTarget,"routing")).isNull();
        assertThat(slaWriter.assign(frozen(ownB,null,0),literal,"routing","rules","r177")).isFalse();
        assertThat(snapshot(ownB)).isEqualTo(before);verifyNoInteractions(globalReads);
    }
    private String resolve(String path,UiEventTicketScope scope,String target,boolean roundTrip) throws Exception {
        if(path.startsWith("channel")) {
            var rule=channelRule(path.endsWith("single")?"single_operator":"operator_pool","round_robin",List.of(target),target);
            Channel channel=roundTrip?saveChannel(rule):channel(mapper.writeValueAsString(Map.of("assignmentRouting",rule)));
            return channels.resolveScoped(channel,ChannelAssignmentRoutingService.RoutingEvent.NEW_PUBLIC_APPEAL,scope).assignee();
        }
        var config=slaConfig(path,target);
        if(roundTrip) {
            var payload=new LinkedHashMap<String,Object>();config.forEach((key,value)->payload.put("dialog_"+key,value));
            var saved=new LinkedHashMap<String,Object>();new SettingsDialogSlaAiConfigService().applySettings(payload,saved);
            var store=new SharedConfigService(mapper,settingsDir.toString());store.saveSettings(Map.of("dialog_config",saved));
            config=(Map<String,Object>)new SharedConfigService(mapper,settingsDir.toString()).loadSettings().get("dialog_config");
        }
        var result=decisions(scope,config,null,0);return result.isEmpty()?null:result.get(0).assignee();
    }
    private Map<String,Object> channelRule(String mode,String strategy,List<String> pool,String single) {
        return Map.of("enabled",true,"mode",mode,"strategy",strategy,"assignResponsibleOnCreate",true,
            "operatorUsername",single,"operatorUsernames",pool,"department"," Support ");
    }
    private Channel saveChannel(Map<String,Object> rule) throws Exception {
        var repository=mock(ChannelRepository.class);var existing=channel("{}");when(repository.findById(channelId)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(call->{Channel value=call.getArgument(0);jdbc.update("UPDATE channels SET questions_cfg=? WHERE id=?",value.getQuestionsCfg(),channelId);return value;});
        var transport=new ChannelTransportService(repository,mapper,mock(SharedConfigService.class),mock(IntegrationNetworkService.class));
        assertThat(transport.updateChannel(channelId,Map.of("questions_cfg",Map.of("assignmentRouting",rule))).getStatusCode().value()).isEqualTo(200);
        verify(repository).save(existing);
        return channel(jdbc.queryForObject("SELECT questions_cfg FROM channels WHERE id=?",String.class,channelId));
    }
    private Channel channel(String config) {var channel=new Channel();channel.setId(channelId);channel.setPlatform("web");channel.setQuestionsCfg(config);return channel;}
    private Map<String,Object> slaConfig(String path,String target) {
        var config=new LinkedHashMap<String,Object>();config.put("sla_critical_auto_assign_enabled",true);
        if(path.equals("sla-fallback"))config.put("sla_critical_auto_assign_to",target);
        else config.put("sla_critical_auto_assign_rules",List.of(path.equals("sla-single")?Map.of("match_channel","web","assign_to",target):
            Map.of("match_channel","web","assign_to_pool",List.of(target),"assign_to_pool_strategy","round_robin")));
        return config;
    }
    private List<SlaEscalationWebhookNotifier.AutoAssignDecision> decisions(UiEventTicketScope scope,Map<String,Object> config,String previous,long revision) {
        return sla.resolveScopedAutoAssignDecisions(List.of(Map.of("ticket_id",scope.ticketId(),"channel","web")),config,List.of(frozen(scope,previous,revision)));
    }
    private DialogLookupReadService.ScopedSlaDialog frozen(UiEventTicketScope scope,String previous,long revision) {
        var dialog=new DialogListItem(scope.ticketId(),"REQ",scope.ticketUserId(),"client","Client","R177",channelId,"web",null,null,"Help","2026-01-01T00:00:00Z",
            "open",false,null,null,previous,null,null,null,null,null,0,null,null,null,null);
        return new DialogLookupReadService.ScopedSlaDialog(dialog,scope,channelId,revision,previous);
    }
    private boolean write(String path,UiEventTicketScope scope,String target) {
        return path.startsWith("channel")?channelWriter.assignScopedResponsibleIfMissing(scope,target,"routing")!=null:
            slaWriter.assign(frozen(scope,null,0),target,"sla_orchestrator","rules","r177");
    }
    private void changeLiteral(String variant) {
        literal=switch(variant){case "prefix"->" "+plain;case "suffix"->plain+" ";case "tab"->"\t"+plain+"\t";case "newline"->"\n"+plain+"\r";default->" "+plain+" ";};
        jdbc.update("UPDATE users SET username=? WHERE id=?",literal,literalId);
    }
    private Map<String,Object> snapshot(UiEventTicketScope scope) {
        return Map.of("root",jdbc.queryForList("SELECT * FROM tickets WHERE ticket_id=? ORDER BY user_id",scope.ticketId()),
            "assignment",jdbc.queryForList("SELECT * FROM ticket_responsibles WHERE ticket_id=?",scope.ticketId()),
            "events",jdbc.queryForList("SELECT * FROM scoped_sla_assignment_events WHERE ticket_id=? ORDER BY revision",scope.ticketId()));
    }
    private long account(String username) {return jdbc.queryForObject("INSERT INTO users(username,password,department) VALUES (?,'unused',' Support ') RETURNING id",Long.class,username);}
    private long business() {return jdbc.queryForObject("INSERT INTO businesses(code,display_name) VALUES (?,'R177') RETURNING id",Long.class,"r177-"+UUID.randomUUID());}
    private UiEventTicketScope owner(long business) {
        long user=System.nanoTime();String id=" r177/"+UUID.randomUUID()+" ",pub=UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,channel_id,status) VALUES (?,?,?,?,?,'open')",user,id,pub,business,channelId);
        return new UiEventTicketScope(user,id,pub,"BUSINESS",business);
    }
}
