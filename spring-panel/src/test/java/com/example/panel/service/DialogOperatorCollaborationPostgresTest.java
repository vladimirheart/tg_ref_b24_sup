package com.example.panel.service;

import static org.assertj.core.api.Assertions.*;
import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

class DialogOperatorCollaborationPostgresTest {
    JdbcTemplate jdbc; DialogOperatorCollaborationService service; BusinessTicketAssignmentService assignment;
    MultibusinessFeatureFlags flags;
    final String raw=" raw/322? ",pub="a".repeat(32);
    @BeforeEach void setup() throws Exception {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("operator_collaboration");
        jdbc.execute("""
            CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT,full_name TEXT,enabled BOOLEAN DEFAULT TRUE,is_blocked BOOLEAN DEFAULT FALSE);
            CREATE TABLE businesses(id BIGINT PRIMARY KEY,status TEXT DEFAULT 'active',display_name TEXT);
            CREATE TABLE business_memberships(user_id BIGINT,business_id BIGINT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE business_access_grants(user_id BIGINT,grant_scope TEXT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE general_ticket_read_grants(user_id BIGINT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE general_ticket_operate_grants(user_id BIGINT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,ticket_public_id TEXT,business_id BIGINT,is_general BOOLEAN DEFAULT FALSE,
                channel_id BIGINT,status TEXT,PRIMARY KEY(user_id,ticket_id));
            CREATE TABLE ticket_responsibles(ticket_id TEXT PRIMARY KEY,responsible TEXT,assigned_by TEXT,assigned_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP);
            CREATE TABLE ui_event_outbox(id BIGINT PRIMARY KEY,event_type TEXT,ticket_id TEXT,channel_id BIGINT,created_at TIMESTAMPTZ,
                ticket_user_id BIGINT,ticket_public_id TEXT,ticket_scope_kind TEXT,ticket_business_id BIGINT);
            CREATE TABLE chat_history(body TEXT); INSERT INTO chat_history VALUES('Customer history untouched');
            INSERT INTO users(id,username) VALUES(1,'Operator'),(2,'viewer'),(3,'foreign');
            INSERT INTO businesses(id,display_name) VALUES(11,'Source'),(22,'Target');
            INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(1,11,'OPERATOR'),(1,22,'OPERATOR'),(2,11,'VIEWER'),(3,22,'ADMIN');
            """);
        for(String migration:List.of("V77__scoped_sla_assignment_revision.sql","V78__business_manual_assignment_events.sql",
                "V81__business_ticket_manual_work.sql","V106__dialog_operator_collaboration.sql","V107__dialog_internal_read_markers.sql"))
            jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql/"+migration)));
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,channel_id,status) VALUES(-42,?,?,11,9,'open')",raw,pub);
        flags=new MultibusinessFeatureFlags();flags.getFoundation().setEnabled(true);flags.setUiSelector(true);flags.setGeneralTicketQueue(true);
        var outbox=new UiEventOutboxAppendService(jdbc,new LegacyTicketIdJdbcGuard(jdbc));
        service=new DialogOperatorCollaborationService(jdbc,flags,outbox);
        assignment=new BusinessTicketAssignmentService(jdbc,flags,outbox);
    }
    Authentication auth(long id,String name) {
        return UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(id,name,"unused",true,List.of()),null,List.of());
    }
    Authentication operator(){return auth(1,"Operator");}
    DialogOperatorCollaborationService.Context context(){return service.find(pub,operator()).orElseThrow();}
    @Test void internalMessageHasExactOwnerAuthorAndNoClientOrOutboxSideEffects() {
        var id=UUID.randomUUID();var state=context();
        assertThat(service.post(state,operator(),id,"<script>internal</script>\nSecond line")).isTrue();
        assertThat(service.post(state,operator(),id,"<script>internal</script>\nSecond line")).isTrue();
        assertThat(service.post(state,operator(),id,"Different payload")).isFalse();
        var result=service.messages(pub,auth(2,"viewer"),0);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).author()).isEqualTo("Operator");
        assertThat(result.get(0).body()).isEqualTo("<script>internal</script>\nSecond line");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ui_event_outbox",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT body FROM chat_history",String.class)).isEqualTo("Customer history untouched");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_responsibles",Long.class)).isZero();
        assertThatThrownBy(()->jdbc.update("UPDATE dialog_internal_messages SET body='changed'")).hasMessageContaining("append-only");
    }
    @ParameterizedTest @ValueSource(strings={"viewer","foreign","blocked","renamed","unbound","collision","revoked","flags"})
    void postingCannotBorrowScopeOrAnInvalidAccount(String variant) {
        var state=context(); Authentication actor=operator();
        switch(variant) {
            case "viewer"->actor=auth(2,"viewer");case "foreign"->actor=auth(3,"foreign");
            case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=1");
            case "renamed"->jdbc.update("UPDATE users SET username='Operator2' WHERE id=1");
            case "unbound"->actor=UsernamePasswordAuthenticationToken.authenticated("Operator",null,List.of());
            case "collision"->jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,status) VALUES(88,?, ?,22,'open')",raw,"b".repeat(32));
            case "revoked"->jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=1 AND business_id=11");
            case "flags"->flags.getFoundation().setEnabled(false);
        }
        assertThat(service.post(state,actor,UUID.randomUUID(),"Denied")).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dialog_internal_messages",Long.class)).isZero();
    }
    @Test void generalNeedsExplicitReadAndOperateButNoInvitation() {
        jdbc.update("UPDATE tickets SET business_id=NULL,is_general=TRUE");
        assertThat(service.find(pub,operator())).isEmpty();
        jdbc.update("INSERT INTO general_ticket_read_grants(user_id) VALUES(1)");
        assertThat(context().canWrite()).isFalse();
        jdbc.update("INSERT INTO general_ticket_operate_grants(user_id) VALUES(1)");
        assertThat(service.post(context(),operator(),UUID.randomUUID(),"General internal")).isTrue();
        jdbc.update("UPDATE general_ticket_read_grants SET is_active=FALSE");
        assertThat(service.messages(pub,operator(),0)).isEmpty();
    }
    @Test void readerCanSeeInternalMessagesWithoutParticipatingAndPagesAreBounded() {
        for(int i=0;i<53;i++) jdbc.update("INSERT INTO dialog_internal_messages(ticket_user_id,ticket_id,ticket_public_id,author_user_id,author,request_id,body) VALUES(-42,?,?,1,'Operator',?,?)",
            raw,pub,UUID.randomUUID(),"Note "+i);
        var page=service.messages(pub,auth(2,"viewer"),0);
        assertThat(page).hasSize(50);
        assertThat(service.messages(pub,auth(2,"viewer"),Long.parseLong(page.get(49).id()))).hasSize(3);
        assertThat(service.messages(pub,auth(3,"foreign"),0)).isEmpty();
    }
    @Test void transferPreservesHistoryAndInternalMessagesWhileResettingBusinessProjections() {
        jdbc.update("UPDATE tickets SET business_categories='[\"Billing\"]'::jsonb");
        jdbc.update("INSERT INTO ticket_responsibles(ticket_id,responsible,assigned_by) VALUES(?,'viewer','Operator')",raw);
        var state=context();assertThat(service.post(state,operator(),UUID.randomUUID(),"Internal before move")).isTrue();
        assertThat(service.transfer(state,22,operator())).isTrue();
        assertThat(context().scope().businessId()).isEqualTo(22);
        assertThat(context().revision()).isEqualTo(state.revision()+1);
        assertThat(jdbc.queryForObject("SELECT business_categories::text FROM tickets",String.class)).isEqualTo("[]");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_responsibles",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dialog_business_transfer_events",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ui_event_outbox",Long.class)).isEqualTo(2);
        assertThat(service.messages(pub,auth(2,"viewer"),0)).isEmpty();
        assertThat(service.messages(pub,auth(3,"foreign"),0)).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT body FROM chat_history",String.class)).isEqualTo("Customer history untouched");
        assertThat(service.transfer(state,11,operator())).isFalse();
    }
    @ParameterizedTest @ValueSource(strings={"target","source","closed","stale","archived"})
    void transferDenialDoesNotChangeRoot(String variant) {
        var state=context();
        if(variant.equals("target"))jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=1 AND business_id=22");
        if(variant.equals("source"))jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=1 AND business_id=11");
        if(variant.equals("closed"))jdbc.update("UPDATE tickets SET status='closed'");
        if(variant.equals("stale"))jdbc.update("UPDATE tickets SET business_categories='[\"New\"]'::jsonb");
        if(variant.equals("archived"))jdbc.update("UPDATE businesses SET status='archived' WHERE id=22");
        assertThat(service.transfer(state,22,operator())).isFalse();
        assertThat(jdbc.queryForObject("SELECT business_id FROM tickets",Long.class)).isEqualTo(11);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dialog_business_transfer_events",Long.class)).isZero();
    }
    @Test void requiredAuditFailureRollsBackTransferAssignmentAndOutbox() {
        jdbc.update("INSERT INTO ticket_responsibles(ticket_id,responsible,assigned_by) VALUES(?,'Operator','Operator')",raw);
        jdbc.execute("ALTER TABLE dialog_business_transfer_events ADD CONSTRAINT fail_test CHECK(FALSE) NOT VALID");
        var state=context();
        assertThatThrownBy(()->service.transfer(state,22,operator())).isInstanceOf(RuntimeException.class);
        assertThat(context()).isEqualTo(state);
        assertThat(jdbc.queryForObject("SELECT responsible FROM ticket_responsibles",String.class)).isEqualTo("Operator");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ui_event_outbox",Long.class)).isZero();
    }
    @Test void scopedTakeUsesCanonicalAssignmentAuditAndRevision() {
        var state=assignment.find(pub,operator()).orElseThrow();
        assertThat(assignment.change(state.scope(),operator(),state.revision(),null,true)).isTrue();
        assertThat(jdbc.queryForObject("SELECT responsible FROM ticket_responsibles",String.class)).isEqualTo("Operator");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_ticket_assignment_events",Long.class)).isEqualTo(1);
        assertThat(assignment.change(state.scope(),auth(2,"viewer"),state.revision(),null,true)).isFalse();
    }
    @Test @Timeout(20) void twoTransfersCannotBothCommitFromTheSameOwnerRevision() throws Exception {
        var expected=context();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<Boolean> move=()->{start.await();return service.transfer(expected,22,operator());};
            var a=pool.submit(move);var b=pool.submit(move);start.countDown();
            assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM dialog_business_transfer_events",Long.class)).isEqualTo(1);
        } finally {pool.shutdownNow();}
    }
    @Test @Timeout(20) void concurrentRetriesOfAnInternalMessageAreIdempotent() throws Exception {
        var expected=context();var id=UUID.randomUUID();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<Boolean> post=()->{start.await();return service.post(expected,operator(),id,"One internal message");};
            var a=pool.submit(post);var b=pool.submit(post);start.countDown();
            assertThat(a.get(15,TimeUnit.SECONDS)).isTrue();assertThat(b.get(15,TimeUnit.SECONDS)).isTrue();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM dialog_internal_messages",Long.class)).isEqualTo(1);
        } finally {pool.shutdownNow();}
    }
    @Test void authorNameUsesTheNumericEmployeeProfileWithSnapshotLoginFallback() {
        assertThat(service.post(context(),operator(),UUID.randomUUID(),"Internal")).isTrue();
        jdbc.update("UPDATE users SET full_name=' Иван Иванов ',username='Renamed' WHERE id=1");
        var message=service.messages(pub,auth(2,"viewer"),0).get(0);
        assertThat(message.authorName()).isEqualTo("Иван Иванов");
        assertThat(message.author()).isEqualTo("Operator");
        jdbc.update("UPDATE users SET full_name='   ' WHERE id=1");
        assertThat(service.messages(pub,auth(2,"viewer"),0).get(0).authorName()).isEqualTo("Operator");
    }
    @Test void personalReadMarkerExcludesOwnMessagesAndNeverMarksLaterMessages() {
        assertThat(service.post(context(),operator(),UUID.randomUUID(),"First")).isTrue();
        long first=Long.parseLong(service.messages(pub,auth(2,"viewer"),0).get(0).id());
        assertThat(service.unread(List.of(pub),operator()).get(0).unreadCount()).isEqualTo("0");
        assertThat(service.unread(List.of(pub),auth(2,"viewer")).get(0).unreadCount()).isEqualTo("1");
        var viewer=service.find(pub,auth(2,"viewer")).orElseThrow();
        assertThat(service.markRead(viewer,auth(2,"viewer"),first)).isTrue();
        assertThat(service.post(context(),operator(),UUID.randomUUID(),"Second")).isTrue();
        assertThat(service.markRead(viewer,auth(2,"viewer"),first)).isTrue();
        assertThat(service.unread(List.of(pub),auth(2,"viewer")).get(0).unreadCount()).isEqualTo("1");
        long second=Long.parseLong(service.messages(pub,auth(2,"viewer"),0).get(0).id());
        assertThat(service.markRead(viewer,auth(2,"viewer"),second)).isTrue();
        assertThat(service.markRead(viewer,auth(2,"viewer"),first)).isTrue();
        assertThat(service.unread(List.of(pub),auth(2,"viewer")).get(0).unreadCount()).isEqualTo("0");
        assertThat(jdbc.queryForObject("SELECT last_read_id FROM dialog_internal_read_markers",Long.class)).isEqualTo(second);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ui_event_outbox",Long.class)).isZero();
    }
    @Test void readMarkerCannotUseFutureForeignOwnerOrRevokedRead() {
        assertThat(service.post(context(),operator(),UUID.randomUUID(),"Source")).isTrue();
        var viewer=service.find(pub,auth(2,"viewer")).orElseThrow();
        long id=Long.parseLong(service.messages(pub,auth(2,"viewer"),0).get(0).id());
        assertThat(service.markRead(viewer,auth(2,"viewer"),id+1)).isFalse();
        assertThat(service.unread(List.of(pub),auth(3,"foreign"))).isEmpty();
        jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=2");
        assertThat(service.markRead(viewer,auth(2,"viewer"),id)).isFalse();
        assertThat(service.unread(List.of(pub),auth(2,"viewer"))).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dialog_internal_read_markers",Long.class)).isZero();
    }
}

