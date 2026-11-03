package com.example.supportbot.service;

import com.example.supportbot.config.IntegrationPanelApiProperties;
import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Stream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.*;

/** A completed, versioned 404 is absence; network/old-server/parser failures are not. */
class PanelActiveTicketAvailabilityTest {
    static final String HEADER="X-Iguana-Active-Lookup-Version";
    HttpServer server;PanelTicketReadClient client;
    final AtomicBoolean signed=new AtomicBoolean();final AtomicReference<String> uri=new AtomicReference<>();
    volatile int status=200;volatile List<String> markers=List.of("1");volatile String body="{\"ticketId\":\" raw-ticket \",\"userIdentity\":\"client\",\"lastSeen\":\"2026-11-03T07:00:00Z\"}";volatile String transport="normal";
    @BeforeEach void start()throws Exception{
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",exchange->{
            try{
                uri.set(exchange.getRequestURI().toASCIIString());String timestamp=exchange.getRequestHeaders().getFirst(PanelApiRequestHeadersFactory.TIMESTAMP_HEADER);
                String canonical="GET\n"+uri.get()+"\n"+timestamp+"\n"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new byte[0]));
                var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec("fixture-signature".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
                signed.set("fixture-token".equals(exchange.getRequestHeaders().getFirst(PanelApiRequestHeadersFactory.AUTH_HEADER))&&HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8))).equals(exchange.getRequestHeaders().getFirst(PanelApiRequestHeadersFactory.SIGNATURE_HEADER)));
                if(transport.equals("disconnect"))return;if(transport.equals("timeout"))Thread.sleep(600);
                for(String value:markers)exchange.getResponseHeaders().add(HEADER,value);
                var bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,status==204?-1:bytes.length);
                if(status!=204)try(var out=exchange.getResponseBody()){out.write(bytes);}
            }catch(Exception ignored){ /* timeout/disconnect intentionally abandon this response */ }finally{exchange.close();}
        });server.start();var properties=new IntegrationPanelApiProperties();properties.setBaseUrl("http://127.0.0.1:"+server.getAddress().getPort());properties.setToken("fixture-token");properties.setSignatureSecret("fixture-signature");properties.setRequestTimeout(Duration.ofMillis(300));
        client=new PanelTicketReadClient(properties,new ObjectMapper().findAndRegisterModules().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false),new PanelApiRequestHeadersFactory(properties));
    }
    @AfterEach void stop(){if(server!=null)server.stop(0);}
    void rejected(){assertThatThrownBy(()->client.findActiveTicket(9007199254740993L," @client name ",55L)).isInstanceOf(IllegalStateException.class);assertThat(signed).isTrue();assertThat(uri.get()).isEqualTo("/internal/api/bot/tickets/active?userId=9007199254740993&username=%40client+name&channelId=55");}
    static Stream<Arguments> failedStatuses(){return Stream.of(201,202,204,206,301,400,401,403,409,429,500,503).flatMap(code->Stream.of(false,true).map(marker->Arguments.of(code,marker)));}
    @ParameterizedTest @MethodSource("failedStatuses") void responseFailuresCannotMeanNoActiveTicket(int code,boolean marker){status=code;markers=marker?List.of("1"):List.of();rejected();}
    static Stream<Arguments> invalidMarkers(){return Stream.of(200,404).flatMap(code->Stream.of("missing","empty","old","future","duplicate","combined").map(marker->Arguments.of(code,marker)));}
    @ParameterizedTest @MethodSource("invalidMarkers") void oldOrAmbiguousProofCannotAuthorizeAbsence(int code,String marker){status=code;markers=switch(marker){case "missing"->List.of();case "empty"->List.of("");case "old"->List.of("0");case "future"->List.of("2");case "duplicate"->List.of("1","1");default->List.of("1, 2");};rejected();}
    @ParameterizedTest @ValueSource(strings={"","null","[]","{}","{\"ticketId\":null}","{\"ticketId\":\"  \"}","{broken}","{\"ticketId\":\"root\"} {}","{\"ticketId\":\"root\",\"lastSeen\":\"invalid\"}"})
    void malformedPositiveResponseCannotBecomeAnEmptyLookup(String invalid){body=invalid;rejected();}
    @ParameterizedTest @ValueSource(strings={"disconnect","timeout"}) void transportFailureCannotBecomeAnEmptyLookup(String mode){transport=mode;rejected();}
    @Test void disabledBoundaryCannotClaimVerifiedAbsence(){var properties=new IntegrationPanelApiProperties();var disabled=new PanelTicketReadClient(properties,new ObjectMapper(),new PanelApiRequestHeadersFactory(properties));assertThatThrownBy(()->disabled.findActiveTicket(1001L,"client",55L)).isInstanceOf(IllegalStateException.class);}
    @ParameterizedTest @ValueSource(strings={"","null","{}","<html>Not found</html>"}) void completedNotFoundPreservesAbsenceRegardlessOfErrorBody(String content){status=404;body=content;assertThat(client.findActiveTicket(1001L,"client",55L)).isEmpty();assertThat(signed).isTrue();}
    @ParameterizedTest @ValueSource(strings={" raw-ticket ","123","заявка+/#?%"}) void completedLookupPreservesExactCanonicalTicketId(String id)throws Exception{body=new ObjectMapper().writeValueAsString(Map.of("ticketId",id,"userIdentity"," @client "));var found=client.findActiveTicket(9007199254740993L," @client name ",55L).orElseThrow();assertThat(found.getTicketId()).isEqualTo(id);assertThat(found.getUser()).isEqualTo(" @client ");assertThat(signed).isTrue();}
    @ParameterizedTest @ValueSource(ints={401,403,404,409,500,503}) void unrelatedTicketLookupKeepsItsExistingOptionalContract(int code){status=code;markers=List.of();assertThat(client.findTicket("root")).isEmpty();assertThat(signed).isTrue();}
}
