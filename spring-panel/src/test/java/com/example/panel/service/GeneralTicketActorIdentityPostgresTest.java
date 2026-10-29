package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.runtime.UiEventFanoutProperties;
import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.storage.AttachmentObjectStorageService;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import static com.example.panel.support.GeneralTicketActorTestSupport.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Frozen SQL/transaction regression across GENERAL work, delivery and long-lived streams. */
class GeneralTicketActorIdentityPostgresTest {
    enum Operation { workFind,open,close,reopen,assignment,assignmentCandidates,assignmentHistory,assign,
        revision,members,participantCandidates,participantHistory,add,remove,summary,intervals,
        replyDestination,replies,reply,prepareReply,changeTargets,changes,change,prepareChange,
        documentDestination,documents,document,prepareDocument,canCreate,create,canRead,connect }
    private static final String RAW=" raw/Ю?%#+ ",PUBLIC="a".repeat(32),SOURCE="1".repeat(32);
    private static final long OWNER=9007199254740993L;
    private static final GeneralTicketEventScope SCOPE=new GeneralTicketEventScope(OWNER,RAW,PUBLIC);
    private static final Set<Operation> OPERATE=EnumSet.of(Operation.open,Operation.close,Operation.reopen,Operation.assignmentCandidates,
        Operation.assign,Operation.participantCandidates,Operation.add,Operation.remove,Operation.replyDestination,Operation.reply,
        Operation.prepareReply,Operation.changeTargets,Operation.change,Operation.prepareChange,Operation.documentDestination,
        Operation.document,Operation.prepareDocument,Operation.canCreate,Operation.create);
    private static final Set<Operation> WRITES=EnumSet.of(Operation.open,Operation.close,Operation.reopen,Operation.assign,Operation.add,
        Operation.remove,Operation.reply,Operation.prepareReply,Operation.change,Operation.prepareChange,Operation.document,Operation.prepareDocument,Operation.create);
    private static final List<String> DATA=List.of("tickets","messages","chat_history","general_ticket_work_events","general_ticket_work_intervals",
        "general_ticket_assignment_events","general_ticket_participants","general_ticket_participant_events","general_ticket_text_replies",
        "general_ticket_reply_events","general_ticket_reply_changes","general_ticket_reply_change_events","general_ticket_document_replies",
        "general_ticket_document_events","manual_general_clients","general_manual_ticket_requests","ui_event_outbox","chat_attachment_metadata");
    private static JdbcTemplate jdbc;
    private static SingleConnectionDataSource connection;
    private GeneralTicketWorkService work;
    private GeneralTicketWorkTimeService time;
    private GeneralTicketAssignmentService assignment;
    private GeneralTicketParticipantService participants;
    private GeneralTicketReplyService replies;
    private GeneralTicketReplyChangeService changes;
    private GeneralTicketDocumentReplyService documents;
    private GeneralManualTicketCreationService manual;
    private GeneralTicketRealtimeService realtime;
    private DialogReplyTransportService transport;
    private AttachmentObjectStorageService storage;
    private Authentication actor;
    private long actorId,revision;
    private String name,replyDestination,documentDestination,expected,key;
    @BeforeAll static void schema()throws Exception{
        var fresh=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("general_actor_identity");
        connection=new SingleConnectionDataSource(fresh.getDataSource().getConnection(),true);jdbc=new JdbcTemplate(connection);
        assertThat(jdbc.queryForObject("SELECT current_schema()",String.class)).startsWith("general_actor_identity_");
        jdbc.execute("SET client_min_messages=warning");
        Flyway.configure().dataSource(connection).locations("filesystem:src/main/resources/db/migration/postgresql").load().migrate();
    }
    @AfterAll static void closeConnection(){if(connection!=null)connection.destroy();}
    @BeforeEach void setup(TestInfo test)throws Exception{
        if(!Set.of("exactRawNamesUseTheSameBoundSqlAccount","bigintActorIdsRemainExact").contains(test.getTestMethod().orElseThrow().getName()))seed(2L,"caller");
    }
    private void seed(long id,String rawName)throws Exception{
        jdbc.execute("TRUNCATE users,channels,messages,tickets,bot_users,manual_business_clients,manual_general_clients,chat_history CASCADE");
        // Audit tables intentionally retain actors without a users FK; reset the complete dedicated fixture.
        jdbc.execute("TRUNCATE "+String.join(",",DATA)+" CASCADE");
        actorId=id;name=rawName;actor=auth(id,name);key=UUID.randomUUID().toString().replace("-","");revision=2;
        jdbc.update("INSERT INTO users(id,username,password,enabled,is_blocked) OVERRIDING SYSTEM VALUE VALUES(?,?,'unused',TRUE,FALSE),(6,'target','unused',TRUE,FALSE),(7,'member','unused',TRUE,FALSE)",id,name);
        jdbc.update("INSERT INTO general_ticket_read_grants(user_id) VALUES(?),(6),(7)",id);
        jdbc.update("INSERT INTO general_ticket_operate_grants(user_id) VALUES(?),(6)",id);
        jdbc.update("INSERT INTO channels(id,token,channel_name,platform,is_active) OVERRIDING SYSTEM VALUE VALUES(9,'fixture-general-token','fixture','telegram',TRUE)");
        jdbc.update("INSERT INTO messages(group_msg_id,user_id,ticket_id,channel_id,problem) VALUES(?,?,?,9,'private problem')",OWNER,OWNER,RAW);
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,group_msg_id,channel_id,ticket_public_id,is_general,general_selection_source,status) VALUES(?,?,?,9,?,TRUE,'GENERAL_CHANNEL','pending')",OWNER,RAW,OWNER,PUBLIC);
        for(String table:List.of("users","tickets","messages","channels","general_ticket_read_grants","general_ticket_operate_grants"))jdbc.execute("ANALYZE "+table);
        var flags=new MultibusinessFeatureFlags();flags.getFoundation().setEnabled(true);flags.setGeneralTicketQueue(true);flags.setManualTicketCreation(true);
        var ids=new UiEventOutboxAppendService(jdbc,new LegacyTicketIdJdbcGuard(jdbc));
        work=new GeneralTicketWorkService(jdbc,ids,flags);time=new GeneralTicketWorkTimeService(jdbc,flags);
        assignment=new GeneralTicketAssignmentService(jdbc,flags,ids);participants=new GeneralTicketParticipantService(jdbc,flags,ids);
        transport=mock(DialogReplyTransportService.class);var providerIds=new AtomicLong(100);
        when(transport.sendTextGuarded(any(),anyLong(),anyString(),isNull(),any())).thenAnswer(c->DialogReplyTransportService.DialogReplyTransportResult.success(providerIds.incrementAndGet(),1L));
        when(transport.editTelegramMessageGuarded(any(),anyLong(),anyLong(),anyString(),any())).thenReturn(DialogReplyTransportService.DialogReplyTransportResult.success(101L,1L));
        when(transport.deleteTelegramMessageGuarded(any(),anyLong(),anyLong(),any())).thenReturn(DialogReplyTransportService.DialogReplyTransportResult.success(101L,1L));
        when(transport.sendTelegramDocumentGuarded(any(),anyLong(),any(),anyString(),any())).thenAnswer(c->DialogReplyTransportService.DialogReplyTransportResult.success(providerIds.incrementAndGet(),1L));
        storage=mock(AttachmentObjectStorageService.class);when(storage.providerLabel()).thenReturn("s3");
        when(storage.storeDialogAttachment(anyString(),anyString(),anyString(),any())).thenAnswer(c->{byte[] bytes=((java.io.InputStream)c.getArgument(3)).readAllBytes();return new AttachmentObjectStorageService.StoredBinary(c.getArgument(0)+"/"+c.getArgument(1),"application/octet-stream",bytes.length,new ByteArrayInputStream(bytes));});
        replies=new GeneralTicketReplyService(jdbc,flags,transport,ids);changes=new GeneralTicketReplyChangeService(jdbc,flags,transport,ids);
        documents=new GeneralTicketDocumentReplyService(jdbc,flags,transport,storage,ids);manual=new GeneralManualTicketCreationService(jdbc,flags,ids);
        var fanout=new UiEventFanoutProperties();fanout.setMode("local");
        realtime=new GeneralTicketRealtimeService(jdbc,flags,fanout,mock(org.springframework.data.redis.core.StringRedisTemplate.class),new ObjectMapper());
        assertThat((Boolean)call(work,"transition",1,SCOPE,actor,0L,"pending","open")).isTrue();
        assertThat((Boolean)call(work,"transition",1,SCOPE,actor,1L,"open","pending")).isTrue();
        assertThat((Boolean)call(assignment,"assign",1,SCOPE,actor,2L,0L,6L)).isTrue();
        assertThat((Boolean)call(participants,"change",1,SCOPE,actor,2L,0L,7L,true)).isTrue();
        replyDestination=((Optional<String>)call(replies,"destinationFingerprint",1,SCOPE,actor,2L)).orElseThrow();
        assertThat((Boolean)call(replies,"submit",1,SCOPE,actor,2L,SOURCE,"private original",replyDestination)).isTrue();
        expected=((List<GeneralTicketReplyChangeService.Target>)call(changes,"targets",1,SCOPE,actor,2L)).get(0).fingerprint();
        assertThat((Boolean)call(changes,"submit",1,SCOPE,actor,2L,"2".repeat(32),SOURCE,expected,"EDIT","private seeded edit")).isTrue();
        expected=((List<GeneralTicketReplyChangeService.Target>)call(changes,"targets",1,SCOPE,actor,2L)).get(0).fingerprint();
        documentDestination=((Optional<String>)call(documents,"destinationFingerprint",1,SCOPE,actor,2L)).orElseThrow();
        assertThat((Boolean)call(documents,"submit",1,SCOPE,actor,2L,"3".repeat(32),"private document",file(),documentDestination)).isTrue();
        clearInvocations(transport,storage);
    }
    private MockMultipartFile file(){return new MockMultipartFile("file","fixture.bin","application/octet-stream",new byte[]{1,2,3});}
    private void prepare(Operation operation){if(operation==Operation.reopen){assertThat((Boolean)call(work,"transition",1,SCOPE,actor,2L,"pending","closed")).isTrue();revision=3;}}
    private Object perform(Operation op,Authentication reader){return switch(op){
        case workFind->call(work,"find",1,PUBLIC,reader);
        case open,close,reopen->call(work,"transition",1,SCOPE,reader,revision,op==Operation.reopen?"closed":"pending",op==Operation.open?"open":op==Operation.close?"closed":"pending");
        case assignment->call(assignment,"current",1,SCOPE,reader);
        case assignmentCandidates->call(assignment,"candidates",1,SCOPE,reader);
        case assignmentHistory->call(assignment,"recent",1,SCOPE,reader);
        case assign->call(assignment,"assign",1,SCOPE,reader,revision,1L,null);
        case revision->call(participants,"revision",1,SCOPE,reader);
        case members->call(participants,"members",1,SCOPE,reader);
        case participantCandidates->call(participants,"candidates",1,SCOPE,reader,revision);
        case participantHistory->call(participants,"recent",1,SCOPE,reader);
        case add,remove->call(participants,"change",1,SCOPE,reader,revision,1L,op==Operation.add?6L:7L,op==Operation.add);
        case summary->call(time,"summary",1,SCOPE,reader);
        case intervals->call(time,"recent",1,SCOPE,reader);
        case replyDestination->call(replies,"destinationFingerprint",1,SCOPE,reader,revision);
        case replies->call(replies,"recent",1,SCOPE,reader);
        case reply,prepareReply->call(replies,op==Operation.reply?"submit":"prepare",1,SCOPE,reader,revision,key,"private new reply",replyDestination);
        case changeTargets->call(changes,"targets",1,SCOPE,reader,revision);
        case changes->call(changes,"recent",1,SCOPE,reader);
        case change,prepareChange->call(changes,op==Operation.change?"submit":"prepare",1,SCOPE,reader,revision,key,SOURCE,expected,"EDIT","private new edit");
        case documentDestination->call(documents,"destinationFingerprint",1,SCOPE,reader,revision);
        case documents->call(documents,"recent",1,SCOPE,reader);
        case document,prepareDocument->call(documents,op==Operation.document?"submit":"prepare",1,SCOPE,reader,revision,key,"private new document",file(),documentDestination);
        case canCreate->call(manual,"canCreate",0,reader);
        case create->call(manual,"create",0,reader,key,"private client","private manual problem");
        case canRead->call(realtime,"canRead",0,reader,SCOPE);
        case connect->call(realtime,"connect",0,reader);
    };}
    private Map<String,List<String>> snapshot(){var result=new LinkedHashMap<String,List<String>>();for(String table:DATA)result.put(table,jdbc.queryForList("SELECT row_to_json(snapshot_row)::text FROM (SELECT * FROM "+table+" ORDER BY 1,2) snapshot_row",String.class));return result;}
    private void assertResult(Operation op,Authentication reader,boolean allowed){
        var before=snapshot();Object result;try{result=perform(op,reader);}catch(AccessDeniedException e){result=false;}
        boolean visible=result instanceof Boolean b?b:result instanceof Optional<?> value?value.isPresent():result instanceof List<?> list?!list.isEmpty():result!=null;
        assertThat(visible).as(op.name()).isEqualTo(allowed);
        if(!allowed||!WRITES.contains(op))assertThat(snapshot()).isEqualTo(before);
        if(!allowed)verifyNoInteractions(transport,storage);
        if(allowed&&WRITES.contains(op)){
            String table=switch(op){case open,close,reopen->"general_ticket_work_events";case assign->"general_ticket_assignment_events";case add,remove->"general_ticket_participant_events";case reply,prepareReply->"general_ticket_text_replies";case change,prepareChange->"general_ticket_reply_changes";case document,prepareDocument->"general_ticket_document_replies";default->"general_manual_ticket_requests";};
            assertThat(jdbc.queryForList("SELECT actor_user_id,actor FROM "+table)).allSatisfy(row->assertThat(row).containsEntry("actor_user_id",actorId).containsEntry("actor",name));
        }
    }
    static Stream<Arguments> stale(){return Stream.of("rename-reuse","delete-reuse","case-change","wrong-id","unbound","disabled","blocked").flatMap(m->Arrays.stream(Operation.values()).map(o->Arguments.of(m,o)));}
    private void mutate(String mode){switch(mode){
        case "rename-reuse","delete-reuse"->{if(mode.equals("rename-reuse"))jdbc.update("UPDATE users SET username='renamed' WHERE id=?",actorId);else jdbc.update("DELETE FROM users WHERE id=?",actorId);jdbc.update("INSERT INTO users(id,username,password,enabled) OVERRIDING SYSTEM VALUE VALUES(55,?,'unused',TRUE)",name);jdbc.update("INSERT INTO general_ticket_read_grants(user_id) VALUES(55)");jdbc.update("INSERT INTO general_ticket_operate_grants(user_id) VALUES(55)");}
        case "case-change"->jdbc.update("UPDATE users SET username=upper(username) WHERE id=?",actorId);
        case "wrong-id"->actor=auth(55,name);
        case "unbound"->actor=UsernamePasswordAuthenticationToken.authenticated(name,null,List.of());
        case "disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?",actorId);
        case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=?",actorId);
        default->throw new AssertionError(mode);
    }}
    @ParameterizedTest @MethodSource("stale") void aSessionCannotFollowAReplacementAccount(String mode,Operation op){prepare(op);mutate(mode);assertResult(op,actor,false);}
    @ParameterizedTest @EnumSource(Operation.class) void theBoundAccountKeepsItsExistingReadOperateAndPayload(Operation op){prepare(op);assertResult(op,actor,true);}
    @ParameterizedTest @EnumSource(Operation.class) void readAndOperateRemainIndependent(Operation op){prepare(op);jdbc.update("DELETE FROM general_ticket_operate_grants WHERE user_id=?",actorId);assertResult(op,actor,!OPERATE.contains(op));}
    static Stream<Arguments> raw(){return Stream.of(" Caller ","\u0130").flatMap(n->Arrays.stream(Operation.values()).map(o->Arguments.of(n,o)));}
    @ParameterizedTest @MethodSource("raw") void exactRawNamesUseTheSameBoundSqlAccount(String rawName,Operation op)throws Exception{seed(2,rawName);prepare(op);assertResult(op,actor,true);}
    static Stream<Arguments> ids(){return Stream.of(9007199254740993L,Long.MAX_VALUE).flatMap(id->Arrays.stream(Operation.values()).map(o->Arguments.of(id,o)));}
    @ParameterizedTest @MethodSource("ids") void bigintActorIdsRemainExact(long id,Operation op)throws Exception{seed(id,"caller");prepare(op);assertResult(op,actor,true);}
    @ParameterizedTest @EnumSource(Operation.class) void nullableBlockedKeepsTheEstablishedGeneralPolicy(Operation op){
        prepare(op);
        // Synthetic compatibility probe: V83 enforces NOT NULL, while CURRENT_ACCOUNT retains COALESCE policy.
        jdbc.execute("ALTER TABLE users ALTER COLUMN is_blocked DROP NOT NULL");
        try{jdbc.update("UPDATE users SET is_blocked=NULL WHERE id=?",actorId);assertResult(op,actor,true);}
        finally{jdbc.update("UPDATE users SET is_blocked=FALSE WHERE is_blocked IS NULL");jdbc.execute("ALTER TABLE users ALTER COLUMN is_blocked SET NOT NULL");}
    }
    static Stream<Arguments> queued(){return Stream.of("reply","change","document").flatMap(kind->Stream.of("rename-reuse","delete-reuse","case-change","blocked").map(mode->Arguments.of(kind,mode)));}
    @ParameterizedTest @MethodSource("queued") void queuedCommandsRetainTheOriginalIdAndRawName(String kind,String mode){
        Object service=switch(kind){case "reply"->replies;case "change"->changes;default->documents;};
        assertResult(switch(kind){case "reply"->Operation.prepareReply;case "change"->Operation.prepareChange;default->Operation.prepareDocument;},actor,true);
        clearInvocations(transport,storage);mutate(mode);call(service,"dispatch",-1,key);
        String table=switch(kind){case "reply"->"general_ticket_text_replies";case "change"->"general_ticket_reply_changes";default->"general_ticket_document_replies";};
        assertThat(jdbc.queryForObject("SELECT state FROM "+table+" WHERE request_key=?",String.class,key)).isEqualTo("CANCELLED");verifyNoInteractions(transport,storage);
    }
    @ParameterizedTest @ValueSource(strings={"rename-reuse","delete-reuse","case-change","disabled","blocked"})
    void openStreamsDoNotChangeAccountWhenANameIsReused(String mode)throws Exception{
        var emitter=mock(SseEmitter.class);register(realtime,actor,emitter);mutate(mode);
        realtime.publish(new UiEventTicketScope(OWNER,RAW,PUBLIC,"GENERAL",null),"participants_changed");verifyNoInteractions(emitter);
        realtime.sendHeartbeat();verify(emitter).complete();clearInvocations(emitter);realtime.sendHeartbeat();verifyNoInteractions(emitter);
    }
    static Stream<Arguments> boundaries(){return Stream.of("rename-reuse","case-change").flatMap(m->Arrays.stream(Operation.values()).map(o->Arguments.of(m,o)));}
    @ParameterizedTest @MethodSource("boundaries")
    void everyStatementRetainsTheBoundActorAtItsSqlBoundary(String mode,Operation op){
        prepare(op);
        Object service=switch(op){
            case workFind,open,close,reopen->work;case assignment,assignmentCandidates,assignmentHistory,assign->assignment;
            case revision,members,participantCandidates,participantHistory,add,remove->participants;case summary,intervals->time;
            case replyDestination,replies,reply,prepareReply->replies;case changeTargets,changes,change,prepareChange->changes;
            case documentDestination,documents,document,prepareDocument->documents;case canCreate,create->manual;case canRead,connect->realtime;
        };
        String field=service instanceof GeneralTicketReplyService||service instanceof GeneralTicketReplyChangeService||service instanceof GeneralTicketDocumentReplyService?"sql":"jdbc";
        var observed=spy((org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate)org.springframework.test.util.ReflectionTestUtils.getField(service,field));
        var fired=new java.util.concurrent.atomic.AtomicBoolean();
        org.mockito.stubbing.Answer<Object> boundary=invocation->{if(fired.compareAndSet(false,true))mutate(mode);return invocation.callRealMethod();};
        doAnswer(boundary).when(observed).query(anyString(),any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class),org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<Object>>any());
        doAnswer(boundary).when(observed).queryForObject(anyString(),any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class),eq(Boolean.class));
        doAnswer(boundary).when(observed).update(anyString(),any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class));
        org.springframework.test.util.ReflectionTestUtils.setField(service,field,observed);
        assertResult(op,actor,false);assertThat(fired).isTrue();
    }
}
