package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Real PostgreSQL transactions and dispatcher; a confirmation retry must never repeat provider I/O. */
class FeedbackResultConfirmationTest {
    private static PostgreSQLContainer<?> postgres;
    private static String url;
    private static JdbcTemplate admin;
    private String schema;
    private JdbcTemplate inspect;
    private ResultJdbc jdbc;
    private OutboundTransportDeliveryLedgerService ledger;
    private final AtomicBoolean loseCommitReply = new AtomicBoolean();
    private final AtomicInteger lostCommitReplies = new AtomicInteger();

    @BeforeAll static void database() {
        url = System.getenv("FEEDBACK_CHAIN_PG_JDBC_URL");
        if (url == null) {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("r259_chain")
                .withUsername("r259_test").withPassword("r259-test-only")
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withMemory(192L*1024*1024).withNanoCPUs(1_000_000_000L));
            postgres.start(); url = postgres.getJdbcUrl();
        } else assertThat(url).matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/r259_chain");
        admin = new JdbcTemplate(new DriverManagerDataSource(url,"r259_test","r259-test-only"));
    }
    @AfterAll static void stop() { if (postgres != null) postgres.stop(); }
    @BeforeEach void setup() {
        schema = "r262_confirmation_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE SCHEMA " + schema);
        DataSource base = new DriverManagerDataSource(url+"?currentSchema="+schema,"r259_test","r259-test-only");
        inspect = new JdbcTemplate(base);
        DataSource faults = new AbstractDataSource() {
            public Connection getConnection() throws SQLException { return wrap(base.getConnection()); }
            public Connection getConnection(String user,String password) throws SQLException { return wrap(base.getConnection(user,password)); }
            private Connection wrap(Connection delegate) {
                return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
                    try {
                        Object result = method.invoke(delegate,args);
                        if (method.getName().equals("commit") && loseCommitReply.compareAndSet(true,false)) {
                            lostCommitReplies.incrementAndGet();
                            throw new SQLException("Test: commit completed but its acknowledgement was lost","08006");
                        }
                        return result;
                    } catch (InvocationTargetException wrapped) { throw wrapped.getCause(); }
                });
            }
        };
        jdbc = new ResultJdbc(faults);
        ledger = new OutboundTransportDeliveryLedgerService(jdbc);
    }
    @AfterEach void cleanup() {
        Thread.interrupted();
        if (schema != null) {
            assertThat(schema).matches("r262_confirmation_[0-9a-f]{32}");
            admin.execute("DROP SCHEMA "+schema+" CASCADE");
        }
    }
    static Stream<Arguments> cases() {
        return Stream.of("telegram","vk","max").flatMap(platform -> Stream.of("ok","transient","two-transients",
            "commit-ack-loss","unavailable","provider-false","provider-error","programming-error")
            .map(change -> Arguments.of(platform,change)));
    }
    @ParameterizedTest @MethodSource("cases")
    void confirmsOnlyKnownSuccessWithoutRepeatingDelivery(String platform,String change) {
        var channel=MessagingPlatformIsolationTest.channel(platform);
        var channels=mock(ChannelService.class); when(channels.findById(channel.getId())).thenReturn(Optional.of(channel));
        var recipient=mock(PanelFeedbackPromptDeliveryClient.class); when(recipient.authorize(any(),anyString())).thenReturn(true);
        var sender=mock(PanelOutboundSenderAuthorizationClient.class); when(sender.authorize(any())).thenReturn(true);
        var provider=MessagingPlatformIsolationTest.messenger(platform,true);
        when(provider.sendToUser(anyLong(),anyString())).thenAnswer(call -> {
            assertThat(state()).isEqualTo("sending");
            assertThat(jdbc.resultWrites).hasValue(0);
            switch (change) {
                case "transient" -> jdbc.failures=1;
                case "two-transients" -> jdbc.failures=2;
                case "unavailable" -> jdbc.failures=Integer.MAX_VALUE;
                case "programming-error" -> jdbc.programmingError=true;
                case "commit-ack-loss" -> loseCommitReply.set(true);
                case "provider-false" -> { return false; }
                case "provider-error" -> throw new IllegalStateException("Test: provider outcome unknown");
                default -> { }
            }
            return true;
        });
        var dispatch=new OutboundFeedbackPromptDispatchService(channels,new MessagingService(List.of(provider),sender),ledger,recipient);
        var event=new OutboundFeedbackPromptEvent("r262","feedback.prompt.dispatch","r262",platform,channel.getId(),100L,
            9007199254740993L," padded/Ю%?' ","test prompt");
        boolean success=List.of("ok","transient","two-transients","commit-ack-loss").contains(change);
        if (success) dispatch.dispatch(event,"isolated.route");
        else assertThatThrownBy(() -> dispatch.dispatch(event,"isolated.route")).isInstanceOf(RuntimeException.class);
        assertThat(state()).isEqualTo(success?"delivered":"uncertain");
        int expectedWrites=switch(change) {
            case "transient","commit-ack-loss" -> 2;
            case "two-transients","unavailable" -> 3;
            case "provider-false","provider-error" -> 0;
            default -> 1;
        };
        assertThat(jdbc.resultWrites).hasValue(expectedWrites);
        assertThat(lostCommitReplies).hasValue(change.equals("commit-ack-loss")?1:0);
        assertThat(inspect.queryForObject("SELECT ticket_id FROM integration_outbound_event_deliveries",String.class)).isEqualTo(event.ticketId());
        Object delivered=inspect.queryForObject("SELECT delivered_at FROM integration_outbound_event_deliveries",Object.class);
        if(success)assertThat(delivered).isNotNull();else assertThat(delivered).isNull();
        var before=inspect.queryForMap("SELECT * FROM integration_outbound_event_deliveries");
        // New service instance represents ledger restoration, not an in-memory duplicate cache.
        var restored=new OutboundTransportDeliveryLedgerService(new JdbcTemplate(jdbc.getDataSource()));
        var replay=new OutboundFeedbackPromptDispatchService(channels,new MessagingService(List.of(provider),sender),restored,recipient);
        replay.dispatch(event,"isolated.route");
        assertThat(inspect.queryForMap("SELECT * FROM integration_outbound_event_deliveries")).isEqualTo(before);
        verify(provider,times(1)).sendToUser(event.userId(),event.prompt());
        verify(provider,never()).sendToSupportChat(anyString(),anyString());
        verify(sender,times(1)).authorize(any()); verify(recipient,times(1)).authorize(any(),anyString());
        assertThat(jdbc.resultWrites).hasValue(expectedWrites);
    }
    @ParameterizedTest @ValueSource(strings={"processing","retryable","uncertain","failed","unknown-state","delivered"})
    void confirmationDoesNotResolveAnUnprovenOrMissingSendingClaim(String state) {
        assertThat(ledger.beginDelivery("r262","feedback.prompt.dispatch","route",1L,2L," raw ",3L)).isTrue();
        inspect.update("UPDATE integration_outbound_event_deliveries SET status=?",state);
        var before=inspect.queryForMap("SELECT * FROM integration_outbound_event_deliveries");
        assertThatThrownBy(() -> ledger.markDelivered("r262")).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.resultWrites).hasValue(1);
        assertThat(inspect.queryForMap("SELECT * FROM integration_outbound_event_deliveries")).isEqualTo(before);
    }
    @Test void repeatingConfirmationIsReadOnlyAfterTheFirstCommittedSuccess() {
        assertThat(ledger.beginDelivery("r262","feedback.prompt.dispatch","route",1L,2L," raw ",3L)).isTrue();
        ledger.markSending("r262"); ledger.markDelivered("r262");
        var before=inspect.queryForMap("SELECT * FROM integration_outbound_event_deliveries");
        ledger.markDelivered("r262"); ledger.markDelivered("r262");
        assertThat(inspect.queryForMap("SELECT * FROM integration_outbound_event_deliveries")).isEqualTo(before);
    }
    @Test void absentClaimIsNotInventedByAConfirmation() {
        assertThatThrownBy(() -> ledger.markDelivered("missing")).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.resultWrites).hasValue(1);
        assertThat(inspect.queryForObject("SELECT count(*) FROM integration_outbound_event_deliveries",Integer.class)).isZero();
    }
    private String state() { return inspect.queryForObject("SELECT status FROM integration_outbound_event_deliveries WHERE event_id='r262'",String.class); }
    private static class ResultJdbc extends JdbcTemplate {
        final AtomicInteger resultWrites=new AtomicInteger();
        int failures;
        boolean programmingError;
        ResultJdbc(DataSource source) { super(source); }
        @Override public int update(String sql,Object... args) {
            if(sql.contains("delivered_at = ?")){
                resultWrites.incrementAndGet();
                if(programmingError)throw new IllegalStateException("Test: non-persistence defect must not be retried");
                if(failures>0){failures--;throw new DataAccessResourceFailureException("Test: confirmation database unavailable");}
            }
            return super.update(sql,args);
        }
    }
}
