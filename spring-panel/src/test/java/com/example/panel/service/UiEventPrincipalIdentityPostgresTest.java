package com.example.panel.service;

import static com.example.panel.service.UiEventActorFixture.stored;
import static com.example.panel.service.UiEventActorFixture.key;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.runtime.RuntimeRoleProperties;
import com.example.panel.runtime.UiEventFanoutProperties;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class UiEventPrincipalIdentityPostgresTest {
    private JdbcTemplate jdbc;
    private UiEventTicketAccessService access;
    private UiEventStreamService stream;

    @BeforeEach void prepare() {
        jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("sse_principal_identity");
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY, username TEXT UNIQUE, enabled BOOLEAN, is_blocked BOOLEAN)");
        jdbc.execute("CREATE TABLE businesses(id BIGINT PRIMARY KEY, status TEXT)");
        jdbc.execute("CREATE TABLE business_memberships(user_id BIGINT, business_id BIGINT, role_code TEXT, is_active BOOLEAN)");
        jdbc.execute("CREATE TABLE business_access_grants(user_id BIGINT, grant_scope TEXT, role_code TEXT, is_active BOOLEAN)");
        jdbc.execute("CREATE TABLE tickets(user_id BIGINT, ticket_id TEXT, ticket_public_id TEXT, is_general BOOLEAN, business_id BIGINT, PRIMARY KEY(user_id,ticket_id))");
        jdbc.update("INSERT INTO users VALUES (1,'reader',TRUE,FALSE),(2,' reader ',TRUE,FALSE)");
        jdbc.update("INSERT INTO businesses VALUES (11,'active'),(22,'active')");
        jdbc.update("INSERT INTO business_memberships VALUES (1,11,'VIEWER',TRUE),(2,22,'VIEWER',TRUE)");
        jdbc.update("INSERT INTO tickets VALUES (101,' T-A ',?,FALSE,11),(102,'T-B',?,FALSE,22)", "a".repeat(32),"b".repeat(32));
        access = new UiEventTicketAccessService(jdbc,new MultibusinessFeatureFlags());
        stream = new UiEventStreamService(mock(UiEventFanoutPublisher.class),new LegacyTicketIdJdbcGuard(jdbc),access);
    }

    @ParameterizedTest @ValueSource(strings={" reader","reader "," reader ","\treader\t","\nreader\r"})
    void whitespacePrincipalCannotReceiveAnotherAccountsBusinessEvents(String identity) {
        SseEmitter forged = connect(identity), canonical = connect("READER");
        var scope = access.captureScope(" T-A ").orElseThrow();
        assertThat(access.canReadTicket(stored(jdbc,identity),scope)).isFalse();
        stream.publishDialogsChanged("private",scope.ticketId());
        stream.deliverFanoutEvent(envelope(null,"dialog_history_changed",scope));
        stream.deliverFanoutEvent(envelope("READER","dialogs_changed",scope));
        assertThat(payloads(forged)).isEmpty();
        assertThat(payloads(canonical)).hasSize(3).allSatisfy(p -> assertThat(p).containsEntry("ticketId"," T-A "));
    }

    @ParameterizedTest @ValueSource(strings={" reader","reader "," reader ","\treader\t","\nreader\r"})
    void targetedNotificationRefreshNeverSharesAnotherConnectionsKey(String identity) {
        SseEmitter spaced = connect(identity), plain = connect("reader");
        stream.publishNotificationsChanged("READER","plain");
        assertThat(payloads(spaced)).isEmpty();
        assertThat(payloads(plain)).hasSize(1);
        clear(spaced);clear(plain);
        stream.publishNotificationsChanged(Set.of(identity.toUpperCase(Locale.ROOT)),"spaced");
        stream.deliverFanoutEvent(new UiEventFanoutEnvelope("queued","worker",identity.toUpperCase(Locale.ROOT),
            "notifications_changed",Map.of("reason","queued-spaced"),null));
        assertThat(payloads(spaced)).hasSize(2);
        assertThat(payloads(plain)).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings={"plain","spaced"})
    void redisJsonSubscriberRoundTripPreservesTargetAndCurrentBusinessRead(String account) throws Exception {
        String identity = account.equals("spaced") ? " reader " : "reader";
        String other = account.equals("spaced") ? "reader" : " reader ";
        String raw = account.equals("spaced") ? "T-B" : " T-A ";
        SseEmitter recipient = connect(identity.toUpperCase(Locale.ROOT)), neighbor = connect(other);
        var scope = access.captureScope(raw).orElseThrow();
        var mapper = new ObjectMapper();var redis = mock(StringRedisTemplate.class);
        var properties = new UiEventFanoutProperties();properties.setMode("redis");
        var publisher = new UiEventFanoutPublisher(redis,mapper,properties,new RuntimeRoleProperties());
        Map<String,Object> payload = Map.of("ticketId",raw,"preview","private");
        assertThat(publisher.publish(identity.toUpperCase(Locale.ROOT),"dialogs_changed",payload,scope)).isTrue();
        var serialized = ArgumentCaptor.forClass(String.class);verify(redis).convertAndSend(anyString(),serialized.capture());
        var decoded = mapper.readValue(serialized.getValue(),UiEventFanoutEnvelope.class);
        assertThat(decoded.targetUser()).isEqualTo(identity);assertThat(decoded.ticketScope()).isEqualTo(scope);
        var message = mock(Message.class);when(message.getBody()).thenReturn(serialized.getValue().getBytes(StandardCharsets.UTF_8));
        var subscriber = new UiEventRedisSubscriber(mapper,stream);subscriber.onMessage(message,null);
        assertThat(payloads(recipient)).containsExactly(payload);assertThat(payloads(neighbor)).isEmpty();
        clear(recipient);jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=?",account.equals("spaced")?2:1);
        subscriber.onMessage(message,null);
        assertThat(payloads(recipient)).isEmpty();assertThat(payloads(neighbor)).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings={"revoked","disabled","blocked","archived","duplicate"})
    void genuineSpacedAccountKeepsItsOwnGrantAndLosesItImmediately(String mutation) {
        SseEmitter spaced = connect(" READER "), plain = connect("reader");
        var own = access.captureScope("T-B").orElseThrow();
        assertThat(access.canReadTicket(stored(jdbc," READER "),own)).isTrue();
        assertThat(access.canReadTicket(stored(jdbc,"reader"),own)).isFalse();
        stream.publishDialogHistoryChanged("T-B",7L,"own");
        assertThat(payloads(spaced)).hasSize(1);assertThat(payloads(plain)).isEmpty();clear(spaced);
        switch(mutation) {
            case "revoked" -> jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=2");
            case "disabled" -> jdbc.update("UPDATE users SET enabled=FALSE WHERE id=2");
            case "blocked" -> jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=2");
            case "archived" -> jdbc.update("UPDATE businesses SET status='archived' WHERE id=22");
            case "duplicate" -> jdbc.update("INSERT INTO users VALUES (3,' READER ',FALSE,FALSE)");
            default -> throw new IllegalArgumentException(mutation);
        }
        stream.deliverFanoutEvent(envelope(" READER ","dialogs_changed",own));
        stream.publishDialogsChanged("late","T-B");
        assertThat(payloads(spaced)).isEmpty();assertThat(payloads(plain)).isEmpty();
    }

    @Test void redisTargetCaseFoldingUsesRootLocale() throws Exception {
        Locale previous=Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            var redis=mock(StringRedisTemplate.class);var mapper=new ObjectMapper();
            var properties=new UiEventFanoutProperties();properties.setMode("redis");
            var publisher=new UiEventFanoutPublisher(redis,mapper,properties,new RuntimeRoleProperties());
            assertThat(publisher.publish(" IDENTITY ","notifications_changed",Map.of())).isTrue();
            var serialized=ArgumentCaptor.forClass(String.class);verify(redis).convertAndSend(anyString(),serialized.capture());
            assertThat(mapper.readValue(serialized.getValue(),UiEventFanoutEnvelope.class).targetUser()).isEqualTo(" identity ");
        } finally { Locale.setDefault(previous); }
    }

    private UiEventFanoutEnvelope envelope(String target,String name,UiEventTicketScope scope) {
        return new UiEventFanoutEnvelope("queued","worker",target,name,Map.of("ticketId",scope.ticketId()),scope);
    }
    private SseEmitter connect(String identity) { var emitter=stream.connect(stored(jdbc,identity));clear(emitter);return emitter; }
    private Collection<?> pending(SseEmitter emitter) { return (Collection<?>)ReflectionTestUtils.getField(emitter,"earlySendAttempts"); }
    private void clear(SseEmitter emitter) { pending(emitter).clear(); }
    @SuppressWarnings("unchecked") private List<Map<String,Object>> payloads(SseEmitter emitter) {
        return pending(emitter).stream().map(item->ReflectionTestUtils.getField(item,"data"))
            .filter(Map.class::isInstance).map(item->(Map<String,Object>)item).toList();
    }
}
