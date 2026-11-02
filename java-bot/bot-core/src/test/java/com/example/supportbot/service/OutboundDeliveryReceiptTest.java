package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.*;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;

/** V85 on real PostgreSQL, historical acceptance survives service restoration; never contacts a provider. */
class OutboundDeliveryReceiptTest {
    static PostgreSQLContainer<?> postgres;
    static String url;
    static JdbcTemplate admin;
    String schema;
    DataSource base;
    JdbcTemplate jdbc;
    OutboundTransportDeliveryLedgerService ledger;
    OutboundDeliveryReceiptService receipts;
    static final long CHANNEL=9007199254740995L,USER=9007199254740993L;
    static final String RAW=" padded/Ю%?' ",PROMPT="private prompt never stored",EVENT="r263";
    @BeforeAll static void database(){
        url=System.getenv("FEEDBACK_CHAIN_PG_JDBC_URL");
        if(url==null){postgres=new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("r259_chain")
            .withUsername("r259_test").withPassword("r259-test-only")
            .withCreateContainerCmdModifier(cmd->cmd.getHostConfig().withMemory(192L*1024*1024).withNanoCPUs(1_000_000_000L));
            postgres.start();url=postgres.getJdbcUrl();}
        else assertThat(url).matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/r259_chain");
        admin=new JdbcTemplate(new DriverManagerDataSource(url,"r259_test","r259-test-only"));
    }
    @AfterAll static void stop(){if(postgres!=null)postgres.stop();}
    @BeforeEach void setup(){
        schema="r263_receipts_"+UUID.randomUUID().toString().replace("-","");admin.execute("CREATE SCHEMA "+schema);
        base=new DriverManagerDataSource(url+"?currentSchema="+schema,"r259_test","r259-test-only");jdbc=new JdbcTemplate(base);
        ledger=new OutboundTransportDeliveryLedgerService(jdbc);
        ledger.beginDelivery("pre-v85","feedback.prompt.dispatch","old.route",CHANNEL,USER,RAW,100L);
        ledger.markSending("pre-v85");ledger.markDelivered("pre-v85");
        var old=jdbc.queryForMap("SELECT * FROM integration_outbound_event_deliveries WHERE event_id='pre-v85'");
        Flyway flyway=Flyway.configure().dataSource(base).schemas(schema).defaultSchema(schema).baselineOnMigrate(true)
            .baselineVersion("84").locations("classpath:receipt-migration").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);flyway.validate();
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("85");
        assertThat(jdbc.queryForMap("SELECT * FROM integration_outbound_event_deliveries WHERE event_id='pre-v85'")).isEqualTo(old);
        receipts=new OutboundDeliveryReceiptService(jdbc);
    }
    @AfterEach void cleanup(){if(schema!=null){assertThat(schema).matches("r263_receipts_[0-9a-f]{32}");admin.execute("DROP SCHEMA "+schema+" CASCADE");}}
    OutboundSenderIdentity sender(String platform){return new OutboundSenderIdentity(CHANNEL,platform,"a".repeat(64));}
    String prepare(String platform){
        assertThat(ledger.beginDelivery(EVENT,"feedback.prompt.dispatch","route."+platform,CHANNEL,USER,RAW,100L)).isTrue();
        ledger.markSending(EVENT);return receipts.prepare(EVENT,sender(platform),PROMPT);
    }
    Map<String,Object> delivery(){return jdbc.queryForMap("SELECT * FROM integration_outbound_event_deliveries WHERE event_id=?",EVENT);}
    static Stream<Arguments> destinations(){return Stream.of("telegram","vk","max").flatMap(p->Stream.of("sending","uncertain").map(s->Arguments.of(p,s)));}
    @ParameterizedTest @MethodSource("destinations")
    void durablePositiveReceiptRestoresOnlyItsOriginalDeliveryAfterRestart(String platform,String state){
        String attempt=prepare(platform);
        receipts.record(EVENT,attempt,sender(platform),USER,ProviderSendResult.accepted("r263.42"));
        var stored=jdbc.queryForMap("SELECT * FROM integration_outbound_delivery_receipts WHERE event_id=?",EVENT);
        assertThat(stored.values().toString()).doesNotContain(PROMPT,"r259-test-only");
        assertThat(stored.get("ticket_id")).isEqualTo(RAW);assertThat(stored.get("user_id")).isEqualTo(USER);
        jdbc.update("UPDATE integration_outbound_event_deliveries SET status=?,last_error='unknown local result' WHERE event_id=?",state,EVENT);
        var restored=new OutboundDeliveryReceiptService(new JdbcTemplate(base));
        assertThat(restored.reconcile(EVENT)).isTrue();
        assertThat(delivery().get("status")).isEqualTo("delivered");
        assertThat(delivery().get("delivered_at")).isEqualTo(stored.get("accepted_at"));
        assertThat(delivery().get("last_error")).isNull();
        var delivered=delivery();assertThat(restored.reconcile(EVENT)).isFalse();assertThat(delivery()).isEqualTo(delivered);
        receipts.record(EVENT,attempt,sender(platform),USER,ProviderSendResult.accepted("r263.42"));
        assertThat(jdbc.queryForMap("SELECT * FROM integration_outbound_delivery_receipts WHERE event_id=?",EVENT)).isEqualTo(stored);
    }
    @ParameterizedTest @ValueSource(strings={"event_kind","routing_key","channel_id","user_id","ticket_id","request_id"})
    void changedLedgerContextCannotBorrowAReceipt(String column){
        String attempt=prepare("telegram");receipts.record(EVENT,attempt,sender("telegram"),USER,ProviderSendResult.accepted("42"));
        Object value=Set.of("channel_id","user_id","request_id").contains(column)?17L:"different ";
        jdbc.update("UPDATE integration_outbound_event_deliveries SET "+column+"=? WHERE event_id=?",value,EVENT);
        var before=delivery();assertThat(receipts.reconcile(EVENT)).isFalse();assertThat(delivery()).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"attempt","channel","platform","credential","recipient","conflicting-id"})
    void receiptCannotBeReassignedOrOverwritten(String change){
        String attempt=prepare("telegram");var identity=sender("telegram");long user=USER;String id="42";
        receipts.record(EVENT,attempt,identity,user,ProviderSendResult.accepted(id));
        var original=jdbc.queryForMap("SELECT * FROM integration_outbound_delivery_receipts WHERE event_id=?",EVENT);
        switch(change){
            case "attempt"->attempt=UUID.randomUUID().toString();
            case "channel"->identity=new OutboundSenderIdentity(CHANNEL+1,"telegram","a".repeat(64));
            case "platform"->identity=sender("max");
            case "credential"->identity=new OutboundSenderIdentity(CHANNEL,"telegram","b".repeat(64));
            case "recipient"->user++;
            case "conflicting-id"->id="other-id";
        }
        String a=attempt,msg=id;var who=identity;long recipient=user;
        assertThatThrownBy(()->receipts.record(EVENT,a,who,recipient,ProviderSendResult.accepted(msg))).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForMap("SELECT * FROM integration_outbound_delivery_receipts WHERE event_id=?",EVENT)).isEqualTo(original);
    }
    @ParameterizedTest @ValueSource(strings={"sending","uncertain"})
    void noReceiptMeansNoRecoveryEvenWithPreparedAttempt(String state){
        prepare("telegram");jdbc.update("UPDATE integration_outbound_event_deliveries SET status=? WHERE event_id=?",state,EVENT);
        var before=delivery();assertThat(receipts.reconcile(EVENT)).isFalse();assertThat(delivery()).isEqualTo(before);
        assertThat(receipts.reconcile("absent")).isFalse();
    }
    @ParameterizedTest @ValueSource(strings={"processing","retryable","failed","unknown-state","delivered"})
    void positiveReceiptDoesNotReopenUnsupportedLedgerStates(String state){
        String attempt=prepare("max");receipts.record(EVENT,attempt,sender("max"),USER,ProviderSendResult.accepted("mid.42"));
        jdbc.update("UPDATE integration_outbound_event_deliveries SET status=? WHERE event_id=?",state,EVENT);
        var before=delivery();assertThat(receipts.reconcile(EVENT)).isFalse();assertThat(delivery()).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(ints={1,256,257})
    void applicationAndDatabaseAgreeOnReceiptLength(int length){
        String attempt=prepare("max"),id="x".repeat(length);
        if(length<=256){receipts.record(EVENT,attempt,sender("max"),USER,ProviderSendResult.accepted(id));assertThat(receipts.reconcile(EVENT)).isTrue();}
        else{
            assertThatThrownBy(()->receipts.record(EVENT,attempt,sender("max"),USER,ProviderSendResult.accepted(id))).isInstanceOf(IllegalArgumentException.class);
            assertThat(receipts.reconcile(EVENT)).isFalse();
        }
    }
    @ParameterizedTest @ValueSource(strings={"prepare","record","reconcile"})
    void persistenceFailureIsBoundedAndDoesNotResolveUncommittedEvidence(String stage){
        String attempt=prepare("max");
        if(stage.equals("reconcile"))receipts.record(EVENT,attempt,sender("max"),USER,ProviderSendResult.accepted("mid.42"));
        var writes=new java.util.concurrent.atomic.AtomicInteger();
        var fault=new JdbcTemplate(base){@Override public int update(String sql,Object... args){
            if(sql.contains("integration_outbound_delivery_receipts")){
                writes.incrementAndGet();throw new org.springframework.dao.DataAccessResourceFailureException("Test: receipt storage unavailable");
            }return super.update(sql,args);
        }};
        var service=new OutboundDeliveryReceiptService(fault);var before=delivery();
        assertThatThrownBy(()->{
            switch(stage){case "prepare"->service.prepare(EVENT,sender("max"),PROMPT);case "record"->service.record(EVENT,attempt,sender("max"),USER,ProviderSendResult.accepted("mid.42"));default->service.reconcile(EVENT);}
        }).isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
        assertThat(writes).hasValue(3);assertThat(delivery()).isEqualTo(before);
    }
    @Test void migrationIsRequiredAndNeverRunByReceiptService(){
        jdbc.execute("DROP TABLE integration_outbound_delivery_receipts");
        assertThatThrownBy(()->new OutboundDeliveryReceiptService(jdbc)).isInstanceOf(IllegalStateException.class).hasMessageContaining("V85");
        assertThat(jdbc.queryForObject("SELECT to_regclass('integration_outbound_delivery_receipts') IS NULL",Boolean.class)).isTrue();
    }
    @Test void outerRollbackCannotEraseAcceptedReceipt(){
        String attempt=prepare("vk");
        new TransactionTemplate(new JdbcTransactionManager(base)).executeWithoutResult(tx->{
            receipts.record(EVENT,attempt,sender("vk"),USER,ProviderSendResult.accepted("42"));tx.setRollbackOnly();
        });
        assertThat(new OutboundDeliveryReceiptService(new JdbcTemplate(base)).reconcile(EVENT)).isTrue();
    }
    @ParameterizedTest @ValueSource(strings={"prepare","record"})
    void lostCommitAcknowledgementIsIdempotent(String stage){
        var lose=new AtomicBoolean();
        DataSource faulty=new AbstractDataSource(){
            public Connection getConnection() throws SQLException{return wrap(base.getConnection());}
            public Connection getConnection(String u,String p)throws SQLException{return wrap(base.getConnection(u,p));}
            Connection wrap(Connection connection){return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
                try{Object result=method.invoke(connection,args);if(method.getName().equals("commit")&&lose.compareAndSet(true,false))throw new SQLException("Test: committed reply lost","08006");return result;}
                catch(InvocationTargetException failure){throw failure.getCause();}
            });}
        };
        var service=new OutboundDeliveryReceiptService(new JdbcTemplate(faulty));
        ledger.beginDelivery(EVENT,"feedback.prompt.dispatch","route",CHANNEL,USER,RAW,100L);ledger.markSending(EVENT);
        lose.set(stage.equals("prepare"));String attempt=service.prepare(EVENT,sender("telegram"),PROMPT);
        lose.set(stage.equals("record"));service.record(EVENT,attempt,sender("telegram"),USER,ProviderSendResult.accepted("42"));
        assertThat(lose).isFalse();assertThat(receipts.reconcile(EVENT)).isTrue();
    }
}
