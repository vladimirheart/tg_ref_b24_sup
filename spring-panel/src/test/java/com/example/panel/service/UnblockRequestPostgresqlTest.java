package com.example.panel.service;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UnblockRequestPostgresqlTest {
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    BlacklistHistoryService history;
    DialogNotificationService notifications;
    UiEventStreamService events;
    UnblockRequestService service;

    @BeforeEach
    void setup() {
        jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate("unblock_decision");
        jdbc.execute("""
            CREATE TABLE client_blacklist(user_id TEXT PRIMARY KEY,is_blacklisted BOOLEAN,
                unblock_requested BOOLEAN,unblock_requested_at TIMESTAMPTZ);
            CREATE TABLE client_unblock_requests(id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                user_id TEXT,status TEXT,reason TEXT,channel_id BIGINT,created_at TIMESTAMPTZ DEFAULT now(),
                decided_at TIMESTAMPTZ,decided_by TEXT,decision_comment TEXT);
            """);
        tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        history = mock(BlacklistHistoryService.class);
        notifications = mock(DialogNotificationService.class);
        events = mock(UiEventStreamService.class);
        service = new UnblockRequestService(jdbc,history,notifications,events);
    }

    long request(String user, Boolean blocked) {
        if(blocked != null) jdbc.update("INSERT INTO client_blacklist VALUES(?,?,TRUE,CURRENT_TIMESTAMP)",user,blocked);
        return jdbc.queryForObject("INSERT INTO client_unblock_requests(user_id,status) VALUES(?,'pending') RETURNING id",Long.class,user);
    }

    UnblockRequestService.DecisionOutcome decide(long id,boolean approve) {
        return tx.execute(status -> service.decideRequest(id,approve,"Проверено","operator"));
    }

    @Test
    void approvesBlockedClientWithCanonicalPostgresBooleansAndRejectsRepeat() {
        long id=request("77",true);
        assertThat(decide(id,true).success()).isTrue();
        assertThat(jdbc.queryForObject("SELECT is_blacklisted FROM client_blacklist WHERE user_id='77'",Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("SELECT unblock_requested FROM client_blacklist WHERE user_id='77'",Boolean.class)).isFalse();
        assertThat(status(id)).isEqualTo("approved");
        assertThat(decide(id,false).success()).isFalse();
        verify(history,times(1)).recordEvent(eq("77"),eq("unblocked"),eq("Проверено"),eq("operator"),any());
        verify(notifications,times(1)).notifyUserByLastChannel(eq(77L),anyString(),eq(true));
        verify(events,times(1)).publishSidebarUnblockChanged("unblock_request_decided");
    }

    @Test
    void rejectionPreservesBlockAndClearsOnlyRequestFlag() {
        long id=request("77",true);
        assertThat(decide(id,false).success()).isTrue();
        assertThat(status(id)).isEqualTo("rejected");
        assertThat(jdbc.queryForObject("SELECT is_blacklisted FROM client_blacklist WHERE user_id='77'",Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT unblock_requested FROM client_blacklist WHERE user_id='77'",Boolean.class)).isFalse();
        verifyNoInteractions(history);
        verify(notifications).notifyUserByLastChannel(eq(77L),contains("отклонен"),eq(true));
    }

    @ParameterizedTest
    @ValueSource(booleans={true,false})
    void obsoleteRequestExpiresWithoutFalseHistoryOrClientNotification(boolean approve) {
        long id=request("77",false);
        var result=decide(id,approve);
        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("уже разблокирован");
        assertThat(status(id)).isEqualTo("expired");
        assertThat(jdbc.queryForObject("SELECT decision_comment FROM client_unblock_requests WHERE id=?",String.class,id))
            .contains("утратила актуальность","Проверено");
        assertThat(jdbc.queryForObject("SELECT is_blacklisted FROM client_blacklist WHERE user_id='77'",Boolean.class)).isFalse();
        verifyNoInteractions(history,notifications);
        verify(events).publishSidebarUnblockChanged("unblock_request_decided");
    }

    @Test
    void missingBlacklistEntryDoesNotCreateOneOrNotifyClient() {
        long id=request("77",null);
        assertThat(decide(id,true).success()).isTrue();
        assertThat(status(id)).isEqualTo("expired");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM client_blacklist",Integer.class)).isZero();
        verifyNoInteractions(history,notifications);
    }

    @Test
    void databaseChangesRollBackTogetherWhenHistoryFails() {
        long id=request("77",true);
        doThrow(new IllegalStateException("fixture failure")).when(history)
            .recordEvent(anyString(),anyString(),anyString(),anyString(),any());
        assertThatThrownBy(() -> decide(id,true)).isInstanceOf(IllegalStateException.class);
        assertThat(status(id)).isEqualTo("pending");
        assertThat(jdbc.queryForObject("SELECT is_blacklisted AND unblock_requested FROM client_blacklist WHERE user_id='77'",Boolean.class)).isTrue();
        verifyNoInteractions(events,notifications);
    }

    @Test
    void concurrentDecisionsProduceOnlyOneResultAndOneNotification() throws Exception {
        long id=request("77",true);
        var executor=Executors.newFixedThreadPool(2);
        var start=new CountDownLatch(1);
        try {
            var futures=new ArrayList<Future<UnblockRequestService.DecisionOutcome>>();
            for(boolean approve:List.of(true,false)) futures.add(executor.submit(() -> {
                start.await(); return decide(id,approve);
            }));
            start.countDown();
            int successes=0;
            for(var future:futures) if(future.get(10,TimeUnit.SECONDS).success()) successes++;
            assertThat(successes).isEqualTo(1);
            verify(notifications,times(1)).notifyUserByLastChannel(eq(77L),anyString(),eq(true));
            verify(events,times(1)).publishSidebarUnblockChanged("unblock_request_decided");
        } finally {executor.shutdownNow();}
    }

    @Test
    void repairMigrationPreservesHistoryAndActiveBlockedRequests() throws Exception {
        long obsolete=request("77",false);
        long active=request("88",true);
        long absent=request("99",null);
        long processed=request("100",false);
        jdbc.update("UPDATE client_unblock_requests SET status='rejected',decision_comment='original' WHERE id=?",processed);
        String migration=new ClassPathResource("db/migration/postgresql/V105__expire_obsolete_client_unblock_requests.sql")
            .getContentAsString(StandardCharsets.UTF_8);
        jdbc.execute(migration);
        assertThat(status(obsolete)).isEqualTo("expired");
        assertThat(status(absent)).isEqualTo("expired");
        assertThat(status(active)).isEqualTo("pending");
        assertThat(status(processed)).isEqualTo("rejected");
        assertThat(jdbc.queryForObject("SELECT decision_comment FROM client_unblock_requests WHERE id=?",String.class,processed)).isEqualTo("original");
        assertThat(service.countPendingRequests()).isEqualTo(1);
        var before=jdbc.queryForList("SELECT * FROM client_unblock_requests ORDER BY id");
        jdbc.execute(migration);
        assertThat(jdbc.queryForList("SELECT * FROM client_unblock_requests ORDER BY id")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT is_blacklisted AND unblock_requested FROM client_blacklist WHERE user_id='88'",Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT unblock_requested FROM client_blacklist WHERE user_id='77'",Boolean.class)).isFalse();
    }

    String status(long id) {
        return jdbc.queryForObject("SELECT status FROM client_unblock_requests WHERE id=?",String.class,id);
    }
}
