package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.supportbot.config.*;
import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import java.lang.reflect.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class OwnerClientEditClientTest {
    static final long CHANNEL=9_007_199_254_740_995L,USER=CHANNEL-2,MESSAGE=Long.MAX_VALUE;
    final ObjectMapper mapper=new ObjectMapper();final List<String> paths=new CopyOnWriteArrayList<>();final List<JsonNode> bodies=new CopyOnWriteArrayList<>();
    HttpServer server;PanelTicketWriteClient client;int status=200;
    final String path="/internal/api/bot/channels/"+CHANNEL+"/users/"+USER+"/messages/"+MESSAGE+"/client-edit";
    @BeforeEach void start()throws Exception{
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",exchange->{
            String actual=exchange.getRequestURI().getRawPath();paths.add(exchange.getRequestMethod()+" "+actual);bodies.add(mapper.readTree(exchange.getRequestBody()));
            int code=actual.equals(path)?status:404;byte[] reply="{\"updated\":true,\"exists\":true}".getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(code,reply.length);try(var output=exchange.getResponseBody()){output.write(reply);}
        });server.start();var properties=new IntegrationPanelApiProperties();properties.setBaseUrl("http://127.0.0.1:"+server.getAddress().getPort());properties.setToken("r290-test-only");properties.setRequestSigningEnabled(false);properties.setRetryAttempts(1);client=new PanelTicketWriteClient(properties,mapper,new PanelApiRequestHeadersFactory(properties));
    }
    @AfterEach void stop(){if(server!=null)server.stop(0);}
    boolean invoke(String platform,Long channel,Long user,Long message,String text){try{return (boolean)PanelTicketWriteClient.class.getMethod("markClientMessageEdited",String.class,Long.class,Long.class,Long.class,String.class).invoke(client,platform,channel,user,message,text);}catch(NoSuchMethodException baseline){return client.markClientMessageEdited(channel,message,text);}catch(InvocationTargetException failure){throw new IllegalStateException(failure.getCause());}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}}
    @ParameterizedTest @ValueSource(strings={"telegram","vk","max"})
    void wireBindsPlatformAuthorChannelAndProviderId(String platform){assertThat(invoke(platform,CHANNEL,USER,MESSAGE," edited Ю ")).isTrue();assertThat(paths).containsExactly("PUT "+path);assertThat(bodies).hasSize(1);assertThat(bodies.get(0).path("platform").asText()).isEqualTo(platform);assertThat(bodies.get(0).path("message").asText()).isEqualTo("edited Ю");}
    @ParameterizedTest @ValueSource(ints={400,401,403,404,409,410,500,503})
    void failedScopedApiNeverFallsBackToAnUnownedEndpoint(int code){status=code;assertThat(invoke("telegram",CHANNEL,USER,MESSAGE,"edited")).isFalse();assertThat(paths).containsExactly("PUT "+path);}
    static Stream<Arguments> invalid(){return Stream.of(Arguments.of(null,CHANNEL,USER,MESSAGE,"edited"),Arguments.of("MAX",CHANNEL,USER,MESSAGE,"edited"),Arguments.of("unknown",CHANNEL,USER,MESSAGE,"edited"),Arguments.of("telegram",CHANNEL,null,MESSAGE,"edited"),Arguments.of("telegram",CHANNEL,0L,MESSAGE,"edited"),Arguments.of("telegram",0L,USER,MESSAGE,"edited"),Arguments.of("telegram",CHANNEL,USER,0L,"edited"),Arguments.of("telegram",CHANNEL,USER,MESSAGE," "));}
    @ParameterizedTest @MethodSource("invalid")
    void invalidIdentityNeverMakesHttpRequest(String platform,Long channel,Long user,Long message,String text){assertThat(invoke(platform,channel,user,message,text)).isFalse();assertThat(paths).isEmpty();}
    @Test void ticketServiceCannotFallBackToJdbcWithoutAnOwnerAwarePanel(){
        var history=mock(ChatHistoryService.class);var mode=mock(BotIntegrationTransportMode.class);when(mode.isRabbitMqMode()).thenReturn(false);
        var service=new TicketService(null,null,null,null,null,null,history,null,null,null,null,null,mode,null,null,client);
        boolean result;
        try{result=(boolean)TicketService.class.getMethod("markClientMessageEdited",String.class,Long.class,Long.class,Long.class,String.class).invoke(service,"telegram",CHANNEL,USER,MESSAGE,"edited");}
        catch(NoSuchMethodException baseline){result=service.markClientMessageEdited(CHANNEL,MESSAGE,"edited");}
        catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
        assertThat(result).isFalse();verifyNoInteractions(history);assertThat(paths).isEmpty();
    }
}
