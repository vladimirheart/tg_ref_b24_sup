package com.example.supportbot.max;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.supportbot.config.BotIngressCoordinationProperties;
import com.example.supportbot.entity.TicketActive;
import com.example.supportbot.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.resource.DefaultClientResources;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.*;
import org.springframework.http.ResponseEntity;

/** Real Redis session/claim Lua; canonical backend and provider remain controlled fixtures. */
class MaxSessionLossRedisIT extends MaxQuestionnaireSelectionTest {
    private static final ObjectMapper JSON=org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build();
    private static DefaultClientResources resources;
    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;
    private static String nonce;
    private final String namespace="r282-"+UUID.randomUUID();

    @BeforeAll static void connectOwnedRedis() {
        nonce=required("QUESTIONNAIRE_REDIS_OWNER");
        assertThat(nonce).matches("[0-9a-f-]{36}");
        assertThat(required("QUESTIONNAIRE_REDIS_HOST")).isEqualTo("r282-redis");
        assertThat(required("QUESTIONNAIRE_REDIS_PORT")).isEqualTo("6379");
        var server=new RedisStandaloneConfiguration("r282-redis",6379);
        server.setPassword(required("QUESTIONNAIRE_REDIS_PASSWORD"));
        resources=DefaultClientResources.builder().ioThreadPoolSize(2).computationThreadPoolSize(2).build();
        var client=LettuceClientConfiguration.builder().clientResources(resources)
            .commandTimeout(Duration.ofSeconds(2)).shutdownTimeout(Duration.ofMillis(100)).build();
        factory=new LettuceConnectionFactory(server,client);factory.afterPropertiesSet();
        redis=new StringRedisTemplate(factory);redis.afterPropertiesSet();
        assertOwned();
        assertThat(redis.opsForValue().get("r282:acceptance-only")).isEqualTo("true");
    }
    private static String required(String name) {
        String value=System.getenv(name);
        if(value==null||value.isBlank())throw new IllegalStateException("Explicit disposable Redis setting required: "+name);
        return value;
    }
    private static void assertOwned() {assertThat(redis.opsForValue().get("r282:owner")).isEqualTo(nonce);}
    @Override protected BotIngressCoordinationProperties coordinationProperties() {
        var p=new BotIngressCoordinationProperties();p.setMode("redis");p.setLeaseNamespace(namespace);return p;
    }
    @Override @SuppressWarnings("unchecked") protected ObjectProvider<StringRedisTemplate> redisProvider() {
        ObjectProvider<StringRedisTemplate> p=mock(ObjectProvider.class);when(p.getIfAvailable()).thenReturn(redis);return p;
    }
    private Set<String> keys(String suffix) {
        assertOwned();var result=new LinkedHashSet<String>();
        try(var cursor=redis.scan(ScanOptions.scanOptions().match(namespace+":"+suffix).count(100).build())) {
            while(cursor.hasNext()) {String key=cursor.next();assertThat(key).startsWith(namespace+":");result.add(key);assertThat(result.size()).isLessThan(10000);}
        }
        return result;
    }
    @Override protected void discardDeliveryCache() {var keys=keys("bot-webhook-delivery:*");if(!keys.isEmpty())redis.delete(keys);}
    @AfterEach void cleanupOwnNamespace() {
        if(redis!=null) {var own=keys("*");if(!own.isEmpty())redis.delete(own);assertThat(keys("*")).isEmpty();}
    }
    @AfterAll static void disconnect() {if(factory!=null)factory.destroy();if(resources!=null)resources.shutdown();}
    private String sessionKey() {return namespace+":bot-session:max:channel:55:user:1001";}
    private String indexKey() {return namespace+":bot-session-index:max:channel:55";}

    private FollowupFixture published(boolean general) {
        r268Choose(4,general);send("r282-first","One");send("r282-second","Two");send("r282-last","Three");
        r268AssertCommand(general,Map.of("free-first","One","free-second","Two","free-third","Three"));
        assertThat(state().selection().submittedTicketId()).isEqualTo("T-MAX");
        assertThat(state().scope().scopeKind()).isEqualTo(general?"GENERAL":"BUSINESS");
        var f=followupFixture();var active=new TicketActive();active.setTicketId("T-MAX");
        doReturn(Optional.of(active)).when(f.tickets()).findActiveTicketForUser(eq(1001L),anyString(),eq(55L));
        when(f.tickets().findByTicketId("T-MAX")).thenReturn(Optional.of(root(1001L,55L,1)));
        // Current catalog/support-chat rebinding cannot change a published immutable v4 root.
        f.channel().setSupportChatId("rebound-other-business-chat");
        doThrow(new IllegalStateException("Published root must not read rebound catalog")).when(f.runtime()).questionnaireChoices(any());
        doThrow(new IllegalStateException("Published root must not reselect")).when(f.runtime()).selectQuestionnaire(any(),any(),anyLong());
        doThrow(new IllegalStateException("Published GENERAL must not reselect")).when(f.runtime()).selectGeneralQuestionnaire(any(),any());
        clearInvocations(f.tickets(),f.messages(),f.api(),f.attachments(),f.runtime());
        return f;
    }
    private TicketService.TicketWithUser root(Long user,Long channel,Integer context) {
        return new TicketService.TicketWithUser(user,"T-MAX","open",channel,4,context);
    }
    private void loseSession(boolean expire) {
        assertThat(redis.hasKey(sessionKey())).isTrue();
        assertThat(redis.opsForSet().members(indexKey())).contains("1001");
        if(expire)assertThat(redis.expireAt(sessionKey(),Instant.EPOCH)).isTrue();
        else assertThat(redis.delete(sessionKey())).isTrue();
        assertThat(redis.hasKey(sessionKey())).isFalse();
        assertThat(redis.opsForSet().members(indexKey())).contains("1001"); // Real expiration leaves a stale index.
        rebuild();
    }
    private void assertSessionRemainsAbsent(FollowupFixture f) throws Exception {
        assertThat(f.store().load("max",55L,1001L,MaxConversationSession.State.class)).isEmpty();
        assertThat(f.store().loadAll("max",55L,MaxConversationSession.State.class)).isEmpty();
        assertThat(redis.hasKey(sessionKey())).isFalse();
        assertThat(redis.opsForSet().members(indexKey())).doesNotContain("1001");
        verify(f.tickets(),never()).createConversationTicket(any());verify(f.tickets(),never()).clearTicketActivity(any());
        verifyNoInteractions(f.runtime(),f.attachments());verify(f.api(),never()).downloadAttachment(anyString());
        verify(f.messages(),never()).sendToSupportChat(any(),anyString());
    }
    static Stream<Arguments> scopesAndLoss() {return Stream.of(false,true).flatMap(general->Stream.of(false,true).map(expire->Arguments.of(general,expire)));}
    @ParameterizedTest @MethodSource("scopesAndLoss")
    void publishedRootSurvivesSessionLossCatalogRebindAndDedupLoss(boolean general,boolean expire) throws Exception {
        var f=published(general);loseSession(expire);
        String otherChannel=namespace+":bot-session:max:channel:56:user:1001";
        String otherUser=namespace+":bot-session:max:channel:55:user:1002";
        redis.opsForValue().set(otherChannel,"other-channel");redis.opsForValue().set(otherUser,"other-user");
        assertThat(send("r282-followup","Plain reply").getBody()).containsEntry("ticket_id","T-MAX");
        assertThat(send("r282-followup","Plain reply").getBody()).containsEntry("duplicate",true);
        verify(f.tickets(),times(1)).recordActiveClientMessage(any());
        discardDeliveryCache();rebuild();
        assertThat(send("r282-followup","Plain reply").getBody()).containsEntry("ticket_id","T-MAX");
        var capture=org.mockito.ArgumentCaptor.forClass(ActiveInboundClientMessageCommand.class);
        verify(f.tickets(),times(2)).recordActiveClientMessage(capture.capture());
        var first=capture.getAllValues().get(0);var second=capture.getAllValues().get(1);
        assertThat(first.ticketId()).isEqualTo("T-MAX");assertThat(second.ticketId()).isEqualTo(first.ticketId());
        assertThat(second.providerMessageId()).isEqualTo(first.providerMessageId());
        assertThat(second.userId()).isEqualTo(1001L);assertThat(second.text()).isEqualTo("Plain reply");
        assertThat(redis.opsForValue().get(otherChannel)).isEqualTo("other-channel");
        assertThat(redis.opsForValue().get(otherUser)).isEqualTo("other-user");
        assertSessionRemainsAbsent(f); // Replayed queue command identity, not an exactly-once provider claim.
    }
    static Stream<Arguments> mediaCases() {return scopesAndLoss().flatMap(a->Stream.of("caption","opaque","forwarded","empty").map(kind->Arguments.of(a.get()[0],a.get()[1],kind)));}
    private ResponseEntity<Map<String,Object>> media(String id,String kind) {
        var body=Map.of("text",kind.equals("empty")?"":"Private caption","attachments",List.of(Map.of("type","file","payload",
            kind.equals("opaque")?Map.of("token","opaque-token"):Map.of("url","https://example.invalid/private.pdf"))));
        var message=new LinkedHashMap<String,Object>();message.put("sender",Map.of("user_id",1001,"username","client","name","Client"));message.put("recipient",Map.of("chat_id",2002));
        message.put("body",kind.equals("forwarded")?Map.of():body);
        if(kind.equals("forwarded"))message.put("link",Map.of("type","forward","message",Map.of("body",body)));
        return newControllerUpdate(id,message);
    }
    private ResponseEntity<Map<String,Object>> newControllerUpdate(String id,Map<String,Object> message) {
        // Reuse the actual handler, not its private admission helpers.
        return handleFollowupUpdate(JSON.valueToTree(Map.of("update_type","message_created","update_id",id,"message",message)));
    }
    @ParameterizedTest @MethodSource("mediaCases")
    void immutableV4StillRejectsMediaAfterRealKeyLoss(boolean general,boolean expire,String kind) throws Exception {
        var f=published(general);loseSession(expire);
        assertThat(media("r282-media",kind).getBody()).containsEntry("questionnaire_media_unsupported",true);
        discardDeliveryCache();rebuild();
        assertThat(media("r282-media",kind).getBody()).containsEntry("questionnaire_media_unsupported",true);
        verify(f.messages(),times(2)).sendToUser(eq(f.channel()),eq(1001L),contains("Вложение не сохранено"));
        verify(f.tickets(),never()).recordActiveClientMessage(any());assertSessionRemainsAbsent(f);
    }
    static Stream<Arguments> retryCases() {return scopesAndLoss().flatMap(a->Stream.of("root-missing","wrong-channel","wrong-owner","old-context","active-unavailable").map(kind->Arguments.of(a.get()[0],a.get()[1],kind)));}
    @ParameterizedTest @MethodSource("retryCases")
    void invalidRootReleasesRealRedisClaimAndSameEventRecovers(boolean general,boolean expire,String kind) throws Exception {
        var f=published(general);loseSession(expire);discardDeliveryCache();
        if(kind.equals("active-unavailable"))when(f.tickets().findActiveTicketForUser(eq(1001L),anyString(),eq(55L)))
            .thenThrow(new IllegalStateException("Canonical active lookup unavailable"));
        else when(f.tickets().findByTicketId("T-MAX")).thenReturn(kind.equals("root-missing")?Optional.empty():
            Optional.of(root(kind.equals("wrong-owner")?1002L:1001L,kind.equals("wrong-channel")?56L:55L,kind.equals("old-context")?null:1)));
        if(kind.equals("active-unavailable"))assertThatThrownBy(()->send("r282-retry","Plain reply")).hasMessageContaining("lookup unavailable");
        else assertThat(send("r282-retry","Plain reply").getStatusCode().value()).isEqualTo(503);
        assertThat(keys("bot-webhook-delivery:*")).isEmpty();verify(f.tickets(),never()).recordActiveClientMessage(any());
        assertSessionRemainsAbsent(f);
        var active=new TicketActive();active.setTicketId("T-MAX");
        doReturn(Optional.of(active)).when(f.tickets()).findActiveTicketForUser(eq(1001L),anyString(),eq(55L));
        when(f.tickets().findByTicketId("T-MAX")).thenReturn(Optional.of(root(1001L,55L,1)));
        rebuild();assertThat(send("r282-retry","Plain reply").getBody()).containsEntry("ticket_id","T-MAX");
        verify(f.tickets(),times(1)).recordActiveClientMessage(argThat(c->"T-MAX".equals(c.ticketId())));
        assertThat(keys("bot-webhook-delivery:*")).hasSize(1);
        assertThat(redis.opsForValue().get(keys("bot-webhook-delivery:*").iterator().next())).startsWith("processed:");
        assertSessionRemainsAbsent(f);
    }
}
