package com.example.supportbot.vk;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.example.supportbot.config.*;
import com.example.supportbot.entity.*;
import com.example.supportbot.service.*;
import com.example.supportbot.service.PanelRuntimeConfigClient.*;
import com.example.supportbot.settings.BotSettingsService;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.actions.Messages;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.messages.Message;
import com.vk.api.sdk.objects.messages.MessageAttachment;
import com.vk.api.sdk.queries.messages.MessagesSendQueryWithDeprecated;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.util.ReflectionTestUtils;

/** Real bot and Callback with JSON/CAS store and delivery guard; SDK and panel are doubles. */
class VkQuestionnaireSelectionTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path mediaRoot;
    private boolean mediaEnabled;
    private final java.util.concurrent.atomic.AtomicInteger downloads=new java.util.concurrent.atomic.AtomicInteger();
    private byte[] mediaBytes="VK owned bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private int mediaStatus=200;
    private final ObjectMapper mapper=Jackson2ObjectMapperBuilder.json().build();
    private final Channel channel=new Channel();
    private final RuntimeConfigService runtime=mock(RuntimeConfigService.class);
    private final SharedConfigService local=mock(SharedConfigService.class);
    private final TicketService tickets=mock(TicketService.class);
    private final ChannelService channels=mock(ChannelService.class);
    private final BlacklistService blacklist=mock(BlacklistService.class);
    private final FeedbackService feedback=mock(FeedbackService.class);
    private final AttachmentService attachments=mock(AttachmentService.class);
    private final BotIngressCoordinationService ingress=mock(BotIngressCoordinationService.class);
    private final VkApiClient sdk=mock(VkApiClient.class);
    private final Messages sdkMessages=mock(Messages.class);
    private final MessagesSendQueryWithDeprecated send=mock(MessagesSendQueryWithDeprecated.class,RETURNS_SELF);
    private BotSessionStoreService store;
    private BotWebhookDeliveryGuardService guard;
    private BotSettingsService settings;
    private VkBotProperties properties;
    private VkSupportBot bot;
    private VkCallbackController callback;
    private ChoiceCatalog catalog;
    private SelectedQuestionnaire a,b;
    @BeforeEach void setup() throws Exception {
        channel.setId(55L);channel.setPlatform("vk");channel.setActive(true);
        properties=new VkBotProperties();properties.setEnabled(true);properties.setWebhookEnabled(true);
        properties.setGroupId(101);properties.setChannelId(55L);properties.setToken("test-vk-token");
        var coordination=new BotIngressCoordinationProperties();coordination.setMode("direct");
        @SuppressWarnings("unchecked") ObjectProvider<StringRedisTemplate> redis=mock(ObjectProvider.class);
        store=spy(new BotSessionStoreService(coordination,mapper,redis));
        guard=spy(new BotWebhookDeliveryGuardService(coordination,redis));
        settings=new BotSettingsService(mapper,local,runtime);
        catalog=new ChoiceCatalog(1,55,"c".repeat(64),List.of(
                new Choice("BUSINESS",7,"a","Business A","q-a","a".repeat(64)),
                new Choice("BUSINESS",8,"b","Business B","q-b","b".repeat(64))));
        a=selected(7,"q-a","a");b=selected(8,"q-b","b");
        when(channels.ensurePublicIdForToken("test-vk-token","VK","vk")).thenReturn(channel);
        when(blacklist.getStatus(1001L)).thenReturn(new BlacklistService.BlacklistStatus(false,false));
        when(runtime.findChannelConfig(channel)).thenThrow(unavailable());
        when(runtime.questionnaireChoices(channel)).thenReturn(catalog);
        when(runtime.selectQuestionnaire(channel,catalog,7L)).thenReturn(a);
        when(runtime.selectQuestionnaire(channel,catalog,8L)).thenReturn(b);
        when(tickets.createConversationTicket(any())).thenReturn(new TicketService.TicketCreationResult("T-VK",null,"queued"));
        when(tickets.awaitClientTicketNumber(any())).thenReturn(Optional.empty());
        when(ingress.tryAcquireOrRenew("vk",55L)).thenReturn(true);
        when(sdk.messages()).thenReturn(sdkMessages);
        when(sdkMessages.sendDeprecated(any(GroupActor.class))).thenReturn(send);
        when(send.execute()).thenReturn(123);
        rebuild();
    }
    private SelectedQuestionnaire selected(long owner,String id,String hash) {
        var template=Map.of("id",id,"name",id,"question_flow",List.of(
                Map.of("id","free-second","type","text","order",20,"text",id+" second"),
                Map.of("id","free-first","type","text","order",10,"text",id+" first")));
        return new SelectedQuestionnaire(1,55,"BUSINESS","USER_SELECTION",owner,id,hash.repeat(64),"c".repeat(64),
                Map.of("active_template_id",id,"question_templates",List.of(template)),Map.of(),Map.of());
    }
    private void rebuild() {
        bot=new VkSupportBot(properties,blacklist,mock(UnblockRequestService.class),attachments,channels,settings,tickets,
                mock(ChatHistoryService.class),feedback,ingress,store,guard,runtime,mapper);
        ReflectionTestUtils.setField(bot,"vkClient",sdk);
        var http=mock(java.net.http.HttpClient.class);
        ReflectionTestUtils.setField(bot,"httpClient",http);
        if(mediaEnabled) {
            try {
                when(http.send(any(java.net.http.HttpRequest.class),org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<java.io.InputStream>>any())).thenAnswer(call->{
                    java.net.http.HttpRequest request=call.getArgument(0);
                    if("api.vk.com".equals(request.uri().getHost())) {
                        @SuppressWarnings("unchecked") var profile=(java.net.http.HttpResponse<String>)mock(java.net.http.HttpResponse.class);
                        when(profile.statusCode()).thenReturn(200);when(profile.body()).thenReturn("{\"response\":[]}");return profile;
                    }
                    downloads.incrementAndGet();
                    @SuppressWarnings("unchecked") var response=(java.net.http.HttpResponse<java.io.InputStream>)mock(java.net.http.HttpResponse.class);
                    when(response.statusCode()).thenReturn(mediaStatus);
                    when(response.headers()).thenReturn(java.net.http.HttpHeaders.of(Map.of(),(k,v)->true));
                    when(response.body()).thenReturn(new java.io.ByteArrayInputStream(mediaBytes));return response;
                });
            } catch(Exception ex) {throw new IllegalStateException(ex);}
        }
        callback=new VkCallbackController(bot,properties,ingress,guard);
    }
    private void resetDeliveryCache() {
        var coordination=new BotIngressCoordinationProperties();coordination.setMode("direct");
        @SuppressWarnings("unchecked") ObjectProvider<StringRedisTemplate> redis=mock(ObjectProvider.class);
        guard=spy(new BotWebhookDeliveryGuardService(coordination,redis));
    }
    private Message message(int id,String text,String payload) {
        return new Message().setFromId(1001L).setPeerId(1001L).setId(id).setConversationMessageId(id).setText(text).setPayload(payload);
    }
    private void receive(int id,String text,String payload) {bot.handleIncomingMessage(message(id,text,payload));}
    private void receive(int id,String text) {receive(id,text,null);}
    private JsonNode state() {return store.load("vk-questionnaire-v1",55L,1001L,JsonNode.class)
            .or(()->store.load("vk",properties.getChannelId(),1001L,JsonNode.class)).orElseThrow().payload();}
    private String choice(long id) {return VkQuestionnaireChoiceSupport.payload(state().at("/selection/nonce").asText(),"business",id);}
    private void chooseA() {receive(1,"/start");receive(2,"Business A",choice(7));}
    private void first() {chooseA();receive(3,"One");}
    private void complete() {first();receive(4,"Two");}
    private ScopedRuntimeConfigUnavailableException unavailable() {return new ScopedRuntimeConfigUnavailableException("test unavailable");}
    private void webhook(String envelope,int id,String text) {
        callback.handle(101,Map.of("type","message_new","event_id",envelope,"object",Map.of("message",
                Map.of("id",id,"conversation_message_id",id,"peer_id",1001L,"from_id",1001L,"date",12345,"text",text))));
    }
    @Test void menuIsSavedBeforeNativeKeyboardAndNoGlobalSettingsOrFeedbackAreRead() {
        doAnswer(call->{assertThat(state().at("/selection/nonce").asText()).isNotEmpty();return call.getMock();}).when(send).keyboard(any());
        receive(1,"/start");assertThat(state().path("flow")).isEmpty();assertThat(state().path("scope").isNull()).isTrue();
        verify(send).keyboard(any());verifyNoInteractions(local,feedback);
        verify(tickets,never()).createConversationTicket(any());
    }
    @Test void operatorChatIdIsNotTheCanonicalChannelOrSelectedSessionNamespace() {
        properties.setChannelId(900077L);rebuild();chooseA();
        assertThat(state().at("/selection/catalog/channelId").asLong()).isEqualTo(55);
        assertThat(store.load("vk-questionnaire-v1",55L,1001L,JsonNode.class)).isPresent();
        assertThat(store.load("vk-questionnaire-v1",900077L,1001L,JsonNode.class)).isEmpty();
        verify(guard).tryClaim("vk",55L,"vk-message:1001:1");
        verify(store,never()).saveIfUnchanged(eq("vk"),eq(900077L),eq(1001L),any(),any());
    }
    @Test void differentCanonicalChannelsSharingOperatorChatAndClientKeepSeparateMenus() {
        receive(1,"/start");String original=choice(7);
        var second=new Channel();second.setId(66L);second.setPlatform("vk");second.setActive(true);
        when(channels.ensurePublicIdForToken("test-second-token","VK","vk")).thenReturn(second);
        when(runtime.findChannelConfig(second)).thenThrow(unavailable());
        when(runtime.questionnaireChoices(second)).thenReturn(new ChoiceCatalog(1,66,"d".repeat(64),catalog.choices()));
        properties.setToken("test-second-token");rebuild();receive(1,"/start");
        var other=store.load("vk-questionnaire-v1",66L,1001L,JsonNode.class).orElseThrow().payload();
        assertThat(other.at("/selection/catalog/channelId").asLong()).isEqualTo(66);
        assertThat(other.path("scope").isNull()).isTrue();
        properties.setToken("test-vk-token");rebuild();receive(2,"Business A",original);
        assertThat(state().at("/scope/businessId").asLong()).isEqualTo(7);
        assertThat(store.load("vk-questionnaire-v1",66L,1001L,JsonNode.class).orElseThrow().payload().path("scope").isNull()).isTrue();
    }
    @Test void restartRestoresExactMenuChosenOrderBootstrapAndAnswers() {
        receive(1,"Initial problem");String nonce=state().at("/selection/nonce").asText(),code=choice(7);
        rebuild();receive(2,"Business A",code);rebuild();receive(3,"One");
        assertThat(state().at("/selection/nonce").asText()).isEqualTo(nonce);
        assertThat(state().at("/scope/businessId").asLong()).isEqualTo(7);
        assertThat(state().at("/flow/0/id").asText()).isEqualTo("free-first");
        assertThat(state().path("bootstrapProblemText").asText()).isEqualTo("Initial problem");
        assertThat(state().at("/answers/free-first").asText()).isEqualTo("One");
        verify(send).message(startsWith("q-a first"));verify(send).message(startsWith("q-a second"));verifyNoInteractions(local,feedback);
    }
    @Test void arbitraryQuestionnaireCreatesOnlyFrozenOwnerAndDoesNotSendRawSummaryOrRating() {
        complete();var commands=org.mockito.ArgumentCaptor.forClass(ConversationTicketCreationCommand.class);
        verify(tickets).createConversationTicket(commands.capture());var command=commands.getValue();
        assertThat(command.answers()).containsExactlyInAnyOrderEntriesOf(Map.of("free-first","One","free-second","Two"));
        assertThat(command.businessId()).isEqualTo(7);assertThat(command.scopeKind()).isEqualTo("BUSINESS");
        assertThat(command.scopeSelectionSource()).isEqualTo("USER_SELECTION");assertThat(command.questionTemplateId()).isEqualTo("q-a");
        assertThat(command.templateSha256()).isEqualTo("a".repeat(64));assertThat(command.catalogSha256()).isEqualTo("c".repeat(64));
        assertThat(command.providerEventKey()).isEqualTo("vk-message:1001:4");
        assertThat(state().at("/selection/submittedTicketId").asText()).isEqualTo("T-VK");
        verify(send,never()).peerId(55L);verifyNoInteractions(local,attachments,feedback);
    }
    @ParameterizedTest @ValueSource(strings={"label","number","foreign-owner","old-nonce","broken"})
    void invalidMenuInputCannotSelectOrBecomeAnAnswer(String kind) {
        receive(1,"/start");String payload=switch(kind) {
            case "foreign-owner" -> choice(999);case "old-nonce" -> VkQuestionnaireChoiceSupport.payload("ffffffffffff","business",7);
            case "broken" -> "{iguana_questionnaire";default -> null;
        };
        receive(2,kind.equals("number")?"1":"Business A",payload);
        assertThat(state().path("scope").isNull()).isTrue();assertThat(state().path("answers")).isEmpty();
        verify(runtime,never()).selectQuestionnaire(any(),any(),anyLong());verifyNoInteractions(feedback);
    }
    @Test void explicitStartChangesMenuGenerationAndOldButtonsAreIgnored() {
        receive(1,"/start");String old=choice(7);receive(2,"/start");assertThat(choice(7)).isNotEqualTo(old);
        receive(3,"Business A",old);assertThat(state().path("scope").isNull()).isTrue();
    }
    @Test void repeatedChoiceAndRepeatedAnswerDoNotAdvanceEvenAcrossTransports() {
        chooseA();String code=choice(7);rebuild();receive(20,"Business A",code);
        assertThat(state().path("answers")).isEmpty();
        receive(3,"One");resetDeliveryCache();rebuild();webhook("different-envelope",3,"One");
        assertThat(state().path("currentIndex").asInt()).isEqualTo(1);
        assertThat(state().path("answers").size()).isEqualTo(1);verify(tickets,never()).createConversationTicket(any());
    }
    @Test void menuDeliveryFailurePreservesNonceAndCallbackCanRetry() throws Exception {
        when(send.execute()).thenThrow(new ClientException("test send failure")).thenReturn(123);
        assertThatThrownBy(()->webhook("start",1,"/start")).hasMessageContaining("delivery failed");
        String nonce=state().at("/selection/nonce").asText();rebuild();webhook("start",1,"/start");
        assertThat(state().at("/selection/nonce").asText()).isEqualTo(nonce);
        verify(runtime,times(1)).questionnaireChoices(channel);verify(guard,times(2)).release(any());
    }
    @Test void firstQuestionDeliveryFailureKeepsChosenScopeAndReplayDoesNotAnswer() throws Exception {
        receive(1,"/start");String code=choice(7);
        when(send.execute()).thenThrow(new ClientException("test send failure")).thenReturn(123);
        assertThatThrownBy(()->receive(2,"Business A",code)).hasMessageContaining("delivery failed");
        rebuild();receive(2,"Business A",code);assertThat(state().path("answers")).isEmpty();
        assertThat(state().at("/scope/businessId").asLong()).isEqualTo(7);
    }
    @Test void publicationFailurePreservesFinalAnswerAndExactFrozenCommand() {
        first();when(tickets.createConversationTicket(any())).thenThrow(new IllegalStateException("test publication failure"))
                .thenReturn(new TicketService.TicketCreationResult("T-VK",null,"queued"));
        assertThatThrownBy(()->receive(4,"Two")).hasMessageContaining("publication failure");
        assertThat(state().at("/answers/free-second").asText()).isEqualTo("Two");
        when(runtime.selectQuestionnaire(channel,catalog,7L)).thenThrow(unavailable());rebuild();receive(4,"Two");
        var commands=org.mockito.ArgumentCaptor.forClass(ConversationTicketCreationCommand.class);
        verify(tickets,times(2)).createConversationTicket(commands.capture());
        assertThat(commands.getAllValues().get(0)).isEqualTo(commands.getAllValues().get(1));
    }
    @Test void staleBeforeSelectionKeepsOriginalMenuAndRequiresExplicitStart() {
        receive(1,"/start");when(runtime.selectQuestionnaire(channel,catalog,7L)).thenThrow(unavailable());
        receive(2,"Business A",choice(7));assertThat(state().path("scope").isNull()).isTrue();
        verify(runtime,times(1)).questionnaireChoices(channel);verify(send).message(startsWith("Вопросник недоступен"));
    }
    @Test void staleBeforePublicationKeepsAnswersButCannotEnqueueOrRecapture() {
        first();when(runtime.selectQuestionnaire(channel,catalog,7L)).thenThrow(unavailable());receive(4,"Two");
        assertThat(state().at("/answers/free-second").asText()).isEqualTo("Two");
        assertThat(state().at("/selection/submissionEventKey").isNull()).isTrue();
        verify(tickets,never()).createConversationTicket(any());verify(runtime,times(1)).questionnaireChoices(channel);
    }
    @Test void requiredConfirmationFailureOrCallbackAckLossCannotPublishTwice() throws Exception {
        first();when(send.execute()).thenThrow(new ClientException("test confirmation failure")).thenReturn(123);
        assertThatThrownBy(()->webhook("final",4,"Two")).hasMessageContaining("delivery failed");
        assertThat(state().at("/selection/submittedTicketId").asText()).isEqualTo("T-VK");
        rebuild();webhook("other-envelope",4,"Two");verify(tickets,times(1)).createConversationTicket(any());
    }
    @Test void callbackAckLossReleasesDeliveryAndRetainsSubmissionMarker() {
        first();var once=new AtomicBoolean(true);
        doAnswer(call->{if(once.getAndSet(false))throw new IllegalStateException("test ack loss");return call.callRealMethod();})
                .when(guard).markProcessed(any());
        assertThatThrownBy(()->webhook("final",4,"Two")).hasMessageContaining("ack loss");
        rebuild();webhook("final",4,"Two");verify(tickets,times(1)).createConversationTicket(any());
    }
    @Test void queueAcceptanceBeforeMarkerWriteFailureRetriesExactlyTheSameCommand() {
        first();var once=new AtomicBoolean(true);
        doAnswer(call->{
            JsonNode incoming=mapper.valueToTree(call.getArgument(4));
            if(!incoming.at("/selection/submittedTicketId").isNull() && once.getAndSet(false))
                throw new IllegalStateException("test marker persistence failure");
            return call.callRealMethod();
        }).when(store).saveIfUnchanged(eq("vk-questionnaire-v1"),eq(55L),eq(1001L),anyString(),any());
        assertThatThrownBy(()->receive(4,"Two")).hasMessageContaining("marker persistence failure");
        assertThat(state().at("/selection/submissionEventKey").asText()).isEqualTo("vk-message:1001:4");
        rebuild();receive(4,"Edited provider retry must not replace Two");
        var commands=org.mockito.ArgumentCaptor.forClass(ConversationTicketCreationCommand.class);
        verify(tickets,times(2)).createConversationTicket(commands.capture());
        assertThat(commands.getAllValues().get(0)).isEqualTo(commands.getAllValues().get(1));
        assertThat(commands.getValue().answers()).containsEntry("free-second","Two");
    }
    @Test void aNewMessageThatRetriesPendingPublicationIsNotSilentlyAcknowledged() {
        first();when(tickets.createConversationTicket(any())).thenThrow(new IllegalStateException("test queue failure"))
                .thenReturn(new TicketService.TicketCreationResult("T-VK",null,"queued"));
        assertThatThrownBy(()->receive(4,"Two")).hasMessageContaining("queue failure");
        assertThatThrownBy(()->receive(5,"New followup")).hasMessageContaining("Retry the message");
        var active=new TicketActive();active.setTicketId("T-VK");
        when(tickets.findActiveTicketForUser(eq(1001L),anyString(),eq(55L))).thenReturn(Optional.of(active));
        when(tickets.findByTicketId("T-VK")).thenReturn(Optional.of(new TicketService.TicketWithUser(1001L,"T-VK","open")));
        receive(5,"New followup");verify(tickets).recordActiveClientMessage(any());
    }
    @Test void sharedChannelCannotRelayAnUnselectedActiveTicketToRawOperatorChat() {
        var active=new TicketActive();active.setTicketId("OTHER");
        when(tickets.findActiveTicketForUser(eq(1001L),anyString(),eq(55L))).thenReturn(Optional.of(active));
        receive(1,"/start");assertThat(state().path("selection").isObject()).isTrue();
        verify(tickets,never()).recordActiveClientMessage(any());verify(send,never()).peerId(55L);
    }
    @Test void newFollowupWaitsForExactCanonicalRootAndCannotUseAnotherActiveTicket() {
        complete();assertThatThrownBy(()->receive(5,"Followup")).hasMessageContaining("not registered");
        var active=new TicketActive();active.setTicketId("OTHER");
        when(tickets.findActiveTicketForUser(eq(1001L),anyString(),eq(55L))).thenReturn(Optional.of(active));
        assertThatThrownBy(()->receive(5,"Followup")).hasMessageContaining("differs");
        verify(tickets,never()).recordActiveClientMessage(any());
        active.setTicketId("T-VK");when(tickets.findByTicketId("T-VK")).thenReturn(Optional.of(new TicketService.TicketWithUser(1001L,"T-VK","open")));
        receive(5,"Followup");var command=org.mockito.ArgumentCaptor.forClass(ActiveInboundClientMessageCommand.class);
        verify(tickets).recordActiveClientMessage(command.capture());assertThat(command.getValue().ticketId()).isEqualTo("T-VK");
        assertThat(command.getValue().providerMessageId()).isEqualTo(5L);verify(send,never()).peerId(55L);
    }
    @Test void closedCanonicalRootDoesNotStartANewQuestionnaireImplicitly() {
        complete();when(tickets.findByTicketId("T-VK")).thenReturn(Optional.of(new TicketService.TicketWithUser(1001L,"T-VK","closed")));
        receive(5,"Followup");verify(runtime,times(1)).questionnaireChoices(channel);verify(tickets,never()).recordActiveClientMessage(any());
    }
    @Test void secondActiveTicketBlocksFreshPublicationWithoutGuessingOwner() {
        first();var active=new TicketActive();active.setTicketId("OTHER");
        when(tickets.findActiveTicketForUser(eq(1001L),anyString(),eq(55L))).thenReturn(Optional.of(active));
        receive(4,"Two");verify(tickets,never()).createConversationTicket(any());assertThat(state().at("/scope/businessId").asLong()).isEqualTo(7);
    }
    @Test void cancelRemovesMenuAndOldButtonCannotBecomeAnUnscopedActiveMessage() {
        receive(1,"/start");String old=choice(7);receive(2,"/cancel");
        assertThat(store.load("vk-questionnaire-v1",55L,1001L,JsonNode.class)).isEmpty();receive(3,"Business A",old);
        verify(tickets,never()).recordActiveClientMessage(any());verify(runtime,times(1)).questionnaireChoices(channel);
    }
    @Test void concurrentChoiceCannotOverwriteAnAlreadySavedDifferentOwner() {
        receive(1,"/start");String code=choice(7);var once=new AtomicBoolean(true);
        doAnswer(call->{
            JsonNode incoming=mapper.valueToTree(call.getArgument(4));
            if(!incoming.path("scope").isNull() && once.getAndSet(false)) receive(22,"Business B",choice(8));
            return call.callRealMethod();
        }).when(store).saveIfUnchanged(eq("vk-questionnaire-v1"),eq(55L),eq(1001L),anyString(),any());
        receive(2,"Business A",code);assertThat(state().at("/scope/businessId").asLong()).isEqualTo(8);
        assertThat(state().path("answers")).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"user","peer","channel","flow","settings","marker"})
    void corruptedSessionCannotCrossScopeOrPublish(String kind) {
        chooseA();var tree=(ObjectNode)state();
        switch(kind) {
            case "user" -> tree.put("userId",9999);case "peer" -> tree.put("peerId",9999);
            case "channel" -> ((ObjectNode)tree.at("/selection/catalog")).put("channelId",999);
            case "flow" -> tree.set("flow",mapper.createArrayNode());
            case "settings" -> ((ObjectNode)tree.path("settings")).put("active_template_id","foreign");
            case "marker" -> ((ObjectNode)tree.path("selection")).put("submissionEventKey","foreign");
        }
        store.save("vk-questionnaire-v1",55L,1001L,tree);rebuild();assertThatThrownBy(()->receive(3,"Unsafe")).isInstanceOf(RuntimeException.class);
        verify(tickets,never()).createConversationTicket(any());verify(tickets,never()).recordActiveClientMessage(any());
    }
    @Test void missingStableProviderIdentityCannotStartTheSharedQuestionnaire() {
        assertThatThrownBy(()->bot.handleIncomingMessage(message(1,"/start",null).setConversationMessageId(null)))
                .hasMessageContaining("stable conversation message ID");
        assertThat(store.load("vk-questionnaire-v1",55L,1001L,JsonNode.class)).isEmpty();verifyNoInteractions(local);
    }
    @Test void legacySessionCannotContinueAfterChannelRebindButExplicitStartCanReplaceIt() {
        store.save("vk",55L,1001L,Map.of("peerId",1001,"userId",1001,"flow",List.of(Map.of("id","old","type","text","order",1,"text","Old"))));
        receive(1,"Unsafe answer");assertThat(state().path("answers").isMissingNode()).isTrue();
        receive(2,"/start");assertThat(state().path("selection").isObject()).isTrue();verifyNoInteractions(local);
    }
    @Test void selectedMediaDoesNotUseUnscopedAttachmentStorageOrConsumeAnAnswer() {
        chooseA();bot.handleIncomingMessage(message(3,"Answer with photo",null).setAttachments(List.of(new MessageAttachment())));
        assertThat(state().path("answers")).isEmpty();verifyNoInteractions(attachments);
    }
    @Test void selectedSessionsAreNotSilentlyRemovedByLegacyFirstResponseExpiry() {
        receive(1,"/start");when(ingress.tryAcquireOrRenewJob(eq("vk"),eq(55L),anyString())).thenReturn(true);
        bot.expireSilentQuestionFlowSessions();assertThat(state().path("selection").isObject()).isTrue();
    }
    @Test void terminalMarkerProtectsAllOldAnswerEventsEvenAfterDeliveryCacheLoss() {
        complete();resetDeliveryCache();rebuild();
        receive(1,"/start");receive(2,"Business A",choice(7));receive(3,"One");receive(4,"Two");
        verify(tickets,times(1)).createConversationTicket(any());verify(runtime,times(1)).questionnaireChoices(channel);
        assertThat(state().at("/selection/submittedTicketId").asText()).isEqualTo("T-VK");
    }
    @Test void delayedPreviousGenerationAnswerCannotBeConsumedByAnotherBusiness() {
        chooseA();receive(10,"/start");receive(11,"Business B",choice(8));
        resetDeliveryCache();rebuild();receive(3,"Private answer for A");
        assertThat(state().at("/scope/businessId").asLong()).isEqualTo(8);
        assertThat(state().path("answers")).isEmpty();
        assertThat(state().path("history")).isEmpty();verify(tickets,never()).createConversationTicket(any());
    }
    @Test void menuPageSurvivesRestoreAndOldStartReplayDoesNotRecaptureCatalog() {
        var choices=new ArrayList<>(catalog.choices());
        for(long id=100;id<110;id++) choices.add(new Choice("BUSINESS",id,"b-"+id,"Business "+id,"q-"+id,"a".repeat(64)));
        catalog=new ChoiceCatalog(1,55,"c".repeat(64),choices);when(runtime.questionnaireChoices(channel)).thenReturn(catalog);
        receive(1,"/start");String nonce=state().at("/selection/nonce").asText();
        receive(2,"Далее",VkQuestionnaireChoiceSupport.payload(nonce,"page",1));
        resetDeliveryCache();rebuild();receive(1,"/start");
        assertThat(state().at("/selection/page").asInt()).isEqualTo(1);
        assertThat(state().at("/selection/nonce").asText()).isEqualTo(nonce);verify(runtime,times(1)).questionnaireChoices(channel);
    }
    @Test void longPollBatchRetrySkipsEarlierAnswersAndRetriesOnlyFrozenSubmission() {
        chooseA();
        var gson=new com.google.gson.Gson();
        var response=gson.fromJson(gson.toJson(Map.of("ts","next","updates",List.of(
                Map.of("type","message_new","object",Map.of("message",Map.of("id",3,"conversation_message_id",3,"from_id",1001,"peer_id",1001,"text","One"))),
                Map.of("type","message_new","object",Map.of("message",Map.of("id",4,"conversation_message_id",4,"from_id",1001,"peer_id",1001,"text","Two")))))),
                com.vk.api.sdk.objects.callback.longpoll.responses.GetLongPollEventsResponse.class);
        when(tickets.createConversationTicket(any())).thenThrow(new IllegalStateException("test queue failure"))
                .thenReturn(new TicketService.TicketCreationResult("T-VK",null,"queued"));
        var actor=new GroupActor(101L,"test-vk-token");
        assertThatThrownBy(()->ReflectionTestUtils.invokeMethod(bot,"handleUpdates",actor,response)).hasMessageContaining("queue failure");
        rebuild();ReflectionTestUtils.invokeMethod(bot,"handleUpdates",actor,response);
        var commands=org.mockito.ArgumentCaptor.forClass(ConversationTicketCreationCommand.class);
        verify(tickets,times(2)).createConversationTicket(commands.capture());
        assertThat(commands.getAllValues().get(0)).isEqualTo(commands.getAllValues().get(1));
        assertThat(state().path("answers").size()).isEqualTo(2);
    }
    @Test void repeatedCasConflictsRemainRetryableAndAreNotAcknowledged() {
        receive(1,"/start");String code=choice(7);clearInvocations(guard);
        doReturn(Optional.empty()).when(store).saveIfUnchanged(eq("vk-questionnaire-v1"),eq(55L),eq(1001L),anyString(),any());
        assertThatThrownBy(()->receive(2,"Business A",code)).isInstanceOf(SessionStateConflictException.class);
        verify(guard,never()).markProcessed(any());verify(guard).release(any());
        assertThat(state().path("scope").isNull()).isTrue();
    }
    @Test void generalOnlyRuntimeStillCompletesWithoutBusinessSelectionOrForcedQuestions() {
        doReturn(Optional.of(new RuntimeConfigSnapshot(55L,a.botSettings(),Map.of(),Map.of(),
                "GENERAL","GENERAL_CHANNEL","q-a","a".repeat(64)))).when(runtime).findChannelConfig(channel);
        receive(1,"/start");assertThat(state().path("flow").size()).isEqualTo(2);
        assertThat(state().path("selection").isNull()).isTrue();
        receive(2,"General one");receive(3,"General two");
        var commands=org.mockito.ArgumentCaptor.forClass(ConversationTicketCreationCommand.class);
        verify(tickets).createConversationTicket(commands.capture());
        assertThat(commands.getValue().scopeKind()).isEqualTo("GENERAL");assertThat(commands.getValue().businessId()).isNull();
        assertThat(commands.getValue().scopeSelectionSource()).isEqualTo("GENERAL_CHANNEL");
        assertThat(store.load("vk",55L,1001L,JsonNode.class)).isEmpty();verify(runtime,never()).questionnaireChoices(any());
    }
    @Test void oldGeneralSessionCannotContinueThroughANewSharedChannelBinding() {
        doReturn(Optional.of(new RuntimeConfigSnapshot(55L,a.botSettings(),Map.of(),Map.of(),
                "GENERAL","GENERAL_CHANNEL","q-a","a".repeat(64)))).when(runtime).findChannelConfig(channel);
        receive(1,"/start");doThrow(unavailable()).when(runtime).findChannelConfig(channel);
        receive(2,"Previous general answer");
        assertThat(state().path("answers")).isEmpty();verify(tickets,never()).createConversationTicket(any());
        receive(3,"/start");assertThat(state().path("selection").isObject()).isTrue();
    }
    @Test void unboundLegacyRuntimeRetainsItsProblemQuestionAndDuplicateGuard() {
        doReturn(Optional.empty()).when(runtime).findChannelConfig(channel);
        when(local.loadSettings()).thenReturn(Map.of("bot_settings",a.botSettings()));
        receive(1,"/start");assertThat(state().path("flow").size()).isEqualTo(3);
        assertThat(state().at("/flow/2/id").asText()).isEqualTo("problem");
        receive(2,"Legacy one");receive(2,"Legacy one");assertThat(state().path("currentIndex").asInt()).isEqualTo(1);
        verify(runtime,never()).questionnaireChoices(any());verify(tickets,never()).createConversationTicket(any());
    }

    private void generalMenu() {
        var payload=selected(7L,"q-general","d");
        var general=new SelectedQuestionnaire(2,55,"GENERAL","USER_SELECTION",(Long)null,"q-general","d".repeat(64),"c".repeat(64),payload.botSettings(),Map.of(),Map.of());
        catalog=new ChoiceCatalog(2,55,"c".repeat(64),List.of(new Choice("BUSINESS",7,"a","Business A","q-a","a".repeat(64)),
            new Choice("GENERAL",(Long)null,"GENERAL","Без бизнеса","q-general","d".repeat(64))));
        doReturn(catalog).when(runtime).questionnaireChoices(channel);
        doReturn(general).when(runtime).selectGeneralQuestionnaire(channel,catalog);
    }

    private void chooseGeneral() throws Exception {generalMenu();receive(1,"/start");receive(2,"Без бизнеса","{\"iguana_questionnaire\":\""+state().at("/selection/nonce").asText()+"\",\"kind\":\"general\",\"value\":\"general\"}");}
    @Test void generalChoiceRestartsAndPublishesNullOwnerWithOnlyItsFrozenFreeAnswers() throws Exception {
        chooseGeneral();assertThat(state().at("/scope/businessId").isNull()).isTrue();rebuild();receive(3,"One");rebuild();receive(4,"Two");
        var capture=org.mockito.ArgumentCaptor.forClass(ConversationTicketCreationCommand.class);verify(tickets).createConversationTicket(capture.capture());
        var command=capture.getValue();assertThat(command.businessId()).isNull();assertThat(command.scopeKind()).isEqualTo("GENERAL");
        assertThat(command.scopeSelectionSource()).isEqualTo("USER_SELECTION");assertThat(command.questionTemplateId()).isEqualTo("q-general");
        assertThat(command.answers()).containsExactlyInAnyOrderEntriesOf(Map.of("free-first","One","free-second","Two"));verifyNoInteractions(local,feedback);
    }
    @Test void generalPublicationFailureRetriesTheSameCommandAfterSessionRestore() throws Exception {
        chooseGeneral();receive(3,"One");
        when(tickets.createConversationTicket(any())).thenThrow(new IllegalStateException("general queue failure")).thenReturn(new TicketService.TicketCreationResult("GENERAL-T",null,"queued"));
        assertThatThrownBy(()->receive(4,"Two")).hasMessageContaining("general queue failure");
        doThrow(unavailable()).when(runtime).selectGeneralQuestionnaire(channel,catalog);rebuild();receive(4,"Edited retry");
        var capture=org.mockito.ArgumentCaptor.forClass(ConversationTicketCreationCommand.class);verify(tickets,times(2)).createConversationTicket(capture.capture());
        assertThat(capture.getAllValues().get(0)).isEqualTo(capture.getAllValues().get(1));assertThat(capture.getValue().businessId()).isNull();
    }
    @ParameterizedTest @ValueSource(strings={"choice","submission"})
    void generalStaleScopeNeverRecapturesOrPublishes(String boundary) throws Exception {
        generalMenu();receive(1,"/start");
        if(boundary.equals("submission")){receive(2,"Без бизнеса","{\"iguana_questionnaire\":\""+state().at("/selection/nonce").asText()+"\",\"kind\":\"general\",\"value\":\"general\"}");receive(3,"One");}
        doThrow(unavailable()).when(runtime).selectGeneralQuestionnaire(channel,catalog);
        if(boundary.equals("choice")){receive(2,"Без бизнеса","{\"iguana_questionnaire\":\""+state().at("/selection/nonce").asText()+"\",\"kind\":\"general\",\"value\":\"general\"}");}else receive(4,"Two");
        verify(tickets,never()).createConversationTicket(any());verify(runtime,times(1)).questionnaireChoices(channel);
    }


    @Test void oldGeneralCommandNeverSelectsAfterExplicitRestart() throws Exception {
        generalMenu();receive(1,"/start");String old="/general "+state().at("/selection/nonce").asText();receive(10,"/start");receive(11,old);assertThat(state().path("scope").isNull()).isTrue();
        verify(runtime,never()).selectGeneralQuestionnaire(any(),any());verify(tickets,never()).createConversationTicket(any());
    }

    private void r150Menu() {
        var flow=List.of(
            Map.of("id","city-q","binding_key","city_answer","type","preset","text","City","order",10,"preset",Map.of("group","locations","field","city")),
            Map.of("id","spot","type","preset","text","Location","order",20,"preset",Map.of("group","locations","field","location_name")),
            Map.of("id","team","type","preset","text","Department","order",30,"preset",Map.of("group","department","field","name")));
        var tree=Map.<String,Object>of("businessId",7L,"locations",List.of(
            Map.of("id",91L,"name","North site","locationType","Store","city","North"),
            Map.of("id",92L,"name","South site","locationType","Store","city","South")));
        var presets=Map.<String,Object>of("businessId",7L,"groups",Map.of("department",Map.of("name",List.of("Kitchen","Delivery"))));
        var resourcesSha=com.example.supportbot.service.SelectedQuestionnaireOptions.resourceSha256(tree,presets);
        a=new SelectedQuestionnaire(3,55,"BUSINESS","USER_SELECTION",7L,"q-a","a".repeat(64),"c".repeat(64),
            Map.of("active_template_id","q-a","question_templates",List.of(Map.of("id","q-a","name","A","question_flow",flow))),tree,presets);
        catalog=new ChoiceCatalog(3,55,"c".repeat(64),List.of(new Choice("BUSINESS",7L,"a","Business A","q-a","a".repeat(64),resourcesSha)));
        doReturn(catalog).when(runtime).questionnaireChoices(channel);doReturn(a).when(runtime).selectQuestionnaire(channel,catalog,7L);
    }

    @Test void v3ResourcesSurviveJsonRestoreAndRejectForeignOrWrongCityAnswers() throws Exception {
        r150Menu();chooseA();rebuild();receive(3,"Foreign B");assertThat(state().path("answers")).isEmpty();
        receive(4,"North");rebuild();receive(5,"South site");
        verify(tickets,never()).createConversationTicket(any());receive(6,"North site");rebuild();receive(8,"Kitchen");
        var capture=org.mockito.ArgumentCaptor.forClass(ConversationTicketCreationCommand.class);verify(tickets).createConversationTicket(capture.capture());
        assertThat(capture.getValue().businessId()).isEqualTo(7L);
        assertThat(capture.getValue().answers()).containsExactlyInAnyOrderEntriesOf(Map.of("city_answer","North","spot","North site","team","Kitchen"));
        verifyNoInteractions(local,feedback);
    }
    @Test void v3QueueRetryKeepsTheFrozenResourceAnswersAndPublicationIdentity() throws Exception {
        r150Menu();chooseA();receive(4,"North");receive(6,"North site");
        when(tickets.createConversationTicket(any())).thenThrow(new IllegalStateException("r150 queue failure"))
            .thenReturn(new TicketService.TicketCreationResult("R150-T",null,"queued"));
        assertThatThrownBy(()->receive(8,"Kitchen")).hasMessageContaining("r150 queue failure");
        doThrow(unavailable()).when(runtime).selectQuestionnaire(channel,catalog,7L);rebuild();receive(8,"Edited retry");
        var capture=org.mockito.ArgumentCaptor.forClass(ConversationTicketCreationCommand.class);verify(tickets,times(2)).createConversationTicket(capture.capture());
        assertThat(capture.getAllValues().get(0)).isEqualTo(capture.getAllValues().get(1));
        assertThat(capture.getValue().answers()).containsEntry("team","Kitchen");verifyNoInteractions(local,feedback);
    }


    @Test void v3GeneralRestoresWithoutAnyBusinessResourcesOrOwner() throws Exception {
        var payload=selected(7L,"q-general","d");var emptySha=com.example.supportbot.service.SelectedQuestionnaireOptions.resourceSha256(Map.of(),Map.of());
        var general=new SelectedQuestionnaire(3,55,"GENERAL","USER_SELECTION",(Long)null,"q-general","d".repeat(64),"c".repeat(64),payload.botSettings(),Map.of(),Map.of());
        catalog=new ChoiceCatalog(3,55,"c".repeat(64),List.of(
            new Choice("BUSINESS",7L,"a","Business A","q-a","a".repeat(64),"a".repeat(64)),
            new Choice("GENERAL",(Long)null,"GENERAL","Без бизнеса","q-general","d".repeat(64),emptySha)));
        doReturn(catalog).when(runtime).questionnaireChoices(channel);doReturn(general).when(runtime).selectGeneralQuestionnaire(channel,catalog);
        receive(1,"/start");receive(2,"/general "+state().at("/selection/nonce").asText());rebuild();receive(3,"One");rebuild();receive(4,"Two");
        var capture=org.mockito.ArgumentCaptor.forClass(ConversationTicketCreationCommand.class);verify(tickets).createConversationTicket(capture.capture());
        assertThat(capture.getValue().scopeKind()).isEqualTo("GENERAL");assertThat(capture.getValue().businessId()).isNull();
        assertThat(capture.getValue().answers()).containsExactlyInAnyOrderEntriesOf(Map.of("free-first","One","free-second","Two"));verifyNoInteractions(local,feedback);
    }

    private void r152LargeMenu(boolean includeGeneral) {
        r150Menu();a=new SelectedQuestionnaire(4,55,"BUSINESS","USER_SELECTION",7L,"q-a",a.templateSha256(),a.catalogSha256(),a.botSettings(),a.locationTree(),a.presetDefinitions());
        var choices=new ArrayList<Choice>();for(long id=100;id<203;id++)choices.add(new Choice("BUSINESS",id,"b-"+id,"Business "+id,"q-"+id,"a".repeat(64),"b".repeat(64)));
        choices.add(new Choice("BUSINESS",7L,"a","Business A","q-a",a.templateSha256(),SelectedQuestionnaireOptions.resourceSha256(a.locationTree(),a.presetDefinitions())));
        var payload=selected(7L,"q-general","d");
        var general=new SelectedQuestionnaire(4,55,"GENERAL","USER_SELECTION",(Long)null,"q-general","d".repeat(64),"c".repeat(64),payload.botSettings(),Map.of(),Map.of());
        if(includeGeneral)choices.add(new Choice("GENERAL",(Long)null,"GENERAL","Без бизнеса","q-general","d".repeat(64),SelectedQuestionnaireOptions.resourceSha256(Map.of(),Map.of())));
        catalog=new ChoiceCatalog(4,55,"c".repeat(64),choices);doReturn(catalog).when(runtime).questionnaireChoices(channel);
        doReturn(a).when(runtime).selectQuestionnaire(channel,catalog,7L);doReturn(general).when(runtime).selectGeneralQuestionnaire(channel,catalog);
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void v4LastPageRestoresThenSelectsItsOwnFrozenBusinessOrGeneral(boolean general) throws Exception {
        r152LargeMenu(general);receive(1,"/start");String nonce=state().at("/selection/nonce").asText();int page=general?13:12;
        receive(2,"Далее",VkQuestionnaireChoiceSupport.payload(nonce,"page",page));rebuild();assertThat(state().at("/selection/page").asInt()).isEqualTo(page);
        receive(3,general?"/general "+nonce:"/business "+nonce+" 7");rebuild();receive(4,"Назад",VkQuestionnaireChoiceSupport.payload(nonce,"page",0));assertThat(state().path("answers")).isEmpty();
        receive(5,general?"One":"North");rebuild();receive(6,general?"Two":"North site");if(!general){rebuild();receive(7,"Kitchen");}
        var capture=org.mockito.ArgumentCaptor.forClass(ConversationTicketCreationCommand.class);verify(tickets).createConversationTicket(capture.capture());
        assertThat(capture.getValue().scopeKind()).isEqualTo(general?"GENERAL":"BUSINESS");assertThat(capture.getValue().businessId()).isEqualTo(general?null:7L);
        assertThat(capture.getValue().catalogSha256()).isEqualTo(catalog.catalogSha256());verify(runtime,times(1)).questionnaireChoices(channel);verifyNoInteractions(local,feedback);
    }

    private void mediaMenu(boolean general) throws Exception {
        mediaEnabled=true;rebuild();
        var original=selected(7,"q-a","a");
        var tree=Map.<String,Object>of("businessId",7L,"locations",List.of());
        var presets=Map.<String,Object>of("businessId",7L,"groups",Map.of());
        a=new SelectedQuestionnaire(4,55,"BUSINESS","USER_SELECTION",7L,"q-a",original.templateSha256(),original.catalogSha256(),original.botSettings(),tree,presets);
        var generalPayload=selected(7,"q-general","d");
        var selectedGeneral=new SelectedQuestionnaire(4,55,"GENERAL","USER_SELECTION",(Long)null,"q-general","d".repeat(64),"c".repeat(64),generalPayload.botSettings(),Map.of(),Map.of());
        catalog=new ChoiceCatalog(4,55,"c".repeat(64),List.of(
            new Choice("BUSINESS",7L,"a","Business A","q-a",a.templateSha256(),SelectedQuestionnaireOptions.resourceSha256(tree,presets)),
            new Choice("GENERAL",(Long)null,"GENERAL","Без бизнеса","q-general","d".repeat(64),SelectedQuestionnaireOptions.resourceSha256(Map.of(),Map.of()))));
        doReturn(catalog).when(runtime).questionnaireChoices(channel);doReturn(a).when(runtime).selectQuestionnaire(channel,catalog,7L);
        doReturn(selectedGeneral).when(runtime).selectGeneralQuestionnaire(channel,catalog);
        var storage=new AttachmentService(mediaRoot,new ObjectStorageProperties());
        doAnswer(call->storage.storeVkQuestionnaire(call.getArgument(0),call.getArgument(1),call.getArgument(2),call.getArgument(3),call.getArgument(4),call.getArgument(5),call.getArgument(6),call.getArgument(7)))
            .when(attachments).storeVkQuestionnaire(anyLong(),nullable(Long.class),anyLong(),anyString(),anyLong(),anyInt(),anyString(),any(java.io.InputStream.class));
    }
    private void mediaChoose(boolean general) throws Exception {
        mediaMenu(general);receive(1,"/start");receive(2,general?"/general "+state().at("/selection/nonce").asText():"Business A",general?null:choice(7));
    }
    private Message mediaMessage(int id,String text,String... types) {
        var list=new ArrayList<MessageAttachment>();
        for(String type:types) {
            String json=switch(type) {
                case "photo"->"{\"type\":\"photo\",\"photo\":{\"sizes\":[{\"width\":1,\"height\":1,\"url\":\"https://cdn.userapi.com/small\"},{\"width\":50,\"height\":60,\"url\":\"https://cdn.userapi.com/large\"}]}}";
                case "document"->"{\"type\":\"doc\",\"doc\":{\"url\":\"https://cdn.userapi.com/file\",\"title\":\"../proof.HTML\",\"ext\":\"HTML\",\"size\":10}}";
                case "audio_message"->"{\"type\":\"audio_message\",\"audio_message\":{\"link_ogg\":\"https://cdn.vkuseraudio.net/voice.ogg\"}}";
                default->"{\"type\":\"sticker\"}";
            };
            list.add(new com.google.gson.Gson().fromJson(json,MessageAttachment.class));
        }
        return message(id,text,null).setDate(1792584000).setAttachments(list);
    }
    private long mediaFileCount() throws Exception {try(var files=java.nio.file.Files.walk(mediaRoot)){return files.filter(java.nio.file.Files::isRegularFile).count();}}
    @ParameterizedTest @ValueSource(booleans={false,true})
    void v4MediaBatchSurvivesRestartAndDuplicateWithExactProviderNamesTimesAndScope(boolean general) throws Exception {
        mediaChoose(general);var message=mediaMessage(3,"Caption","photo","document","audio_message");bot.handleIncomingMessage(message);
        assertThat(state().path("history")).hasSize(3);assertThat(downloads).hasValue(3);assertThat(mediaFileCount()).isEqualTo(3);
        resetDeliveryCache();rebuild();bot.handleIncomingMessage(message);assertThat(downloads).hasValue(3);receive(4,"Two");
        var capture=org.mockito.ArgumentCaptor.forClass(ConversationTicketCreationCommand.class);verify(tickets).createConversationTicket(capture.capture());var command=capture.getValue();
        assertThat(command.businessId()).isEqualTo(general?null:7L);assertThat(command.scopeKind()).isEqualTo(general?"GENERAL":"BUSINESS");
        assertThat(command.historyEntries()).filteredOn(entry->entry.attachmentPath()!=null).hasSize(3).allSatisfy(entry->{
            assertThat(entry.providerMessageId()).isEqualTo("3");assertThat(entry.occurredAt()).isEqualTo(java.time.Instant.ofEpochSecond(1792584000).atOffset(java.time.ZoneOffset.UTC));
            assertThat(entry.attachmentPath()).startsWith("questionnaires/vk/55/"+(general?"general":"business-7")+"/1001/"+catalog.catalogSha256()+"/3/");
            assertThat(entry.attachmentPath()).doesNotContain("https:");
        });
        assertThat(command.historyEntries().get(1).attachmentName()).isEqualTo("proof.HTML");
        assertThat(command.historyEntries()).extracting(ConversationHistoryEntry::messageType).containsExactly("photo","document","audio_message","text");
        verify(send,never()).peerId(55L);verifyNoInteractions(local,feedback);
    }
    @Test void v4MediaWithoutCaptionAnswersTheFreeQuestionAndNeverInvokesLegacyStorage() throws Exception {
        mediaChoose(false);bot.handleIncomingMessage(mediaMessage(3,"","document"));assertThat(state().at("/answers/free-first").asText()).isEqualTo("Вложений: 1");
        verify(attachments,never()).store(anyString(),anyString(),any());assertThat(mediaFileCount()).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"pending","choice","preset","unsupported","eleven","old-version","stale"})
    void mediaIsRejectedBeforeAnyDownloadOrAnswerForInvalidScopeStepOrBatch(String kind) throws Exception {
        if(kind.equals("old-version")){chooseA();}else{mediaMenu(false);if(kind.equals("preset"))r152LargeMenu(false);receive(1,"/start");}
        var message=mediaMessage(3,"Caption","document");
        switch(kind) {
            case "pending"->{ }
            case "choice"->message.setText("Business A").setPayload(choice(7));
            case "preset"->receive(2,"Business A",choice(7));
            case "unsupported"->{receive(2,"Business A",choice(7));message=mediaMessage(3,"Caption","document","sticker");}
            case "eleven"->{receive(2,"Business A",choice(7));message.setAttachments(java.util.Collections.nCopies(11,message.getAttachments().get(0)));}
            case "stale"->{receive(2,"Business A",choice(7));doThrow(unavailable()).when(runtime).selectQuestionnaire(channel,catalog,7L);}
        }
        bot.handleIncomingMessage(message);
        if(kind.equals("stale")){verify(send).message(contains("Вопросник недоступен"));verify(runtime,times(2)).selectQuestionnaire(channel,catalog,7L);}
        assertThat(downloads).hasValue(0);assertThat(state().path("answers")).isEmpty();verifyNoInteractions(attachments);verify(tickets,never()).createConversationTicket(any());
    }
    @Test void batchBudgetIsCheckedAcrossAnswersBeforeDownloadingMore() throws Exception {
        mediaChoose(false);var message=mediaMessage(3,"First","document");message.setAttachments(java.util.Collections.nCopies(10,message.getAttachments().get(0)));bot.handleIncomingMessage(message);
        assertThat(downloads).hasValue(10);assertThat(state().path("history")).hasSize(10);bot.handleIncomingMessage(mediaMessage(4,"Second","document"));
        assertThat(downloads).hasValue(10);assertThat(state().path("currentIndex").asInt()).isEqualTo(1);verify(tickets,never()).createConversationTicket(any());
    }
    @ParameterizedTest @ValueSource(strings={"download","storage","empty","oversize"})
    void failedMediaNeverAdvancesOrPublishesAndTheSameProviderMessageCanRetry(String failure) throws Exception {
        mediaChoose(false);var incoming=mediaMessage(3,"Caption","document");
        if(failure.equals("download"))mediaStatus=503;
        if(failure.equals("storage"))doThrow(new java.io.IOException("storage unavailable")).when(attachments).storeVkQuestionnaire(anyLong(),nullable(Long.class),anyLong(),anyString(),anyLong(),anyInt(),anyString(),any());
        if(failure.equals("empty"))mediaBytes=new byte[0];if(failure.equals("oversize"))mediaBytes=new byte[QuestionnaireMedia.MAX_BYTES+1];
        if(failure.equals("download")||failure.equals("storage"))assertThatThrownBy(()->bot.handleIncomingMessage(incoming)).hasMessageContaining("media is unavailable");else bot.handleIncomingMessage(incoming);
        assertThat(state().path("answers")).isEmpty();assertThat(state().path("history")).isEmpty();verify(tickets,never()).createConversationTicket(any());
        assertThat(mediaFileCount()).isZero();
        if(failure.equals("download")||failure.equals("storage")){
            mediaStatus=200;mediaMenu(false);rebuild();bot.handleIncomingMessage(incoming);assertThat(state().path("currentIndex").asInt()).isEqualTo(1);assertThat(mediaFileCount()).isEqualTo(1);
        }
    }
    @Test void promptFailureAndCasConflictKeepOneObjectAndOneAnswer() throws Exception {
        mediaChoose(false);var incoming=mediaMessage(3,"Caption","document");
        var conflict=new AtomicBoolean();doAnswer(call->{if(conflict.compareAndSet(false,true))throw new SessionStateConflictException("media CAS");return call.callRealMethod();})
            .when(store).saveIfUnchanged(eq("vk-questionnaire-v1"),eq(55L),eq(1001L),any(),any());
        when(send.execute()).thenThrow(new ClientException("required prompt failed")).thenReturn(123);
        assertThatThrownBy(()->bot.handleIncomingMessage(incoming)).hasMessageContaining("delivery failed");
        assertThat(state().path("history")).hasSize(1);assertThat(mediaFileCount()).isEqualTo(1);int count=downloads.get();
        rebuild();bot.handleIncomingMessage(incoming);assertThat(downloads).hasValue(count);assertThat(state().path("history")).hasSize(1);
    }
    @Test void finalMediaQueueRetryNeverRecapturesRuntimeOrDownloadsAgain() throws Exception {
        mediaChoose(false);receive(3,"One");when(tickets.createConversationTicket(any())).thenThrow(new IllegalStateException("media queue failure"))
            .thenReturn(new TicketService.TicketCreationResult("VK-MEDIA",null,"queued"));
        assertThatThrownBy(()->bot.handleIncomingMessage(mediaMessage(4,"Caption","photo","document"))).hasMessageContaining("media queue failure");
        doThrow(unavailable()).when(runtime).selectQuestionnaire(channel,catalog,7L);rebuild();bot.handleIncomingMessage(mediaMessage(4,"Changed retry","audio_message"));
        var capture=org.mockito.ArgumentCaptor.forClass(ConversationTicketCreationCommand.class);verify(tickets,times(2)).createConversationTicket(capture.capture());
        assertThat(capture.getAllValues().get(0)).isEqualTo(capture.getAllValues().get(1));assertThat(downloads).hasValue(2);assertThat(mediaFileCount()).isEqualTo(2);
    }
    @Test void restoredForeignMediaIsRejectedBeforePublication() throws Exception {
        mediaChoose(false);bot.handleIncomingMessage(mediaMessage(3,"Caption","document"));ObjectNode saved=(ObjectNode)state();
        ((ObjectNode)saved.path("history").get(0)).put("attachment",saved.at("/history/0/attachment").asText().replace("business-7","business-8"));
        store.save("vk-questionnaire-v1",55L,1001L,saved);rebuild();assertThatThrownBy(()->receive(4,"Two")).hasMessageContaining("Invalid saved VK questionnaire media");verify(tickets,never()).createConversationTicket(any());assertThat(downloads).hasValue(1);
    }
    @Test void submittedButUnregisteredSelectedRootKeepsMediaClosedBeforeDownload() throws Exception {
        mediaChoose(false);receive(3,"One");receive(4,"Two");
        var active=new TicketActive();active.setTicketId("T-VK");
        when(tickets.findActiveTicketForUser(anyLong(),anyString(),anyLong())).thenReturn(Optional.of(active));
        bot.handleIncomingMessage(mediaMessage(5,"Later","document"));assertThat(downloads).hasValue(0);verifyNoInteractions(attachments,feedback);
    }

    @Test void legacySessionsKeepTheirExistingAttachmentPolicyInsteadOfTheNewSelectedBudget() throws Exception {
        doReturn(Optional.empty()).when(runtime).findChannelConfig(channel);when(local.loadSettings()).thenReturn(Map.of("bot_settings",a.botSettings()));
        receive(1,"/start");ObjectNode saved=(ObjectNode)state();var history=mapper.createArrayNode();
        for(int i=0;i<11;i++)history.add(mapper.createObjectNode().put("userId",1001).put("text","legacy").put("messageType","document").put("attachment","legacy/"+i+".bin"));
        saved.set("history",history);store.save("vk",55L,1001L,saved);rebuild();receive(2,"One");
        assertThat(state().path("history")).hasSize(12);verify(tickets,never()).recordSelectedMediaBatch(any(),anyList());
    }
    @Test void postEnqueueCasConflictReplaysTheSameBatchWithoutAnotherDownloadOrQuotaIncrement() throws Exception {
        registeredMediaRoot(false);var saves=new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(call->saves.incrementAndGet()==2?Optional.empty():call.callRealMethod()).when(store)
            .saveIfUnchanged(eq("vk-questionnaire-v1"),eq(55L),eq(1001L),any(),any());
        bot.handleIncomingMessage(mediaMessage(5,"Original","photo","document"));
        var commands=org.mockito.ArgumentCaptor.forClass(ActiveInboundClientMessageCommand.class);
        verify(tickets,times(2)).recordSelectedMediaBatch(commands.capture(),anyList());
        assertThat(commands.getAllValues().get(0)).isEqualTo(commands.getAllValues().get(1));
        assertThat(downloads).hasValue(2);assertThat(state().path("followupMediaCount").asInt()).isEqualTo(2);assertThat(state().path("pendingMedia").isNull()).isTrue();
    }
    private void registeredMediaRoot(boolean general) throws Exception {
        mediaChoose(general);receive(3,"One");receive(4,"Two");
        var active=new TicketActive();active.setTicketId("T-VK");
        when(tickets.findActiveTicketForUser(anyLong(),anyString(),anyLong())).thenReturn(Optional.of(active));
        when(tickets.findByTicketId("T-VK")).thenReturn(Optional.of(new TicketService.TicketWithUser(1001L,"T-VK","open")));
        when(runtime.selectedVkMediaAdmission(eq(channel),eq(1001L),eq("T-VK"),any())).thenAnswer(call->{
            SelectedQuestionnaire selected=call.getArgument(3);
            return new SelectedMediaAdmission(2,55,1001,"T-VK",selected.businessId(),selected.scopeKind(),selected.questionTemplateId(),selected.templateSha256(),selected.catalogSha256(),10);
        });
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void registeredVkBatchUsesOneFrozenCommandAndRestoredDuplicatesNeverDownloadAgain(boolean general) throws Exception {
        registeredMediaRoot(general);var original=state();resetDeliveryCache();rebuild();
        doThrow(unavailable()).when(runtime).selectQuestionnaire(any(),any(),anyLong());
        doThrow(unavailable()).when(runtime).selectGeneralQuestionnaire(any(),any());
        var incoming=mediaMessage(5,"Later","photo","document","audio_message");bot.handleIncomingMessage(incoming);
        resetDeliveryCache();rebuild();bot.handleIncomingMessage(incoming);
        var commands=org.mockito.ArgumentCaptor.forClass(ActiveInboundClientMessageCommand.class);
        @SuppressWarnings("unchecked") var batches=org.mockito.ArgumentCaptor.forClass((Class<List<InboundClientMessageEvent.MediaItem>>)(Class<?>)List.class);
        verify(tickets).recordSelectedMediaBatch(commands.capture(),batches.capture());
        assertThat(commands.getValue().providerMessageId()).isEqualTo(5L);assertThat(commands.getValue().text()).isEqualTo("Later");
        assertThat(batches.getValue()).extracting(InboundClientMessageEvent.MediaItem::messageType).containsExactly("photo","document","audio_message");
        assertThat(batches.getValue()).allSatisfy(item->assertThat(item.attachmentPath()).startsWith("questionnaires/vk/55/"+(general?"general":"business-7")+"/1001/"+catalog.catalogSha256()+"/5/"));
        assertThat(state().path("history")).isEqualTo(original.path("history"));assertThat(state().path("answers")).isEqualTo(original.path("answers"));
        assertThat(state().path("followupMediaCount").asInt()).isEqualTo(3);assertThat(state().path("pendingMedia").isNull()).isTrue();
        assertThat(downloads).hasValue(3);assertThat(mediaFileCount()).isEqualTo(3);
        verify(tickets,never()).recordActiveClientMessage(any());verify(send,never()).peerId(55L);verifyNoInteractions(feedback);
    }
    @Test void preparedVkBatchSurvivesQueueFailureAndUsesExactPayloadEvenWhenProviderRetryChanges() throws Exception {
        registeredMediaRoot(false);doThrow(new IllegalStateException("r156 queue failure")).doNothing().when(tickets).recordSelectedMediaBatch(any(),anyList());
        assertThatThrownBy(()->bot.handleIncomingMessage(mediaMessage(5,"Original","photo","document"))).hasMessageContaining("r156 queue failure");
        assertThat(state().path("pendingMedia").path("items")).hasSize(2);
        // Previous R155 consumer rejects precisely this invariant, rather than silently ignoring the new pending fields.
        var selection=state().path("selection");var keys=selection.path("acceptedEventKeys");
        assertThat(keys.get(keys.size()-1).asText()).isNotEqualTo(selection.path("submissionEventKey").asText());
        doThrow(unavailable()).when(runtime).selectedVkMediaAdmission(any(),anyLong(),anyString(),any());
        resetDeliveryCache();rebuild();bot.handleIncomingMessage(mediaMessage(5,"Edited retry","audio_message"));
        var commands=org.mockito.ArgumentCaptor.forClass(ActiveInboundClientMessageCommand.class);
        @SuppressWarnings("unchecked") var batches=org.mockito.ArgumentCaptor.forClass((Class<List<InboundClientMessageEvent.MediaItem>>)(Class<?>)List.class);
        verify(tickets,times(2)).recordSelectedMediaBatch(commands.capture(),batches.capture());
        assertThat(commands.getAllValues().get(0)).isEqualTo(commands.getAllValues().get(1));
        assertThat(batches.getAllValues().get(0)).isEqualTo(batches.getAllValues().get(1));
        assertThat(downloads).hasValue(2);assertThat(state().path("followupMediaCount").asInt()).isEqualTo(2);
    }
    @ParameterizedTest @ValueSource(strings={"closed","foreign","different-active","admission","quota","unsupported"})
    void registeredVkMediaDenialsNeverDownloadOrEnqueue(String reason) throws Exception {
        registeredMediaRoot(false);var incoming=mediaMessage(5,"Later","document");
        switch(reason) {
            case "closed"->when(tickets.findByTicketId("T-VK")).thenReturn(Optional.of(new TicketService.TicketWithUser(1001L,"T-VK","closed")));
            case "foreign"->when(tickets.findByTicketId("T-VK")).thenReturn(Optional.of(new TicketService.TicketWithUser(1002L,"T-VK","open")));
            case "different-active"->{var other=new TicketActive();other.setTicketId("OTHER");when(tickets.findActiveTicketForUser(anyLong(),anyString(),anyLong())).thenReturn(Optional.of(other));}
            case "admission"->doThrow(unavailable()).when(runtime).selectedVkMediaAdmission(any(),anyLong(),anyString(),any());
            case "quota"->{doReturn(new SelectedMediaAdmission(2,55,1001,"T-VK",7L,"BUSINESS","q-a",a.templateSha256(),a.catalogSha256(),1)).when(runtime).selectedVkMediaAdmission(any(),anyLong(),anyString(),any());incoming=mediaMessage(5,"Later","document","photo");}
            case "unsupported"->incoming=mediaMessage(5,"Later","document","sticker");
        }
        if(Set.of("closed","foreign","different-active").contains(reason)){var rejected=incoming;assertThatThrownBy(()->bot.handleIncomingMessage(rejected)).isInstanceOf(IllegalStateException.class);}
        else bot.handleIncomingMessage(incoming);
        assertThat(downloads).hasValue(0);assertThat(state().path("pendingMedia").isNull()).isTrue();verify(tickets,never()).recordSelectedMediaBatch(any(),anyList());
    }
    @Test void registeredVkQuotaPersistsAcrossRestoreAndNeverAcceptsEleventhFile() throws Exception {
        registeredMediaRoot(false);
        var message=mediaMessage(5,"Ten","document");message.setAttachments(Collections.nCopies(10,message.getAttachments().get(0)));
        bot.handleIncomingMessage(message);rebuild();bot.handleIncomingMessage(mediaMessage(6,"Eleven","photo"));
        assertThat(downloads).hasValue(10);assertThat(state().path("followupMediaCount").asInt()).isEqualTo(10);verify(tickets,times(1)).recordSelectedMediaBatch(any(),anyList());
    }
    @ParameterizedTest @ValueSource(strings={"owner","slot","provider","counter"})
    void corruptedPreparedVkBatchCannotBeReplayed(String mutation) throws Exception {
        registeredMediaRoot(false);doThrow(new IllegalStateException("r156 queue failure")).when(tickets).recordSelectedMediaBatch(any(),anyList());
        assertThatThrownBy(()->bot.handleIncomingMessage(mediaMessage(5,"Original","photo","document"))).hasMessageContaining("r156 queue failure");
        ObjectNode json=(ObjectNode)state();ObjectNode item=(ObjectNode)json.path("pendingMedia").path("items").get(1);
        switch(mutation) {
            case "owner"->item.put("attachment",item.path("attachment").asText().replace("business-7","business-8"));
            case "slot"->item.put("attachment",item.path("attachment").asText().replace("/5/2/","/5/1/"));
            case "provider"->item.put("providerMessageId","6");
            case "counter"->json.put("followupMediaCount",10);
        }
        store.save("vk-questionnaire-v1",55L,1001L,json);resetDeliveryCache();rebuild();clearInvocations(tickets);
        assertThatThrownBy(()->receive(5,"Retry")).hasMessageContaining("Invalid saved VK");
        assertThat(downloads).hasValue(2);verify(tickets,never()).recordSelectedMediaBatch(any(),anyList());
    }
}
