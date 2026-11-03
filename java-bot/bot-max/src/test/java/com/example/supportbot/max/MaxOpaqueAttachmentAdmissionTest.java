package com.example.supportbot.max;

import com.example.supportbot.config.*;
import com.example.supportbot.entity.*;
import com.example.supportbot.service.*;
import com.example.supportbot.service.PanelRuntimeConfigClient.*;
import com.example.supportbot.settings.BotSettingsService;
import com.example.supportbot.settings.dto.*;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** A nameless, URL-less file is still a message; only confirmed rejection can consume it. */
class MaxOpaqueAttachmentAdmissionTest {
    final ObjectMapper mapper=Jackson2ObjectMapperBuilder.json().build();
    final Channel channel=new Channel();
    final TicketService tickets=mock(TicketService.class);
    final MessagingService messages=mock(MessagingService.class);
    final AttachmentService attachments=mock(AttachmentService.class);
    final MaxApiClient api=mock(MaxApiClient.class);
    final RuntimeConfigService runtime=mock(RuntimeConfigService.class);
    final BotSettingsService settings=mock(BotSettingsService.class);
    final FeedbackService feedback=mock(FeedbackService.class);
    BotSessionStoreService sessions;
    BotWebhookDeliveryGuardService guard;
    MaxWebhookController controller;
    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        channel.setId(55L);channel.setPlatform("max");channel.setSupportChatId("unverified-shared-chat");
        var properties=new MaxBotProperties();properties.setEnabled(true);properties.setChannelId(55L);properties.setWebhookSecret("fixture-secret");
        var channels=mock(ChannelService.class);when(channels.resolveConfiguredChannel(55L,"","MAX","max")).thenReturn(channel);
        var blacklist=mock(BlacklistService.class);when(blacklist.resolveStatus(eq(1001L),anyString(),any(),eq("2002")))
            .thenReturn(new BlacklistService.ResolvedBlacklistStatus(null,new BlacklistService.BlacklistStatus(false,false)));
        var ingress=mock(BotIngressCoordinationService.class);when(ingress.tryAcquireOrRenew("max",55L)).thenReturn(true);
        var coordination=new BotIngressCoordinationProperties();coordination.setMode("direct");
        ObjectProvider<StringRedisTemplate> redis=mock(ObjectProvider.class);
        sessions=spy(new BotSessionStoreService(coordination,mapper,redis));guard=spy(new BotWebhookDeliveryGuardService(coordination,redis));
        when(messages.sendToUser(eq(channel),eq(1001L),anyString())).thenReturn(true);
        var active=new TicketActive();active.setTicketId("T-opaque");
        when(tickets.findActiveTicketForUser(eq(1001L),anyString(),eq(55L))).thenReturn(Optional.of(active));
        when(tickets.findByTicketId("T-opaque")).thenReturn(Optional.of(new TicketService.TicketWithUser(1001L,"T-opaque","open",55L,4,1)));
        controller=new MaxWebhookController(properties,blacklist,channels,tickets,mock(ChatHistoryService.class),messages,feedback,settings,
            ingress,guard,sessions,runtime,attachments,api,mock(MaxClientAvatarSyncService.class),mapper);
    }
    void saveSelected(String scope) {
        if(scope.equals("ABSENT"))return;
        Long business=scope.equals("BUSINESS")?7L:null;
        Map<String,Object> tree=Map.of("businessId",7L,"locations",List.of());
        Map<String,Object> presets=Map.of("businessId",7L,"groups",Map.of());
        var businessChoice=new Choice("BUSINESS",7L,"test","Test","q-opaque","a".repeat(64),SelectedQuestionnaireOptions.resourceSha256(tree,presets));
        var generalChoice=new Choice("GENERAL",(Long)null,"GENERAL","Без бизнеса","q-general","d".repeat(64),SelectedQuestionnaireOptions.resourceSha256(Map.of(),Map.of()));
        var choice=business!=null?businessChoice:generalChoice;
        var catalog=new ChoiceCatalog(4,55L,"c".repeat(64),business!=null?List.of(businessChoice):List.of(businessChoice,generalChoice));
        var template=Map.of("id",choice.questionTemplateId(),"question_flow",List.of(Map.of("id","free","type","text","text","Original question")));
        var selected=new SelectedQuestionnaire(4,55L,scope,"USER_SELECTION",business,
            choice.questionTemplateId(),choice.templateSha256(),"c".repeat(64),Map.of("question_templates",List.of(template)),business!=null?tree:Map.of(),business!=null?presets:Map.of());
        var question=new QuestionFlowItemDto();question.setId("free");question.setType("text");question.setText("Original question");
        var session=MaxConversationSession.pending(1001L,2002L,"client","Client",catalog,"start-event")
            .choose(selected,new BotSettingsDto(),List.of(question));
        sessions.save("max",55L,1001L,session.snapshot());clearInvocations(sessions);
    }
    Optional<String> saved() {return sessions.load("max",55L,1001L,MaxConversationSession.State.class).map(BotSessionStoreService.StoredBotSession::rawPayload);}
    JsonNode update(String location,boolean forwarded,boolean declared,boolean sender) {
        var content=mapper.createObjectNode();content.putObject("body").put("text"," \t ");
        var container=location.startsWith("body.")?(com.fasterxml.jackson.databind.node.ObjectNode)content.get("body"):content;
        String field=location.substring(location.lastIndexOf('.')+1);
        var list=container.putArray(field);
        if(declared)list.addObject().put("type","file").putObject("payload").put("token","opaque-without-name-or-url");
        var message=mapper.createObjectNode();if(sender)message.putObject("sender").put("user_id",1001).put("username","client").put("name","Client");
        message.putObject("recipient").put("chat_id",2002);
        if(forwarded){message.putObject("body");message.putObject("link").put("type","forward").set("message",content);}
        else content.fields().forEachRemaining(entry->message.set(entry.getKey(),entry.getValue()));
        var update=mapper.createObjectNode();update.put("update_type","message_created").put("update_id","opaque-event").set("message",message);return update;
    }
    ResponseEntity<Map<String,Object>> send(JsonNode update) {return controller.handleUpdate(update,"fixture-secret");}
    static Stream<Arguments> layouts() {return Stream.of("attachments","body.attachments","body.media","body.files")
        .flatMap(location->Stream.of(false,true).map(forwarded->Arguments.of(location,forwarded)));}
    static Stream<Arguments> contextsAndLayouts() {return Stream.of("ABSENT","BUSINESS","GENERAL").flatMap(scope->Stream.of("attachments","body.attachments","body.media","body.files")
        .flatMap(location->Stream.of(false,true).map(forwarded->Arguments.of(scope,location,forwarded))));}
    void noWriteOrDownload() {
        verify(tickets,never()).recordActiveClientMessage(any());verify(tickets,never()).createConversationTicket(any());verify(tickets,never()).clearTicketActivity(any());
        verify(messages,never()).sendToSupportChat(any(),anyString());verifyNoInteractions(attachments,api,runtime,settings,feedback);
        verify(sessions,never()).saveIfUnchanged(anyString(),any(),any(),any(),any());verify(sessions,never()).deleteIfUnchanged(anyString(),any(),any(),any());
    }
    @ParameterizedTest @MethodSource("contextsAndLayouts")
    void namelessMediaWithoutCaptionMustBeExplicitlyRejected(String scope,String location,boolean forwarded) {
        saveSelected(scope);var before=saved();var update=update(location,forwarded,true,true);
        assertThat(MaxInboundPayloadSupport.resolveInboundPayload(update.path("message"),null).attachments()).isEmpty();
        assertThat(MaxInboundPayloadSupport.hasDeclaredAttachments(update.path("message"))).isTrue();
        var response=send(update);assertThat(response.getBody()).containsEntry("questionnaire_media_unsupported",true).doesNotContainKey("ignored");
        verify(messages).sendToUser(eq(channel),eq(1001L),contains("Вложение не сохранено"));verify(guard).markProcessed(any());
        assertThat(saved()).isEqualTo(before);noWriteOrDownload();
    }
    @ParameterizedTest @MethodSource("layouts")
    void unresolvedRootReleasesTheSameOpaqueEventForRetry(String location,boolean forwarded) {
        when(tickets.findByTicketId("T-opaque")).thenReturn(Optional.empty());var update=update(location,forwarded,true,true);
        for(int attempt=0;attempt<2;attempt++){
            var response=send(update);assertThat(response.getStatusCode().value()).isEqualTo(503);
            assertThat(response.getBody()).containsEntry("error","canonical-ticket-not-ready").containsEntry("retryable",true);
        }
        verify(guard,times(2)).release(any());verify(guard,never()).markProcessed(any());verifyNoInteractions(messages);noWriteOrDownload();
    }
    static Stream<Arguments> warningFailures(){return Stream.of("ABSENT","BUSINESS","GENERAL").flatMap(scope->Stream.of(false,true).map(throwsError->Arguments.of(scope,throwsError)));}
    @ParameterizedTest @MethodSource("warningFailures")
    void unconfirmedWarningDoesNotConsumeFileAndSameEventCanRetry(String scope,boolean throwsError) {
        saveSelected(scope);var before=saved();var update=update("body.attachments",true,true,true);
        if(throwsError)when(messages.sendToUser(eq(channel),eq(1001L),contains("Вложение не сохранено"))).thenThrow(new IllegalStateException("provider unavailable"));
        else when(messages.sendToUser(eq(channel),eq(1001L),contains("Вложение не сохранено"))).thenReturn(false);
        assertThatThrownBy(()->send(update)).isInstanceOf(IllegalStateException.class);
        verify(guard).release(any());verify(guard,never()).markProcessed(any());assertThat(saved()).isEqualTo(before);noWriteOrDownload();
        doReturn(true).when(messages).sendToUser(eq(channel),eq(1001L),contains("Вложение не сохранено"));
        assertThat(send(update).getBody()).containsEntry("questionnaire_media_unsupported",true);
        assertThat(send(update).getBody()).containsEntry("duplicate",true);
        verify(messages,times(2)).sendToUser(eq(channel),eq(1001L),contains("Вложение не сохранено"));verify(guard).markProcessed(any());
        assertThat(saved()).isEqualTo(before);noWriteOrDownload();
    }
    @ParameterizedTest @ValueSource(strings={"ABSENT","BUSINESS","GENERAL"})
    void genuinelyEmptyMessageStillUsesTheExistingIgnoreContract(String scope) {
        saveSelected(scope);var before=saved();assertThat(send(update("body.attachments",false,false,true)).getBody()).containsEntry("ignored","missing-user-or-text");
        verifyNoInteractions(messages,tickets);assertThat(saved()).isEqualTo(before);noWriteOrDownload();
    }
    @Test void declaredFileWithoutSenderStillUsesTheExistingIgnoreContract() {
        assertThat(send(update("body.media",false,true,false)).getBody()).containsEntry("ignored","missing-user-or-text");verifyNoInteractions(messages,tickets);noWriteOrDownload();
    }
    @ParameterizedTest @NullSource @ValueSource(ints={1,2,3})
    void unresolvableLegacyFileIsNotPublishedAsAPlaceholder(Integer version) {
        when(tickets.findByTicketId("T-opaque")).thenReturn(Optional.of(new TicketService.TicketWithUser(1001L,"T-opaque","open",55L,version,1)));
        assertThat(send(update("body.files",false,true,true)).getBody()).containsEntry("attachment_unresolved",true);
        verify(messages).sendToUser(eq(channel),eq(1001L),contains("Вложение не сохранено"));noWriteOrDownload();
    }
    @Test void absentActiveRootDoesNotStartAQuestionnaireForAnUnresolvableFile() {
        when(tickets.findActiveTicketForUser(eq(1001L),anyString(),eq(55L))).thenReturn(Optional.empty());
        assertThat(send(update("body.files",true,true,true)).getBody()).containsEntry("attachment_unresolved",true);
        verify(messages).sendToUser(eq(channel),eq(1001L),contains("Вложение не сохранено"));assertThat(saved()).isEmpty();noWriteOrDownload();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void unconfirmedGenericWarningAlsoLeavesTheEventRetryable(boolean active) {
        when(tickets.findByTicketId("T-opaque")).thenReturn(Optional.of(new TicketService.TicketWithUser(1001L,"T-opaque","open",55L,3,1)));
        if(!active)when(tickets.findActiveTicketForUser(eq(1001L),anyString(),eq(55L))).thenReturn(Optional.empty());
        var update=update("body.attachments",false,true,true);
        when(messages.sendToUser(eq(channel),eq(1001L),contains("Вложение не сохранено"))).thenReturn(false);
        assertThatThrownBy(()->send(update)).isInstanceOf(IllegalStateException.class);verify(guard).release(any());verify(guard,never()).markProcessed(any());
        doReturn(true).when(messages).sendToUser(eq(channel),eq(1001L),contains("Вложение не сохранено"));
        assertThat(send(update).getBody()).containsEntry("attachment_unresolved",true);assertThat(send(update).getBody()).containsEntry("duplicate",true);
        verify(messages,times(2)).sendToUser(eq(channel),eq(1001L),contains("Вложение не сохранено"));noWriteOrDownload();
    }
    @Test void ordinaryTextStillReachesCanonicalFollowup() {
        var update=update("body.attachments",false,false,true);((com.fasterxml.jackson.databind.node.ObjectNode)update.at("/message/body")).put("text","Plain text");
        assertThat(send(update).getBody()).containsEntry("ticket_id","T-opaque");
        verify(tickets).recordActiveClientMessage(argThat(command->"Plain text".equals(command.text())&&"T-opaque".equals(command.ticketId())));
        verifyNoInteractions(messages,attachments,api,runtime,settings,feedback);
    }
}
