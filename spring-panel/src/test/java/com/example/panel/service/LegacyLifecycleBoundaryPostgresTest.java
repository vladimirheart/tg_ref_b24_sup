package com.example.panel.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.storage.AttachmentService;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

class LegacyLifecycleBoundaryPostgresTest {
    private static final String RAW=" R166/Ю?%#+ ";
    private DialogListItem legacyItem() {
        return new DialogListItem(RAW,"1",42L,"client","Client",null,9L,"Telegram",
            null,null,null,null,"pending",false,null,null,null,null,null,null,null,0,null,"Old");
    }
    private record Fixture(JdbcTemplate jdbc,LegacyTicketIdJdbcGuard guard,MultibusinessFeatureFlags flags,DialogResponsibilityService assignment) {
        DialogTicketLifecycleService service(JdbcTemplate observed){return new DialogTicketLifecycleService(observed,assignment,guard,flags);}
    }
    private Fixture fixture(boolean reopen,boolean feedback,boolean active) {
        var jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("legacy_lifecycle_boundary");
        jdbc.execute("CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,status TEXT,resolved_at TIMESTAMPTZ,resolved_by TEXT,channel_id BIGINT,"
            +"closed_count INT DEFAULT 4,reopen_count INT DEFAULT 7,last_reopen_at TIMESTAMPTZ,business_id BIGINT,is_general BOOLEAN DEFAULT FALSE,PRIMARY KEY(user_id,ticket_id))");
        jdbc.execute("CREATE TABLE ticket_categories(ticket_id TEXT,category TEXT,created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(ticket_id,category))");
        jdbc.execute("CREATE TABLE ticket_active(ticket_id TEXT PRIMARY KEY,user_identity TEXT,last_seen TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE pending_feedback_requests(user_id BIGINT,channel_id BIGINT,ticket_id TEXT,source TEXT,created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,expires_at TIMESTAMPTZ)");
        jdbc.execute("CREATE TABLE ticket_responsibles(ticket_id TEXT PRIMARY KEY,responsible TEXT,assigned_by TEXT)");
        jdbc.execute("CREATE TABLE dialog_action_audit(ticket_id TEXT,actor TEXT,action TEXT,result TEXT,detail TEXT,created_at TIMESTAMPTZ)");
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,status,channel_id) VALUES(42,?,?,9),(88,?,'Sibling',19)",RAW,reopen?"closed":"pending",RAW.trim());
        jdbc.update("INSERT INTO ticket_categories(ticket_id,category) VALUES(?,'Old'),(?,'Sibling')",RAW,RAW.trim());
        jdbc.update("INSERT INTO ticket_active(ticket_id,user_identity) VALUES(?,'sibling')",RAW.trim());
        if(active)jdbc.update("INSERT INTO ticket_active(ticket_id,user_identity) VALUES(?,'sticky')",RAW);
        if(feedback)jdbc.update("INSERT INTO pending_feedback_requests(user_id,channel_id,ticket_id,source) VALUES(42,9,?,'old')",RAW);
        jdbc.update("INSERT INTO ticket_responsibles VALUES(?,'old','original')",RAW);
        return new Fixture(jdbc,spy(new LegacyTicketIdJdbcGuard(jdbc)),new MultibusinessFeatureFlags(),mock(DialogResponsibilityService.class));
    }
    private Map<String,List<Map<String,Object>>> snapshot(Fixture f) {
        var rows=new LinkedHashMap<String,List<Map<String,Object>>>();
        for(String table:List.of("tickets","ticket_categories","ticket_active","pending_feedback_requests","ticket_responsibles","dialog_action_audit"))
            rows.put(table,f.jdbc.queryForList("SELECT * FROM "+table+" ORDER BY ticket_id"));
        return rows;
    }
    private void mutate(Fixture f,String kind) {
        switch(kind) {
            case "business"->f.jdbc.update("UPDATE tickets SET business_id=11 WHERE ticket_id=?",RAW);
            case "general"->f.jdbc.update("UPDATE tickets SET is_general=TRUE WHERE ticket_id=?",RAW);
            case "strict"->f.flags.setStrictReadScope(true);
            case "missing"->f.jdbc.update("DELETE FROM tickets WHERE ticket_id=?",RAW);
            case "collision"->f.jdbc.update("INSERT INTO tickets(user_id,ticket_id,status) VALUES(99,?,'duplicate')",RAW);
            case "null-marker"->f.jdbc.update("UPDATE tickets SET is_general=NULL WHERE ticket_id=?",RAW);
            case "missing-business"->f.jdbc.execute("ALTER TABLE tickets DROP COLUMN business_id");
            case "missing-marker"->f.jdbc.execute("ALTER TABLE tickets DROP COLUMN is_general");
            default->throw new AssertionError(kind);
        }
    }
    private DialogResolveResult command(DialogTicketLifecycleService service,String action) {
        if(action.equals("categories")){service.setTicketCategories(RAW,List.of(" New ","New"));return null;}
        return action.equals("resolve")?service.resolveTicket(RAW," Mixed Actor ",List.of(" New ","New")):service.reopenTicket(RAW," Mixed Actor ");
    }
    static Stream<Arguments> rejected() {
        return Stream.of("categories","resolve","reopen").flatMap(action->Stream.of("business","general","strict","missing","collision","null-marker","missing-business","missing-marker")
            .map(kind->Arguments.of(action,kind)));
    }
    @ParameterizedTest @MethodSource("rejected")
    void initialNamespaceAndStrictDenialNeverChangesExistingRows(String action,String kind) {
        var f=fixture(action.equals("reopen"),true,true);mutate(f,kind);var before=snapshot(f);
        var result=command(f.service(f.jdbc),action);
        if(result!=null)assertThat(result).isEqualTo(new DialogResolveResult(false,false,null));
        assertThat(snapshot(f)).isEqualTo(before);verifyNoInteractions(f.assignment);
    }
    @ParameterizedTest @MethodSource("rejected")
    void anEarlierUniqueProofCannotAuthorizeAnotherNamespaceOrStrict(String action,String kind) {
        var f=fixture(action.equals("reopen"),true,true);var injected=new AtomicBoolean();var before=new AtomicReference<Map<String,List<Map<String,Object>>>>();
        doAnswer(call->{boolean unique=(boolean)call.callRealMethod();if(unique&&injected.compareAndSet(false,true)){mutate(f,kind);before.set(snapshot(f));}return unique;})
            .when(f.guard).hasUniqueTicket(RAW);
        var result=command(f.service(f.jdbc),action);
        assertThat(injected).isTrue();if(result!=null)assertThat(result.updated()).isFalse();
        assertThat(snapshot(f)).isEqualTo(before.get());verifyNoInteractions(f.assignment);
    }
    private enum Site {
        CATEGORY_DELETE("categories","DELETE FROM ticket_categories"),CATEGORY_INSERT("categories","INSERT INTO ticket_categories"),
        RESOLVE_READ("resolve","SELECT status"),RESOLVE_UPDATE("resolve","UPDATE tickets SET status"),
        REOPEN_READ("reopen","SELECT status"),REOPEN_UPDATE("reopen","UPDATE tickets SET status"),
        CLEAR("resolve","DELETE FROM ticket_active"),FEEDBACK_UPDATE("resolve","UPDATE pending_feedback_requests"),
        FEEDBACK_READ("resolve","SELECT user_id, channel_id"),FEEDBACK_INSERT("resolve","INSERT INTO pending_feedback_requests"),
        RESTORE_READ("reopen","SELECT user_id FROM tickets"),RESTORE_UPDATE("reopen","UPDATE ticket_active"),RESTORE_INSERT("reopen","INSERT INTO ticket_active");
        final String action,prefix;
        Site(String action,String prefix){this.action=action;this.prefix=prefix;}
        boolean matches(String sql){return sql.stripLeading().startsWith(prefix);}
        boolean read(){return prefix.startsWith("SELECT");}
    }
    static Stream<Arguments> boundChanges() {
        return Arrays.stream(Site.values()).flatMap(site->Stream.of("business","general","missing","collision","null-marker","missing-business","missing-marker")
            .map(kind->Arguments.of(site,kind)));
    }
    @ParameterizedTest @MethodSource("boundChanges")
    void everyFinalSqlRepeatsNamespaceAfterTheArgumentsAreBound(Site site,String kind) {
        var f=fixture(site.action.equals("reopen"),site!=Site.FEEDBACK_READ&&site!=Site.FEEDBACK_INSERT,site!=Site.RESTORE_INSERT);
        var observed=spy(f.jdbc);var injected=new AtomicBoolean();var before=new AtomicReference<Map<String,List<Map<String,Object>>>>();
        Runnable change=()->{if(injected.compareAndSet(false,true)){mutate(f,kind);before.set(snapshot(f));}};
        doAnswer(call->{String sql=call.getArgument(0);if(!site.read()&&site.matches(sql))change.run();return call.callRealMethod();})
            .when(observed).update(anyString(),any(Object[].class));
        doAnswer(call->{String sql=call.getArgument(0);if(site.read()&&site.matches(sql))change.run();return call.callRealMethod();})
            .when(observed).query(anyString(),any(ResultSetExtractor.class),any(Object[].class));
        command(f.service(observed),site.action);
        assertThat(injected).isTrue();assertThat(snapshot(f)).isEqualTo(before.get());verifyNoInteractions(f.assignment);
    }
    static Stream<Arguments> rootWaits() {
        return Stream.of("resolve","reopen").flatMap(action->Stream.of("business","general").map(kind->Arguments.of(action,kind)));
    }
    @ParameterizedTest @MethodSource("rootWaits")
    void rootUpdateRechecksNamespaceAfterWaitingForACommittedOwnerChange(String action,String kind) throws Exception {
        var f=fixture(action.equals("reopen"),true,true);
        String tag="r166-"+UUID.randomUUID();
        var observed=new JdbcTemplate(f.jdbc.getDataSource()) {
            @Override public int update(String sql,Object... args) {
                return super.update(sql.startsWith("UPDATE tickets SET status")?sql+" /*"+tag+"*/":sql,args);
            }
        };
        try(var connection=f.jdbc.getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try(var lock=connection.prepareStatement("SELECT user_id FROM tickets WHERE ticket_id=? FOR UPDATE")) {
                lock.setString(1,RAW);try(var rows=lock.executeQuery()){assertThat(rows.next()).isTrue();}
            }
            var result=java.util.concurrent.CompletableFuture.supplyAsync(()->command(f.service(observed),action));
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            while(!Boolean.TRUE.equals(f.jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE pid<>pg_backend_pid() AND wait_event_type='Lock' AND query LIKE ?)",
                Boolean.class,"%/*"+tag+"*/%"))) {
                if(System.nanoTime()>deadline)throw new AssertionError("Lifecycle root UPDATE never entered a real PostgreSQL lock wait");
                Thread.sleep(20);
            }
            try(var change=connection.prepareStatement(kind.equals("business")
                    ?"UPDATE tickets SET business_id=11 WHERE ticket_id=?":"UPDATE tickets SET is_general=TRUE WHERE ticket_id=?")) {
                change.setString(1,RAW);assertThat(change.executeUpdate()).isEqualTo(1);
            }
            connection.commit();
            var before=snapshot(f);
            assertThat(result.get(10,java.util.concurrent.TimeUnit.SECONDS).updated()).isFalse();
            assertThat(snapshot(f)).isEqualTo(before);verifyNoInteractions(f.assignment);
        }
    }
    @ParameterizedTest @ValueSource(strings={"categories","resolve","reopen"})
    void strictStopsBeforeAnyJdbcOrUniqueProbe(String action) {
        var jdbc=mock(JdbcTemplate.class);var guard=mock(LegacyTicketIdJdbcGuard.class);var assignment=mock(DialogResponsibilityService.class);
        var flags=new MultibusinessFeatureFlags();flags.setStrictReadScope(true);
        command(new DialogTicketLifecycleService(jdbc,assignment,guard,flags),action);
        verifyNoInteractions(jdbc,guard,assignment);
    }
    @ParameterizedTest @ValueSource(strings={"categories","resolve","reopen"})
    void unresolvedPreStrictPreservesGoldenNormalizationsAndOpaqueNeighbour(String action) {
        var f=fixture(action.equals("reopen"),true,true);
        var sibling=f.jdbc.queryForMap("SELECT * FROM tickets WHERE ticket_id=?",RAW.trim());
        var result=command(f.service(f.jdbc),action);
        if(result!=null)assertThat(result.updated()).isTrue();
        assertThat(f.jdbc.queryForMap("SELECT * FROM tickets WHERE ticket_id=?",RAW.trim())).isEqualTo(sibling);
        if(!action.equals("reopen"))assertThat(f.jdbc.queryForList("SELECT category FROM ticket_categories WHERE ticket_id=?",String.class,RAW)).containsExactly("New");
        if(action.equals("reopen"))verify(f.assignment).assignResponsibleIfMissing(RAW," Mixed Actor ");else verifyNoInteractions(f.assignment);
    }
    @ParameterizedTest @ValueSource(strings={"quick_close","resolve","reopen","categories","mark_spam"})
    void unresolvedLifecycleAuditPreservesOpaqueIdentityAndNormalization(String action) {
        var f=fixture(false,true,true);
        new DialogAuditService(f.jdbc,f.guard,f.flags).logDialogActionAudit(RAW," Mixed Actor "," "+action+" "," success "," Detail ");
        var row=f.jdbc.queryForMap("SELECT * FROM dialog_action_audit WHERE ticket_id=?",RAW);
        assertThat(row).containsEntry("ticket_id",RAW).containsEntry("actor","Mixed Actor").containsEntry("action",action)
            .containsEntry("result","success").containsEntry("detail","Detail");
        assertThat(row.get("created_at")).isNotNull();
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM dialog_action_audit WHERE ticket_id=?",Long.class,RAW.trim())).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"quick_close","resolve","reopen","categories","mark_spam"})
    void strictLifecycleAuditStopsBeforeAnyJdbcOrUniqueProbe(String action) {
        var jdbc=mock(JdbcTemplate.class);var guard=mock(LegacyTicketIdJdbcGuard.class);
        var flags=new MultibusinessFeatureFlags();flags.setStrictReadScope(true);
        new DialogAuditService(jdbc,guard,flags).logDialogActionAudit(RAW,"actor",action,"success","denied");
        verifyNoInteractions(jdbc,guard);
    }
    static Stream<Arguments> audits() {
        return Stream.of("quick_close","resolve","reopen","categories","mark_spam").flatMap(action->Stream.of("business","general","strict","missing","collision","null-marker","missing-business","missing-marker")
            .map(kind->Arguments.of(action,kind)));
    }
    @ParameterizedTest @MethodSource("audits")
    void lifecycleAuditCannotClaimSuccessForAnotherNamespace(String action,String kind) {
        var f=fixture(false,true,true);mutate(f,kind);
        new DialogAuditService(f.jdbc,f.guard,f.flags).logDialogActionAudit(RAW,"operator",action,"success","must not be appended");
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM dialog_action_audit",Long.class)).isZero();
    }
    @ParameterizedTest @MethodSource("audits")
    void finalAuditStatementRechecksNamespaceAfterEarlierProof(String action,String kind) {
        var f=fixture(false,true,true);var injected=new AtomicBoolean();
        doAnswer(call->{boolean unique=(boolean)call.callRealMethod();if(unique&&injected.compareAndSet(false,true))mutate(f,kind);return unique;})
            .when(f.guard).hasUniqueTicket(RAW);
        new DialogAuditService(f.jdbc,f.guard,f.flags).logDialogActionAudit(RAW,"operator",action,"success","late namespace");
        assertThat(injected).isTrue();assertThat(f.jdbc.queryForObject("SELECT count(*) FROM dialog_action_audit",Long.class)).isZero();
    }
    static Stream<Arguments> callbackChanges(){return Stream.of("resolve","reopen","categories").flatMap(action->Stream.of("business","general","strict").map(kind->Arguments.of(action,kind)));}
    @ParameterizedTest @MethodSource("callbackChanges")
    void quickActionRepeatsEligibilityAfterLifecycleReturnsBeforeSuccessCallbacks(String action,String kind) {
        var f=fixture(action.equals("reopen"),true,true);var lifecycle=spy(f.service(f.jdbc));
        var changed=new AtomicBoolean();
        var before=new AtomicReference<Map<String,List<Map<String,Object>>>>();
        org.mockito.stubbing.Answer<Object> after=call->{
            Object result=call.callRealMethod();mutate(f,kind);changed.set(true);before.set(snapshot(f));return result;
        };
        if(action.equals("resolve"))doAnswer(after).when(lifecycle).resolveTicket(eq(RAW),eq("operator"),anyList());
        if(action.equals("reopen"))doAnswer(after).when(lifecycle).reopenTicket(RAW,"operator");
        if(action.equals("categories"))doAnswer(after).when(lifecycle).setTicketCategories(eq(RAW),anyList());
        var lookup=mock(DialogLookupReadService.class);
        when(lookup.findDialog(RAW,"operator")).thenReturn(Optional.of(legacyItem()));
        var notice=mock(DialogNotificationService.class);var ai=mock(DialogAiAssistantService.class);var notifications=mock(NotificationService.class);
        var stream=mock(UiEventStreamService.class);var blacklist=mock(ClientBlacklistService.class);
        var service=new DialogQuickActionService(lifecycle,lookup,f.assignment,mock(DialogParticipantService.class),mock(DialogReplyService.class),
            notice,ai,notifications,stream,mock(AttachmentService.class),blacklist);
        if(action.equals("resolve"))assertThat(service.resolveTicket(RAW,"operator",List.of("New")).exists()).isFalse();
        if(action.equals("reopen"))assertThat(service.reopenTicket(RAW,"operator").exists()).isFalse();
        if(action.equals("categories"))assertThat(service.updateCategories(RAW,"operator",List.of("New")).exists()).isFalse();
        assertThat(changed).isTrue();assertThat(snapshot(f)).isEqualTo(before.get());
        verifyNoInteractions(notice,ai,notifications,stream,blacklist);
    }
    static Stream<Arguments> quickActions(){return Stream.of("resolve","reopen","categories","spam").flatMap(action->Stream.of("business","general","strict").map(kind->Arguments.of(action,kind)));}
    @ParameterizedTest @MethodSource("quickActions")
    void quickActionCannotPublishOrBlacklistOnNamespaceDenial(String action,String kind) {
        var f=fixture(action.equals("reopen"),true,true);mutate(f,kind);var before=snapshot(f);
        var lookup=mock(DialogLookupReadService.class);var item=legacyItem();
        when(lookup.findDialog(RAW,"operator")).thenReturn(Optional.of(item));
        var notice=mock(DialogNotificationService.class);var ai=mock(DialogAiAssistantService.class);var notifications=mock(NotificationService.class);
        var stream=mock(UiEventStreamService.class);var blacklist=mock(ClientBlacklistService.class);
        when(blacklist.blockClient(anyString(),anyString(),anyString(),anyBoolean()))
            .thenReturn(ClientBlacklistService.BlacklistMutationResult.success("would block"));
        var service=new DialogQuickActionService(f.service(f.jdbc),lookup,f.assignment,mock(DialogParticipantService.class),mock(DialogReplyService.class),
            notice,ai,notifications,stream,mock(AttachmentService.class),blacklist);
        if(action.equals("resolve"))assertThat(service.resolveTicket(RAW,"operator",List.of("New")).exists()).isFalse();
        if(action.equals("reopen"))assertThat(service.reopenTicket(RAW,"operator").exists()).isFalse();
        if(action.equals("categories"))assertThat(service.updateCategories(RAW,"operator",List.of("New")).exists()).isFalse();
        if(action.equals("spam"))assertThat(service.markClientAsSpam(RAW,"operator","Spam").exists()).isFalse();
        assertThat(snapshot(f)).isEqualTo(before);verifyNoInteractions(notice,ai,notifications,stream,blacklist,f.assignment);
    }
}
