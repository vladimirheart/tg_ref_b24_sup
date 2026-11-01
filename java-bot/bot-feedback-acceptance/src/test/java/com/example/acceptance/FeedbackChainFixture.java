package com.example.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.config.RuntimeCoordinationProperties;
import com.example.panel.controller.BotOutboundSenderAuthorizationController;
import com.example.panel.controller.FeedbackPromptDeliveryAuthorizationController;
import com.example.panel.entity.Channel;
import com.example.panel.model.channel.BotCredential;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.security.InternalBotApiProperties;
import com.example.panel.security.InternalBotApiRequestGuardService;
import com.example.panel.service.BotOutboundSenderAuthorizationService;
import com.example.panel.service.LegacyTicketIdJdbcGuard;
import com.example.panel.service.RuntimeCoordinationService;
import com.example.panel.service.SharedConfigService;
import com.example.panel.service.integration.FeedbackPromptDeliveryAuthorizationService;
import com.example.panel.service.integration.OutboundFeedbackPromptPublishOutboxService;
import com.example.supportbot.config.IntegrationPanelApiProperties;
import com.example.supportbot.config.IntegrationRabbitProperties;
import com.example.supportbot.config.RabbitIntegrationTransportConfig;
import com.example.supportbot.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.springframework.amqp.core.*;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.*;
import org.springframework.amqp.rabbit.listener.*;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Real storage/broker/HTTP and production decisions; only the catalog, channel reader and provider are test seams. */
final class FeedbackChainFixture implements AutoCloseable {
    static final long USER = 9_007_199_254_740_993L, CHANNEL = USER + 2, BUSINESS = USER + 6;
    static final String RAW = " padded/Ю%?' ", TOKEN = "r259-test-only", DATABASE = "r259_chain";
    static final String RECIPIENT = "/internal/api/bot/feedback-prompts/authorize-delivery";
    static final String SENDER = "/internal/api/bot/outbound/authorize-sender";
    final ObjectMapper mapper = new ObjectMapper();
    final MultibusinessFeatureFlags flags = new MultibusinessFeatureFlags();
    final IntegrationPanelApiProperties api = new IntegrationPanelApiProperties();
    final List<JsonNode> providerAttempts = new CopyOnWriteArrayList<>();
    final List<Throwable> bridgeFailures = new CopyOnWriteArrayList<>();
    final AtomicInteger recipientCalls = new AtomicInteger(), senderCalls = new AtomicInteger();
    final AtomicInteger receivedMessages = new AtomicInteger();
    final List<JsonNode> wirePayloads = new CopyOnWriteArrayList<>();
    final List<Object> recipientInputs = new CopyOnWriteArrayList<>();
    final AtomicInteger providerStatus = new AtomicInteger(200);
    final AtomicBoolean apiUnavailable = new AtomicBoolean();
    final AtomicReference<List<BotCredential>> credentials = new AtomicReference<>(List.of());
    final BlockingQueue<Throwable> consumerFailures = new LinkedBlockingQueue<>();
    final String exchange = "r259.feedback." + UUID.randomUUID();
    final JdbcTemplate jdbc;
    final HikariDataSource datasource;
    final RabbitTemplate rabbit;
    final RabbitAdmin admin;
    final OutboundFeedbackPromptPublishOutboxService outbox;
    final PanelFeedbackPromptDeliveryClient recipientClient;
    final PanelOutboundSenderAuthorizationClient senderClient;
    final CachingConnectionFactory connections;
    final LocalContainerEntityManagerFactoryBean entities;
    final HttpServer http;
    private final ExecutorService httpThreads;
    private final PostgreSQLContainer<?> postgres;
    private final GenericContainer<?> broker;
    private SimpleMessageListenerContainer consumer;
    private String platform, queue;

    FeedbackChainFixture() throws Exception {
        String jdbcUrl = System.getenv("FEEDBACK_CHAIN_PG_JDBC_URL");
        String amqpPort = System.getenv("FEEDBACK_CHAIN_AMQP_PORT");
        assertThat(jdbcUrl == null).as("Supply BOTH isolated endpoint overrides or neither").isEqualTo(amqpPort == null);
        if (jdbcUrl == null) {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName(DATABASE).withUsername("r259_test").withPassword(TOKEN)
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withMemory(192L*1024*1024).withNanoCPUs(1_000_000_000L));
            broker = new GenericContainer<>(DockerImageName.parse("rabbitmq:3.13-management-alpine"))
                .withExposedPorts(5672).withEnv("RABBITMQ_DEFAULT_USER", "r259_test").withEnv("RABBITMQ_DEFAULT_PASS", TOKEN)
                .withEnv("RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS", "+S 1:1 +A 4 +sbwt none +sbwtdcpu none +sbwtdio none -rabbit vm_memory_high_watermark 0.7")
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withMemory(256L*1024*1024).withNanoCPUs(1_000_000_000L));
            try { postgres.start(); broker.start(); }
            catch (Throwable failure) { broker.stop(); postgres.stop(); throw failure; }
            jdbcUrl = postgres.getJdbcUrl();
            amqpPort = broker.getMappedPort(5672).toString();
        } else {
            postgres = null; broker = null;
            assertThat(jdbcUrl).matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/r259_chain");
            assertThat(Integer.parseInt(amqpPort)).isBetween(1025, 65535).isNotEqualTo(5672);
        }
        String brokerHost = broker == null ? "127.0.0.1" : broker.getHost();
        // Overrides are loopback-only and fixed, fake credentials cannot authenticate to the working stack.
        datasource = new HikariDataSource(); datasource.setJdbcUrl(jdbcUrl); datasource.setUsername("r259_test"); datasource.setPassword(TOKEN);
        datasource.setMaximumPoolSize(4); datasource.setMinimumIdle(0); datasource.setConnectionTimeout(5000);
        datasource.addDataSourceProperty("connectTimeout", "5"); datasource.addDataSourceProperty("socketTimeout", "10");
        jdbc = new JdbcTemplate(datasource);
        entities = new LocalContainerEntityManagerFactoryBean();
        entities.setDataSource(Objects.requireNonNull(jdbc.getDataSource()));
        entities.setManagedTypes(PersistenceManagedTypes.of(Channel.class.getName()));
        entities.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        entities.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop", "hibernate.physical_naming_strategy",
            "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
        entities.afterPropertiesSet();
        var channels = new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(entities.getObject()))
            .getRepository(ChannelRepository.class);
        schema();
        var config = mock(SharedConfigService.class);
        when(config.loadBotCredentials()).thenAnswer(call -> credentials.get());
        var guardProperties = new InternalBotApiProperties();
        guardProperties.setToken(TOKEN); guardProperties.setRequireRequestSignature(true);
        var guard = new InternalBotApiRequestGuardService(guardProperties, new RuntimeCoordinationProperties(), mapper,
            new StaticListableBeanFactory().getBeanProvider(StringRedisTemplate.class));
        var recipientService = spy(new FeedbackPromptDeliveryAuthorizationService(jdbc, mapper, flags));
        doAnswer(call -> { recipientInputs.add(call.getArgument(0)); return call.callRealMethod(); }).when(recipientService).authorize(any(), any());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new FeedbackPromptDeliveryAuthorizationController(guard, recipientService),
            new BotOutboundSenderAuthorizationController(guard, new BotOutboundSenderAuthorizationService(channels, config)))
            .addFilter((request,response,chain) -> chain.doFilter(new ContentCachingRequestWrapper((HttpServletRequest)request), response)).build();
        http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpThreads = Executors.newFixedThreadPool(2, task -> { var thread = new Thread(task, "r259-loopback-http"); thread.setDaemon(true); return thread; });
        http.setExecutor(httpThreads);
        http.createContext("/internal/api/bot/", request -> bridge(request, mvc));
        http.createContext("/provider", request -> {
            providerAttempts.add(mapper.readTree(request.getRequestBody().readAllBytes()));
            respond(request, providerStatus.get(), "{}");
        });
        http.start();
        api.setBaseUrl("http://127.0.0.1:" + http.getAddress().getPort()); api.setToken(TOKEN);
        api.setRequestSigningEnabled(true); api.setRequestTimeout(Duration.ofSeconds(5));
        recipientClient = new PanelFeedbackPromptDeliveryClient(api, mapper, new PanelApiRequestHeadersFactory(api));
        senderClient = new PanelOutboundSenderAuthorizationClient(api, mapper, new PanelApiRequestHeadersFactory(api));
        connections = new CachingConnectionFactory(brokerHost, Integer.parseInt(amqpPort));
        connections.setUsername("r259_test"); connections.setPassword(TOKEN);
        var transport = new RabbitIntegrationTransportConfig();
        rabbit = transport.rabbitTemplate(connections, transport.integrationRabbitMessageConverter(mapper));
        admin = new RabbitAdmin(connections); admin.declareExchange(new TopicExchange(exchange, true, false));
        var panelRabbit = new com.example.panel.config.IntegrationRabbitProperties(); panelRabbit.setOutboundExchange(exchange);
        var coordination = mock(RuntimeCoordinationService.class);
        doAnswer(call -> { ((Runnable)call.getArgument(2)).run(); return null; }).when(coordination).runWithLease(anyString(), any(), any(Runnable.class));
        outbox = new OutboundFeedbackPromptPublishOutboxService(jdbc, mapper, rabbit, panelRabbit, coordination, new LegacyTicketIdJdbcGuard(jdbc));
        new OutboundTransportDeliveryLedgerService(jdbc);
    }

    void reset(String scope, String platform) {
        stopConsumer(); this.platform = platform;
        for (String table : List.of("integration_outbound_event_deliveries", "integration_transport_outbox", "pending_feedback_requests",
                "tickets", "channels", "businesses", "business_channel_bindings", "general_channel_question_template_bindings", "shared_channel_general_question_template_bindings"))
            jdbc.update("DELETE FROM " + table);
        flags.getFoundation().setEnabled(!scope.equals("LEGACY")); flags.setGeneralChannelResolution(true); flags.setSharedChannelResolution(true);
        providerAttempts.clear(); bridgeFailures.clear(); consumerFailures.clear(); recipientCalls.set(0); senderCalls.set(0);
        receivedMessages.set(0); wirePayloads.clear(); recipientInputs.clear();
        providerStatus.set(200); apiUnavailable.set(false); api.setSignatureSecret(null);
        credentials.set(List.of(new BotCredential(7L, "test sender", platform, TOKEN, true)));
        jdbc.update("INSERT INTO channels(id,token,channel_name,platform,is_active,credential_id) VALUES(?,'never-inline-fallback','isolated feedback',?,TRUE,7)", CHANNEL, platform);
        jdbc.update("INSERT INTO businesses VALUES(?,'active')", BUSINESS);
        if (scope.equals("BUSINESS")) jdbc.update("INSERT INTO business_channel_bindings VALUES(?,?,TRUE,'SINGLE_BUSINESS')", CHANNEL, BUSINESS);
        if (scope.equals("GENERAL")) jdbc.update("INSERT INTO general_channel_question_template_bindings VALUES(?,TRUE)", CHANNEL);
        if (scope.equals("SHARED_GENERAL")) jdbc.update("INSERT INTO shared_channel_general_question_template_bindings VALUES(?,TRUE)", CHANNEL);
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,channel_id,ticket_public_id,business_id,is_general) VALUES(?,?,?,?,?,?)",
            USER, RAW, CHANNEL, "a".repeat(32), scope.equals("BUSINESS") ? BUSINESS : null, scope.contains("GENERAL"));
        jdbc.update("INSERT INTO pending_feedback_requests(id,user_id,channel_id,ticket_id,created_at,expires_at) VALUES(100,?,?,?,clock_timestamp(),clock_timestamp()+interval '1 hour')", USER, CHANNEL, RAW);
        queue = "iguana.integration.outbound.feedback-prompt." + platform + ".channel." + CHANNEL + ".bot";
        // The override broker is disposable and fixture-owned; no previous-run DLX argument can leak in.
        admin.deleteQueue(queue); admin.deleteQueue(queue + ".dlq");
        admin.declareQueue(new Queue(queue, true, false, false, Map.of("x-dead-letter-exchange", exchange, "x-dead-letter-routing-key", queue + ".dlq")));
        admin.declareQueue(new Queue(queue + ".dlq", true));
        admin.declareBinding(new Binding(queue, Binding.DestinationType.QUEUE, exchange, route(), null));
        admin.declareBinding(new Binding(queue + ".dlq", Binding.DestinationType.QUEUE, exchange, queue + ".dlq", null));
        admin.purgeQueue(queue); admin.purgeQueue(queue + ".dlq");
    }

    com.example.panel.service.integration.OutboundFeedbackPromptEvent enqueue() {
        String id = UUID.randomUUID().toString();
        var event = new com.example.panel.service.integration.OutboundFeedbackPromptEvent(id,"feedback.prompt.dispatch",id,platform,CHANNEL,100L,USER,RAW,"Rate " + RAW);
        outbox.enqueue(event, route()); return event;
    }
    String route() { return "integration.outbound.feedback.prompt." + platform + ".channel." + CHANNEL; }
    String state(String table, String eventId) {
        return jdbc.query("SELECT status FROM " + table + " WHERE event_id=?", (row,index) -> row.getString(1), eventId).stream().findFirst().orElse(null);
    }
    void publish() { outbox.dispatchScheduled(); }
    void replay(Object event) { rabbit.convertAndSend(exchange, route(), event); }
    void replaceQueuedMessage(Object event) { assertThat(admin.purgeQueue(queue)).isEqualTo(1); replay(event); }
    OutboundFeedbackPromptEvent botEvent(Object event) { return mapper.convertValue(event, OutboundFeedbackPromptEvent.class); }
    OutboundSenderIdentity senderIdentity() throws Exception {
        return new OutboundSenderIdentity(CHANNEL, platform, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(TOKEN.getBytes(StandardCharsets.UTF_8))));
    }
    void startConsumer() throws Exception {
        var channelReader = mock(ChannelService.class);
        when(channelReader.findById(CHANNEL)).thenAnswer(call -> jdbc.query("SELECT id,platform,is_active FROM channels WHERE id=?", (row,index) -> {
            var channel = new com.example.supportbot.entity.Channel(); channel.setId(row.getLong(1)); channel.setPlatform(row.getString(2)); channel.setActive(row.getBoolean(3)); return channel;
        }, CHANNEL).stream().findFirst());
        var identity = senderIdentity();
        String senderPlatform = platform;
        OutboundMessenger provider = new OutboundMessenger() {
            public String platform() { return senderPlatform; }
            public OutboundSenderIdentity senderIdentity() { return identity; }
            public boolean sendToSupportChat(String chat, String text) { throw new AssertionError("No support-chat delivery in feedback prompt flow"); }
            public boolean sendToUser(Long userId, String text) {
                try {
                    var request = HttpRequest.newBuilder(URI.create(api.getBaseUrl() + "/provider")).timeout(Duration.ofSeconds(5))
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("platform", senderPlatform, "userId", userId, "text", text)))).build();
                    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
                } catch (Exception failure) { throw new IllegalStateException(failure); }
            }
        };
        var headers = new PanelApiRequestHeadersFactory(api);
        var messages = new MessagingService(List.of(provider), new PanelOutboundSenderAuthorizationClient(api, mapper, headers));
        var dispatch = new OutboundFeedbackPromptDispatchService(channelReader, messages, new OutboundTransportDeliveryLedgerService(jdbc), recipientClient);
        var properties = new IntegrationRabbitProperties(); properties.setOutboundQueue(queue); properties.setOutboundRoutingKey(route());
        properties.setOutboundConcurrency(1); properties.setOutboundMaxConcurrency(1); properties.setOutboundPrefetch(1);
        var listener = new OutboundFeedbackPromptListener(dispatch, properties);
        var endpoint = new MethodRabbitListenerEndpoint(); endpoint.setId("r259-" + UUID.randomUUID()); endpoint.setBean(listener);
        endpoint.setMethod(OutboundFeedbackPromptListener.class.getMethod("onFeedbackPrompt", OutboundFeedbackPromptEvent.class, String.class));
        endpoint.setQueueNames(queue);
        var handler = new DefaultMessageHandlerMethodFactory(); handler.afterPropertiesSet(); endpoint.setMessageHandlerMethodFactory(handler);
        var transport = new RabbitIntegrationTransportConfig();
        consumer = transport.outboundFeedbackPromptListenerContainerFactory(connections, transport.integrationRabbitMessageConverter(mapper), properties).createListenerContainer(endpoint);
        consumer.setAfterReceivePostProcessors(message -> {
            try { wirePayloads.add(mapper.readTree(message.getBody())); receivedMessages.incrementAndGet(); return message; }
            catch (java.io.IOException malformed) { throw new IllegalStateException(malformed); }
        });
        consumer.setErrorHandler(consumerFailures::add); consumer.setShutdownTimeout(3000); consumer.setReceiveTimeout(50);
        consumer.start();
    }
    void stopConsumer() { if (consumer != null) { consumer.stop(); consumer = null; } }
    void delivered(String eventId) {
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
            assertThat(state("integration_outbound_event_deliveries", eventId)).isEqualTo("delivered"));
        assertThat(bridgeFailures).isEmpty(); assertThat(consumerFailures).isEmpty();
    }
    void rejected() {
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
            assertThat(admin.getQueueInfo(queue + ".dlq").getMessageCount()).isEqualTo(1));
        assertThat(providerAttempts).isEmpty(); assertThat(consumerFailures).hasSize(1); assertThat(bridgeFailures).isEmpty();
    }
    void drained() {
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(admin.getQueueInfo(queue).getMessageCount()).isZero();
            assertThat(consumer.getActiveConsumerCount()).isEqualTo(1);
            assertThat(receivedMessages).hasValue(2);
        });
    }
    void assertQueueEmpty() { assertThat(admin.getQueueInfo(queue).getMessageCount()).isZero(); }
    private void bridge(HttpExchange request, MockMvc mvc) throws java.io.IOException {
        try {
            String path = request.getRequestURI().getPath();
            if (path.equals(RECIPIENT)) recipientCalls.incrementAndGet(); else if (path.equals(SENDER)) senderCalls.incrementAndGet();
            if (apiUnavailable.get()) { respond(request, 503, "{}"); return; }
            // Standalone MockMvc lacks Boot's UTF-8 CharacterEncodingFilter; preserve the real HTTP JSON bytes.
            var builder = post(path).characterEncoding(StandardCharsets.UTF_8).contentType("application/json").content(request.getRequestBody().readAllBytes());
            request.getRequestHeaders().forEach((name,values) -> values.forEach(value -> builder.header(name,value)));
            var response = mvc.perform(builder).andReturn().getResponse();
            respond(request, response.getStatus(), response.getContentAsString());
        } catch (Throwable failure) { bridgeFailures.add(failure); respond(request,500,"{}"); }
    }
    private static void respond(HttpExchange request, int code, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8); request.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        request.sendResponseHeaders(code, bytes.length); try (var output = request.getResponseBody()) { output.write(bytes); }
    }
    private void schema() throws Exception {
        jdbc.execute("CREATE TABLE tickets(user_id BIGINT NOT NULL,ticket_id TEXT NOT NULL,channel_id BIGINT,ticket_public_id VARCHAR(32),business_id BIGINT,is_general BOOLEAN NOT NULL,PRIMARY KEY(user_id,ticket_id))");
        jdbc.execute("CREATE TABLE pending_feedback_requests(id BIGINT PRIMARY KEY,user_id BIGINT NOT NULL,channel_id BIGINT,ticket_id TEXT NOT NULL,created_at TIMESTAMPTZ NOT NULL,expires_at TIMESTAMPTZ,sent_at TIMESTAMPTZ)");
        jdbc.execute("""
            CREATE TABLE integration_transport_outbox (
                event_id VARCHAR(120) PRIMARY KEY,transport_source VARCHAR(120) NOT NULL,event_kind VARCHAR(120) NOT NULL,
                exchange_name VARCHAR(255) NOT NULL,routing_key VARCHAR(255) NOT NULL,payload_json TEXT NOT NULL,
                channel_id BIGINT,user_id BIGINT,ticket_id TEXT,request_id BIGINT,status VARCHAR(32) NOT NULL,
                attempt_count INTEGER NOT NULL DEFAULT 0,last_error TEXT,available_at TIMESTAMPTZ,processing_started_at TIMESTAMPTZ,
                published_at TIMESTAMPTZ,created_at TIMESTAMPTZ NOT NULL,updated_at TIMESTAMPTZ NOT NULL)
            """);
        try (var migration = Objects.requireNonNull(getClass().getResourceAsStream("/db/V84__pending_feedback_context.sql"))) {
            jdbc.execute(new String(migration.readAllBytes(), StandardCharsets.UTF_8));
        }
        jdbc.execute("CREATE TABLE businesses(id BIGINT PRIMARY KEY,status TEXT)");
        jdbc.execute("CREATE TABLE business_channel_bindings(channel_id BIGINT,business_id BIGINT,is_active BOOLEAN,resolution_mode TEXT)");
        jdbc.execute("CREATE TABLE general_channel_question_template_bindings(channel_id BIGINT PRIMARY KEY,is_active BOOLEAN)");
        jdbc.execute("CREATE TABLE shared_channel_general_question_template_bindings(channel_id BIGINT PRIMARY KEY,is_active BOOLEAN)");
    }
    @Override public void close() {
        try { stopConsumer(); connections.destroy(); http.stop(0); httpThreads.shutdownNow(); entities.destroy(); datasource.close(); }
        finally { if (broker != null) broker.stop(); if (postgres != null) postgres.stop(); }
    }
}
