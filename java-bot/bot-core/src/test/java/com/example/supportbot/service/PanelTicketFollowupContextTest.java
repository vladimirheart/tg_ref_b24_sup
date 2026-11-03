package com.example.supportbot.service;

import com.example.supportbot.config.IntegrationPanelApiProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import static org.assertj.core.api.Assertions.*;

/** Signed loopback HTTP contract; absent metadata stays unproved rather than becoming legacy. */
class PanelTicketFollowupContextTest {
    HttpServer server;PanelTicketReadClient client;String body;int status;AtomicBoolean signed=new AtomicBoolean();
    @BeforeEach void start()throws Exception{
        status=200;body="{}";server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            try{
                String timestamp=exchange.getRequestHeaders().getFirst(PanelApiRequestHeadersFactory.TIMESTAMP_HEADER);
                String canonical="GET\n"+exchange.getRequestURI().toASCIIString()+"\n"+timestamp+"\n"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new byte[0]));
                var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec("fixture-signature".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
                signed.set("fixture-token".equals(exchange.getRequestHeaders().getFirst(PanelApiRequestHeadersFactory.AUTH_HEADER))&&HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8))).equals(exchange.getRequestHeaders().getFirst(PanelApiRequestHeadersFactory.SIGNATURE_HEADER)));
                var bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,bytes.length);try(var out=exchange.getResponseBody()){out.write(bytes);}
            }catch(Exception e){throw new java.io.IOException(e);}finally{exchange.close();}
        });server.start();var properties=new IntegrationPanelApiProperties();properties.setBaseUrl("http://127.0.0.1:"+server.getAddress().getPort());properties.setToken("fixture-token");properties.setSignatureSecret("fixture-signature");
        client=new PanelTicketReadClient(properties,new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false),new PanelApiRequestHeadersFactory(properties));
    }
    @AfterEach void stop(){server.stop(0);}
    static Object field(Object value,String name)throws Exception{try{return value.getClass().getMethod(name).invoke(value);}catch(NoSuchMethodException beforeProductChange){return null;}}
    @ParameterizedTest @ValueSource(strings={"null","1","2","3","4","5"}) void preservesCanonicalContextAndNullableLegacyVersion(String version)throws Exception{
        body="{\"userId\":9007199254740993,\"ticketId\":\" raw-ticket \",\"status\":\"open\",\"channelId\":55,\"questionnaireVersion\":"+version+",\"followupContextVersion\":1}";
        var result=client.findTicket(" raw-ticket ").orElseThrow();assertThat(result.userId()).isEqualTo(9007199254740993L);assertThat(result.ticketId()).isEqualTo(" raw-ticket ");assertThat(field(result,"channelId")).isEqualTo(55L);assertThat(field(result,"followupContextVersion")).isEqualTo(1);assertThat(field(result,"questionnaireVersion")).isEqualTo(version.equals("null")?null:Integer.valueOf(version));assertThat(signed).isTrue();
    }
    @Test void oldServerResponseDoesNotAuthorizeALegacyFallback()throws Exception{
        body="{\"userId\":1001,\"ticketId\":\"root\",\"status\":\"open\"}";var result=client.findTicket("root").orElseThrow();assertThat(field(result,"followupContextVersion")).isNull();assertThat(field(result,"questionnaireVersion")).isNull();assertThat(field(result,"channelId")).isNull();assertThat(signed).isTrue();
    }
    @ParameterizedTest @ValueSource(ints={401,403,404,409,500,503}) void failedLookupDoesNotInventContext(int code){status=code;body="{}";assertThat(client.findTicket("root")).isEmpty();assertThat(signed).isTrue();}
}
