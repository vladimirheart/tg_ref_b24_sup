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
    private final AttachmentService attachments=mock(AttachmentService.class);
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
        var coordination=coordinationProperties();
        var redis=redisProvider();
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
    protected BotIngressCoordinationProperties coordinationProperties() {
        var properties=new BotIngressCoordinationProperties();properties.setMode("direct");return properties;
    }
    @SuppressWarnings("unchecked")
    protected ObjectProvider<StringRedisTemplate> redisProvider() {return mock(ObjectProvider.class);}
    protected void discardDeliveryCache() { /* Direct mode loses its local cache with the guard instance. */ }
    protected void rebuild() {
        controller=new MaxWebhookController(properties,blacklist,channels,tickets,mock(ChatHistoryService.class),messages,
            feedback,settings,ingress,guard,store,runtime,attachments,api,mock(MaxClientAvatarSyncService.class),mapper);
    }
    protected ResponseEntity<Map<String,Object>> send(String id,String text) {
        var update=mapper.valueToTree(Map.of("update_type","message_created","update_id",id,"message",
            Map.of("sender",Map.of("user_id",1001,"username","client","name","Client"),
                "recipient",Map.of("chat_id",2002),"body",Map.of("text",text))));
        return controller.handleUpdate(update,"test-secret");
    }
    protected MaxConversationSession.State state() {return store.load("max",55L,1001L,MaxConversationSession.State.class).orElseThrow().payload();}
    protected ResponseEntity<Map<String,Object>> handleFollowupUpdate(com.fasterxml.jackson.databind.JsonNode update) {
        return controller.handleUpdate(update,"test-secret");
    }
    protected FollowupFixture followupFixture() {
        return new FollowupFixture(channel,runtime,tickets,messages,api,attachments,store);
    }
    protected record FollowupFixture(Channel channel,RuntimeConfigService runtime,TicketService tickets,
            MessagingService messages,MaxApiClient api,AttachmentService attachments,BotSessionStoreService store) {}
    private ResponseEntity<Map<String,Object>> r271Media(String id,String text,boolean forwarded) {
        return r271Media(id,text,forwarded,2002L);
    }
    private ResponseEntity<Map<String,Object>> r271Media(String id,String text,boolean forwarded,long chatId) {
        var body=Map.of("text",text,"attachments",List.of(Map.of("type","file","payload",Map.of("url","https://example.invalid/private.pdf"),"name","private.pdf")));
        var message=new LinkedHashMap<String,Object>();
        message.put("sender",Map.of("user_id",1001,"username","client","name","Client"));
        message.put("recipient",Map.of("chat_id",chatId));
        message.put("body",forwarded?Map.of():body);
        if(forwarded)message.put("link",Map.of("type","forward","message",Map.of("body",body)));
        return controller.handleUpdate(mapper.valueToTree(Map.of("update_type","message_created","update_id",id,"message",message)),"test-secret");
    }
    private ResponseEntity<Map<String,Object>> r271OpaqueMedia(String id,boolean forwarded) {
        var body=Map.of("text","Do not consume this private caption","attachments",List.of(Map.of("type","file","payload",Map.of("token","private-without-download-url"))));
        var message=new LinkedHashMap<String,Object>();message.put("sender",Map.of("user_id",1001,"username","client","name","Client"));
        message.put("recipient",Map.of("chat_id",2002));message.put("body",forwarded?Map.of():body);
        if(forwarded)message.put("link",Map.of("type","forward","message",Map.of("body",body)));
        return controller.handleUpdate(mapper.valueToTree(Map.of("update_type","message_created","update_id",id,"message",message)),"test-secret");
    }
    private String choice(int index) {var saved=state();return MaxQuestionnaireChoiceSupport.buttonText(saved.selection().catalog(),saved.selection().nonce(),index);}
    private void chooseA() {send("start","/start");send("choose",choice(0));}
    private void answerFirst() {chooseA();send("first","One");}
    private ScopedRuntimeConfigUnavailableException unavailable() {return new ScopedRuntimeConfigUnavailableException("test unavailable");}

    @Test void menuIsDurableBeforeAnyQuestionAndDoesNotReadGlobalSettings() {
        send("start","/start");
        assertThat(mapper.valueToTree(state()).at("/selection/page").isMissingNode()).isTrue();
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
        discardDeliveryCache();
        var coordination=coordinationProperties();
        var redis=redisProvider();
        guard=new BotWebhookDeliveryGuardService(coordination,redis);rebuild();
        assertThat(send("last","Two").getBody()).containsEntry("ticket_id","T-MAX");
        verify(tickets,times(1)).createConversationTicket(any());
    }
    @Test void newClientMessageWaitsForItsCanonicalRootThenUsesOnlyThatTicketAndSuppressesUnverifiedChat() {
        answerFirst();send("last","Two");
        assertThat(send("followup","Followup").getStatusCode().value()).isEqualTo(503);
        var active=new TicketActive();active.setTicketId("T-MAX");
        when(tickets.findActiveTicketForUser(eq(1001L),anyString(),eq(55L))).thenReturn(Optional.of(active));
        when(tickets.findByTicketId("T-MAX")).thenReturn(Optional.of(new TicketService.TicketWithUser(1001L,"T-MAX","open",55L,a.version(),1)));
        assertThat(send("followup","Followup").getBody()).containsEntry("ticket_id","T-MAX");
        var command=org.mockito.ArgumentCaptor.forClass(ActiveInboundClientMessageCommand.class);
        verify(tickets).recordActiveClientMessage(command.capture());assertThat(command.getValue().ticketId()).isEqualTo("T-MAX");
        verify(messages,never()).sendToSupportChat(any(),anyString());
    }
    @Test void aDifferentActiveTicketCannotCaptureTheNewBusinessFollowup() {
        answerFirst();send("last","Two");
        var active=new TicketActive();active.setTicketId("OTHER");
        when(tickets.findActiveTicketForUser(eq(1001L),anyString(),eq(55L))).thenReturn(Optional.of(active));
        when(tickets.findByTicketId("OTHER")).thenReturn(Optional.of(new TicketService.TicketWithUser(1001L,"OTHER","open",55L,a.version(),1)));
        assertThat(send("followup","Followup").getStatusCode().value()).isEqualTo(503);
        verify(tickets,never()).recordActiveClientMessage(any());
    }
    @Test void intermediateAnswerAckFailureMustNotAnswerTheNextQuestion() {
        chooseA();
        var broken=new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(call->{if(broken.getAndSet(false))throw new IllegalStateException("test intermediate ack failure");return call.callRealMethod();})
            .when(guard).markProcessed(any());
        assertThatThrownBy(()->send("first","One")).hasMessageContaining("intermediate ack failure");
        assertThat(state().answers()).containsExactlyEntriesOf(Map.of("free-first","One"));
        rebuild();
        assertThat(send("first","Changed retry").getBody()).containsEntry("questionnaire_replayed",true);
        assertThat(state().answers()).containsExactlyEntriesOf(Map.of("free-first","One"));
        assertThat(state().currentIndex()).isEqualTo(1);
        verify(tickets,never()).createConversationTicket(any());
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

    private void r152LargeMenu(boolean includeGeneral) {
        r150Menu();a=new SelectedQuestionnaire(4,55,"BUSINESS","USER_SELECTION",7L,"q-a",a.templateSha256(),a.catalogSha256(),a.botSettings(),a.locationTree(),a.presetDefinitions());
        var choices=new ArrayList<Choice>();for(long id=100;id<203;id++)choices.add(new Choice("BUSINESS",id,"b-"+id,"Business "+id,"q-"+id,"a".repeat(64),"b".repeat(64)));
        choices.add(new Choice("BUSINESS",7L,"a","Business A","q-a",a.templateSha256(),SelectedQuestionnaireOptions.resourceSha256(a.locationTree(),a.presetDefinitions())));
        var payload=selected(7L,"q-general","d");
        var general=new SelectedQuestionnaire(4,55,"GENERAL","USER_SELECTION",(Long)null,"q-general","d".repeat(64),"c".repeat(64),payload.botSettings(),Map.of(),Map.of());
        if(includeGeneral)choices.add(new Choice("GENERAL",(Long)null,"GENERAL","Без бизнеса","q-general","d".repeat(64),SelectedQuestionnaireOptions.resourceSha256(Map.of(),Map.of())));
        catalog=new ChoiceCatalog(4,55,"c".repeat(64),choices);doReturn(catalog).when(runtime).questionnaireChoices(channel);
        doReturn(a).when(runtime).selectQuestionnaire(channel,catalog,7L);doReturn(general).when(runtime).selectGeneralQuestionnaire(channel,catalog);doReturn(true).when(api).sendQuestionnaireChoices(eq(1001L),any(),anyString(),anyInt());
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void v4LastPageRestoresThenSelectsItsOwnFrozenBusinessOrGeneral(boolean general) throws Exception {
        r152LargeMenu(general);send("start","/start");String nonce=state().selection().nonce();int page=general?13:12;
        send("page",MaxQuestionnaireChoiceSupport.pageButton(nonce,page,true));rebuild();assertThat(state().selection().page()).isEqualTo(page);
        verify(api).sendQuestionnaireChoices(1001L,catalog,nonce,page);
        send("choose",choice(general?104:103));rebuild();send("old-page",MaxQuestionnaireChoiceSupport.pageButton(nonce,0,false));assertThat(state().answers()).isEmpty();
        send("first",general?"One":"North");rebuild();send("second",general?"Two":"North site");if(!general){rebuild();send("third","Kitchen");}
        var capture=org.mockito.ArgumentCaptor.forClass(ConversationTicketCreationCommand.class);verify(tickets).createConversationTicket(capture.capture());
        assertThat(capture.getValue().scopeKind()).isEqualTo(general?"GENERAL":"BUSINESS");assertThat(capture.getValue().businessId()).isEqualTo(general?null:7L);
        assertThat(capture.getValue().catalogSha256()).isEqualTo(catalog.catalogSha256());assertThat(state().selection().page()).isEqualTo(page);
        verify(runtime,times(1)).questionnaireChoices(channel);verifyNoInteractions(local,feedback);
    }
    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> r271MediaCases() {
        return java.util.stream.Stream.of(false,true).flatMap(general->
            java.util.stream.Stream.of("menu","answer","followup").flatMap(phase->
                java.util.stream.Stream.of("caption","empty","forwarded").map(kind->
                    org.junit.jupiter.params.provider.Arguments.of(general,phase,kind))));
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("r271MediaCases")
    void v4AttachmentCannotBecomeAnAnswerOrEnterLegacyFollowup(boolean general,String phase,String kind) throws Exception {
        r268Choose(4,general);
        if("menu".equals(phase))send("r271-new-menu","/start");
        if("followup".equals(phase)) {
            send("r271-first","One");send("r271-second","Two");send("r271-last","Three");
            var active=new TicketActive();active.setTicketId("T-MAX");
            when(tickets.findActiveTicketForUser(eq(1001L),anyString(),eq(55L))).thenReturn(Optional.of(active));
            when(tickets.findByTicketId("T-MAX")).thenReturn(Optional.of(new TicketService.TicketWithUser(1001L,"T-MAX","open",55L,a.version(),1)));
        }
        String caption="menu".equals(phase)?choice(general?1:0):"Do not consume this caption";
        if("empty".equals(kind))caption="";
        String before=r268Raw();clearInvocations(tickets,messages,api,attachments);
        assertThat(r271Media("r271-media",caption,"forwarded".equals(kind)).getBody()).containsEntry("questionnaire_media_unsupported",true);
        assertThat(r268Raw()).isEqualTo(before);
        r268ResetDeliveryCache();
        assertThat(r271Media("r271-media",caption,"forwarded".equals(kind)).getBody()).containsEntry("questionnaire_media_unsupported",true);
        assertThat(r268Raw()).isEqualTo(before);
        verify(messages,times(2)).sendToUser(eq(channel),eq(1001L),contains("Вложение не сохранено"));
        verifyNoInteractions(attachments);verify(api,never()).downloadAttachment(anyString());
        verify(tickets,never()).recordActiveClientMessage(any());verify(tickets,never()).createConversationTicket(any());
        if("menu".equals(phase))send("r271-text",choice(general?1:0));
        else send("r271-text","Text without attachment");
        if("followup".equals(phase))verify(tickets).recordActiveClientMessage(any());
        else if("menu".equals(phase))assertThat(state().scope().scopeKind()).isEqualTo(general?"GENERAL":"BUSINESS");
        else assertThat(state().answers()).containsEntry("free-first","Text without attachment");
        verifyNoInteractions(local,feedback);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void firstV4AttachmentStartsOnlyAMenuWithoutCapturingItsCaption(boolean general) throws Exception {
        r268Choose(4,general);send("r271-cancel","/cancel");clearInvocations(messages,api,attachments,tickets);
        r271Media("r271-first-media","Private attachment caption",true);
        assertThat(state().scope()).isNull();assertThat(state().answers()).isEmpty();
        assertThat(state().bootstrapProblemText()).isNullOrEmpty();
        assertThat(state().history()).isEmpty();
        verify(messages).sendToUser(eq(channel),eq(1001L),contains("Вложение не сохранено"));
        verifyNoInteractions(attachments);verify(api,never()).downloadAttachment(anyString());
        verify(tickets,never()).createConversationTicket(any());verify(tickets,never()).recordActiveClientMessage(any());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void failedV4MediaWarningIsRetriedWithoutSavingProgress(boolean general) {
        r268Choose(4,general);String before=r268Raw();
        doReturn(false).doReturn(true).when(messages).sendToUser(eq(channel),eq(1001L),contains("Вложение не сохранено"));
        assertThatThrownBy(()->r271Media("r271-warning","Caption",false)).hasMessageContaining("media warning");
        assertThat(r268Raw()).isEqualTo(before);
        assertThat(r271Media("r271-warning","Caption",false).getBody()).containsEntry("questionnaire_media_unsupported",true);
        assertThat(r268Raw()).isEqualTo(before);verifyNoInteractions(attachments);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void v4MediaCannotBypassTheExistingSessionChatBoundary(boolean general) {
        r268Choose(4,general);String before=r268Raw();clearInvocations(messages,api,attachments,tickets);
        when(blacklist.resolveStatus(eq(1001L),anyString(),any(),eq("9999")))
            .thenReturn(new BlacklistService.ResolvedBlacklistStatus(null,new BlacklistService.BlacklistStatus(false,false)));
        assertThat(r271Media("r271-foreign-chat","Caption",false,9999L).getStatusCode().value()).isEqualTo(409);
        assertThat(r268Raw()).isEqualTo(before);verifyNoInteractions(messages,api,attachments,tickets);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void firstV4MediaWarningFailureRestoresTheMenuAndRetriesTheSameEvent(boolean explicitStart) {
        r268Choose(4,false);send("r271-cancel","/cancel");
        doReturn(false).doReturn(true).when(messages).sendToUser(eq(channel),eq(1001L),contains("Вложение не сохранено"));
        assertThatThrownBy(()->r271Media("r271-first-warning",explicitStart?"/start":"Caption",false)).hasMessageContaining("media warning");
        String before=r268Raw();assertThat(state().scope()).isNull();
        assertThat(r271Media("r271-first-warning",explicitStart?"/start":"Caption",false).getBody()).containsEntry("questionnaire_media_unsupported",true);
        assertThat(r268Raw()).isEqualTo(before);assertThat(state().history()).isEmpty();verifyNoInteractions(attachments);
    }
    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> r271OpaqueCases() {
        return java.util.stream.Stream.of(false,true).flatMap(general->java.util.stream.Stream.of(false,true)
            .map(forwarded->org.junit.jupiter.params.provider.Arguments.of(general,forwarded)));
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("r271OpaqueCases")
    void v4OpaqueAttachmentsDoNotBecomeDraftOrBootstrapAnswers(boolean general,boolean forwarded) {
        r268Choose(4,general);String before=r268Raw();
        assertThat(r271OpaqueMedia("r271-opaque",forwarded).getBody()).containsEntry("questionnaire_media_unsupported",true);
        assertThat(r268Raw()).isEqualTo(before);send("r271-opaque-cancel","/cancel");
        assertThat(r271OpaqueMedia("r271-opaque-start",forwarded).getBody()).containsEntry("questionnaire_media_unsupported",true);
        assertThat(state().scope()).isNull();assertThat(state().history()).isEmpty();
        assertThat(state().bootstrapProblemText()).isNullOrEmpty();verifyNoInteractions(attachments);
        verify(tickets,never()).recordActiveClientMessage(any());verify(tickets,never()).createConversationTicket(any());
    }
    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> r268ReplayCases() {
        return java.util.stream.IntStream.rangeClosed(1,4).boxed().flatMap(version->
            java.util.stream.Stream.of(false,true).filter(general->!general || version>=2).flatMap(general->
                java.util.stream.Stream.of("ack","prompt","dedup-reset").map(failure->
                    org.junit.jupiter.params.provider.Arguments.of(version,general,failure))));
    }
    protected void r268Choose(int version,boolean general) {
        String id=general?"q-general":"q-a";Long owner=general?null:7L;
        var flow=List.of(
            Map.of("id","free-third","type","text","order",30,"text","Third","required",true),
            Map.of("id","free-first","type","text","order",10,"text","First","required",true),
            Map.of("id","free-second","type","text","order",20,"text","Second","required",false));
        var tree=version>=3 && !general?Map.<String,Object>of("businessId",7L,"locations",List.of()):Map.<String,Object>of();
        var presets=version>=3 && !general?Map.<String,Object>of("businessId",7L,"groups",Map.of()):Map.<String,Object>of();
        a=new SelectedQuestionnaire(version,55,general?"GENERAL":"BUSINESS","USER_SELECTION",owner,id,"a".repeat(64),"c".repeat(64),
            Map.of("active_template_id",id,"question_templates",List.of(Map.of("id",id,"name",id,"question_flow",flow))),tree,presets);
        var resourceSha=version>=3?SelectedQuestionnaireOptions.resourceSha256(tree,presets):null;
        var choices=new ArrayList<Choice>();
        if(general) choices.add(new Choice("BUSINESS",8L,"b","Business B","q-b","b".repeat(64),version>=3?
            SelectedQuestionnaireOptions.resourceSha256(Map.of("businessId",8L,"locations",List.of()),Map.of("businessId",8L,"groups",Map.of())):null));
        choices.add(new Choice(a.scopeKind(),owner,general?"GENERAL":"a",general?"Без бизнеса":"Business A",id,"a".repeat(64),resourceSha));
        catalog=new ChoiceCatalog(version,55,"c".repeat(64),choices);
        doReturn(catalog).when(runtime).questionnaireChoices(channel);
        if(general) doReturn(a).when(runtime).selectGeneralQuestionnaire(channel,catalog);
        else doReturn(a).when(runtime).selectQuestionnaire(channel,catalog,7L);
        doReturn(true).when(api).sendQuestionnaireChoices(anyLong(),any(),anyString(),anyInt());
        send("r268-start","/start");send("r268-choose",choice(general?1:0));
    }
    private void r268Failure(String failure,String prompt) {
        if("ack".equals(failure)) {
            var broken=new java.util.concurrent.atomic.AtomicBoolean(true);
            doAnswer(call->{if(broken.getAndSet(false))throw new IllegalStateException("r268 ack failure");return call.callRealMethod();})
                .when(guard).markProcessed(any());
        } else if("prompt".equals(failure)) {
            doReturn(false).doReturn(true).when(messages).sendToUser(eq(channel),eq(1001L),startsWith(prompt));
        }
    }
    protected void r268ResetDeliveryCache() {
        discardDeliveryCache();
        var coordination=coordinationProperties();
        var redis=redisProvider();
        guard=spy(new BotWebhookDeliveryGuardService(coordination,redis));rebuild();
    }
    protected String r268Raw() {return store.load("max",55L,1001L,MaxConversationSession.State.class).orElseThrow().rawPayload();}
    private void r268CommitOrFail(String failure,String key,String answer) {
        if("dedup-reset".equals(failure)) assertThat(send(key,answer).getStatusCode().value()).isEqualTo(200);
        else assertThatThrownBy(()->send(key,answer)).isInstanceOf(IllegalStateException.class);
    }
    protected void r268AssertCommand(boolean general,Map<String,String> answers) {
        var capture=org.mockito.ArgumentCaptor.forClass(ConversationTicketCreationCommand.class);
        verify(tickets,times(1)).createConversationTicket(capture.capture());
        assertThat(capture.getValue().answers()).containsExactlyInAnyOrderEntriesOf(answers);
        assertThat(capture.getValue().scopeKind()).isEqualTo(general?"GENERAL":"BUSINESS");
        assertThat(capture.getValue().businessId()).isEqualTo(general?null:7L);
        assertThat(capture.getValue().catalogSha256()).isEqualTo(catalog.catalogSha256());
        assertThat(capture.getValue().templateSha256()).isEqualTo(a.templateSha256());
        assertThat(capture.getValue().questionTemplateId()).isEqualTo(a.questionTemplateId());
        verify(messages,never()).sendToSupportChat(any(),anyString());verifyNoInteractions(local,feedback);
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("r268ReplayCases")
    void intermediateAnswerReplayKeepsFrozenScopeAndProgressAfterRestore(int version,boolean general,String failure) {
        r268Choose(version,general);r268Failure(failure,"Second");
        r268CommitOrFail(failure,"r268-first","One");
        assertThat(state().currentIndex()).isEqualTo(1);
        assertThat(state().answers()).containsExactlyEntriesOf(Map.of("free-first","One"));
        String raw=r268Raw();r268ResetDeliveryCache();
        assertThat(send("r268-first","Changed retry").getBody()).containsEntry("questionnaire_replayed",true);
        assertThat(r268Raw()).isEqualTo(raw);verify(tickets,never()).createConversationTicket(any());
        send("r268-second","Two");send("r268-last","Three");
        r268AssertCommand(general,Map.of("free-first","One","free-second","Two","free-third","Three"));
        assertThat(state().selection().submissionEventKey()).isEqualTo("update:r268-last");
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("r268ReplayCases")
    void backReplayCannotStepBackTwiceOrEraseAnotherAnswer(int version,boolean general,String failure) {
        r268Choose(version,general);send("r268-first","One");send("r268-second","Two");
        r268Failure(failure,"Second");r268CommitOrFail(failure,"r268-back","Назад");
        assertThat(state().currentIndex()).isEqualTo(1);
        assertThat(state().answers()).containsExactlyEntriesOf(Map.of("free-first","One"));
        String raw=r268Raw();r268ResetDeliveryCache();
        assertThat(send("r268-back","Назад").getBody()).containsEntry("questionnaire_replayed",true);
        assertThat(r268Raw()).isEqualTo(raw);verify(tickets,never()).createConversationTicket(any());
        send("r268-corrected","Corrected");send("r268-last","Three");
        r268AssertCommand(general,Map.of("free-first","One","free-second","Corrected","free-third","Three"));
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("r268ReplayCases")
    void optionalSkipReplayCannotSkipTheFollowingRequiredQuestion(int version,boolean general,String failure) {
        r268Choose(version,general);send("r268-first","One");r268Failure(failure,"Third");
        r268CommitOrFail(failure,"r268-skip","Пропустить");
        assertThat(state().currentIndex()).isEqualTo(2);
        assertThat(state().answers()).containsExactlyInAnyOrderEntriesOf(Map.of("free-first","One","free-second",""));
        String raw=r268Raw();r268ResetDeliveryCache();
        assertThat(send("r268-skip","Пропустить").getBody()).containsEntry("questionnaire_replayed",true);
        assertThat(r268Raw()).isEqualTo(raw);verify(tickets,never()).createConversationTicket(any());
        send("r268-last","Three");r268AssertCommand(general,Map.of("free-first","One","free-second","","free-third","Three"));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void finalAnswerCasCommitBeforeFailureKeepsOriginalSubmissionEventOnAnotherDelivery(boolean general) {
        r268Choose(4,general);send("r268-first","One");send("r268-second","Two");
        var broken=new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(call->{
            MaxConversationSession.State incoming=call.getArgument(4);Object result=call.callRealMethod();
            if(incoming.currentIndex()==3 && incoming.selection().submissionEventKey()==null && broken.getAndSet(false))
                throw new IllegalStateException("r268 saved final answer before reply loss");
            return result;
        }).when(store).saveIfUnchanged(eq("max"),eq(55L),eq(1001L),anyString(),any());
        assertThatThrownBy(()->send("r268-last","Three")).hasMessageContaining("saved final answer");
        assertThat(state().selection().submissionEventKey()).isNull();verify(tickets,never()).createConversationTicket(any());
        r268ResetDeliveryCache();
        assertThat(send("r268-new-message","Do not consume as final answer").getStatusCode().value()).isEqualTo(503);
        assertThat(state().selection().submissionEventKey()).isEqualTo("update:r268-last");
        assertThat(send("r268-last","Changed final answer").getBody()).containsEntry("ticket_id","T-MAX");
        r268AssertCommand(general,Map.of("free-first","One","free-second","Two","free-third","Three"));
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void ambiguousAnswerCasCommitRetriesTheSameEventWithoutAdvancingAgain(boolean general) {
        r268Choose(4,general);
        var broken=new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(call->{
            MaxConversationSession.State incoming=call.getArgument(4);Object result=call.callRealMethod();
            if(incoming.currentIndex()==1 && incoming.selection().acceptedEventKeys().contains("update:r268-first") && broken.getAndSet(false))
                throw new SessionStateConflictException("r268 concurrent writer already committed this answer");
            return result;
        }).when(store).saveIfUnchanged(eq("max"),eq(55L),eq(1001L),anyString(),any());
        assertThat(send("r268-first","One").getBody()).containsEntry("questionnaire_replayed",true);
        assertThat(state().currentIndex()).isEqualTo(1);
        assertThat(state().history()).hasSize(1);
        assertThat(state().answers()).containsExactlyEntriesOf(Map.of("free-first","One"));
        verify(tickets,never()).createConversationTicket(any());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void olderAcceptedAnswerAfterTwoQuestionsCannotOverwriteOrFinalize(boolean general) {
        r268Choose(4,general);send("r268-first","One");send("r268-second","Two");
        String raw=r268Raw();r268ResetDeliveryCache();
        assertThat(send("r268-first","Changed old answer").getBody()).containsEntry("questionnaire_replayed",true);
        assertThat(state().currentIndex()).isEqualTo(2);assertThat(r268Raw()).isEqualTo(raw);
        verify(tickets,never()).createConversationTicket(any());
    }
    @Test void v4PageDeliveryFailureKeepsThePageAndNonceBeforeRetry() {
        r152LargeMenu(false);send("start","/start");String nonce=state().selection().nonce();
        doReturn(false).doReturn(true).when(api).sendQuestionnaireChoices(1001L,catalog,nonce,1);
        String button=MaxQuestionnaireChoiceSupport.pageButton(nonce,1,true);
        assertThatThrownBy(()->send("page",button)).hasMessageContaining("menu delivery");assertThat(state().selection().page()).isEqualTo(1);
        rebuild();send("page",button);assertThat(state().selection().nonce()).isEqualTo(nonce);assertThat(state().answers()).isEmpty();
        verify(runtime,times(1)).questionnaireChoices(channel);verify(tickets,never()).createConversationTicket(any());
    }

}
