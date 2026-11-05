package com.example.acceptance;

import static org.mockito.Mockito.*;
import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.controller.SelectedTicketFollowupMediaController;
import com.example.panel.entity.*;
import com.example.panel.repository.*;
import com.example.panel.security.*;
import com.example.panel.service.ChatAttachmentMetadataService;
import com.example.panel.service.integration.*;
import com.example.panel.storage.*;
import com.example.supportbot.service.ActiveInboundClientMessageCommand;
import com.example.supportbot.service.InboundClientMessagePublisher;
import com.example.supportbot.service.IntegrationTransportOutboxService;
import com.example.supportbot.service.PanelRuntimeConfigClient;
import com.example.supportbot.service.PanelApiRequestHeadersFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.zaxxer.hikari.HikariDataSource;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.*;
import org.springframework.amqp.rabbit.listener.*;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.*;
import org.testcontainers.utility.DockerImageName;

/** Opt-in only. Real PostgreSQL/Rabbit/transactions/bytes; narrow JDBC repository adapters, no Boot/config/provider. */
final class MaxMediaChainFixture implements AutoCloseable {
    static final long USER=9_007_199_254_740_993L,CHANNEL=USER+2,BUSINESS=USER+6;
    static final String RAW=" padded/Ю%?' ",TOKEN="r287-test-only",DATABASE="r287_max_media";
    static final String OWNER="01-278-r287-max-media";
    static final List<Class<?>> PANEL_CLASSES=List.of(SelectedTicketFollowupMediaService.class,SelectedTicketFollowupMediaController.class,
        InboundClientMessageIngestionService.class,InboundClientMessageListener.class,IntegrationInboundEventInboxService.class,
        LegacyTicketIdConcurrencyGuard.class,ChatAttachmentMetadataService.class,com.example.panel.config.RabbitIntegrationTransportConfig.class);
    final ObjectMapper mapper=org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build();
    final AtomicBoolean requeueAfterCommit=new AtomicBoolean();
    final List<byte[]> wire=new CopyOnWriteArrayList<>();
    final List<Boolean> redelivered=new CopyOnWriteArrayList<>();
    final AtomicReference<Runnable> beforeHistory=new AtomicReference<>(()->{});
    final String nonce,schema,exchange,queue,dlq;
    final PostgreSQLContainer<?> postgres;
    final GenericContainer<?> broker;
    final Network network;
    HikariDataSource datasource;JdbcTemplate jdbc;DataSourceTransactionManager manager;TransactionTemplate tx;
    CachingConnectionFactory connections;RabbitTemplate rabbit;RabbitAdmin admin;
    IntegrationTransportOutboxService outbox;InboundClientMessagePublisher publisher;
    com.example.supportbot.config.IntegrationRabbitProperties routes;
    InboundClientMessageIngestionService ingestion;SelectedTicketFollowupMediaService admission;
    InboundClientMessageListener listener;SimpleMessageListenerContainer consumer;
    HttpServer http;PanelRuntimeConfigClient client;
    final Path files;

    static String nonce(String value){if(value==null||!value.matches("[a-f0-9]{32}"))throw new IllegalArgumentException("An isolated run nonce is required");return value;}
    static void endpoint(String jdbc,String host,int amqp,String schema){
        if(jdbc==null||!jdbc.matches("jdbc:postgresql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/r287_max_media")
                || !("127.0.0.1".equals(host)||"localhost".equals(host)) || amqp<=1024||amqp>65535||amqp==5672
                || schema==null||!schema.matches("r287_[a-f0-9]{32}"))throw new IllegalArgumentException("Owned loopback endpoints required");
        int port=URI.create(jdbc.substring(5)).getPort();if(port<=1024||port>65535||port==5432)throw new IllegalArgumentException("Isolated PostgreSQL port required");
    }
    static void verifyPanelClasses()throws Exception{
        Path classes=Path.of("../../spring-panel/target/classes").toAbsolutePath().normalize();
        for(var type:PANEL_CLASSES){String name=type.getName().replace('.','/')+".class";try(var loaded=type.getClassLoader().getResourceAsStream(name)){
            if(loaded==null||!Arrays.equals(loaded.readAllBytes(),Files.readAllBytes(classes.resolve(name))))throw new IllegalStateException("Stale panel bytecode: "+name);
        }}
    }
    MaxMediaChainFixture(Path files,String runNonce)throws Exception{
        verifyPanelClasses();this.files=files;nonce=nonce(runNonce);schema="r287_"+nonce;exchange="r287."+nonce;queue=exchange+".inbound";dlq=queue+".dlq";
        network=Network.builder().createNetworkCmdModifier(c->c.withInternal(true).withLabels(Map.of("io.iguana.owner",OWNER,"io.iguana.test-nonce",nonce))).build();
        postgres=new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName(DATABASE).withUsername("r287_test").withPassword(TOKEN).withNetwork(network)
            .withLabel("io.iguana.owner",OWNER).withLabel("io.iguana.test-nonce",nonce)
            .withCreateContainerCmdModifier(c->c.getHostConfig().withMemory(192L*1024*1024).withNanoCPUs(1_000_000_000L)
                .withPortBindings(new com.github.dockerjava.api.model.PortBinding(com.github.dockerjava.api.model.Ports.Binding.bindIpAndPort("127.0.0.1",0),new com.github.dockerjava.api.model.ExposedPort(5432))));
        broker=new GenericContainer<>(DockerImageName.parse("rabbitmq:3.13-management-alpine")).withExposedPorts(5672).withNetwork(network)
            .withLabel("io.iguana.owner",OWNER).withLabel("io.iguana.test-nonce",nonce).withEnv("RABBITMQ_DEFAULT_USER","r287_test").withEnv("RABBITMQ_DEFAULT_PASS",TOKEN)
            .withEnv("RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS","+S 1:1 +A 4 +sbwt none +sbwtdcpu none +sbwtdio none")
            .withCreateContainerCmdModifier(c->c.getHostConfig().withMemory(384L*1024*1024).withNanoCPUs(1_000_000_000L)
                .withPortBindings(new com.github.dockerjava.api.model.PortBinding(com.github.dockerjava.api.model.Ports.Binding.bindIpAndPort("127.0.0.1",0),new com.github.dockerjava.api.model.ExposedPort(5672))));
        try{
            postgres.start();broker.start();String jdbcUrl="jdbc:postgresql://"+postgres.getHost()+":"+postgres.getMappedPort(5432)+"/"+DATABASE;endpoint(jdbcUrl,broker.getHost(),broker.getMappedPort(5672),schema);
            new JdbcTemplate(new DriverManagerDataSource(jdbcUrl,"r287_test",TOKEN)).execute("CREATE SCHEMA "+schema);
            datasource=new HikariDataSource();datasource.setJdbcUrl(jdbcUrl);datasource.setUsername("r287_test");datasource.setPassword(TOKEN);datasource.setSchema(schema);
            datasource.setMaximumPoolSize(8);datasource.setMinimumIdle(0);datasource.setConnectionTimeout(5000);
            datasource.setConnectionInitSql("SET lock_timeout='5s'");datasource.addDataSourceProperty("socketTimeout","15");
            jdbc=new JdbcTemplate(datasource);manager=new DataSourceTransactionManager(datasource);tx=new TransactionTemplate(manager);schema();
            connections=new CachingConnectionFactory(broker.getHost(),broker.getMappedPort(5672));connections.setUsername("r287_test");connections.setPassword(TOKEN);
            var transport=new com.example.supportbot.config.RabbitIntegrationTransportConfig();rabbit=transport.rabbitTemplate(connections,transport.integrationRabbitMessageConverter(mapper));admin=new RabbitAdmin(connections);
            admin.declareExchange(new TopicExchange(exchange,true,false));admin.declareQueue(new Queue(queue,true,false,false,Map.of("x-dead-letter-exchange",exchange,"x-dead-letter-routing-key",dlq)));
            admin.declareQueue(new Queue(dlq,true));admin.declareBinding(new Binding(queue,Binding.DestinationType.QUEUE,exchange,"r287.max",null));admin.declareBinding(new Binding(dlq,Binding.DestinationType.QUEUE,exchange,dlq,null));
            routes=new com.example.supportbot.config.IntegrationRabbitProperties();routes.setInboundExchange(exchange);routes.setRoutingMax("r287.max");routes.setRoutingTicketCreated("r287.created");
            outbox=new IntegrationTransportOutboxService(jdbc,mapper,rabbit);publisher=new InboundClientMessagePublisher(outbox,routes);
            backend();http();
        }catch(Throwable failure){close();throw failure;}
    }
    @SuppressWarnings("unchecked") <T>T transactional(T target){var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));return(T)proxy.getProxy();}
    void backend()throws Exception{
        var storage=new AttachmentObjectStorageService(new ObjectStorageProperties(),files.toString(),files.resolve("kb").toString(),files.resolve("photos").toString(),files.resolve("avatars").toString());
        var flags=new MultibusinessFeatureFlags();flags.getFoundation().setEnabled(true);flags.setTicketWriteRequiresBusiness(true);flags.setSharedChannelResolution(true);flags.setGeneralChannelResolution(true);
        admission=transactional(new SelectedTicketFollowupMediaService(jdbc,flags,storage));
        var channels=mock(ChannelRepository.class);when(channels.findById(anyLong())).thenAnswer(c->jdbc.query("SELECT id,platform FROM channels WHERE id=?",(r,n)->{var channel=new Channel();channel.setId(r.getLong(1));channel.setPlatform(r.getString(2));return channel;},c.getArgument(0)).stream().findFirst());
        var tickets=mock(TicketRepository.class);when(tickets.findById(any(TicketId.class))).thenAnswer(c->{TicketId id=c.getArgument(0);return jdbc.query("SELECT channel_id,questionnaire_version,questionnaire_catalog_sha256 FROM tickets WHERE user_id=? AND ticket_id=?",(r,n)->{var root=new Ticket();root.setId(id);var channel=new Channel();channel.setId(r.getLong(1));root.setChannel(channel);root.setQuestionnaireVersion(r.getInt(2));root.setQuestionnaireCatalogSha256(r.getString(3));return root;},id.getUserId(),id.getTicketId()).stream().findFirst();});
        var history=mock(ChatHistoryRepository.class);when(history.save(any(ChatHistory.class))).thenAnswer(c->{beforeHistory.get().run();ChatHistory h=c.getArgument(0);Long id=jdbc.queryForObject("INSERT INTO chat_history(user_id,sender,message,timestamp,ticket_id,ticket_owner_user_id,message_type,attachment,channel_id,tg_message_id,reply_to_tg_id,forwarded_from,file_name) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id",Long.class,h.getUserId(),h.getSender(),h.getMessage(),h.getTimestamp(),h.getTicketId(),h.getTicketOwnerUserId(),h.getMessageType(),h.getAttachment(),h.getChannel().getId(),h.getTgMessageId(),h.getReplyToTgId(),h.getForwardedFrom(),h.getFileName());h.setId(id);return h;});
        var active=mock(TicketActiveRepository.class);when(active.findById(anyString())).thenAnswer(c->jdbc.query("SELECT ticket_id FROM ticket_active WHERE ticket_id=?",(r,n)->{var a=new TicketActive();a.setTicketId(r.getString(1));return a;},c.getArgument(0)).stream().findFirst());
        when(active.save(any(TicketActive.class))).thenAnswer(c->{TicketActive a=c.getArgument(0);jdbc.update("UPDATE ticket_active SET user_identity=?,last_seen=? WHERE ticket_id=?",a.getUserIdentity(),a.getLastSeen(),a.getTicketId());return a;});
        var inbox=transactional(new IntegrationInboundEventInboxService(jdbc,mapper));
        var target=new InboundClientMessageIngestionService(inbox,new LegacyTicketIdConcurrencyGuard(jdbc),channels,tickets,history,mock(MessageRepository.class),active,new ChatAttachmentMetadataService(jdbc,storage));target.setSelectedMedia(admission);ingestion=transactional(target);
        var proxy=new ProxyFactory(new InboundClientMessageListener(ingestion));proxy.setProxyTargetClass(true);
        proxy.addAdvice((MethodInterceptor)call->{Object value=call.proceed();if(call.getMethod().getName().equals("onInboundClientMessage")&&requeueAfterCommit.compareAndSet(true,false))throw new ImmediateRequeueAmqpException("Isolated committed-before-ACK failure");return value;});listener=(InboundClientMessageListener)proxy.getProxy();
    }
    void http()throws Exception{
        var p=new InternalBotApiProperties();p.setToken(TOKEN);p.setRequireRequestSignature(true);
        var guard=new InternalBotApiRequestGuardService(p,new com.example.panel.config.RuntimeCoordinationProperties(),mapper,new StaticListableBeanFactory().getBeanProvider(StringRedisTemplate.class));
        MockMvc mvc=MockMvcBuilders.standaloneSetup(new SelectedTicketFollowupMediaController(admission,guard)).build();
        http=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        http.createContext("/internal/api/bot/",request->{try{var builder=MockMvcRequestBuilders.get(request.getRequestURI()).characterEncoding("UTF-8");request.getRequestHeaders().forEach((name,values)->values.forEach(v->builder.header(name,v)));var response=mvc.perform(builder).andReturn().getResponse();byte[] body=response.getContentAsByteArray();response.getHeaderNames().forEach(name->request.getResponseHeaders().put(name,new ArrayList<>(response.getHeaders(name))));request.sendResponseHeaders(response.getStatus(),body.length);try(var output=request.getResponseBody()){output.write(body);}}catch(Exception failure){request.sendResponseHeaders(500,-1);request.close();}});http.start();
        var api=new com.example.supportbot.config.IntegrationPanelApiProperties();api.setBaseUrl("http://127.0.0.1:"+http.getAddress().getPort());api.setToken(TOKEN);api.setRequestSigningEnabled(true);api.setRetryAttempts(1);client=new PanelRuntimeConfigClient(api,mapper,new PanelApiRequestHeadersFactory(api));
    }
    void startConsumer()throws Exception{
        var endpoint=new MethodRabbitListenerEndpoint();endpoint.setId("r287-"+nonce);endpoint.setBean(listener);endpoint.setMethod(InboundClientMessageListener.class.getMethod("onInboundClientMessage",InboundClientMessageEvent.class,String.class));endpoint.setQueueNames(queue);
        var handlers=new DefaultMessageHandlerMethodFactory();handlers.afterPropertiesSet();endpoint.setMessageHandlerMethodFactory(handlers);
        var p=new com.example.panel.config.IntegrationRabbitProperties();p.setInboundConcurrency(1);p.setInboundMaxConcurrency(1);p.setInboundPrefetch(1);
        var config=new com.example.panel.config.RabbitIntegrationTransportConfig();consumer=config.inboundClientMessageListenerContainerFactory(connections,config.integrationRabbitMessageConverter(mapper),p).createListenerContainer(endpoint);
        consumer.setAfterReceivePostProcessors(message->{wire.add(message.getBody().clone());redelivered.add(Boolean.TRUE.equals(message.getMessageProperties().getRedelivered()));return message;});consumer.setErrorHandler(failure->{});consumer.setShutdownTimeout(3000);consumer.setReceiveTimeout(50);consumer.start();
    }
    void stopConsumer(){if(consumer!=null){consumer.stop();consumer=null;}}
    void reset(boolean general){
        stopConsumer();beforeHistory.set(()->{});requeueAfterCommit.set(false);wire.clear();redelivered.clear();
        for(String table:List.of("chat_history","chat_attachment_metadata","ticket_active","integration_inbound_event_inbox"))jdbc.execute("DROP TRIGGER IF EXISTS r287_fail ON "+table);
        jdbc.execute("DROP FUNCTION IF EXISTS r287_fail()");jdbc.execute("TRUNCATE chat_attachment_metadata,chat_history,ticket_active,tickets,channels,integration_transport_outbox,integration_inbound_event_inbox RESTART IDENTITY CASCADE");
        admin.purgeQueue(queue);admin.purgeQueue(dlq);jdbc.update("INSERT INTO channels VALUES(?,'max',TRUE)",CHANNEL);
        jdbc.update("INSERT INTO tickets VALUES(?,?,?,'open',?,?,?,?,'q-frozen',?,?,4)",USER,RAW,CHANNEL,general?null:BUSINESS,general,general?"USER_SELECTION":null,general?null:"USER_SELECTION","a".repeat(64),"c".repeat(64));
        jdbc.update("INSERT INTO ticket_active(ticket_id,user_identity,last_seen) VALUES(?,?,?)",RAW,"original-profile",OffsetDateTime.parse("2026-11-05T08:00:00Z"));
    }
    List<com.example.supportbot.service.InboundClientMessageEvent.MediaItem> files(boolean general,long provider,int count)throws Exception{
        var result=new ArrayList<com.example.supportbot.service.InboundClientMessageEvent.MediaItem>();for(int slot=1;slot<=count;slot++){byte[] bytes={1,2,3,(byte)slot};String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));String key="questionnaires/max/"+CHANNEL+"/"+(general?"general":"business-"+BUSINESS)+"/"+USER+"/"+"c".repeat(64)+"/"+provider+"/"+slot+"/"+sha+".bin";Path path=files.resolve(key);Files.createDirectories(path.getParent());Files.write(path,bytes);result.add(new com.example.supportbot.service.InboundClientMessageEvent.MediaItem("document",key,"proof.bin"));}return List.copyOf(result);
    }
    InboundClientMessageEvent enqueue(boolean general,long provider,int count)throws Exception{
        var items=files(general,provider,count);var channel=new com.example.supportbot.entity.Channel();channel.setId(CHANNEL);channel.setPlatform("max");channel.setActive(true);
        var command=new ActiveInboundClientMessageCommand(USER,"new-profile","client","Client",channel,RAW,"Private caption","selected_media_batch.v1",null,null,provider,null,null,OffsetDateTime.parse("2026-11-05T08:01:00Z"));
        tx.executeWithoutResult(s->publisher.publishSelectedMediaBatch(command,items));String id=com.example.supportbot.service.InboundClientMessageEvent.mediaBatchId(CHANNEL,USER,RAW,Long.toString(provider));
        return mapper.readValue(jdbc.queryForObject("SELECT payload_json FROM integration_transport_outbox WHERE event_id=?",String.class,id),InboundClientMessageEvent.class);
    }
    String state(String id){return jdbc.query("SELECT status FROM integration_inbound_event_inbox WHERE event_id=?",(r,n)->r.getString(1),id).stream().findFirst().orElse(null);}
    int count(String table){if(!Set.of("chat_history","chat_attachment_metadata","integration_transport_outbox").contains(table))throw new IllegalArgumentException();return jdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class);}
    void failWrite(String table){
        if(!Set.of("chat_history","chat_attachment_metadata","ticket_active").contains(table))throw new IllegalArgumentException();
        String condition=table.equals("chat_history")?"NEW.attachment LIKE '%/2/%'":table.equals("chat_attachment_metadata")?"NEW.storage_key LIKE '%/2/%'":"TRUE";
        jdbc.execute("CREATE FUNCTION r287_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF "+condition+" THEN RAISE EXCEPTION 'isolated write failure'; END IF; RETURN NEW; END $$");jdbc.execute("CREATE TRIGGER r287_fail BEFORE INSERT OR UPDATE ON "+table+" FOR EACH ROW EXECUTE FUNCTION r287_fail()");
    }
    static void await(java.util.function.BooleanSupplier condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(!condition.getAsBoolean()){if(System.nanoTime()>end)throw new AssertionError("Isolated chain timed out");Thread.sleep(25);}}
    void schema(){jdbc.execute("""
        CREATE TABLE channels(id BIGINT PRIMARY KEY,platform TEXT,is_active BOOLEAN);
        CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,channel_id BIGINT,status TEXT,business_id BIGINT,is_general BOOLEAN,general_selection_source TEXT,business_selection_source TEXT,question_template_id TEXT,question_template_sha256 TEXT,questionnaire_catalog_sha256 TEXT,questionnaire_version INTEGER,PRIMARY KEY(user_id,ticket_id));
        CREATE TABLE ticket_active(ticket_id TEXT PRIMARY KEY,user_identity TEXT,last_seen TIMESTAMPTZ);
        CREATE TABLE chat_history(id BIGSERIAL PRIMARY KEY,user_id BIGINT,sender TEXT,message TEXT,timestamp TIMESTAMPTZ,ticket_id TEXT,ticket_owner_user_id BIGINT,message_type TEXT,attachment TEXT,channel_id BIGINT,tg_message_id BIGINT,reply_to_tg_id BIGINT,forwarded_from TEXT,file_name TEXT,FOREIGN KEY(ticket_owner_user_id,ticket_id) REFERENCES tickets(user_id,ticket_id));
        CREATE TABLE chat_attachment_metadata(chat_history_id BIGINT PRIMARY KEY REFERENCES chat_history(id),ticket_id TEXT,channel_id BIGINT,storage_key TEXT,storage_provider TEXT,storage_class TEXT,original_name TEXT,mime_type TEXT,size BIGINT,content_hash TEXT,legacy_attachment_ref TEXT,normalization_status TEXT,availability_status TEXT,created_at TIMESTAMPTZ,updated_at TIMESTAMPTZ,archived_at TIMESTAMPTZ,deleted_at TIMESTAMPTZ);
        CREATE TABLE integration_inbound_event_inbox(event_id TEXT PRIMARY KEY,event_kind TEXT,platform TEXT,channel_id BIGINT,ticket_id TEXT,transport_source TEXT,routing_key TEXT,payload_json TEXT,status TEXT,received_at TIMESTAMPTZ,processed_at TIMESTAMPTZ,last_error TEXT,attempt_count INTEGER,processing_started_at TIMESTAMPTZ,updated_at TIMESTAMPTZ);
        """);}
    @Override public void close(){try{stopConsumer();if(http!=null)http.stop(0);if(connections!=null)connections.destroy();if(datasource!=null)datasource.close();}finally{try{broker.stop();}finally{try{postgres.stop();}finally{network.close();}}}}
}
