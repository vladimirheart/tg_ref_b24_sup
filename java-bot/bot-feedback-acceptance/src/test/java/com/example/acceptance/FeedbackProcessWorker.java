package com.example.acceptance;

import com.example.supportbot.config.*;
import com.example.supportbot.entity.Channel;
import com.example.supportbot.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.listener.MethodRabbitListenerEndpoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory;

/** Test-only subprocess: real listener/dispatch/storage/HTTP, never loads application configuration. */
public final class FeedbackProcessWorker {
    enum Point { AFTER_CLAIM, AFTER_PREPARE, AFTER_PROVIDER, AFTER_RECEIPT, AFTER_DELIVERED, NONE }
    record Config(String jdbc, String schema, int amqp, String api, String platform, Point point) {
        static Config parse(String[] args) {
            if (args.length != 6 || !args[0].matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/r259_chain")
                    || !args[1].matches("r263_chain_[0-9a-f]{32}")
                    || !Set.of("telegram", "vk", "max").contains(args[4])) throw new IllegalArgumentException("Isolated endpoints required");
            URI db=URI.create(args[0].substring(5)), api=URI.create(args[3]);
            int amqp=Integer.parseInt(args[2]);
            if(db.getPort()<=1024 || db.getPort()==5432 || db.getPort()>65535 || amqp<=1024 || amqp==5672 || amqp>65535
                    || !"http".equals(api.getScheme()) || !"127.0.0.1".equals(api.getHost()) || api.getPort()<=1024
                    || api.getPort()>65535 || api.getUserInfo()!=null || api.getQuery()!=null || api.getFragment()!=null
                    || !api.getPath().isEmpty()) throw new IllegalArgumentException("Only isolated loopback ports accepted");
            return new Config(args[0],args[1],amqp,args[3],args[4],Point.valueOf(args[5]));
        }
    }
    private static void marker(String text) { System.out.println("R265_"+text); System.out.flush(); }
    private static void barrier(Config config, Point point) {
        if(config.point()!=point)return;
        marker("BOUNDARY "+point);
        try { new CountDownLatch(1).await(); }
        catch(InterruptedException failure){Thread.currentThread().interrupt();throw new IllegalStateException(failure);}
    }
    public static void main(String[] args) throws Exception {
        Config config=Config.parse(args);
        var mapper=new ObjectMapper();
        try(var datasource=new HikariDataSource()) {
            datasource.setJdbcUrl(config.jdbc());datasource.setSchema(config.schema());
            datasource.setUsername("r259_test");datasource.setPassword(FeedbackChainFixture.TOKEN);
            datasource.setMaximumPoolSize(2);datasource.setMinimumIdle(0);datasource.setConnectionTimeout(5000);
            datasource.addDataSourceProperty("connectTimeout","5");datasource.addDataSourceProperty("socketTimeout","10");
            var jdbc=new JdbcTemplate(datasource);
            var ledger=new OutboundTransportDeliveryLedgerService(jdbc) {
                @Override public boolean beginDelivery(String id,String kind,String route,Long channel,Long user,String ticket,Long request){
                    boolean claim=super.beginDelivery(id,kind,route,channel,user,ticket,request);
                    if(claim)barrier(config,Point.AFTER_CLAIM);return claim;
                }
                @Override public void markDelivered(String id){super.markDelivered(id);barrier(config,Point.AFTER_DELIVERED);}
            };
            var receipts=new OutboundDeliveryReceiptService(jdbc) {
                @Override public String prepare(String id,OutboundSenderIdentity sender,String prompt){
                    String attempt=super.prepare(id,sender,prompt);barrier(config,Point.AFTER_PREPARE);return attempt;
                }
                @Override public void record(String id,String attempt,OutboundSenderIdentity sender,Long user,ProviderSendResult result){
                    super.record(id,attempt,sender,user,result);barrier(config,Point.AFTER_RECEIPT);
                }
            };
            var channelReader=new ChannelService(null,null,null) {
                @Override public Optional<Channel> findById(Long id){
                    return jdbc.query("SELECT id,platform,is_active FROM channels WHERE id=?",(row,index)->{
                        var c=new Channel();c.setId(row.getLong(1));c.setPlatform(row.getString(2));c.setActive(row.getBoolean(3));return c;
                    },id).stream().findFirst();
                }
            };
            var api=new IntegrationPanelApiProperties();api.setBaseUrl(config.api());api.setToken(FeedbackChainFixture.TOKEN);
            api.setRequestSigningEnabled(true);api.setRequestTimeout(Duration.ofSeconds(5));
            var headers=new PanelApiRequestHeadersFactory(api);
            var identity=new OutboundSenderIdentity(FeedbackChainFixture.CHANNEL,config.platform(),HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(FeedbackChainFixture.TOKEN.getBytes(StandardCharsets.UTF_8))));
            var provider=new OutboundMessenger() {
                public String platform(){return config.platform();}
                public OutboundSenderIdentity senderIdentity(){return identity;}
                public boolean sendToSupportChat(String chat,String text){throw new AssertionError("No support chat");}
                public boolean sendToUser(Long user,String text){throw new AssertionError("Receipt API required");}
                public ProviderSendResult sendToUserWithReceipt(Long user,String text){
                    try{
                        var request=HttpRequest.newBuilder(URI.create(config.api()+"/provider")).timeout(Duration.ofSeconds(5))
                            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("platform",config.platform(),"userId",user,"text",text)))).build();
                        var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
                        if(response.statusCode()!=200)return ProviderSendResult.unknown();
                        var result=ProviderSendResult.accepted(mapper.readTree(response.body()).path("messageId").textValue());
                        barrier(config,Point.AFTER_PROVIDER);return result;
                    }catch(Exception failure){throw new IllegalStateException(failure);}
                }
            };
            var messages=new MessagingService(List.of(provider),new PanelOutboundSenderAuthorizationClient(api,mapper,headers));
            var dispatch=new OutboundFeedbackPromptDispatchService(channelReader,messages,ledger,
                new PanelFeedbackPromptDeliveryClient(api,mapper,headers),receipts);
            String destination=config.platform()+".channel."+FeedbackChainFixture.CHANNEL;
            var properties=new IntegrationRabbitProperties();
            properties.setOutboundQueue("iguana.integration.outbound.feedback-prompt."+destination+".bot");
            properties.setOutboundRoutingKey("integration.outbound.feedback.prompt."+destination);
            properties.setOutboundConcurrency(1);properties.setOutboundMaxConcurrency(1);properties.setOutboundPrefetch(1);
            var listener=new OutboundFeedbackPromptListener(dispatch,properties) {
                @Override public void onFeedbackPrompt(OutboundFeedbackPromptEvent event,String route){super.onFeedbackPrompt(event,route);marker("DISPATCHED");}
            };
            var endpoint=new MethodRabbitListenerEndpoint();endpoint.setId("r265-child");endpoint.setBean(listener);
            endpoint.setMethod(OutboundFeedbackPromptListener.class.getMethod("onFeedbackPrompt",OutboundFeedbackPromptEvent.class,String.class));
            endpoint.setQueueNames(properties.getOutboundQueue());
            var handler=new DefaultMessageHandlerMethodFactory();handler.afterPropertiesSet();endpoint.setMessageHandlerMethodFactory(handler);
            var connections=new CachingConnectionFactory("127.0.0.1",config.amqp());
            connections.setUsername("r259_test");connections.setPassword(FeedbackChainFixture.TOKEN);
            var transport=new RabbitIntegrationTransportConfig();
            var container=transport.outboundFeedbackPromptListenerContainerFactory(connections,transport.integrationRabbitMessageConverter(mapper),properties).createListenerContainer(endpoint);
            container.setAfterReceivePostProcessors(message->{
                marker("REDELIVERED "+message.getMessageProperties().isRedelivered());
                try{marker("PAYLOAD "+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(message.getBody())));}
                catch(Exception failure){throw new IllegalStateException(failure);}return message;
            });
            container.setErrorHandler(failure->marker("FAILURE "+failure.getClass().getSimpleName()));
            container.setShutdownTimeout(5000);container.setReceiveTimeout(50);
            try{container.start();System.in.read();}
            finally{container.stop();connections.destroy();}
        }
    }
}
