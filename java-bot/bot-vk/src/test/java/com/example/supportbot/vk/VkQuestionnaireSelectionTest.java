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
}
