package com.example.panel.security;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.HandlerMapping;

/** Fresh native loopback cluster, nonce-checked before any DDL. No application datasource/config. */
class ChildPrincipalPostgresTest {
    private static final String MASTER="r291-native-only-master-012345678901234567890";
    private static String url;
    private static String adminPassword="";
    private static org.testcontainers.containers.PostgreSQLContainer<?> postgres;
    private static JdbcTemplate admin;
    private JdbcTemplate child;
    @BeforeAll static void schema() throws Exception {
        url=System.getProperty("r291.principals.jdbc","");
        String nonce=System.getProperty("r291.principals.nonce","");
        if(url.isEmpty()) {
            nonce=java.util.UUID.randomUUID().toString().replace("-","");adminPassword="r291-disposable-test-only";
            postgres=new org.testcontainers.containers.PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("r291_principals").withUsername("r290_test").withPassword(adminPassword)
                .withLabel("io.iguana.owner","01-278-r291-principals").withLabel("io.iguana.test-nonce",nonce)
                .withCreateContainerCmdModifier(c->c.getHostConfig().withMemory(192L*1024*1024).withNanoCPUs(1_000_000_000L)
                    .withPortBindings(new com.github.dockerjava.api.model.PortBinding(com.github.dockerjava.api.model.Ports.Binding.bindIpAndPort("127.0.0.1",0),new com.github.dockerjava.api.model.ExposedPort(5432))));
            postgres.start();url="jdbc:postgresql://127.0.0.1:"+postgres.getMappedPort(5432)+"/r291_principals";
            var owner=new JdbcTemplate(new DriverManagerDataSource(url,"r290_test",adminPassword));
            owner.execute("CREATE TABLE r290_native_owner(nonce text NOT NULL)");owner.update("INSERT INTO r290_native_owner VALUES(?)",nonce);
        }
        if(!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/r291_principals") || !nonce.matches("[a-f0-9]{32}"))
            throw new IllegalArgumentException("Nonce-owned disposable PostgreSQL required");
        int port=java.net.URI.create(url.substring(5)).getPort();
        if(port<=1024 || port==5432 || port>65535)throw new IllegalArgumentException("Disposable port required");
        admin=new JdbcTemplate(new DriverManagerDataSource(url,"r290_test",adminPassword));
        if(!nonce.equals(admin.queryForObject("SELECT nonce FROM r290_native_owner",String.class)))throw new IllegalStateException("Wrong native owner");
        admin.execute("CREATE TABLE channels(id bigint PRIMARY KEY, platform text, is_active boolean)");
        admin.execute("CREATE TABLE tickets(user_id bigint,ticket_id text,channel_id bigint,is_general boolean,PRIMARY KEY(user_id,ticket_id))");
        admin.execute("CREATE TABLE pending_feedback_requests(id bigint PRIMARY KEY,ticket_id text,channel_id bigint)");
        admin.execute("CREATE TABLE settings(secret text)");
        admin.execute("INSERT INTO channels VALUES(22,'max',true),(23,'max',true),(24,'telegram',true),(25,'vk',true)");
        for(String migration:List.of("V24__integration_transport_outbox.sql","V85__outbound_delivery_receipts.sql","V86__child_transport_principals.sql"))
            admin.execute(new ClassPathResource("db/migration/postgresql/"+migration).getContentAsString(StandardCharsets.UTF_8));
        String provision=Files.readString(Path.of("../scripts/sql/provision-child-transport.sql"))
            .replaceAll("(?m)^\\\\[^\\r\\n]*[\\r\\n]+","");
        for(int id:List.of(22,23,24,25)) {
            String platform=id==24?"telegram":id==25?"vk":"max";
            admin.execute(provision.replace(":'channel_id'","'"+id+"'").replace(":'platform'","'"+platform+"'").replace(":'password'","'"+"a".repeat(64)+"'"));
        }
    }
    @AfterAll static void stopOwnedContainer() {if(postgres!=null)postgres.stop();}
    @BeforeEach void fixture() {
        admin.execute("TRUNCATE integration_outbound_delivery_receipts,integration_outbound_event_deliveries,integration_transport_outbox,tickets,pending_feedback_requests");
        admin.execute("UPDATE channels SET is_active=true,platform=CASE id WHEN 24 THEN 'telegram' WHEN 25 THEN 'vk' ELSE 'max' END");
        admin.execute("UPDATE bot_child_principals SET enabled=true");
        admin.execute("INSERT INTO tickets VALUES(7,'own',22,false),(8,'foreign',23,false),(9,'general',22,true)");
        for(int id:List.of(22,23)) {
            admin.update("INSERT INTO integration_outbound_event_deliveries(event_id,channel_id,user_id,ticket_id,status,created_at,updated_at) VALUES(?,?,7,?,'sending',now(),now())","e"+id,id,id==22?"own":"foreign");
            enqueue(admin,id,"max","o"+id);
        }
        child=client(22);
    }
    private JdbcTemplate client(int id) { return new JdbcTemplate(new DriverManagerDataSource(url,"iguana_child_"+id,"a".repeat(64))); }
    private static void enqueue(JdbcTemplate jdbc,long channel,String platform,String event) {
        String payload="{\"eventId\":\""+event+"\",\"eventKind\":\"client_message.active_ticket\",\"channelId\":"+channel+",\"platform\":\""+platform+"\",\"userId\":7,\"ticketId\":\"own\"}";
        jdbc.update("INSERT INTO integration_transport_outbox(event_id,transport_source,event_kind,exchange_name,routing_key,payload_json,channel_id,user_id,ticket_id,status) VALUES(?,'bot.inbound.client-message','client_message.active_ticket','x','route',?,?,7,'own','queued')",event,payload,channel);
    }
    @ParameterizedTest @ValueSource(ints={22,23,24,25})
    void loginIsARealRestrictedChannelPrincipal(int id) {
        var jdbc=client(id);
        assertThat(jdbc.queryForObject("SELECT session_user::text",String.class)).isEqualTo("iguana_child_"+id);
        assertThat(jdbc.queryForObject("SELECT bot_child_channel()",Long.class)).isEqualTo((long)id);
    }
    @Test void wrongPasswordCannotAuthenticate() {
        var bad=new JdbcTemplate(new DriverManagerDataSource(url,"iguana_child_22","wrong"));
        assertThatThrownBy(()->bad.queryForObject("SELECT 1",Integer.class)).isInstanceOf(DataAccessException.class);
    }
    @Test void childSchedulerSeesOnlyOwnOutboxAndLedger() {
        assertThat(child.queryForList("SELECT event_id FROM integration_transport_outbox",String.class)).containsExactly("o22");
        assertThat(child.queryForList("SELECT event_id FROM integration_outbound_event_deliveries",String.class)).containsExactly("e22");
        assertThat(child.update("UPDATE integration_transport_outbox SET status='published'")).isEqualTo(1);
        assertThat(admin.queryForObject("SELECT status FROM integration_transport_outbox WHERE event_id='o23'",String.class)).isEqualTo("queued");
    }
    @ParameterizedTest @ValueSource(strings={"SELECT * FROM tickets","SELECT * FROM channels","SELECT * FROM settings","SELECT * FROM bot_child_principals",
        "UPDATE channels SET is_active=false","UPDATE bot_child_principals SET enabled=false","CREATE TABLE escaped(id int)",
        "ALTER TABLE integration_transport_outbox DISABLE ROW LEVEL SECURITY","TRUNCATE integration_transport_outbox",
        "SET ROLE r290_test","SET ROLE iguana_child_23","SET SESSION AUTHORIZATION r290_test"})
    void businessDataDdlAndRoleEscapesAreDenied(String sql) {
        assertThatThrownBy(()->child.execute(sql)).isInstanceOf(DataAccessException.class);
    }
    @ParameterizedTest @ValueSource(strings={"channel_id=23","payload_json=jsonb_set(payload_json::jsonb,'{channelId}','23')::text",
        "payload_json=jsonb_set(payload_json::jsonb,'{platform}','\"telegram\"')::text",
        "transport_source='panel.outbound.feedback-prompt'","payload_json=jsonb_set(payload_json::jsonb,'{eventId}','\"other\"')::text",
        "payload_json=jsonb_set(payload_json::jsonb,'{userId}','8')::text","payload_json=jsonb_set(payload_json::jsonb,'{ticketId}','\"foreign\"')::text"})
    void envelopeCannotMoveOrForgeItsTransportScope(String assignment) {
        assertThatThrownBy(()->child.execute("UPDATE integration_transport_outbox SET "+assignment+" WHERE event_id='o22'"))
            .isInstanceOf(DataAccessException.class);
    }
    @Test void ownInitialAndFollowupEnvelopesRemainWritable() {
        enqueue(child,22,"max","own-new");
        assertThat(child.queryForObject("SELECT COUNT(*) FROM integration_transport_outbox",Integer.class)).isEqualTo(2);
        assertThatThrownBy(()->enqueue(child,23,"max","foreign-new")).isInstanceOf(DataAccessException.class);
    }
    @ParameterizedTest @ValueSource(strings={"UPDATE bot_child_principals SET enabled=false WHERE channel_id=22",
        "UPDATE channels SET is_active=false WHERE id=22","UPDATE channels SET platform='vk' WHERE id=22"})
    void revokeDisableAndPlatformRebindCloseSqlAccess(String revoke) {
        admin.execute(revoke);
        assertThat(child.queryForObject("SELECT COUNT(*) FROM integration_transport_outbox",Integer.class)).isZero();
        assertThatThrownBy(()->enqueue(child,22,"max","after-revoke")).isInstanceOf(DataAccessException.class);
    }
    @Test void gucAndSetRoleCannotChooseAnotherTenant() {
        new TransactionTemplate(new JdbcTransactionManager(child.getDataSource())).executeWithoutResult(tx->{
            child.execute("SET LOCAL app.bot_channel_id='23'"); child.execute("SET LOCAL row_security=on");
            assertThat(child.queryForObject("SELECT bot_child_channel()",Long.class)).isEqualTo(22L);
            assertThat(child.queryForList("SELECT event_id FROM integration_transport_outbox",String.class)).containsExactly("o22");
        });
    }
    @Test void receiptCannotBeWrittenForAnotherChannelOrPlatform() {
        String insert="INSERT INTO integration_outbound_delivery_receipts(event_id,attempt_id,channel_id,user_id,platform,sender_fingerprint,prompt_sha256,prepared_at) VALUES(?,?::uuid,?,7,?,?,?,now())";
        child.update(insert,"e22",java.util.UUID.randomUUID().toString(),22,"max","a".repeat(64),"b".repeat(64));
        assertThatThrownBy(()->child.update(insert,"e23",java.util.UUID.randomUUID().toString(),23,"max","a".repeat(64),"b".repeat(64))).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->child.update("UPDATE integration_outbound_delivery_receipts SET platform='vk'")).isInstanceOf(DataAccessException.class);
        assertThat(child.update("UPDATE integration_outbound_delivery_receipts SET provider_message_id='123',accepted_at=now() WHERE event_id='e22'")).isEqualTo(1);
    }
    @Test void apiLocksHoldChannelAndRootUntilOwningTransactionEnds() throws Exception {
        var service=new ChildBotRequestScopeService(admin,new ObjectMapper());
        var request=new MockHttpServletRequest("GET","/internal/api/bot/tickets/own");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,"/internal/api/bot/tickets/{ticketId}");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,Map.of("ticketId","own"));
        var executor=Executors.newSingleThreadExecutor();var started=new CountDownLatch(1);
        try {
            final Future<?>[] writer=new Future<?>[1];
            new TransactionTemplate(new JdbcTransactionManager(admin.getDataSource())).executeWithoutResult(tx->{
                service.authorize(request,new ChildBotCredentials.Principal(22,"max"),new byte[0]);
                writer[0]=executor.submit(()->{started.countDown();admin.update("UPDATE tickets SET channel_id=23 WHERE ticket_id='own'");});
                try { assertThat(started.await(2,TimeUnit.SECONDS)).isTrue();
                    assertThatThrownBy(()->writer[0].get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                } catch(InterruptedException ex) {Thread.currentThread().interrupt();throw new IllegalStateException(ex);}
            });
            writer[0].get(5,TimeUnit.SECONDS);
            assertThat(admin.queryForObject("SELECT channel_id FROM tickets WHERE ticket_id='own'",Long.class)).isEqualTo(23L);
        } finally { executor.shutdownNow(); }
    }

    @ParameterizedTest @ValueSource(strings={"own","foreign","general"})
    void actualSignedMvcRouteUsesAuthenticatedChannel(String root) throws Exception {
        var properties=new InternalBotApiProperties();properties.setToken(MASTER);
        var mapper=new ObjectMapper();var coordination=new com.example.panel.config.RuntimeCoordinationProperties();coordination.setMode("direct");
        var guard=new InternalBotApiRequestGuardService(properties,coordination,mapper,
            new org.springframework.beans.factory.support.StaticListableBeanFactory().getBeanProvider(org.springframework.data.redis.core.StringRedisTemplate.class));
        guard.setChildScope(new ChildBotRequestScopeService(admin,mapper));
        var reads=org.mockito.Mockito.mock(com.example.panel.service.BotRuntimeTicketReadService.class);
        org.mockito.Mockito.when(reads.findTicket(root)).thenReturn(java.util.Optional.of(new com.example.panel.service.BotRuntimeTicketReadService.TicketLookup(7L,root,"pending",22L,4,1)));
        var controller=new com.example.panel.controller.BotRuntimeReadApiController(reads,
            org.mockito.Mockito.mock(com.example.panel.service.BotRuntimeChannelService.class),
            org.mockito.Mockito.mock(com.example.panel.service.BotRuntimeConfigService.class),
            org.mockito.Mockito.mock(com.example.panel.service.BotRuntimeBlacklistService.class),guard);
        var proxy=new org.springframework.aop.framework.ProxyFactory(controller);proxy.setProxyTargetClass(true);
        proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(new JdbcTransactionManager(admin.getDataSource()),
            new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(proxy.getProxy())
            .addFilters(new InternalBotApiContentCachingFilter()).build();
        String route="/internal/api/bot/tickets/"+root;
        var result=mvc.perform(signedGet(route,"max",22,false)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(root.equals("foreign")?403:200);
        if(root.equals("foreign"))org.mockito.Mockito.verifyNoInteractions(reads);else org.mockito.Mockito.verify(reads).findTicket(root);
    }

    @ParameterizedTest @ValueSource(strings={"missing-signature","wrong-key","foreign-path","revoked","inactive","platform-rebind"})
    void signedMvcRechecksRevocationBeforeAnyRead(String change) throws Exception {
        var properties=new InternalBotApiProperties();properties.setToken(MASTER);var mapper=new ObjectMapper();
        var guard=new InternalBotApiRequestGuardService(properties,new com.example.panel.config.RuntimeCoordinationProperties(),mapper,
            new org.springframework.beans.factory.support.StaticListableBeanFactory().getBeanProvider(org.springframework.data.redis.core.StringRedisTemplate.class));
        guard.setChildScope(new ChildBotRequestScopeService(admin,mapper));
        var channels=org.mockito.Mockito.mock(com.example.panel.service.BotRuntimeChannelService.class);
        var controller=new com.example.panel.controller.BotRuntimeReadApiController(
            org.mockito.Mockito.mock(com.example.panel.service.BotRuntimeTicketReadService.class),channels,
            org.mockito.Mockito.mock(com.example.panel.service.BotRuntimeConfigService.class),
            org.mockito.Mockito.mock(com.example.panel.service.BotRuntimeBlacklistService.class),guard);
        var proxy=new org.springframework.aop.framework.ProxyFactory(controller);proxy.setProxyTargetClass(true);
        proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(new JdbcTransactionManager(admin.getDataSource()),
            new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(proxy.getProxy()).addFilters(new InternalBotApiContentCachingFilter()).build();
        if(change.equals("revoked"))admin.execute("UPDATE bot_child_principals SET enabled=false WHERE channel_id=22");
        if(change.equals("inactive"))admin.execute("UPDATE channels SET is_active=false WHERE id=22");
        if(change.equals("platform-rebind"))admin.execute("UPDATE channels SET platform='vk' WHERE id=22");
        String route="/internal/api/bot/channels/"+(change.equals("foreign-path")?23:22);
        var request=signedGet(route,"max",22,change.equals("wrong-key"));
        if(change.equals("missing-signature"))request=org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(route)
            .header(InternalBotApiRequestGuardService.AUTH_HEADER,ChildBotCredentials.apiToken(MASTER,null,new ChildBotCredentials.Principal(22,"max")));
        assertThat(mvc.perform(request).andReturn().getResponse().getStatus()).isIn(401,403);
        org.mockito.Mockito.verifyNoInteractions(channels);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder signedGet(String route,String platform,long channel,boolean wrong) throws Exception {
        String token=ChildBotCredentials.apiToken(MASTER,null,new ChildBotCredentials.Principal(channel,platform));
        String timestamp=java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString();
        var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec((wrong?MASTER:token).getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        String digest=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(new byte[0]));
        String signature=java.util.HexFormat.of().formatHex(mac.doFinal(("GET\n"+route+"\n"+timestamp+"\n"+digest).getBytes(StandardCharsets.UTF_8)));
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(route).header(InternalBotApiRequestGuardService.AUTH_HEADER,token)
            .header(InternalBotApiRequestGuardService.TIMESTAMP_HEADER,timestamp).header(InternalBotApiRequestGuardService.SIGNATURE_HEADER,signature);
    }

    @Test void completeFlywayChainIncludesSchemaIsolatedPrincipalPolicies() {
        String schema="r291_flyway_"+java.util.UUID.randomUUID().toString().replace("-","");
        var source=new DriverManagerDataSource(url+"?currentSchema="+schema,"r290_test",adminPassword);
        try {
            var flyway=org.flywaydb.core.Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration/postgresql").load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(94);flyway.validate();
            assertThat(flyway.info().current().getVersion().toString()).isEqualTo("94");
            var scoped=new JdbcTemplate(source);
            assertThat(scoped.queryForObject("SELECT count(*) FROM pg_policies WHERE schemaname=? AND policyname LIKE '%child_scope'",Integer.class,schema)).isEqualTo(3);
            String definition=scoped.queryForObject("SELECT pg_get_functiondef(oid) FROM pg_proc WHERE pronamespace=?::regnamespace AND proname='bot_child_channel'",String.class,schema);
            assertThat(definition).contains(schema+".bot_child_principals",schema+".channels");
            long channel=scoped.queryForObject("INSERT INTO channels(token,channel_name,platform) VALUES('r291-delete-only','R291 disposable','max') RETURNING id",Long.class);
            scoped.update("INSERT INTO bot_child_principals(role_name,channel_id,platform) VALUES(?,?,'max')","iguana_child_"+channel,channel);
            assertThat(scoped.update("DELETE FROM channels WHERE id=?",channel)).isEqualTo(1);
            assertThat(scoped.queryForObject("SELECT count(*) FROM bot_child_principals WHERE channel_id=?",Integer.class,channel)).isZero();
        } finally { admin.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE"); }
    }

    @ParameterizedTest @ValueSource(strings={"revoke","inactive","platform-rebind"})
    void alreadyAcceptedReceiptSurvivesRevocationButANewAttemptDoesNot(String change) {
        child.update("INSERT INTO integration_outbound_delivery_receipts(event_id,attempt_id,channel_id,user_id,ticket_id,platform,sender_fingerprint,prompt_sha256,prepared_at) VALUES('e22',?::uuid,22,7,'own','max',?,?,now())",
            java.util.UUID.randomUUID().toString(),"a".repeat(64),"b".repeat(64));
        switch(change) {case "revoke"->admin.execute("UPDATE bot_child_principals SET enabled=false WHERE channel_id=22");
            case "inactive"->admin.execute("UPDATE channels SET is_active=false WHERE id=22");
            default->admin.execute("UPDATE channels SET platform='vk' WHERE id=22");}
        assertThat(child.update("UPDATE integration_outbound_delivery_receipts SET provider_message_id='123',accepted_at=now() WHERE event_id='e22'")).isEqualTo(1);
        assertThat(child.update("UPDATE integration_outbound_event_deliveries SET status='delivered',delivered_at=now() WHERE event_id='e22'")).isEqualTo(1);
        assertThatThrownBy(()->child.update("UPDATE integration_outbound_event_deliveries SET status='sending' WHERE event_id='e22'")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->enqueue(child,22,"max","new-after-revoke")).isInstanceOf(DataAccessException.class);
    }

    @ParameterizedTest @ValueSource(strings={"own","general","foreign-channel","foreign-user","duplicate","new","missing","bad-signature","changed-wire","foreign-key"})
    void authenticatedIngressUsesOwningTransactionAndRoot(String change) throws Exception {
        var properties=new InternalBotApiProperties();properties.setToken(MASTER);
        var guard=new ChildBotInboundPrincipalService(admin,properties);
        String root=change.equals("general")?"general":change.equals("foreign-channel")?"foreign":change.equals("new")||change.equals("missing")?"not-yet-created":"own";
        long user=change.equals("general")?9:change.equals("foreign-channel")?8:change.equals("foreign-user")?8:7;
        if(change.equals("duplicate"))admin.execute("INSERT INTO tickets VALUES(10,'own',23,false)");
        byte[] wire=("{\"channelId\":22,\"userId\":"+user+",\"ticketId\":\""+root+"\",\"platform\":\"max\"}").getBytes(StandardCharsets.UTF_8);
        String token=ChildBotCredentials.apiToken(MASTER,null,new ChildBotCredentials.Principal(change.equals("foreign-key")?23:22,"max"));
        var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(token.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        String digest=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(wire));
        String signature=change.equals("bad-signature")?"a".repeat(64):java.util.HexFormat.of().formatHex(mac.doFinal(("iguana-child-event-v1\niguana_child_22\n"+digest).getBytes(StandardCharsets.UTF_8)));
        byte[] bytes=change.equals("changed-wire")?"changed".getBytes(StandardCharsets.UTF_8):wire;
        org.assertj.core.api.ThrowableAssert.ThrowingCallable call=()->new TransactionTemplate(new JdbcTransactionManager(admin.getDataSource()))
            .executeWithoutResult(tx->guard.authorize("iguana_child_22",22L,"max",user,root,change.equals("new"),bytes,signature));
        if(java.util.Set.of("own","general","new").contains(change))assertThatCode(call).doesNotThrowAnyException();
        else assertThatThrownBy(call).isInstanceOf(org.springframework.amqp.AmqpRejectAndDontRequeueException.class);
    }
}
