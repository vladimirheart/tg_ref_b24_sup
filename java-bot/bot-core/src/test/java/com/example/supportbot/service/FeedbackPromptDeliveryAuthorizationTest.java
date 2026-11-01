package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.supportbot.config.IntegrationPanelApiProperties;
import com.example.supportbot.entity.Channel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Loopback signed HTTP plus real dispatcher; only channel/provider/ledger are doubles. */
class FeedbackPromptDeliveryAuthorizationTest {
    private static final long USER=9_007_199_254_740_993L, CHANNEL=USER+2;
    private final ObjectMapper mapper=new ObjectMapper();
    private final AtomicInteger requests=new AtomicInteger();
    private final AtomicReference<String> received=new AtomicReference<>();
    private final AtomicReference<Throwable> serverFailure=new AtomicReference<>();
    private HttpServer server;
    private int responseStatus=200;
    private String responseBody="{\"allowed\":true,\"eventId\":\"evt-r249\"}";
    private ChannelService channels;
    private MessagingService messaging;
    private OutboundTransportDeliveryLedgerService ledger;
    private OutboundFeedbackPromptDispatchService dispatch;
    private Channel channel;

    @BeforeEach void setup() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/api/bot/feedback-prompts/authorize-delivery", exchange->{
            requests.incrementAndGet();
            try {
                String body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
                received.set(body);
                assertThat(exchange.getRequestMethod()).isEqualTo("POST");
                assertThat(exchange.getRequestURI().getRawQuery()).isNull();
                assertThat(exchange.getRequestHeaders().getFirst("X-Iguana-Bot-Api-Token")).isEqualTo("isolated-r249");
                String timestamp=exchange.getRequestHeaders().getFirst("X-Iguana-Request-Timestamp");
                assertThat(timestamp).isNotBlank();
                String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
                String canonical="POST\n"+exchange.getRequestURI().getRawPath()+"\n"+timestamp+"\n"+digest;
                Mac mac=Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec("isolated-r249".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
                assertThat(exchange.getRequestHeaders().getFirst("X-Iguana-Request-Signature"))
                    .isEqualTo(HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8))));
            } catch(Throwable failure) { serverFailure.set(failure); }
            byte[] reply=responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json");
            exchange.sendResponseHeaders(responseStatus,reply.length);
            try(var output=exchange.getResponseBody()){output.write(reply);}
        });
        server.start();
        var properties=new IntegrationPanelApiProperties();
        properties.setBaseUrl("http://127.0.0.1:"+server.getAddress().getPort());
        properties.setToken("isolated-r249");
        channels=mock(ChannelService.class);messaging=mock(MessagingService.class);ledger=mock(OutboundTransportDeliveryLedgerService.class);
        channel=new Channel();channel.setId(CHANNEL);
        when(channels.findById(CHANNEL)).thenReturn(Optional.of(channel));
        when(messaging.sendToUser(any(),anyLong(),anyString(),any(Runnable.class))).thenAnswer(call->{
            ((Runnable)call.getArgument(3)).run();return true;
        });
        when(ledger.beginDelivery(anyString(),anyString(),anyString(),anyLong(),anyLong(),anyString(),anyLong())).thenReturn(true);
        // Constructor bridge only: the unchanged baseline has 3 dependencies; the secured dispatcher has 4.
        // No behavior/assertion changes between the baseline and fixed runs.
        var constructor=OutboundFeedbackPromptDispatchService.class.getConstructors()[0];
        if(constructor.getParameterCount()==3)dispatch=(OutboundFeedbackPromptDispatchService)constructor.newInstance(channels,messaging,ledger);
        else {
            Object client=constructor.getParameterTypes()[3].getConstructor(IntegrationPanelApiProperties.class,ObjectMapper.class,PanelApiRequestHeadersFactory.class)
                .newInstance(properties,mapper,new PanelApiRequestHeadersFactory(properties));
            dispatch=(OutboundFeedbackPromptDispatchService)constructor.newInstance(channels,messaging,ledger,client);
        }
    }
    @AfterEach void stop(){server.stop(0);}
    static Stream<String> platforms(){return Stream.of("telegram","vk","max");}
    static Stream<Arguments> denials(){return platforms().flatMap(p->Stream.of(400,401,403,404,409,429,500,503).map(status->Arguments.of(p,status)));}
    static Stream<Arguments> malformed(){return platforms().flatMap(p->Stream.of("null","{}","not-json","{\"allowed\":false,\"eventId\":\"evt-r249\"}","{\"allowed\":\"true\",\"eventId\":\"evt-r249\"}","{\"allowed\":true,\"eventId\":\"foreign-event\"}").map(body->Arguments.of(p,body)));}
    static Stream<Arguments> revoked(){return platforms().flatMap(p->Stream.of("inactive","platform-changed").map(change->Arguments.of(p,change)));}

    @ParameterizedTest @MethodSource("denials")
    void panelFailureNeverSendsToProvider(String platform,int status){responseStatus=status;deny(platform);}
    @ParameterizedTest @MethodSource("malformed")
    void malformedOrDeniedAuthorizationNeverSends(String platform,String body){responseBody=body;deny(platform);}
    @ParameterizedTest @MethodSource("revoked")
    void localChannelRevocationNeverSends(String platform,String change){
        channel.setPlatform(change.equals("platform-changed")?"foreign-platform":platform);
        if(change.equals("inactive"))channel.setActive(false);
        assertThatThrownBy(()->dispatch.dispatch(event(platform),route(platform))).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(messaging);assertThat(requests.get()).isZero();
        verify(ledger).markFailed(eq("evt-r249"),any(IllegalStateException.class));
    }
    @ParameterizedTest @MethodSource("platforms")
    void validSignedResponseDeliversExactEvent(String platform)throws Exception{
        channel.setPlatform(platform);dispatch.dispatch(event(platform),route(platform));
        verify(messaging).sendToUser(eq(channel),eq(USER),eq("Rate padded/Ю%?' "),any(Runnable.class));
        verify(ledger).markSending("evt-r249");
        verify(ledger).markDelivered("evt-r249");
        assertThat(requests.get()).isEqualTo(1);assertThat(serverFailure.get()).isNull();
        var body=mapper.readTree(received.get());
        assertThat(body.path("event").path("userId").longValue()).isEqualTo(USER);
        assertThat(body.path("event").path("ticketId").asText()).isEqualTo(" padded/Ю%?' ");
        assertThat(body.path("routingKey").asText()).isEqualTo(route(platform));
    }
    @ParameterizedTest @MethodSource("platforms")
    void deliveredDuplicateDoesNotAuthorizeAgain(String platform){
        when(ledger.beginDelivery(anyString(),anyString(),anyString(),anyLong(),anyLong(),anyString(),anyLong())).thenReturn(false);
        dispatch.dispatch(event(platform),route(platform));verifyNoInteractions(channels,messaging);assertThat(requests.get()).isZero();
    }
    private void deny(String platform){
        channel.setPlatform(platform);
        assertThatThrownBy(()->dispatch.dispatch(event(platform),route(platform))).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(messaging);verify(ledger).markFailed(eq("evt-r249"),any(IllegalStateException.class));
        assertThat(serverFailure.get()).isNull();
    }
    private OutboundFeedbackPromptEvent event(String platform){return new OutboundFeedbackPromptEvent("evt-r249","feedback.prompt.dispatch","evt-r249",platform,CHANNEL,100L,USER," padded/Ю%?' ","Rate padded/Ю%?' ");}
    private String route(String platform){return "integration.outbound.feedback.prompt."+platform+".channel."+CHANNEL;}
}
