package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.runtime.*;
import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import static com.example.panel.service.UiEventActorFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UiEventBoundRecipientPostgresTest {
    static JdbcTemplate jdbc;static SingleConnectionDataSource connection;
    MultibusinessFeatureFlags flags;UiEventTicketAccessService access;UiEventStreamService stream;UiEventFanoutPublisher publisher;Authentication actor;
    @BeforeAll static void schema()throws Exception{var fresh=PostgresqlJdbcTestSupport.freshJdbcTemplate("sse_bound_recipient");connection=new SingleConnectionDataSource(fresh.getDataSource().getConnection(),true);jdbc=new JdbcTemplate(connection);UiEventActorFixture.schema(jdbc,false);}
    @AfterAll static void close(){if(connection!=null)connection.destroy();}
    @BeforeEach void setup(){reset(7,"caller",false);}
    void reset(long id,String name,boolean all){seed(jdbc,id,name,"unused",all);actor=auth(id,name);flags=new MultibusinessFeatureFlags();access=new UiEventTicketAccessService(jdbc,flags);publisher=mock(UiEventFanoutPublisher.class);stream=new UiEventStreamService(publisher,new LegacyTicketIdJdbcGuard(jdbc),access);}
    static Stream<Arguments> names(){return Stream.of("caller"," Caller ","Юникод").flatMap(name->Stream.of(false,true).flatMap(all->Stream.of(false,true).map(compat->Arguments.of(name,all,compat))));}
    @ParameterizedTest @MethodSource("names") void rawNamesUseOnlyTheirBoundAccount(String name,boolean all,boolean compat){reset(7,name,all);assertThat(canRead(access,actor,scope(compat))).isTrue();}
    static Stream<Arguments> ids(){return Stream.of(9007199254740993L,Long.MAX_VALUE).flatMap(id->Stream.of(false,true).flatMap(all->Stream.of(false,true).map(compat->Arguments.of(id,all,compat))));}
    @ParameterizedTest @MethodSource("ids") void bigIdsStayExact(long id,boolean all,boolean compat){reset(id,"caller",all);assertThat(canRead(access,actor,scope(compat))).isTrue();}
    static Stream<Arguments> stale(){return Stream.of("rename-reuse","delete-reuse","case","disabled","blocked","null-blocked","duplicate").flatMap(mode->Stream.of(false,true).flatMap(all->Stream.of(false,true).map(compat->Arguments.of(mode,all,compat))));}
    @ParameterizedTest @MethodSource("stale") void staleIdentityCannotReadEitherScope(String mode,boolean all,boolean compat){reset(7,"caller",all);change(jdbc,mode);var before=snapshot(jdbc);assertThat(canRead(access,actor,scope(compat))).isFalse();assertThat(snapshot(jdbc)).isEqualTo(before);}
    static Stream<Arguments> invalid(){return Stream.of("wrong-id","wrong-name","unbound","null","anonymous","unauthenticated","disabled","zero").flatMap(mode->Stream.of(false,true).map(compat->Arguments.of(mode,compat)));}
    @ParameterizedTest @MethodSource("invalid") void invalidAuthenticationCannotRead(String mode,boolean compat){actor=invalidActor(mode);assertThat(canRead(access,actor,scope(compat))).isFalse();}
    Authentication invalidActor(String mode){return switch(mode){case "wrong-id"->auth(55,"caller");case "wrong-name"->auth(7,"CALLER");case "unbound"->UsernamePasswordAuthenticationToken.authenticated("caller",null,List.of());case "null"->null;case "anonymous"->new AnonymousAuthenticationToken("fixture","caller",List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ANONYMOUS")));case "unauthenticated"->UsernamePasswordAuthenticationToken.unauthenticated(new PanelUserPrincipal(7,"caller","unused",true,List.of()),null);case "disabled"->UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(7,"caller","unused",false,List.of()),null,List.of());case "zero"->auth(0,"caller");default->throw new AssertionError();};}
    @ParameterizedTest @ValueSource(strings={"unbound","null","anonymous","unauthenticated","disabled","zero"}) void invalidConnectionCannotBecomeAUsernameSubscription(String mode){assertThatThrownBy(()->connect(stream,invalidActor(mode))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);}
    @ParameterizedTest @MethodSource("stale") void accountIsCheckedAtOwningSqlBoundary(String mode,boolean all,boolean compat)throws Exception{reset(7,"caller",all);var fired=new AtomicBoolean();var raced=boundary(jdbc,()->{if(fired.compareAndSet(false,true))change(jdbc,mode);});assertThat(canRead(new UiEventTicketAccessService(raced,flags),actor,scope(compat))).isFalse();assertThat(fired).isTrue();}
    static Stream<Arguments> delivery(){return Stream.of("local","captured","broadcast","target","json").flatMap(path->Stream.of("rename-reuse","delete-reuse","case","disabled","blocked","null-blocked","duplicate").flatMap(mode->Stream.of(false,true).map(all->Arguments.of(path,mode,all))));}
    void deliver(String path)throws Exception{var scope=scope(false);var event=new UiEventFanoutEnvelope("queued","worker",path.equals("target")?"CALLER":null,"dialogs_changed",Map.of("ticketId",RAW,"preview","private R233"),scope);switch(path){case "local"->stream.publishDialogsChanged("private R233",RAW);case "captured"->stream.publishScopedDialogsChanged("private R233",scope);case "json"->{var message=mock(Message.class);when(message.getBody()).thenReturn(new ObjectMapper().writeValueAsString(event).getBytes(StandardCharsets.UTF_8));new UiEventRedisSubscriber(new ObjectMapper(),stream).onMessage(message,null);}default->stream.deliverFanoutEvent(event);}}
    @ParameterizedTest @MethodSource("delivery") void existingConnectionNeverFollowsAReusedName(String path,String mode,boolean all)throws Exception{reset(7,"caller",all);var emitter=connect(stream,actor);change(jdbc,mode);deliver(path);assertThat(payloads(emitter)).isEmpty();}
    @ParameterizedTest @ValueSource(strings={"local","captured","broadcast","target","json"}) void replacementAccountHasItsOwnConnectionAndCleanup(String path)throws Exception{var old=connect(stream,actor);change(jdbc,"rename-reuse");var current=connect(stream,auth(55,"caller"));deliver(path);assertThat(payloads(old)).isEmpty();assertThat(payloads(current)).hasSize(1);ReflectionTestUtils.invokeMethod(stream,"unregister",key(actor),old);clear(current);deliver(path);assertThat(payloads(current)).hasSize(1);}
    @ParameterizedTest @ValueSource(strings={"rename-reuse","case","disabled"}) void accessIsRecheckedBetweenConnectionsOfOneActor(String mode)throws Exception{var first=mock(SseEmitter.class);var second=mock(SseEmitter.class);register(stream,actor,first);register(stream,actor,second);doAnswer(call->{change(jdbc,mode);return null;}).when(first).send(any(SseEmitter.SseEventBuilder.class));deliver("broadcast");verify(first).send(any(SseEmitter.SseEventBuilder.class));verifyNoInteractions(second);}
    @ParameterizedTest @ValueSource(strings={"rename-reuse","case"}) void publicationFallbackRechecksTheCapturedRecipient(String mode){var emitter=connect(stream,actor);when(publisher.publish(any(),anyString(),any(),any())).thenAnswer(call->{change(jdbc,mode);return false;});stream.publishDialogsChanged("private R233",RAW);assertThat(payloads(emitter)).isEmpty();}
    @Test void scopeRevocationAndStrictCompatibilityRemainIndependent(){var emitter=connect(stream,actor);jdbc.execute("DELETE FROM business_memberships WHERE user_id=7");assertThat(canRead(access,actor,scope(false))).isFalse();assertThat(canRead(access,actor,scope(true))).isTrue();flags.setStrictReadScope(true);assertThat(canRead(access,actor,scope(true))).isFalse();stream.publishNotificationsChanged("CALLER","refresh");stream.sendHeartbeat();assertThat(payloads(emitter)).hasSize(2);}
}
