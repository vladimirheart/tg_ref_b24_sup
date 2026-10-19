package com.example.panel.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.runtime.UiEventFanoutProperties;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class GeneralTicketRealtimePostgresTest {
    private static final String RAW=" general/Ю?% ";
    private static final String PUBLIC="a".repeat(32);
    private JdbcTemplate jdbc;
    private MultibusinessFeatureFlags flags;
    private UiEventFanoutProperties fanout;
    private StringRedisTemplate redis;
    private GeneralTicketRealtimeService service;
    private GeneralTicketEventScope scope;
    private ObjectMapper mapper;
    @BeforeEach void setup() throws Exception {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("general_realtime");
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT,enabled BOOLEAN,is_blocked BOOLEAN)");
        jdbc.execute("CREATE TABLE general_ticket_read_grants(user_id BIGINT,is_active BOOLEAN)");
        jdbc.execute("CREATE TABLE business_access_grants(user_id BIGINT,grant_scope TEXT,role_code TEXT,is_active BOOLEAN)");
        jdbc.execute("CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,ticket_public_id TEXT,is_general BOOLEAN,business_id BIGINT,PRIMARY KEY(user_id,ticket_id))");
        jdbc.execute("CREATE TABLE ui_event_outbox(id BIGINT PRIMARY KEY,event_type TEXT,ticket_id TEXT,channel_id BIGINT,message_text TEXT,message_type TEXT,attachment TEXT,rating INT,created_at TIMESTAMPTZ)");
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql/V61__ui_event_outbox_ticket_scope.sql")));
        jdbc.update("INSERT INTO users VALUES(1,'reader',TRUE,FALSE),(2,'other',TRUE,FALSE)");
        jdbc.update("INSERT INTO general_ticket_read_grants VALUES(1,TRUE)");
        jdbc.update("INSERT INTO business_access_grants VALUES(2,'ALL_BUSINESSES','ADMIN',TRUE)");
        jdbc.update("INSERT INTO tickets VALUES(10,?,?,TRUE,NULL),(20,'plain',?,FALSE,1)",RAW,PUBLIC,"b".repeat(32));
        flags=new MultibusinessFeatureFlags();flags.getFoundation().setEnabled(true);flags.setGeneralTicketQueue(true);
        fanout=new UiEventFanoutProperties();fanout.setMode("local");
        redis=mock(StringRedisTemplate.class);mapper=new ObjectMapper();
        service=new GeneralTicketRealtimeService(jdbc,flags,fanout,redis,mapper);
        scope=new GeneralTicketEventScope(10L,RAW,PUBLIC);
    }
    private UiEventTicketScope captured() { return new UiEventTicketScope(10L,RAW,PUBLIC,"GENERAL",null); }
    @SuppressWarnings("unchecked")
    private SseEmitter register(String actor) {
        var map=(ConcurrentHashMap<String,CopyOnWriteArraySet<SseEmitter>>)ReflectionTestUtils.getField(service,"emittersByUser");
        var emitter=mock(SseEmitter.class);
        map.computeIfAbsent(actor,k->new CopyOnWriteArraySet<>()).add(emitter);return emitter;
    }
    @ParameterizedTest @ValueSource(strings={"ticket_created","client_message_created","client_message_edited","operator_message_edited","feedback_created","ticket_closed_auto","ticket_closed","ticket_reopened","ticket_status_changed"})
    void onlyExplicitGeneralReaderReceivesEachWhitelistedEvent(String event) throws Exception {
        var reader=register("reader");var all=register("other");
        service.publish(captured(),event);
        verify(reader).send(any(SseEmitter.SseEventBuilder.class));verifyNoInteractions(all,redis);
        assertThat(service.canRead("READER",scope)).isTrue();
        assertThat(captured().isValid()).isFalse(); // Never widen legacy scope validation.
    }
    @ParameterizedTest @ValueSource(strings={"revoke","delete-grant","delete-account","disabled","blocked","case-collision","missing-schema","flag"})
    void currentConnectionLosesEventsAndHeartbeatClosesAfterRevocation(String mutation) throws Exception {
        var reader=register("reader");service.publish(captured(),"feedback_created");clearInvocations(reader);
        switch(mutation) {
            case "revoke"->jdbc.update("UPDATE general_ticket_read_grants SET is_active=FALSE");
            case "delete-grant"->jdbc.update("DELETE FROM general_ticket_read_grants");
            case "delete-account"->jdbc.update("DELETE FROM users WHERE id=1");
            case "disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=1");
            case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=1");
            case "case-collision"->jdbc.update("INSERT INTO users VALUES(3,'READER',FALSE,FALSE)");
            case "missing-schema"->jdbc.execute("DROP TABLE general_ticket_read_grants");
            case "flag"->flags.setGeneralTicketQueue(false);
        }
        service.deliver(new GeneralTicketRealtimeService.Event(1,scope,"feedback_created"));
        verifyNoInteractions(reader);
        assertThatThrownBy(()->service.connect("reader")).isInstanceOf(AccessDeniedException.class);
        service.sendHeartbeat();verify(reader).complete();
        clearInvocations(reader);service.sendHeartbeat();verifyNoInteractions(reader);
    }
    @ParameterizedTest @ValueSource(strings={"business","unresolved","general-business","tuple","generation","missing","collision","null-public","trimmed-id"})
    void frozenGeneralOwnerCannotBeRecapturedOrUsedAfterScopeChange(String mutation) throws Exception {
        var reader=register("reader");
        switch(mutation) {
            case "business"->jdbc.update("UPDATE tickets SET is_general=FALSE,business_id=1 WHERE user_id=10");
            case "unresolved"->jdbc.update("UPDATE tickets SET is_general=FALSE WHERE user_id=10");
            case "general-business"->jdbc.update("UPDATE tickets SET business_id=1 WHERE user_id=10");
            case "tuple"->jdbc.update("UPDATE tickets SET user_id=99 WHERE user_id=10");
            case "generation"->jdbc.update("UPDATE tickets SET ticket_public_id=? WHERE user_id=10","c".repeat(32));
            case "missing"->jdbc.update("DELETE FROM tickets WHERE user_id=10");
            case "collision"->jdbc.update("INSERT INTO tickets VALUES(99,?,?,FALSE,1)",RAW,"c".repeat(32));
            case "null-public"->jdbc.update("UPDATE tickets SET ticket_public_id=NULL WHERE user_id=10");
            case "trimmed-id"->jdbc.update("UPDATE tickets SET ticket_id=? WHERE user_id=10",RAW.trim());
        }
        service.publish(captured(),"ticket_created");
        service.deliver(new GeneralTicketRealtimeService.Event(1,scope,"feedback_created"));
        verifyNoInteractions(reader,redis);assertThat(service.isCurrent(scope)).isFalse();
    }
    @Test void eachEmitterReprovesOwnerAndGrantImmediatelyBeforeSend() throws Exception {
        var first=register("reader");var second=register("reader");
        doAnswer(call->{jdbc.update("UPDATE general_ticket_read_grants SET is_active=FALSE");return null;})
            .when(first).send(any(SseEmitter.SseEventBuilder.class));
        service.deliver(new GeneralTicketRealtimeService.Event(1,scope,"ticket_created"));
        verify(first).send(any(SseEmitter.SseEventBuilder.class));verifyNoInteractions(second);
    }
    @Test void lateOwnerChangeBetweenPublishAndLocalDeliveryIsDenied() throws Exception {
        var first=register("reader");var second=register("reader");
        doAnswer(call->{jdbc.update("UPDATE tickets SET business_id=1,is_general=FALSE WHERE user_id=10");return null;})
            .when(first).send(any(SseEmitter.SseEventBuilder.class));
        service.publish(captured(),"client_message_created");
        verify(first).send(any(SseEmitter.SseEventBuilder.class));verifyNoInteractions(second);
    }
    @Test void paddedPrincipalCannotBorrowAnotherCanonicalAccountsGrant() {
        jdbc.update("INSERT INTO users VALUES(3,' reader ',TRUE,FALSE)");
        assertThat(service.canRead(" reader ",scope)).isFalse();
        assertThatThrownBy(()->service.connect(" reader ")).isInstanceOf(AccessDeniedException.class);
        assertThat(service.canRead("reader",scope)).isTrue();
    }
    @Test void browserReceivesOnlyPublicInvalidationAndNoRawOwnerOrText() throws Exception {
        var reader=register("reader");
        doAnswer(call->{
            SseEmitter.SseEventBuilder builder=call.getArgument(0);
            var data=builder.build();
            String serialized=data.stream().map(item->String.valueOf(item.getData())).reduce("",String::concat);
            assertThat(serialized).contains("general_tickets_changed",PUBLIC,"feedback_created")
                .doesNotContain(RAW,"ticketUserId","ticketId=","private","attachment");
            return null;
        }).when(reader).send(any(SseEmitter.SseEventBuilder.class));
        service.publish(captured(),"feedback_created");
        verify(reader).send(any(SseEmitter.SseEventBuilder.class));
    }
    @Test void separateRedisTopicPreservesFrozenScopeAndConsumerReprovesAccess() throws Exception {
        fanout.setMode("redis");var reader=register("reader");
        service.publish(captured(),"ticket_created");
        var payload=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(redis).convertAndSend(eq("iguana:ui-events:v1:general:v1"),payload.capture());verifyNoInteractions(reader);
        assertThat(payload.getValue()).doesNotContain("\"valid\"");
        var event=mapper.readValue(payload.getValue(),GeneralTicketRealtimeService.Event.class);
        assertThat(event.scope()).isEqualTo(scope);
        service.onMessage(message(payload.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8)),null);
        verify(reader).send(any(SseEmitter.SseEventBuilder.class));clearInvocations(reader);
        jdbc.update("UPDATE tickets SET ticket_public_id=? WHERE user_id=10","c".repeat(32));
        service.onMessage(message(payload.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8)),null);
        verifyNoInteractions(reader);
    }
    @Test void malformedUnknownAndCrossScopeEnvelopesAreClosed() throws Exception {
        var reader=register("reader");
        for(var event:java.util.List.of(new GeneralTicketRealtimeService.Event(2,scope,"ticket_created"),
            new GeneralTicketRealtimeService.Event(1,null,"ticket_created"),
            new GeneralTicketRealtimeService.Event(1,scope,"custom"),
            new GeneralTicketRealtimeService.Event(1,scope,null),
            new GeneralTicketRealtimeService.Event(1,new GeneralTicketEventScope(20L,"plain","b".repeat(32)),"ticket_created"))) service.deliver(event);
        service.publish(new UiEventTicketScope(10L,RAW,PUBLIC,"GENERAL",1L),"ticket_created");
        service.publish(captured(),"private message");
        service.onMessage(message("{bad".getBytes()),null);
        service.onMessage(message(new byte[65537]),null);
        verifyNoInteractions(reader,redis);
    }
    @Test void redisFailureDoesNotFallBackOrLeakThroughLegacyChannels() {
        fanout.setMode("redis");var reader=register("reader");
        doThrow(new IllegalStateException("secret-token")).when(redis).convertAndSend(anyString(),anyString());
        assertThatThrownBy(()->service.publish(captured(),"ticket_created"))
            .isInstanceOf(IllegalStateException.class).hasMessage("GENERAL realtime fanout failed");
        verifyNoInteractions(reader);
    }
    @Test void outboxWatcherRoutesGeneralWithoutRawCallbacksAndAdvancesRejectedCursor() throws Exception {
        var callbacks=mock(DialogRealtimeEventService.class);
        var checkpoint=mock(RuntimeWorkerCheckpointService.class);
        when(checkpoint.readLongCursor(anyString())).thenReturn(Optional.of(0L));
        var coordination=mock(RuntimeCoordinationService.class);
        doAnswer(call->{call.getArgument(2,Runnable.class).run();return null;})
            .when(coordination).runWithLease(anyString(),any(Duration.class),any(Runnable.class));
        var guard=new LegacyTicketIdJdbcGuard(jdbc);
        var writer=new UiEventOutboxAppendService(jdbc,guard);
        var watcher=new UiEventOutboxWatcher(jdbc,callbacks,coordination,checkpoint,guard,
            new UiEventTicketAccessService(jdbc,flags),service);
        var reader=register("reader");
        writer.append("ticket_created",RAW,9L,"private client text","photo","private/key",null);
        writer.append("client_message_created",RAW,9L,"private second message","text",null,null);
        jdbc.update("INSERT INTO ui_event_outbox(id,event_type,ticket_id) VALUES(1,'ticket_created','unclassified')");
        watcher.watch();
        verify(reader,times(2)).send(any(SseEmitter.SseEventBuilder.class));verifyNoInteractions(callbacks);
        long maximum=jdbc.queryForObject("SELECT MAX(id) FROM ui_event_outbox",Long.class);
        verify(checkpoint).saveLongCursor("ui-event-outbox-watch",maximum);
    }
    @Test void disconnectedEmitterDoesNotStarveOtherConnections() throws Exception {
        var broken=register("reader");var healthy=register("reader");
        doThrow(new IOException("disconnected")).when(broken).send(any(SseEmitter.SseEventBuilder.class));
        service.publish(captured(),"feedback_created");
        verify(healthy).send(any(SseEmitter.SseEventBuilder.class));verify(broken).complete();
    }
    private Message message(byte[] body) {
        Message message=mock(Message.class);when(message.getBody()).thenReturn(body);return message;
    }
}
