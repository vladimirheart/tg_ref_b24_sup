package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.supportbot.config.IntegrationPanelApiProperties;
import com.example.supportbot.entity.Channel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Stream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class PanelSupportChatAuthorizationTest {
    HttpServer server;
    final AtomicInteger requests=new AtomicInteger();final AtomicBoolean allowed=new AtomicBoolean(true);
    final AtomicReference<Throwable> failure=new AtomicReference<>();
    Channel channel;OutboundMessenger provider;MessagingService service;
    static Stream<Arguments> cases(){return Stream.of("telegram","vk","max").flatMap(p->Stream.of("allow","deny","wrong-chat","missing-chat","old-backend","in-flight-rebind").map(c->Arguments.of(p,c)));}
    private void start(String platform,String change) throws Exception {
        var mapper=new ObjectMapper();String token="iguana-child-v1:"+platform+":22:"+"a".repeat(64);
        channel=new Channel();channel.setId(22L);channel.setPlatform(platform);channel.setSupportChatId("-100");
        provider=mock(OutboundMessenger.class);when(provider.platform()).thenReturn(platform);
        when(provider.senderIdentity()).thenReturn(new OutboundSenderIdentity(22L,platform,"a".repeat(64)));
        when(provider.sendToSupportChat("-100","private payload")).thenReturn(true);
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/api/bot/outbound/",exchange->{
            requests.incrementAndGet();int status=200;String response;
            try {
                byte[] bytes=exchange.getRequestBody().readAllBytes();var json=mapper.readTree(bytes);
                String path=exchange.getRequestURI().getRawPath();boolean recipient=path.endsWith("authorize-support-chat");
                assertThat(exchange.getRequestHeaders().getFirst(InternalHeaders.TOKEN)).isEqualTo(token);
                assertThat(exchange.getRequestHeaders().getFirst("X-Iguana-Idempotency-Key")).isNull();
                String timestamp=exchange.getRequestHeaders().getFirst("X-Iguana-Request-Timestamp");
                Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
                String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                assertThat(exchange.getRequestHeaders().getFirst("X-Iguana-Request-Signature"))
                    .isEqualTo(HexFormat.of().formatHex(mac.doFinal(("POST\n"+path+"\n"+timestamp+"\n"+digest).getBytes(StandardCharsets.UTF_8))));
                assertThat((recipient?json.path("sender"):json).path("channelId").longValue()).isEqualTo(22L);
                boolean decision=!recipient || allowed.get()&&!change.equals("deny");
                response="{\"channelId\":22,\"platform\":\""+platform+"\",\"allowed\":"+decision+(recipient&&!change.equals("missing-chat")?",\"supportChatId\":\""+(change.equals("wrong-chat")?"-101":"-100")+"\"":"")+"}";
                if(recipient) {
                    assertThat(json.path("supportChatId").asText()).isEqualTo("-100");
                    if(change.equals("old-backend"))status=404;
                    if(change.equals("in-flight-rebind"))channel.setSupportChatId("-101");
                }
            } catch(Throwable error) {failure.set(error);status=500;response="{}";}
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,bytes.length);
            try(var output=exchange.getResponseBody()){output.write(bytes);}
        });server.start();
        var properties=new IntegrationPanelApiProperties();properties.setToken(token);properties.setBaseUrl("http://127.0.0.1:"+server.getAddress().getPort());
        service=new MessagingService(List.of(provider),new PanelOutboundSenderAuthorizationClient(properties,mapper,new PanelApiRequestHeadersFactory(properties)));
    }
    @AfterEach void stop(){if(server!=null)server.stop(0);assertThat(failure.get()).isNull();}
    @ParameterizedTest @MethodSource("cases")
    void everySupportSendRequiresAnUncachedExactRecipientDecision(String platform,String change) throws Exception {
        start(platform,change);
        if(Set.of("wrong-chat","missing-chat","old-backend").contains(change))
            assertThatThrownBy(()->service.sendToSupportChat(channel,"private payload")).isInstanceOf(IllegalStateException.class);
        else assertThat(service.sendToSupportChat(channel,"private payload")).isEqualTo(change.equals("allow"));
        if(change.equals("allow"))verify(provider).sendToSupportChat("-100","private payload");
        else verify(provider,never()).sendToSupportChat(anyString(),anyString());
        assertThat(requests.get()).isEqualTo(2);
    }
    @ParameterizedTest @ValueSource(strings={"telegram","vk","max"})
    void previousAllowCannotSurviveRecipientRevocation(String platform) throws Exception {
        start(platform,"allow");assertThat(service.sendToSupportChat(channel,"private payload")).isTrue();
        allowed.set(false);assertThat(service.sendToSupportChat(channel,"private payload")).isFalse();
        verify(provider,times(1)).sendToSupportChat(anyString(),anyString());assertThat(requests.get()).isEqualTo(4);
    }
    private static class InternalHeaders {static final String TOKEN="X-Iguana-Bot-Api-Token";}
}
