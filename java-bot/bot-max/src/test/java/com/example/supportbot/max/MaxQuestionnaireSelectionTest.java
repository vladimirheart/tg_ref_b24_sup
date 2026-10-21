package com.example.supportbot.max;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.example.supportbot.config.*;
import com.example.supportbot.entity.*;
import com.example.supportbot.service.*;
import com.example.supportbot.service.PanelRuntimeConfigClient.*;
import com.example.supportbot.settings.BotSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/** Real handler, settings normalizer, JSON session store/CAS and delivery guard; no real provider writes. */
class MaxQuestionnaireSelectionTest {
    private final ObjectMapper mapper=Jackson2ObjectMapperBuilder.json().build();
    private final Channel channel=new Channel();
    private final RuntimeConfigService runtime=mock(RuntimeConfigService.class);
    private final SharedConfigService local=mock(SharedConfigService.class);
    private final TicketService tickets=mock(TicketService.class);
    private final MessagingService messages=mock(MessagingService.class);
    private final MaxApiClient api=mock(MaxApiClient.class);
    private final BotIngressCoordinationService ingress=mock(BotIngressCoordinationService.class);
    private final ChannelService channels=mock(ChannelService.class);
    private final BlacklistService blacklist=mock(BlacklistService.class);
    private final FeedbackService feedback=mock(FeedbackService.class);
    private BotSessionStoreService store;
    private BotWebhookDeliveryGuardService guard;
    private BotSettingsService settings;
    private MaxWebhookController controller;
    private MaxBotProperties properties;
    private ChoiceCatalog catalog;
    private SelectedQuestionnaire a,b;
    @BeforeEach void setup() {
        channel.setId(55L);channel.setPlatform("max");channel.setSupportChatId("unverified-shared-chat");
        properties=new MaxBotProperties();properties.setEnabled(true);properties.setChannelId(55L);properties.setWebhookSecret("test-secret");
        var coordination=new BotIngressCoordinationProperties();coordination.setMode("direct");
        @SuppressWarnings("unchecked") ObjectProvider<StringRedisTemplate> redis=mock(ObjectProvider.class);
        store=spy(new BotSessionStoreService(coordination,mapper,redis));
        guard=spy(new BotWebhookDeliveryGuardService(coordination,redis));
        settings=new BotSettingsService(mapper,local,runtime);
        catalog=new ChoiceCatalog(1,55,"c".repeat(64),List.of(
            new Choice("BUSINESS",7,"a","Business A","q-a","a".repeat(64)),
            new Choice("BUSINESS",8,"b","Business B","q-b","b".repeat(64))));
        a=selected(7,"q-a","a");b=selected(8,"q-b","b");
        when(ingress.tryAcquireOrRenew("max",55L)).thenReturn(true);
        when(channels.resolveConfiguredChannel(55L,"","MAX","max")).thenReturn(channel);
        when(blacklist.resolveStatus(eq(1001L),anyString(),any(),eq("2002")))
            .thenReturn(new BlacklistService.ResolvedBlacklistStatus(null,new BlacklistService.BlacklistStatus(false,false)));
        when(runtime.findChannelConfig(channel)).thenThrow(unavailable());
        when(runtime.questionnaireChoices(channel)).thenReturn(catalog);
        when(runtime.selectQuestionnaire(channel,catalog,7L)).thenReturn(a);
        when(runtime.selectQuestionnaire(channel,catalog,8L)).thenReturn(b);
        when(messages.sendToUser(eq(channel),eq(1001L),anyString())).thenReturn(true);
        when(api.sendQuestionnaireChoices(eq(1001L),any(),anyString())).thenReturn(true);
        when(tickets.createConversationTicket(any())).thenReturn(new TicketService.TicketCreationResult("T-MAX",null,"queued"));
        when(tickets.awaitClientTicketNumber(any())).thenReturn(Optional.empty());
        rebuild();
    }
    private SelectedQuestionnaire selected(long business,String id,String hash) {
        var template=Map.of("id",id,"name",id,"question_flow",List.of(
            Map.of("id","free-second","type","text","order",20,"text",id+" second"),
            Map.of("id","free-first","type","text","order",10,"text",id+" first")));
        return new SelectedQuestionnaire(1,55,"BUSINESS","USER_SELECTION",business,id,hash.repeat(64),"c".repeat(64),
            Map.of("active_template_id",id,"question_templates",List.of(template)),Map.of(),Map.of());
    }
    private void rebuild() {
        controller=new MaxWebhookController(properties,blacklist,channels,tickets,mock(ChatHistoryService.class),messages,
            feedback,settings,ingress,guard,store,runtime,mock(AttachmentService.class),api,mock(MaxClientAvatarSyncService.class),mapper);
    }
    private ResponseEntity<Map<String,Object>> send(String id,String text) {
        var update=mapper.valueToTree(Map.of("update_type","message_created","update_id",id,"message",
            Map.of("sender",Map.of("user_id",1001,"username","client","name","Client"),
                "recipient",Map.of("chat_id",2002),"body",Map.of("text",text))));
        return controller.handleUpdate(update,"test-secret");
    }
    private MaxConversationSession.State state() {return store.load("max",55L,1001L,MaxConversationSession.State.class).orElseThrow().payload();}
    private String choice(int index) {var saved=state();return MaxQuestionnaireChoiceSupport.buttonText(saved.selection().catalog(),saved.selection().nonce(),index);}
    private void chooseA() {send("start","/start");send("choose",choice(0));}
    private void answerFirst() {chooseA();send("first","One");}
    private ScopedRuntimeConfigUnavailableException unavailable() {return new ScopedRuntimeConfigUnavailableException("test unavailable");}

    @Test void menuIsDurableBeforeAnyQuestionAndDoesNotReadGlobalSettings() {
        send("start","/start");
        assertThat(state().scope()).isNull();assertThat(state().flow()).isEmpty();assertThat(state().answers()).isEmpty();
        assertThat(state().selection().catalog()).isEqualTo(catalog);
        verify(api).sendQuestionnaireChoices(1001L,catalog,state().selection().nonce());
        verifyNoInteractions(local);verify(tickets,never()).createConversationTicket(any());
        verify(messages,never()).sendToUser(any(),any(),contains("q-a first"));
    }
    @Test void restartRestoresTheExactPendingMenuThenTheChosenFirstQuestion() {
        send("start","Initial client problem");String code=choice(0);String nonce=state().selection().nonce();
        rebuild();send("choose",code);
        assertThat(state().scope()).isEqualTo(new QuestionnaireScope("BUSINESS","USER_SELECTION","q-a","a".repeat(64),7L,"c".repeat(64)));
        assertThat(state().selection().nonce()).isEqualTo(nonce);
        assertThat(state().flow()).extracting(item->item.getId()).containsExactly("free-first","free-second");
        assertThat(state().bootstrapProblemText()).isEqualTo("Initial client problem");
        assertThat(state().answers()).isEmpty();
        verify(messages).sendToUser(eq(channel),eq(1001L),startsWith("q-a first"));
        verify(messages,never()).sendToUser(any(),any(),contains("q-b first"));verifyNoInteractions(local);
    }
    @ParameterizedTest @ValueSource(strings={"foreign-nonce","foreign-business","label","number","overflow"})
    void malformedOrOldChoiceCannotSelectOrBecomeAQuestionAnswer(String kind) {
        send("start","/start");String nonce=state().selection().nonce();
        String code=switch(kind) {
            case "foreign-nonce"->"/business ffffffffffff 7 A";
            case "foreign-business"->"/business "+nonce+" 999 A";
            case "label"->"Business A";case "number"->"1";
            default->"/business "+nonce+" 9223372036854775808 A";
        };
        send("bad",code);assertThat(state().scope()).isNull();assertThat(state().answers()).isEmpty();
        verify(runtime,never()).selectQuestionnaire(any(),any(),anyLong());
    }
    @Test void staleCatalogNeedsExplicitRestartAndCannotRecaptureAnotherVersion() {
        send("start","/start");String code=choice(0);
        when(runtime.selectQuestionnaire(channel,catalog,7)).thenThrow(unavailable());
        assertThat(send("choose",code).getBody()).containsEntry("restart_questionnaire",true);
        assertThat(state().scope()).isNull();assertThat(state().selection().catalog().catalogSha256()).isEqualTo("c".repeat(64));
        verify(runtime,times(1)).questionnaireChoices(channel);verifyNoInteractions(local);
    }
    @Test void startingAgainInvalidatesOldButtonsEvenWhenTheCatalogHasNotChanged() {
        send("start","/start");String old=choice(0);
        send("restart","/start");assertThat(choice(0)).isNotEqualTo(old);
        send("late",old);assertThat(state().scope()).isNull();
        verify(runtime,never()).selectQuestionnaire(any(),any(),anyLong());
    }
    @Test void aRepeatedChoiceAfterTheFirstPromptDoesNotAnswerTheFirstQuestion() {
        send("start","/start");String code=choice(0);send("choose",code);
        rebuild();send("new-choice-delivery",code);
        assertThat(state().answers()).isEmpty();assertThat(state().currentIndex()).isZero();
        verify(runtime,times(1)).selectQuestionnaire(channel,catalog,7);
    }
    @Test void menuDeliveryFailureKeepsItsNonceAndReplayDoesNotFetchAFreshMenu() {
        when(api.sendQuestionnaireChoices(anyLong(),any(),anyString())).thenReturn(false).thenReturn(true);
        assertThatThrownBy(()->send("start","/start")).isInstanceOf(IllegalStateException.class);
        String nonce=state().selection().nonce();rebuild();send("start","/start");
        assertThat(state().selection().nonce()).isEqualTo(nonce);
        verify(runtime,times(1)).questionnaireChoices(channel);
    }
    @Test void questionDeliveryFailureKeepsTheChosenScopeAndChoiceReplayDoesNotAdvance() {
        send("start","/start");String code=choice(0);
        when(messages.sendToUser(eq(channel),eq(1001L),startsWith("q-a first"))).thenReturn(false).thenReturn(true);
        assertThatThrownBy(()->send("choose",code)).isInstanceOf(IllegalStateException.class);
        rebuild();send("choose",code);assertThat(state().scope().businessId()).isEqualTo(7);
        assertThat(state().answers()).isEmpty();
    }
    @Test void completeFlowProducesOnlyTheChosenOwnerAndFreeQuestionsWithoutBusinessOrProblemInjection() {
        answerFirst();rebuild();send("last","Two");
        var command=org.mockito.ArgumentCaptor.forClass(ConversationTicketCreationCommand.class);
        verify(tickets).createConversationTicket(command.capture());
        assertThat(command.getValue().businessId()).isEqualTo(7);
        assertThat(command.getValue().scopeSelectionSource()).isEqualTo("USER_SELECTION");
        assertThat(command.getValue().questionTemplateId()).isEqualTo("q-a");
        assertThat(command.getValue().templateSha256()).isEqualTo("a".repeat(64));
        assertThat(command.getValue().catalogSha256()).isEqualTo("c".repeat(64));
        assertThat(command.getValue().answers()).containsExactlyInAnyOrderEntriesOf(Map.of("free-first","One","free-second","Two"));
        assertThat(state().selection().submittedTicketId()).isEqualTo("T-MAX");
        verify(messages,never()).sendToSupportChat(any(),anyString());verifyNoInteractions(local);
    }
    @Test void failedPublicationKeepsTheFinalAnswerAndRetriesTheSameEventWithoutFreshBindings() {
        answerFirst();
        when(tickets.createConversationTicket(any())).thenThrow(new IllegalStateException("test publication failure"))
            .thenReturn(new TicketService.TicketCreationResult("T-MAX",null,"queued"));
        assertThatThrownBy(()->send("last","Two")).hasMessageContaining("publication failure");
        String event=state().selection().submissionEventKey();
        assertThat(state().answers()).containsEntry("free-second","Two");
        when(runtime.selectQuestionnaire(channel,catalog,7)).thenThrow(unavailable());
        rebuild();send("last","Two");
        var commands=org.mockito.ArgumentCaptor.forClass(ConversationTicketCreationCommand.class);
        verify(tickets,times(2)).createConversationTicket(commands.capture());
        assertThat(commands.getAllValues().get(0)).isEqualTo(commands.getAllValues().get(1));
        assertThat(commands.getValue().providerEventKey()).isEqualTo(event);
        assertThat(state().selection().submittedTicketId()).isEqualTo("T-MAX");
    }
    @Test void staleBeforeFreshSubmissionPreservesAnswersAndDoesNotEnqueueOrRecapture() {
        answerFirst();when(runtime.selectQuestionnaire(channel,catalog,7)).thenThrow(unavailable());
        assertThat(send("last","Two").getBody()).containsEntry("restart_questionnaire",true);
        assertThat(state().answers()).containsEntry("free-second","Two");assertThat(state().selection().submissionEventKey()).isNull();
        verify(tickets,never()).createConversationTicket(any());
    }
    @Test void finalDeliveryReplayAfterDedupCacheLossUsesTheSavedSubmissionTombstone() {
        answerFirst();send("last","Two");
        var coordination=new BotIngressCoordinationProperties();coordination.setMode("direct");
        @SuppressWarnings("unchecked") ObjectProvider<StringRedisTemplate> redis=mock(ObjectProvider.class);
        guard=new BotWebhookDeliveryGuardService(coordination,redis);rebuild();
        assertThat(send("last","Two").getBody()).containsEntry("ticket_id","T-MAX");
        verify(tickets,times(1)).createConversationTicket(any());
    }
    @Test void newClientMessageWaitsForItsCanonicalRootThenUsesOnlyThatTicketAndSuppressesUnverifiedChat() {
        answerFirst();send("last","Two");
        assertThat(send("followup","Followup").getStatusCode().value()).isEqualTo(503);
        var active=new TicketActive();active.setTicketId("T-MAX");
        when(tickets.findActiveTicketForUser(eq(1001L),anyString(),eq(55L))).thenReturn(Optional.of(active));
        when(tickets.findByTicketId("T-MAX")).thenReturn(Optional.of(new TicketService.TicketWithUser(1001L,"T-MAX","open")));
        assertThat(send("followup","Followup").getBody()).containsEntry("ticket_id","T-MAX");
        var command=org.mockito.ArgumentCaptor.forClass(ActiveInboundClientMessageCommand.class);
        verify(tickets).recordActiveClientMessage(command.capture());assertThat(command.getValue().ticketId()).isEqualTo("T-MAX");
        verify(messages,never()).sendToSupportChat(any(),anyString());
    }
    @Test void aDifferentActiveTicketCannotCaptureTheNewBusinessFollowup() {
        answerFirst();send("last","Two");
        var active=new TicketActive();active.setTicketId("OTHER");
        when(tickets.findActiveTicketForUser(eq(1001L),anyString(),eq(55L))).thenReturn(Optional.of(active));
        when(tickets.findByTicketId("OTHER")).thenReturn(Optional.of(new TicketService.TicketWithUser(1001L,"OTHER","open")));
        assertThat(send("followup","Followup").getStatusCode().value()).isEqualTo(503);
        verify(tickets,never()).recordActiveClientMessage(any());
    }
    @Test void queueAcceptanceBeforeDeliveryAckFailureStillCannotPublishTwice() {
        answerFirst();
        var broken=new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(call->{if(broken.getAndSet(false))throw new IllegalStateException("test ack failure");return call.callRealMethod();})
            .when(guard).markProcessed(any());
        assertThatThrownBy(()->send("last","Two")).hasMessageContaining("ack failure");
        assertThat(state().selection().submittedTicketId()).isEqualTo("T-MAX");
        rebuild();assertThat(send("last","Two").getBody()).containsEntry("ticket_id","T-MAX");
        verify(tickets,times(1)).createConversationTicket(any());
    }
    @Test void concurrentSelectionCannotBeOverwrittenByAnOlderCasWriter() {
        send("start","/start");String code=choice(0);
        var once=new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(call->{
            MaxConversationSession.State incoming=call.getArgument(4);
            if(incoming.scope()!=null && once.getAndSet(false)) {
                var current=new MaxConversationSession(state());
                var chosenSettings=settings.loadSelectedQuestionnaire(b);
                var other=current.choose(b,chosenSettings,GeneralQuestionnaireFlow.from(settings.questionFlow(chosenSettings)));
                store.save("max",55L,1001L,other.snapshot());
            }
            return call.callRealMethod();
        }).when(store).saveIfUnchanged(eq("max"),eq(55L),eq(1001L),anyString(),any());
        send("choose",code);
        assertThat(state().scope().businessId()).isEqualTo(8L);
        assertThat(state().answers()).isEmpty();verify(tickets,never()).createConversationTicket(any());
    }
    @ParameterizedTest @ValueSource(strings={"user","channel","chat","flow"})
    void corruptedSessionCannotCrossItsStoredOwnerOrChannel(String corruption) {
        chooseA();var tree=mapper.valueToTree(state());
        switch(corruption) {
            case "user"->((com.fasterxml.jackson.databind.node.ObjectNode)tree).put("userId",9999);
            case "channel"->((com.fasterxml.jackson.databind.node.ObjectNode)tree.at("/selection/catalog")).put("channelId",999);
            case "chat"->((com.fasterxml.jackson.databind.node.ObjectNode)tree).put("chatId",9999);
            case "flow"->((com.fasterxml.jackson.databind.node.ObjectNode)tree).set("flow",mapper.createArrayNode());
        }
        store.save("max",55L,1001L,tree);rebuild();
        if(corruption.equals("chat")) assertThat(send("unsafe","Answer").getStatusCode().value()).isEqualTo(409);
        else assertThatThrownBy(()->send("unsafe","Answer")).isInstanceOf(RuntimeException.class);
        verify(tickets,never()).createConversationTicket(any());verify(tickets,never()).recordActiveClientMessage(any());
    }
    @Test void anOldLegacySessionCannotContinueAfterTheChannelRequiresBusinessSelection() {
        var question=new com.example.supportbot.settings.dto.QuestionFlowItemDto("legacy","text","Private legacy",1,null,List.of());
        var legacy=new MaxConversationSession(1001L,2002L,"client","Client",List.of(question),null);
        store.save("max",55L,1001L,legacy.snapshot());
        assertThat(send("answer","Do not recapture").getBody()).containsEntry("restart_questionnaire",true);
        assertThat(state().answers()).isEmpty();assertThat(state().selection()).isNull();
        verify(tickets,never()).createConversationTicket(any());verifyNoInteractions(local);
    }
    @Test void cancellingDeletesPendingMenuAndTheOldButtonCannotStartANewQuestionnaire() {
        send("start","/start");String old=choice(0);send("cancel","/cancel");
        assertThat(store.load("max",55L,1001L,MaxConversationSession.State.class)).isEmpty();
        assertThat(send("late",old).getBody()).containsEntry("choice_ignored",true);
        verify(runtime,times(1)).questionnaireChoices(channel);
    }

    private void generalMenu() {
        var payload=selected(7L,"q-general","d");
        var general=new SelectedQuestionnaire(2,55,"GENERAL","USER_SELECTION",(Long)null,"q-general","d".repeat(64),"c".repeat(64),payload.botSettings(),Map.of(),Map.of());
        catalog=new ChoiceCatalog(2,55,"c".repeat(64),List.of(new Choice("BUSINESS",7,"a","Business A","q-a","a".repeat(64)),
            new Choice("GENERAL",(Long)null,"GENERAL","Без бизнеса","q-general","d".repeat(64))));
        doReturn(catalog).when(runtime).questionnaireChoices(channel);
        doReturn(general).when(runtime).selectGeneralQuestionnaire(channel,catalog);
    }

    @Test void generalChoiceRestartsAndPublishesNullOwnerWithOnlyItsFrozenFreeAnswers() {
        generalMenu();send("start","/start");String button=choice(1);assertThat(button).contains("general]");
        rebuild();send("choose",button);assertThat(state().scope().businessId()).isNull();
        send("late-button",button);assertThat(state().answers()).isEmpty();
        send("first","One");rebuild();send("last","Two");
        var capture=org.mockito.ArgumentCaptor.forClass(ConversationTicketCreationCommand.class);verify(tickets).createConversationTicket(capture.capture());
        var command=capture.getValue();assertThat(command.businessId()).isNull();assertThat(command.scopeKind()).isEqualTo("GENERAL");
        assertThat(command.scopeSelectionSource()).isEqualTo("USER_SELECTION");assertThat(command.questionTemplateId()).isEqualTo("q-general");
        assertThat(command.answers()).containsExactlyInAnyOrderEntriesOf(Map.of("free-first","One","free-second","Two"));
        assertThat(state().selection().submittedTicketId()).isEqualTo("T-MAX");verifyNoInteractions(local);verify(messages,never()).sendToSupportChat(any(),anyString());
    }
    @Test void generalPublicationFailureRetriesTheSameCommandAfterSessionRestore() {
        generalMenu();send("start","/start");send("choose",choice(1));send("first","One");
        when(tickets.createConversationTicket(any())).thenThrow(new IllegalStateException("general queue failure")).thenReturn(new TicketService.TicketCreationResult("T-MAX",null,"queued"));
        assertThatThrownBy(()->send("last","Two")).hasMessageContaining("general queue failure");
        doThrow(unavailable()).when(runtime).selectGeneralQuestionnaire(channel,catalog);rebuild();send("last","Edited retry");
        var capture=org.mockito.ArgumentCaptor.forClass(ConversationTicketCreationCommand.class);verify(tickets,times(2)).createConversationTicket(capture.capture());
        assertThat(capture.getAllValues().get(0)).isEqualTo(capture.getAllValues().get(1));assertThat(capture.getValue().businessId()).isNull();
    }
    @ParameterizedTest @ValueSource(strings={"choice","submission"})
    void generalStaleScopeNeverRecapturesOrPublishes(String boundary) {
        generalMenu();send("start","/start");
        if(boundary.equals("submission")){send("choose",choice(1));send("first","One");}
        doThrow(unavailable()).when(runtime).selectGeneralQuestionnaire(channel,catalog);
        var result=boundary.equals("choice")?send("choose",choice(1)):send("last","Two");
        assertThat(result.getBody()).containsEntry("restart_questionnaire",true);verify(tickets,never()).createConversationTicket(any());
    }


    @Test void oldGeneralCommandNeverSelectsAfterExplicitRestart() throws Exception {
        generalMenu();send("start","/start");String old="/general "+state().selection().nonce();send("restart","/start");send("late",old);assertThat(state().scope()).isNull();
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
        r150Menu();send("r150-start","/start");send("r150-choose",choice(0));rebuild();send("r150-3","Foreign B");assertThat(state().answers()).isEmpty();
        send("r150-4","North");rebuild();send("r150-5","South site");
        verify(tickets,never()).createConversationTicket(any());send("r150-6","North site");rebuild();send("r150-8","Kitchen");
        var capture=org.mockito.ArgumentCaptor.forClass(ConversationTicketCreationCommand.class);verify(tickets).createConversationTicket(capture.capture());
        assertThat(capture.getValue().businessId()).isEqualTo(7L);
        assertThat(capture.getValue().answers()).containsExactlyInAnyOrderEntriesOf(Map.of("city_answer","North","spot","North site","team","Kitchen"));
        verifyNoInteractions(local,feedback);
    }
    @Test void v3QueueRetryKeepsTheFrozenResourceAnswersAndPublicationIdentity() throws Exception {
        r150Menu();send("r150-start","/start");send("r150-choose",choice(0));send("r150-4","North");send("r150-6","North site");
        when(tickets.createConversationTicket(any())).thenThrow(new IllegalStateException("r150 queue failure"))
            .thenReturn(new TicketService.TicketCreationResult("R150-T",null,"queued"));
        assertThatThrownBy(()->send("r150-8","Kitchen")).hasMessageContaining("r150 queue failure");
        doThrow(unavailable()).when(runtime).selectQuestionnaire(channel,catalog,7L);rebuild();send("r150-8","Edited retry");
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
        send("v3-start","/start");send("v3-general",choice(1));rebuild();send("v3-first","One");rebuild();send("v3-last","Two");
        var capture=org.mockito.ArgumentCaptor.forClass(ConversationTicketCreationCommand.class);verify(tickets).createConversationTicket(capture.capture());
        assertThat(capture.getValue().scopeKind()).isEqualTo("GENERAL");assertThat(capture.getValue().businessId()).isNull();
        assertThat(capture.getValue().answers()).containsExactlyInAnyOrderEntriesOf(Map.of("free-first","One","free-second","Two"));verifyNoInteractions(local,feedback);
    }

    @ParameterizedTest @ValueSource(strings={"North","South"})
    void v3PresetRoutesFollowFrozenLabelsAfterRestore(String city) throws Exception {
        r150Menu();
        @SuppressWarnings("unchecked") var template=(Map<String,Object>)((List<?>)a.botSettings().get("question_templates")).get(0);
        @SuppressWarnings("unchecked") var flow=new ArrayList<>((List<Map<String,Object>>)template.get("question_flow"));
        flow.set(0,new HashMap<>(flow.get(0)));flow.get(0).put("routes",List.of(Map.of("value_id",city,"next_question_id","team")));
        a=new SelectedQuestionnaire(3,55,"BUSINESS","USER_SELECTION",7L,"q-a","a".repeat(64),"c".repeat(64),
            Map.of("active_template_id","q-a","question_templates",List.of(Map.of("id","q-a","name","A","question_flow",flow))),a.locationTree(),a.presetDefinitions());
        doReturn(a).when(runtime).selectQuestionnaire(channel,catalog,7L);
        send("r151-start","/start");send("r151-choose",choice(0));rebuild();
        if(city.equals("South")) {
            send("old-city","North");send("old-spot","North site");rebuild();
            send("back-spot","Назад");rebuild();send("back-city","Назад");rebuild();
            assertThat(state().answers()).isEmpty();
        }
        send("r151-city",city);rebuild();send("r151-team","Kitchen");
        var capture=org.mockito.ArgumentCaptor.forClass(ConversationTicketCreationCommand.class);verify(tickets).createConversationTicket(capture.capture());
        assertThat(capture.getValue().answers()).containsExactlyInAnyOrderEntriesOf(Map.of("city_answer",city,"team","Kitchen"));
        assertThat(capture.getValue().attributes()).extracting(TicketService.TicketAttributeInput::questionId).containsExactly("city-q","team");
        verifyNoInteractions(local,feedback);
    }

}
