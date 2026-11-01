package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.supportbot.config.IntegrationPanelApiProperties;
import com.example.supportbot.entity.Channel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PanelOutboundSenderAuthorizationClientTest {
    private static final long CHANNEL=9_007_199_254_740_993L;
    private HttpServer server;
    private final AtomicInteger requests=new AtomicInteger();
    private final AtomicReference<Throwable> serverFailure=new AtomicReference<>();
    private int status=200;
    private String response;
    private OutboundMessenger provider;
    private MessagingService messaging;
    private Channel channel;
    static Stream<String> platforms(){return Stream.of("telegram","vk","max");}
    static Stream<Arguments> failures(){return platforms().flatMap(p->Stream.of("401","404","503","garbage","string-allow","wrong-channel","fraction-channel","overflow-channel","wrong-platform","missing-platform","missing-channel","missing-allow","deny").map(c->Arguments.of(p,c)));}

    private void start(String platform)throws Exception {
        var mapper=new ObjectMapper();
        var identity=new BotRuntimeSenderIdentity(CHANNEL,platform).forToken(platform," sender token/Ю ");
        response="{\"allowed\":true,\"channelId\":"+CHANNEL+",\"platform\":\""+platform+"\"}";
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/api/bot/outbound/authorize-sender",exchange->{
            requests.incrementAndGet();
            try {
                String body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
                assertThat(exchange.getRequestMethod()).isEqualTo("POST");
                assertThat(exchange.getRequestURI().getRawQuery()).isNull();
                assertThat(exchange.getRequestHeaders().getFirst("X-Iguana-Bot-Api-Token")).isEqualTo("isolated-r252");
                assertThat(exchange.getRequestHeaders().getFirst("X-Iguana-Idempotency-Key")).isNull();
                String timestamp=exchange.getRequestHeaders().getFirst("X-Iguana-Request-Timestamp");
                String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
                Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec("isolated-r252".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
                String canonical="POST\n"+exchange.getRequestURI().getRawPath()+"\n"+timestamp+"\n"+digest;
                assertThat(exchange.getRequestHeaders().getFirst("X-Iguana-Request-Signature")).isEqualTo(HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8))));
                var json=mapper.readTree(body);
                assertThat(json.size()).isEqualTo(3);assertThat(json.path("channelId").longValue()).isEqualTo(CHANNEL);
                assertThat(json.path("platform").textValue()).isEqualTo(platform);
                String expected=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(" sender token/Ю ".getBytes(StandardCharsets.UTF_8)));
                assertThat(json.path("tokenSha256").textValue()).isEqualTo(expected);
                assertThat(body).doesNotContain("sender token","payload");
            } catch(Throwable failure){serverFailure.set(failure);}
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,bytes.length);
            try(var output=exchange.getResponseBody()){output.write(bytes);}
        });
        server.start();
        var properties=new IntegrationPanelApiProperties();properties.setBaseUrl("http://127.0.0.1:"+server.getAddress().getPort());properties.setToken("isolated-r252");
        var client=new PanelOutboundSenderAuthorizationClient(properties,mapper,new PanelApiRequestHeadersFactory(properties));
        provider=mock(OutboundMessenger.class);when(provider.platform()).thenReturn(platform);when(provider.senderIdentity()).thenReturn(identity);
        when(provider.sendToUser(77L,"payload")).thenReturn(true);
        messaging=new MessagingService(List.of(provider),client);channel=new Channel();channel.setId(CHANNEL);channel.setPlatform(platform);
    }
    @AfterEach void stop(){if(server!=null)server.stop(0);assertThat(serverFailure.get()).isNull();}
    @ParameterizedTest @MethodSource("platforms")
    void signedExactIdentityAllowsOnlyTheSelectedProvider(String platform)throws Exception {
        start(platform);assertThat(messaging.sendToUser(channel,77L,"payload")).isTrue();
        verify(provider).sendToUser(77L,"payload");assertThat(requests.get()).isEqualTo(1);
    }
    @ParameterizedTest @MethodSource("failures")
    void denyUnavailableOrMalformedApiNeverReachesProvider(String platform,String change)throws Exception {
        start(platform);
        switch(change){
            case "401","404","503" -> status=Integer.parseInt(change);
            case "garbage" -> response="not-json";
            case "string-allow" -> response=response.replace("true","\"true\"");
            case "wrong-channel" -> response=response.replace(Long.toString(CHANNEL),Long.toString(CHANNEL+1));
            case "fraction-channel" -> response=response.replace(Long.toString(CHANNEL),"9007199254740993.5");
            case "overflow-channel" -> response=response.replace(Long.toString(CHANNEL),"9223372036854775808");
            case "wrong-platform" -> response=response.replace(platform,"foreign");
            case "missing-platform" -> response="{\"allowed\":true,\"channelId\":"+CHANNEL+"}";
            case "missing-channel" -> response="{\"allowed\":true,\"platform\":\""+platform+"\"}";
            case "missing-allow" -> response="{\"channelId\":"+CHANNEL+",\"platform\":\""+platform+"\"}";
            case "deny" -> response=response.replace("true","false");
            default -> throw new AssertionError(change);
        }
        if(change.equals("deny"))assertThat(messaging.sendToUser(channel,77L,"payload")).isFalse();
        else assertThatThrownBy(()->messaging.sendToUser(channel,77L,"payload")).isInstanceOf(IllegalStateException.class);
        verify(provider,never()).sendToUser(anyLong(),anyString());assertThat(requests.get()).isEqualTo(1);
    }
    @ParameterizedTest @MethodSource("platforms")
    void previousAllowDoesNotAuthorizeAnotherSendAfterRevocation(String platform)throws Exception {
        start(platform);assertThat(messaging.sendToUser(channel,77L,"payload")).isTrue();response=response.replace("true","false");
        assertThat(messaging.sendToUser(channel,77L,"payload")).isFalse();
        verify(provider,times(1)).sendToUser(77L,"payload");assertThat(requests.get()).isEqualTo(2);
    }
}
