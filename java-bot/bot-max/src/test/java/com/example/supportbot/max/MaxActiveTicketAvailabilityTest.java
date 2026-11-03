package com.example.supportbot.max;

import com.example.supportbot.config.*;
import com.example.supportbot.entity.Channel;
import com.example.supportbot.repository.*;
import com.example.supportbot.service.*;
import com.example.supportbot.service.PanelRuntimeConfigClient.*;
import com.example.supportbot.settings.BotSettingsService;
import com.example.supportbot.settings.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Signed loopback HTTP -> real panel client -> Rabbit-mode TicketService -> MAX admission. */
class MaxActiveTicketAvailabilityTest {
    final ObjectMapper mapper=Jackson2ObjectMapperBuilder.json().build();
    final Channel channel=new Channel();final AtomicInteger unsigned=new AtomicInteger();
    final RuntimeConfigService runtime=mock(RuntimeConfigService.class);final MessagingService messages=mock(MessagingService.class);
    final BotSettingsService settings=mock(BotSettingsService.class);final AttachmentService attachments=mock(AttachmentService.class);
    final MaxApiClient api=mock(MaxApiClient.class);final FeedbackService feedback=mock(FeedbackService.class);
    final InboundClientMessagePublisher inbound=mock(InboundClientMessagePublisher.class);
    final ConversationTicketCreatedPublisher creation=mock(ConversationTicketCreatedPublisher.class);
    final TicketActiveRepository localActive=mock(TicketActiveRepository.class);final TicketRepository localTickets=mock(TicketRepository.class);
    final PanelTicketWriteClient writes=mock(PanelTicketWriteClient.class);
    final String ticketId=" T-active ";
    HttpServer server;TicketService tickets;BotSessionStoreService sessions;BotWebhookDeliveryGuardService guard;MaxWebhookController controller;
    volatile int status=200;volatile String marker="1";volatile String body="{\"ticketId\":\" T-active \",\"userIdentity\":\"client\"}";
    @BeforeEach @SuppressWarnings("unchecked") void setup()throws Exception{
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",exchange->{
            try{
                String timestamp=exchange.getRequestHeaders().getFirst(PanelApiRequestHeadersFactory.TIMESTAMP_HEADER);
                String canonical="GET\n"+exchange.getRequestURI().toASCIIString()+"\n"+timestamp+"\n"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new byte[0]));
                var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec("fixture-signature".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
                if(!"fixture-token".equals(exchange.getRequestHeaders().getFirst(PanelApiRequestHeadersFactory.AUTH_HEADER))||!HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8))).equals(exchange.getRequestHeaders().getFirst(PanelApiRequestHeadersFactory.SIGNATURE_HEADER)))unsigned.incrementAndGet();
                boolean active=exchange.getRequestURI().getPath().equals("/internal/api/bot/tickets/active");
                if(active&&marker!=null)exchange.getResponseHeaders().add("X-Iguana-Active-Lookup-Version",marker);
                var bytes=(active?body:"{\"userId\":1001,\"ticketId\":\" T-active \",\"status\":\"open\",\"channelId\":55,\"questionnaireVersion\":4,\"followupContextVersion\":1}").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(active?status:200,bytes.length);try(var out=exchange.getResponseBody()){out.write(bytes);}
            }catch(Exception ex){throw new java.io.IOException(ex);}finally{exchange.close();}
        });server.start();var panel=new IntegrationPanelApiProperties();panel.setBaseUrl("http://127.0.0.1:"+server.getAddress().getPort());panel.setToken("fixture-token");panel.setSignatureSecret("fixture-signature");
        var reads=new PanelTicketReadClient(panel,mapper,new PanelApiRequestHeadersFactory(panel));
        tickets=spy(new TicketService(localTickets,mock(TicketMessageRepository.class),mock(PendingFeedbackRequestRepository.class),mock(TicketSpanRepository.class),localActive,
            mock(ChatHistoryRepository.class),mock(ChatHistoryService.class),mock(FeedbackRepository.class),mock(AutoCloseFollowUpTaskSupport.class),mock(UiEventOutboxService.class),mock(TicketAttributeService.class),inbound,
            new BotIntegrationTransportMode(new MockEnvironment().withProperty("app.integration.transport.mode","rabbitmq")),creation,reads,writes));
        channel.setId(55L);channel.setPlatform("max");channel.setSupportChatId("unverified-support");
        var properties=new MaxBotProperties();properties.setEnabled(true);properties.setChannelId(55L);properties.setWebhookSecret("fixture-secret");
        var channels=mock(ChannelService.class);when(channels.resolveConfiguredChannel(55L,"","MAX","max")).thenReturn(channel);
        var blacklist=mock(BlacklistService.class);when(blacklist.resolveStatus(eq(1001L),anyString(),any(),eq("2002"))).thenReturn(new BlacklistService.ResolvedBlacklistStatus(null,new BlacklistService.BlacklistStatus(false,false)));
        var ingress=mock(BotIngressCoordinationService.class);when(ingress.tryAcquireOrRenew("max",55L)).thenReturn(true);
        var coordination=new BotIngressCoordinationProperties();coordination.setMode("direct");ObjectProvider<StringRedisTemplate> redis=mock(ObjectProvider.class);
        sessions=spy(new BotSessionStoreService(coordination,mapper,redis));guard=spy(new BotWebhookDeliveryGuardService(coordination,redis));
        when(messages.sendToUser(eq(channel),eq(1001L),anyString())).thenReturn(true);when(api.sendQuestionnaireChoices(eq(1001L),any(),anyString(),anyInt())).thenReturn(true);
        when(runtime.findChannelConfig(channel)).thenThrow(new ScopedRuntimeConfigUnavailableException("fixture scoped catalog"));
        when(runtime.questionnaireChoices(channel)).thenReturn(catalog(false));
        controller=new MaxWebhookController(properties,blacklist,channels,tickets,mock(ChatHistoryService.class),messages,feedback,settings,ingress,guard,sessions,runtime,attachments,api,mock(MaxClientAvatarSyncService.class),mapper);
    }
    @AfterEach void stop(){server.stop(0);}
    ChoiceCatalog catalog(boolean general){
        var business=new Choice("BUSINESS",7L,"test","Test","q-test","a".repeat(64),SelectedQuestionnaireOptions.resourceSha256(Map.of("businessId",7L,"locations",List.of()),Map.of("businessId",7L,"groups",Map.of())));
        var noBusiness=new Choice("GENERAL",(Long)null,"GENERAL","Без бизнеса","q-general","d".repeat(64),SelectedQuestionnaireOptions.resourceSha256(Map.of(),Map.of()));
        return new ChoiceCatalog(4,55L,"c".repeat(64),general?List.of(business,noBusiness):List.of(business));
    }
    void restore(String scope){
        if(scope.equals("ABSENT"))return;boolean general=scope.equals("GENERAL");var catalog=catalog(general);var choice=catalog.choices().get(general?1:0);
        var template=Map.of("id",choice.questionTemplateId(),"question_flow",List.of(Map.of("id","free","type","text","text","Original question")));
        var selected=new SelectedQuestionnaire(4,55L,scope,"USER_SELECTION",general?null:7L,choice.questionTemplateId(),choice.templateSha256(),"c".repeat(64),Map.of("question_templates",List.of(template)),
            general?Map.of():Map.of("businessId",7L,"locations",List.of()),general?Map.of():Map.of("businessId",7L,"groups",Map.of()));
        var question=new QuestionFlowItemDto();question.setId("free");question.setType("text");question.setText("Original question");
        var session=MaxConversationSession.pending(1001L,2002L,"client","Client",catalog,"start-original").choose(selected,new BotSettingsDto(),List.of(question));
        session.recordAnswer("Original answer");session.acceptEvent("submitted-original");session.prepareSubmission("submitted-original");session.markSubmitted(ticketId);
        sessions.save("max",55L,1001L,session.snapshot());clearInvocations(sessions);
    }
    Optional<String> raw(){return sessions.load("max",55L,1001L,MaxConversationSession.State.class).map(BotSessionStoreService.StoredBotSession::rawPayload);}
    ResponseEntity<Map<String,Object>> send(){return controller.handleUpdate(mapper.valueToTree(Map.of("update_type","message_created","update_id","retry-same-event","message",Map.of("sender",Map.of("user_id",1001,"username","client","name","Client"),"recipient",Map.of("chat_id",2002),"body",Map.of("text","Plain reply")))),"fixture-secret");}
    static Stream<Arguments> failures(){return Stream.of("ABSENT","BUSINESS","GENERAL").flatMap(scope->Stream.of("unauthorized","forbidden","not-found-unproved","conflict","server","unavailable","old-server-found","future-marker","malformed","empty-found","blank-ticket").map(mode->Arguments.of(scope,mode)));}
    @ParameterizedTest @MethodSource("failures") void unavailableReadCannotStartOrRebindAQuestionnaireAndCanRetry(String scope,String mode){
        restore(scope);var before=raw();status=switch(mode){case "unauthorized"->401;case "forbidden"->403;case "not-found-unproved"->404;case "conflict"->409;case "server"->500;case "unavailable"->503;default->200;};
        if(mode.equals("not-found-unproved")||mode.equals("old-server-found"))marker=null;if(mode.equals("future-marker"))marker="2";
        if(mode.equals("malformed"))body="{broken}";if(mode.equals("empty-found"))body="null";if(mode.equals("blank-ticket"))body="{\"ticketId\":\"  \"}";
        assertThatThrownBy(this::send).isInstanceOf(IllegalStateException.class);verify(guard).release(any());verify(guard,never()).markProcessed(any());
        assertThat(raw()).isEqualTo(before);verifyNoInteractions(runtime,settings,attachments,api,messages,feedback,inbound,creation,writes,localActive,localTickets);
        verify(sessions,never()).saveIfUnchanged(anyString(),any(),any(),any(),any());
        status=200;marker="1";body="{\"ticketId\":\" T-active \",\"userIdentity\":\"client\"}";
        assertThat(send().getBody()).containsEntry("ticket_id",ticketId);assertThat(send().getBody()).containsEntry("duplicate",true);
        verify(inbound).publish(argThat(command->ticketId.equals(command.ticketId())&&"Plain reply".equals(command.text())));assertThat(raw()).isEqualTo(before);assertThat(unsigned).hasValue(0);
        verifyNoInteractions(runtime,settings,attachments,api,messages,feedback,creation,writes,localActive,localTickets);
    }
    @ParameterizedTest @ValueSource(strings={"ABSENT","BUSINESS","GENERAL"}) void completedActiveLookupRetainsCanonicalFollowup(String scope){restore(scope);var before=raw();assertThat(send().getBody()).containsEntry("ticket_id",ticketId);verify(inbound).publish(any());assertThat(raw()).isEqualTo(before);assertThat(unsigned).hasValue(0);verifyNoInteractions(runtime,settings,api,messages,creation,writes,localActive,localTickets);}
    @Test void provedAbsenceCanStillStartAnExplicitQuestionnaire(){status=404;assertThat(send().getBody()).containsEntry("session_started",true);assertThat(raw()).isPresent();verify(runtime).questionnaireChoices(channel);verify(api).sendQuestionnaireChoices(eq(1001L),any(),anyString(),eq(0));verifyNoInteractions(inbound,creation,writes,localActive,localTickets);assertThat(unsigned).hasValue(0);}
}
