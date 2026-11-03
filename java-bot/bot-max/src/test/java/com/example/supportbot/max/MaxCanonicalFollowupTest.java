package com.example.supportbot.max;

import com.example.supportbot.config.MaxBotProperties;
import com.example.supportbot.entity.*;
import com.example.supportbot.service.*;
import com.example.supportbot.settings.BotSettingsService;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.util.*;
import java.util.stream.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** An absent session cannot turn a frozen v4 root into a legacy download or support-chat send. */
class MaxCanonicalFollowupTest {
    final ObjectMapper mapper=Jackson2ObjectMapperBuilder.json().build();
    final Channel channel=new Channel();
    final TicketService tickets=mock(TicketService.class);
    final MessagingService messages=mock(MessagingService.class);
    final AttachmentService attachments=mock(AttachmentService.class);
    final MaxApiClient api=mock(MaxApiClient.class);
    final RuntimeConfigService runtime=mock(RuntimeConfigService.class);
    final BotSessionStoreService sessions=mock(BotSessionStoreService.class);
    final BotWebhookDeliveryGuardService guard=mock(BotWebhookDeliveryGuardService.class);
    final BotSettingsService settings=mock(BotSettingsService.class);
    final FeedbackService feedback=mock(FeedbackService.class);
    final MaxBotProperties properties=new MaxBotProperties();
    MaxWebhookController controller;
    BotWebhookDeliveryGuardService.DeliveryClaim claim;
    final String ticketId=" ticket-exact ";
    @BeforeEach void setup()throws Exception{
        channel.setId(55L);channel.setPlatform("max");channel.setSupportChatId("unverified-support");
        properties.setEnabled(true);properties.setChannelId(55L);properties.setWebhookSecret("fixture-secret");
        var channels=mock(ChannelService.class);when(channels.resolveConfiguredChannel(55L,"","MAX","max")).thenReturn(channel);
        var blacklist=mock(BlacklistService.class);when(blacklist.resolveStatus(eq(1001L),anyString(),any(),eq("2002")))
            .thenReturn(new BlacklistService.ResolvedBlacklistStatus(null,new BlacklistService.BlacklistStatus(false,false)));
        var ingress=mock(BotIngressCoordinationService.class);when(ingress.tryAcquireOrRenew("max",55L)).thenReturn(true);
        claim=new BotWebhookDeliveryGuardService.DeliveryClaim("event","nonce",BotWebhookDeliveryGuardService.ClaimStatus.ACQUIRED);
        when(guard.tryClaim(eq("max"),eq(55L),anyString())).thenReturn(claim);
        when(messages.sendToUser(eq(channel),eq(1001L),anyString())).thenReturn(true);
        when(runtime.findChannelConfig(any())).thenThrow(new PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException("fixture must not bootstrap"));
        when(runtime.questionnaireChoices(channel)).thenReturn(new PanelRuntimeConfigClient.ChoiceCatalog(1,55L,"c".repeat(64),List.of(new PanelRuntimeConfigClient.Choice("BUSINESS",7L,"a","A","q-a","a".repeat(64)))));
        var active=new TicketActive();active.setTicketId(ticketId);when(tickets.findActiveTicketForUser(eq(1001L),anyString(),eq(55L))).thenReturn(Optional.of(active));
        when(api.downloadAttachment(anyString())).thenAnswer(call->new MaxApiClient.DownloadedAttachment(new ByteArrayInputStream(new byte[]{1,2,3}),"application/pdf","private.pdf",3));
        when(attachments.store(anyString(),anyString(),any())).thenReturn(new AttachmentService.StoredAttachment("legacy/private.pdf","fixture",null));
        rebuild(channels,blacklist,ingress);
    }
    void rebuild(ChannelService channels,BlacklistService blacklist,BotIngressCoordinationService ingress){
        controller=new MaxWebhookController(properties,blacklist,channels,tickets,mock(ChatHistoryService.class),messages,feedback,settings,ingress,guard,sessions,runtime,attachments,api,mock(MaxClientAvatarSyncService.class),mapper);
    }
    TicketService.TicketWithUser root(Long user,String id,String status,Long channel,Integer version,Integer context)throws Exception{
        for(var constructor:TicketService.TicketWithUser.class.getConstructors())if(constructor.getParameterCount()==6)
            return (TicketService.TicketWithUser)constructor.newInstance(user,id,status,channel,version,context);
        return new TicketService.TicketWithUser(user,id,status);
    }
    ResponseEntity<Map<String,Object>> send(String kind){
        var body=new LinkedHashMap<String,Object>();body.put("text",kind.equals("empty")?"":kind.equals("text")?"Plain reply":"Private caption");
        if(!kind.equals("text"))body.put("attachments",List.of(Map.of("type","file","name","private.pdf","payload",
            kind.startsWith("opaque")?Map.of("token","unresolvable-token"):Map.of("url","https://example.invalid/private.pdf"))));
        var message=new LinkedHashMap<String,Object>();message.put("sender",Map.of("user_id",1001,"username","client","name","Client"));message.put("recipient",Map.of("chat_id",2002));
        if(kind.contains("forwarded")){message.put("body",Map.of());message.put("link",Map.of("type","forward","message",Map.of("body",body)));}else message.put("body",body);
        return controller.handleUpdate(mapper.valueToTree(Map.of("update_type","message_created","update_id","event-"+kind,"message",message)),"fixture-secret");
    }
    static Stream<Arguments> denied(){return Stream.of("missing","old-server","wrong-owner","wrong-channel","wrong-ticket","future-context","future-version","invalid-version","unknown-status","null-status")
        .flatMap(mode->Stream.of("text","caption","empty","forwarded","opaque","opaque-forwarded").map(kind->Arguments.of(mode,kind)));}
    @ParameterizedTest @MethodSource("denied") void unknownOrMismatchedRootRetriesBeforeLegacyEffects(String mode,String kind)throws Exception{
        var value=root(mode.equals("wrong-owner")?1002L:1001L,mode.equals("wrong-ticket")?"ticket-exact":ticketId,
            mode.equals("unknown-status")?"other":mode.equals("null-status")?null:"open",mode.equals("wrong-channel")?56L:55L,
            mode.equals("future-version")?5:mode.equals("invalid-version")?0:4,mode.equals("old-server")?null:mode.equals("future-context")?2:1);
        when(tickets.findByTicketId(ticketId)).thenReturn(mode.equals("missing")?Optional.empty():Optional.of(value));
        assertThat(send(kind).getStatusCode().value()).isEqualTo(503);
        verify(guard).release(claim);verify(guard,never()).markProcessed(any());verify(tickets,never()).clearTicketActivity(any());
        verify(tickets,never()).recordActiveClientMessage(any());verify(tickets,never()).createConversationTicket(any());
        verifyNoInteractions(attachments,api,messages,runtime,settings,feedback);verify(sessions,never()).delete(anyString(),any(),any());
    }
    @ParameterizedTest @ValueSource(strings={"caption","empty","forwarded","opaque","opaque-forwarded"})
    void canonicalV4WithoutSessionRejectsMediaWithoutDownloadOrSharedNotification(String kind)throws Exception{
        when(tickets.findByTicketId(ticketId)).thenReturn(Optional.of(root(1001L,ticketId,"open",55L,4,1)));
        var response=send(kind);assertThat(response.getStatusCode().value()).isEqualTo(200);assertThat(response.getBody()).containsEntry("questionnaire_media_unsupported",true);
        verify(messages).sendToUser(eq(channel),eq(1001L),contains("Вложение не сохранено"));verify(messages,never()).sendToSupportChat(any(),anyString());
        verifyNoInteractions(attachments,api,runtime,settings,feedback);verify(tickets,never()).recordActiveClientMessage(any());verify(tickets,never()).clearTicketActivity(any());
        verify(guard).markProcessed(claim);
    }
    @ParameterizedTest @ValueSource(strings={"open","pending","in_progress"})
    void canonicalV4TextIsQueuedWithoutLegacySupportChatEvenAfterSettingsRebind(String status)throws Exception{
        when(tickets.findByTicketId(ticketId)).thenReturn(Optional.of(root(1001L,ticketId,status,55L,4,1)));
        assertThat(send("text").getStatusCode().value()).isEqualTo(200);verify(tickets).recordActiveClientMessage(argThat(c->ticketId.equals(c.ticketId())&&c.userId()==1001L&&"Plain reply".equals(c.text())));
        verify(messages,never()).sendToSupportChat(any(),anyString());verifyNoInteractions(runtime,settings,feedback,attachments,api);
    }
    static Stream<Arguments> legacy(){return Stream.<Integer>of(null,1,2,3).flatMap(version->Stream.of("text","caption","empty","forwarded").map(kind->Arguments.of(version,kind)));}
    @ParameterizedTest @MethodSource("legacy") void positivelyVerifiedLegacyContractIsPreserved(Integer version,String kind)throws Exception{
        when(tickets.findByTicketId(ticketId)).thenReturn(Optional.of(root(1001L,ticketId,"open",55L,version,1)));
        assertThat(send(kind).getStatusCode().value()).isEqualTo(200);verify(tickets).recordActiveClientMessage(any());verify(messages).sendToSupportChat(eq(channel),contains(ticketId));
        if(!kind.equals("text")){verify(api).downloadAttachment("https://example.invalid/private.pdf");verify(attachments).store(anyString(),anyString(),any());}
        verifyNoInteractions(runtime,settings,feedback);
    }
}
