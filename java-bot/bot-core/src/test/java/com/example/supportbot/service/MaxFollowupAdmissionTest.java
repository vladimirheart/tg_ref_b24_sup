package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.supportbot.config.*;
import com.example.supportbot.entity.Channel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

class MaxFollowupAdmissionTest {
    final ObjectMapper mapper=new ObjectMapper();
    HttpServer server; PanelRuntimeConfigClient client; ObjectNode proof;
    String path; boolean signed; int status=200;
    @BeforeEach void setup() throws Exception {
        proof=mapper.valueToTree(new PanelRuntimeConfigClient.SelectedMediaAdmission(3,8,901," T? /& ",7L,"BUSINESS","q-a","a".repeat(64),"c".repeat(64),9));
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/api/bot/channels/8/questionnaire-media",exchange->{
            try {
                path=exchange.getRequestURI().toString();String time=exchange.getRequestHeaders().getFirst("X-Iguana-Request-Timestamp");
                String canonical="GET\n"+path+"\n"+time+"\n"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new byte[0]));
                Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
                signed=HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8))).equals(exchange.getRequestHeaders().getFirst("X-Iguana-Request-Signature"))
                    && "test-token".equals(exchange.getRequestHeaders().getFirst("X-Iguana-Bot-Api-Token"));
                byte[] bytes=mapper.writeValueAsBytes(proof);exchange.sendResponseHeaders(status,bytes.length);try(var out=exchange.getResponseBody()){out.write(bytes);}
            }catch(Exception ex){exchange.close();}
        });server.start();
        var p=new IntegrationPanelApiProperties();p.setBaseUrl("http://127.0.0.1:"+server.getAddress().getPort());p.setToken("test-token");p.setRequestSigningEnabled(true);p.setSignatureSecret("test-secret");
        client=new PanelRuntimeConfigClient(p,mapper,new PanelApiRequestHeadersFactory(p));
    }
    @AfterEach void stop(){server.stop(0);}
    static Object invoke(Object target,String name,Class<?>[] types,Object... args) {
        try{return target.getClass().getMethod(name,types).invoke(target,args);}
        catch(java.lang.reflect.InvocationTargetException e){if(e.getCause() instanceof RuntimeException r)throw r;throw new AssertionError(e.getCause());}
        catch(NoSuchMethodException e){throw new PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException("MAX followup unavailable on baseline");}
        catch(Exception e){throw new AssertionError(e);}
    }
    PanelRuntimeConfigClient.SelectedMediaAdmission admission(long user,String ticket){return (PanelRuntimeConfigClient.SelectedMediaAdmission)invoke(client,"selectedMaxMediaAdmission",new Class<?>[]{long.class,long.class,String.class},8L,user,ticket);}
    @ParameterizedTest @ValueSource(booleans={false,true})
    void signedCanonicalRootAdmissionSurvivesMissingSessionAndCurrentCatalog(boolean general) {
        if(general)proof.putNull("businessId").put("scopeKind","GENERAL");
        var result=admission(901," T? /& ");assertThat(result.version()).isEqualTo(3);assertThat(result.businessId()).isEqualTo(general?null:7L);
        assertThat(signed).isTrue();assertThat(path).isEqualTo("/internal/api/bot/channels/8/questionnaire-media/max-batch?userId=901&ticketId=+T%3F+%2F%26+");
    }
    @ParameterizedTest @ValueSource(strings={"telegram","vk","channel","user","ticket","business-null","business-zero","general-owner","scope","template","hash","catalog","zero","many","missing","unavailable"})
    void malformedOrForeignAdmissionCannotOpenTheByteWriter(String mutation) {
        switch(mutation){case "telegram"->proof.put("version",1);case "vk"->proof.put("version",2);case "channel"->proof.put("channelId",9);case "user"->proof.put("userId",902);case "ticket"->proof.put("ticketId","OTHER");case "business-null"->proof.putNull("businessId");case "business-zero"->proof.put("businessId",0);case "general-owner"->proof.put("scopeKind","GENERAL");case "scope"->proof.put("scopeKind","LEGACY_UNASSIGNED");case "template"->proof.put("questionTemplateId","");case "hash"->proof.put("templateSha256","A".repeat(64));case "catalog"->proof.putNull("catalogSha256");case "zero"->proof.put("remainingFiles",0);case "many"->proof.put("remainingFiles",11);case "missing"->proof.removeAll();case "unavailable"->status=409;}
        assertThatThrownBy(()->admission(901," T? /& ")).isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);
    }
    @ParameterizedTest @ValueSource(strings={"zero-user","blank-ticket","long-ticket"})
    void invalidLookupNeverCallsHttp(String kind) {
        assertThatThrownBy(()->admission(kind.equals("zero-user")?0:901,kind.equals("blank-ticket")?" ":"x".repeat(513))).isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);assertThat(path).isNull();
    }
    @ParameterizedTest @ValueSource(strings={"jdbc","disabled","inactive","telegram","vk","null","zero"})
    void runtimeRequiresActiveMaxAndCanonicalRabbitWithoutLocalFallback(String kind) {
        var panel=mock(PanelRuntimeConfigClient.class);when(panel.isEnabled()).thenReturn(!kind.equals("disabled"));var local=mock(SharedConfigService.class);
        var mode=new BotIntegrationTransportMode(new MockEnvironment().withProperty("app.integration.transport.mode",kind.equals("jdbc")?"jdbc":"rabbitmq"));
        var runtime=new RuntimeConfigService(local,mode,panel,mapper);var channel=new Channel();channel.setId(kind.equals("zero")?0L:8L);channel.setActive(!kind.equals("inactive"));channel.setPlatform(Set.of("telegram","vk").contains(kind)?kind:"max");
        assertThatThrownBy(()->invoke(runtime,"selectedMaxMediaAdmission",new Class<?>[]{Channel.class,long.class,String.class},kind.equals("null")?null:channel,901L,"T")).isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);verifyNoInteractions(local);
    }
}
