package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.*;
import com.example.supportbot.config.IntegrationPanelApiProperties;
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

class SelectedMediaAdmissionClientTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private HttpServer server;private PanelRuntimeConfigClient client;private ObjectNode proof;
    private boolean signed;private String requestPath;private int status=200;
    private PanelRuntimeConfigClient.SelectedQuestionnaire selected;
    @BeforeEach void setup() throws Exception {
        selected=new PanelRuntimeConfigClient.SelectedQuestionnaire(4,8,"BUSINESS","USER_SELECTION",7L,"q-a","a".repeat(64),"c".repeat(64),Map.of(),Map.of(),Map.of());
        proof=mapper.valueToTree(new PanelRuntimeConfigClient.SelectedMediaAdmission(1,8,901,"T? /&",7L,"BUSINESS","q-a","a".repeat(64),"c".repeat(64),9));
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/api/bot/channels/8/questionnaire-media",exchange->{
            try {
                requestPath=exchange.getRequestURI().toString();String time=exchange.getRequestHeaders().getFirst("X-Iguana-Request-Timestamp");
                String canonical="GET\n"+requestPath+"\n"+time+"\n"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new byte[0]));
                Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
                signed=HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8))).equals(exchange.getRequestHeaders().getFirst("X-Iguana-Request-Signature"))
                    && "test-token".equals(exchange.getRequestHeaders().getFirst("X-Iguana-Bot-Api-Token"));
                byte[] bytes=mapper.writeValueAsBytes(proof);exchange.sendResponseHeaders(status,bytes.length);
                try(var out=exchange.getResponseBody()){out.write(bytes);}
            }catch(Exception ex){exchange.close();}
        });server.start();
        var properties=new IntegrationPanelApiProperties();properties.setBaseUrl("http://127.0.0.1:"+server.getAddress().getPort());
        properties.setToken("test-token");properties.setRequestSigningEnabled(true);properties.setSignatureSecret("test-secret");
        client=new PanelRuntimeConfigClient(properties,mapper,new PanelApiRequestHeadersFactory(properties));
    }
    @AfterEach void stop(){server.stop(0);}
    @ParameterizedTest @ValueSource(booleans={false,true})
    void vkBatchHasADistinctSignedCapabilityAndFrozenNullableOwner(boolean general) {
        proof.put("version",2);
        if(general){proof.putNull("businessId").put("scopeKind","GENERAL");selected=new PanelRuntimeConfigClient.SelectedQuestionnaire(4,8,"GENERAL","USER_SELECTION",null,"q-a","a".repeat(64),"c".repeat(64),Map.of(),Map.of(),Map.of());}
        var result=client.selectedVkMediaAdmission(8,901,"T? /&",selected);
        assertThat(result.version()).isEqualTo(2);assertThat(signed).isTrue();assertThat(requestPath).contains("/questionnaire-media/vk-batch?","ticketId=T%3F+%2F%26");
    }
    @ParameterizedTest @ValueSource(strings={"old-telegram","channel","user","ticket","owner","scope","template","hash","catalog","zero","many","missing","unavailable"})
    void vkBatchNeverUsesTelegramOrForeignOrMissingAdmission(String mutation) {
        proof.put("version",2);
        switch(mutation) {
            case "old-telegram"->proof.put("version",1);case "channel"->proof.put("channelId",9);case "user"->proof.put("userId",902);
            case "ticket"->proof.put("ticketId","OTHER");case "owner"->proof.put("businessId",8);case "scope"->proof.put("scopeKind","GENERAL");
            case "template"->proof.put("questionTemplateId","other");case "hash"->proof.put("templateSha256","b".repeat(64));case "catalog"->proof.put("catalogSha256","d".repeat(64));
            case "zero"->proof.put("remainingFiles",0);case "many"->proof.put("remainingFiles",11);case "missing"->proof.removeAll();case "unavailable"->status=404;
        }
        assertThatThrownBy(()->client.selectedVkMediaAdmission(8,901,"T? /&",selected)).isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void signedFrozenAdmissionDoesNotReadTheCurrentCatalog(boolean general) {
        if(general){proof.putNull("businessId").put("scopeKind","GENERAL");selected=new PanelRuntimeConfigClient.SelectedQuestionnaire(4,8,"GENERAL","USER_SELECTION",null,"q-a","a".repeat(64),"c".repeat(64),Map.of(),Map.of(),Map.of());}
        var result=client.selectedMediaAdmission(8,901,"T? /&",selected);
        assertThat(result.remainingFiles()).isEqualTo(9);assertThat(signed).isTrue();assertThat(requestPath).contains("ticketId=T%3F+%2F%26");
    }
    @ParameterizedTest @ValueSource(strings={"version","channel","user","ticket","owner","scope","template","hash","catalog","zero","many","missing","unavailable"})
    void absentOrForeignAdmissionNeverOpensTheWriter(String mutation) {
        switch(mutation){
            case "version"->proof.put("version",2);case "channel"->proof.put("channelId",9);case "user"->proof.put("userId",902);
            case "ticket"->proof.put("ticketId","OTHER");case "owner"->proof.put("businessId",8);case "scope"->proof.put("scopeKind","GENERAL");
            case "template"->proof.put("questionTemplateId","other");case "hash"->proof.put("templateSha256","b".repeat(64));case "catalog"->proof.put("catalogSha256","d".repeat(64));
            case "zero"->proof.put("remainingFiles",0);case "many"->proof.put("remainingFiles",11);case "missing"->proof.removeAll();case "unavailable"->status=404;
        }
        assertThatThrownBy(()->client.selectedMediaAdmission(8,901,"T? /&",selected)).isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);
    }
}
