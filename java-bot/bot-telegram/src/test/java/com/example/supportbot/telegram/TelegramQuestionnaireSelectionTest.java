package com.example.supportbot.telegram;

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
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/** Real SupportBot ingress and selected handler; JSON/CAS and guard, no external SDK writes. */
class TelegramQuestionnaireSelectionTest {
    private final ObjectMapper mapper=Jackson2ObjectMapperBuilder.json().build();
    private final Channel channel=new Channel();
    private final RuntimeConfigService runtime=mock(RuntimeConfigService.class);
    private final SharedConfigService local=mock(SharedConfigService.class);
    private final TicketService tickets=mock(TicketService.class);
    private final ChannelService channels=mock(ChannelService.class);
    private final BlacklistService blacklist=mock(BlacklistService.class);
    private final AttachmentService attachments=mock(AttachmentService.class);
    private final FeedbackService feedback=mock(FeedbackService.class);
    private final List<SendMessage> sent=new ArrayList<>();
    private BotSessionStoreService store;
    private BotWebhookDeliveryGuardService guard;
    private BotSettingsService settings;
    private TelegramQuestionnaireHandler handler;
    private SupportBot bot;
    private BotProperties properties;
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path mediaRoot;
    private byte[] mediaBytes="R153 owned attachment".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private ChoiceCatalog catalog;
    private SelectedQuestionnaire a,b;
    @BeforeEach void setup() throws Exception {
        channel.setId(55L);channel.setPlatform("telegram");channel.setActive(true);channel.setSupportChatId("unverified-shared-chat");
        properties=new BotProperties();properties.setToken("test-telegram-token");properties.setChannelId(900077L);
        var coordination=new BotIngressCoordinationProperties();coordination.setMode("direct");
        @SuppressWarnings("unchecked") ObjectProvider<StringRedisTemplate> redis=mock(ObjectProvider.class);
        store=spy(new BotSessionStoreService(coordination,mapper,redis));guard=spy(new BotWebhookDeliveryGuardService(coordination,redis));
        settings=new BotSettingsService(mapper,local,runtime);
        catalog=new ChoiceCatalog(1,55,"c".repeat(64),List.of(new Choice("BUSINESS",7,"a","Business A","q-a","a".repeat(64)),
                new Choice("BUSINESS",8,"b","Business B","q-b","b".repeat(64))));
        a=selected(7,"q-a","a",false);b=selected(8,"q-b","b",false);
        when(channels.ensurePublicIdForToken("test-telegram-token")).thenReturn(channel);
        when(blacklist.getStatus(1001L)).thenReturn(new BlacklistService.BlacklistStatus(false,false));
        when(runtime.findChannelConfig(channel)).thenThrow(unavailable());when(runtime.questionnaireChoices(channel)).thenReturn(catalog);
        when(runtime.selectQuestionnaire(channel,catalog,7L)).thenReturn(a);when(runtime.selectQuestionnaire(channel,catalog,8L)).thenReturn(b);
        when(tickets.createConversationTicket(any())).thenReturn(new TicketService.TicketCreationResult("T-TG",null,"queued"));
        when(tickets.awaitClientTicketNumber(any())).thenReturn(Optional.empty());rebuild();
    }
    private SelectedQuestionnaire selected(long owner,String id,String hash,boolean branching) {
        List<Map<String,Object>> flow=branching?List.of(
                Map.of("id","choice","type","select","order",10,"text","Choose","options",List.of(Map.of("id","yes","label","Да"),Map.of("id","no","label","Нет")),
                    "routes",List.of(Map.of("value_id","yes","next_question_id","last"))),
                Map.of("id","optional","type","text","order",20,"text","Optional","required",false),
                Map.of("id","last","type","text","order",30,"text","Last")):
                List.of(Map.of("id","free-second","type","text","order",20,"text",id+" second"),
                        Map.of("id","free-first","type","text","order",10,"text",id+" first"));
        return new SelectedQuestionnaire(1,55,"BUSINESS","USER_SELECTION",owner,id,hash.repeat(64),"c".repeat(64),
                Map.of("active_template_id",id,"question_templates",List.of(Map.of("id",id,"name",id,"question_flow",flow))),Map.of(),Map.of());
    }
    private void rebuild() throws Exception {
        handler=new TelegramQuestionnaireHandler(runtime,settings,store,tickets,blacklist,attachments);
        bot=spy(new SupportBot(properties,blacklist,mock(UnblockRequestService.class),attachments,channels,settings,tickets,
                mock(ChatHistoryService.class),feedback,mock(BotIngressCoordinationService.class),guard,runtime,handler,mapper));
        doAnswer(call->{sent.add(call.getArgument(0));return new Message();}).when(bot).execute(any(SendMessage.class));
        doAnswer(call->new java.io.ByteArrayInputStream(mediaBytes)).when(bot).fetchFile(anyString());
        doAnswer(call->new AttachmentService(mediaRoot,new ObjectStorageProperties()).storeQuestionnaire(call.getArgument(0),call.getArgument(1),call.getArgument(2),call.getArgument(3),call.getArgument(4),call.getArgument(5),call.getArgument(6)))
            .when(attachments).storeQuestionnaire(anyLong(),nullable(Long.class),anyLong(),anyString(),anyLong(),anyString(),any(java.io.InputStream.class));
    }
    private void resetDeliveryCache() {
        var config=new BotIngressCoordinationProperties();config.setMode("direct");
        @SuppressWarnings("unchecked") ObjectProvider<StringRedisTemplate> redis=mock(ObjectProvider.class);
        guard=spy(new BotWebhookDeliveryGuardService(config,redis));
    }
    private Update update(int id,String text) {
        var user=new User();user.setId(1001L);user.setFirstName("Client");user.setUserName("client");
        var chat=new Chat();chat.setId(1001L);chat.setType("private");
        var message=new Message();message.setMessageId(id);message.setDate(123456789);message.setFrom(user);message.setChat(chat);message.setText(text);
        var update=new Update();update.setUpdateId(1000+id);update.setMessage(message);return update;
    }
    private void receive(int id,String text) {bot.onUpdateReceived(update(id,text));}
    private TelegramQuestionnaireHandler.State state() {return store.load(TelegramQuestionnaireHandler.SESSION_PLATFORM,55L,1001L,TelegramQuestionnaireHandler.State.class).orElseThrow().payload();}
    private String choice(int index) {var state=state();return TelegramQuestionnaireMenu.button(state.catalog,state.nonce,index);}
    private void chooseA() {receive(1,"/start");receive(2,choice(0));}
    private void first() {chooseA();receive(3,"One");}
    private void complete() {first();receive(4,"Two");}
    private ScopedRuntimeConfigUnavailableException unavailable() {return new ScopedRuntimeConfigUnavailableException("test unavailable");}
    @Test void durableMenuPrecedesNativeKeyboardAndUsesCanonicalChannelNotOperatorChat() {
        receive(1,"/start");assertThat(state().selected).isNull();assertThat(state().answers).isEmpty();
        assertThat(sent.get(0).getReplyMarkup()).isInstanceOf(ReplyKeyboardMarkup.class);
        assertThat(state().channelId).isEqualTo(55);verify(guard).tryClaim("telegram",55L,"update:1001");
        verifyNoInteractions(local,feedback);verify(tickets,never()).createConversationTicket(any());
    }
    @Test void restartRestoresMenuThenChosenPayloadOrderBootstrapAndAnswers() throws Exception {
        receive(1,"Initial complaint");String nonce=state().nonce,button=choice(0);rebuild();receive(2,button);rebuild();receive(3,"One");
        assertThat(state().nonce).isEqualTo(nonce);assertThat(state().selected.businessId()).isEqualTo(7);
        assertThat(state().bootstrap).isEqualTo("Initial complaint");assertThat(state().answers).containsOnlyKeys("free-first");
        assertThat(sent).extracting(SendMessage::getText).anyMatch(t->t.startsWith("q-a first"));verifyNoInteractions(local,feedback);
    }
    @Test void completeFlowPublishesFrozenIdentityWithoutMandatoryFieldsOrRawSupportSummary() {
        complete();var commands=org.mockito.ArgumentCaptor.forClass(ConversationTicketCreationCommand.class);
        verify(tickets).createConversationTicket(commands.capture());var command=commands.getValue();
        assertThat(command.businessId()).isEqualTo(7);assertThat(command.scopeKind()).isEqualTo("BUSINESS");
        assertThat(command.scopeSelectionSource()).isEqualTo("USER_SELECTION");assertThat(command.questionTemplateId()).isEqualTo("q-a");
        assertThat(command.templateSha256()).isEqualTo("a".repeat(64));assertThat(command.catalogSha256()).isEqualTo("c".repeat(64));
        assertThat(command.answers()).containsExactlyInAnyOrderEntriesOf(Map.of("free-first","One","free-second","Two"));
        assertThat(command.providerEventKey()).isEqualTo("telegram-message:1001:4");assertThat(state().submittedTicketId).isEqualTo("T-TG");
        assertThat(sent).allMatch(request->request.getChatId().equals("1001"));verifyNoInteractions(local,attachments,feedback);
    }
    @ParameterizedTest @ValueSource(strings={"Business A","1","/business ffffffffffff 7","/business 123456abcdef 999"})
    void invalidChoicesCannotChooseOrReachFeedback(String text) {
        receive(1,"/start");receive(2,text);assertThat(state().selected).isNull();assertThat(state().answers).isEmpty();
        verify(runtime,never()).selectQuestionnaire(any(),any(),anyLong());verifyNoInteractions(feedback);
    }
    @Test void explicitStartInvalidatesOldButtonsAndDelayedOldAnswers() {
        chooseA();String old=choice(0);receive(10,"/start");receive(11,old);assertThat(state().selected).isNull();
        receive(12,choice(1));receive(3,"Private answer for A");assertThat(state().answers).isEmpty();assertThat(state().selected.businessId()).isEqualTo(8);
    }
    @Test void oldChoiceDoesNotFillFirstQuestionAndAnswerDedupSurvivesCacheLoss() throws Exception {
        chooseA();receive(20,choice(0));assertThat(state().answers).isEmpty();receive(21,"One");
        resetDeliveryCache();rebuild();receive(21,"One");assertThat(state().index).isEqualTo(1);verify(tickets,never()).createConversationTicket(any());
    }
    @Test void menuSendFailurePreservesNonceBeforeDeliveryAndReusesIt() throws Exception {
        doThrow(new TelegramApiException("test send failure")).doAnswer(call->{sent.add(call.getArgument(0));return new Message();})
                .when(bot).execute(any(SendMessage.class));
        assertThatThrownBy(()->receive(1,"/start")).hasMessageContaining("delivery failed");String nonce=state().nonce;
        rebuild();receive(1,"/start");assertThat(state().nonce).isEqualTo(nonce);verify(runtime,times(1)).questionnaireChoices(channel);
    }
    @Test void questionSendFailurePreservesChosenScopeAndChoiceReplayDoesNotAnswer() throws Exception {
        receive(1,"/start");String button=choice(0);doThrow(new TelegramApiException("test failure")).when(bot).execute(any(SendMessage.class));
        assertThatThrownBy(()->receive(2,button)).hasMessageContaining("delivery failed");rebuild();receive(2,button);
        assertThat(state().selected.businessId()).isEqualTo(7);assertThat(state().answers).isEmpty();
    }
    @Test void failedQueuePublicationRetainsFinalAnswerAndExactCommandDespiteRebind() throws Exception {
        first();when(tickets.createConversationTicket(any())).thenThrow(new IllegalStateException("test queue failure"))
                .thenReturn(new TicketService.TicketCreationResult("T-TG",null,"queued"));
        assertThatThrownBy(()->receive(4,"Two")).hasMessageContaining("queue failure");
        when(runtime.selectQuestionnaire(channel,catalog,7L)).thenThrow(unavailable());rebuild();receive(4,"Edited retry");
        var commands=org.mockito.ArgumentCaptor.forClass(ConversationTicketCreationCommand.class);
        verify(tickets,times(2)).createConversationTicket(commands.capture());assertThat(commands.getAllValues().get(0)).isEqualTo(commands.getAllValues().get(1));
        assertThat(state().answers).containsEntry("free-second","Two");
    }
    @Test void staleBeforeSelectionCannotRecaptureANewCatalog() {
        receive(1,"/start");when(runtime.selectQuestionnaire(channel,catalog,7L)).thenThrow(unavailable());receive(2,choice(0));
        assertThat(state().selected).isNull();verify(runtime,times(1)).questionnaireChoices(channel);
    }
    @Test void staleBeforeFreshPublicationKeepsAnswersButCannotEnqueue() {
        first();when(runtime.selectQuestionnaire(channel,catalog,7L)).thenThrow(unavailable());receive(4,"Two");
        assertThat(state().answers).containsEntry("free-second","Two");assertThat(state().submissionMessageId).isNull();verify(tickets,never()).createConversationTicket(any());
    }
    @Test void markerPersistenceFailureAfterQueueAcceptanceRetriesOnlyFrozenCommand() throws Exception {
        first();var once=new AtomicBoolean(true);
        doAnswer(call->{var incoming=(TelegramQuestionnaireHandler.State)call.getArgument(4);
            if(incoming.submittedTicketId!=null && once.getAndSet(false)) throw new IllegalStateException("test marker failure");return call.callRealMethod();})
                .when(store).saveIfUnchanged(eq(TelegramQuestionnaireHandler.SESSION_PLATFORM),eq(55L),eq(1001L),anyString(),any());
        assertThatThrownBy(()->receive(4,"Two")).hasMessageContaining("marker failure");rebuild();receive(4,"Two");
        var commands=org.mockito.ArgumentCaptor.forClass(ConversationTicketCreationCommand.class);verify(tickets,times(2)).createConversationTicket(commands.capture());
        assertThat(commands.getAllValues().get(0)).isEqualTo(commands.getAllValues().get(1));
    }
    @Test void confirmationFailureAndCacheLossCannotCreateAnotherRoot() throws Exception {
        first();doThrow(new TelegramApiException("test confirmation failure")).when(bot).execute(any(SendMessage.class));
        assertThatThrownBy(()->receive(4,"Two")).hasMessageContaining("delivery failed");
        resetDeliveryCache();rebuild();receive(4,"Two");receive(3,"One");receive(1,"/start");verify(tickets,times(1)).createConversationTicket(any());
    }
    @Test void deliveryAckLossReleasesClaimWithoutDroppingTerminalMarker() throws Exception {
        first();var once=new AtomicBoolean(true);
        doAnswer(call->{if(once.getAndSet(false)) throw new IllegalStateException("test ack loss");return call.callRealMethod();}).when(guard).markProcessed(any());
        assertThatThrownBy(()->receive(4,"Two")).hasMessageContaining("ack loss");rebuild();receive(4,"Two");verify(tickets,times(1)).createConversationTicket(any());
    }
    @Test void followupWaitsForOwnCanonicalRootAndRejectsAnotherActiveTicket() {
        complete();assertThatThrownBy(()->receive(5,"Followup")).hasMessageContaining("not registered");
        var active=new TicketActive();active.setTicketId("OTHER");when(tickets.findActiveTicketForUser(eq(1001L),anyString(),eq(55L))).thenReturn(Optional.of(active));
        assertThatThrownBy(()->receive(5,"Followup")).hasMessageContaining("Different active");verify(tickets,never()).recordActiveClientMessage(any());
        active.setTicketId("T-TG");when(tickets.findByTicketId("T-TG")).thenReturn(Optional.of(new TicketService.TicketWithUser(1001L,"T-TG","open")));
        receive(5,"Followup");var commands=org.mockito.ArgumentCaptor.forClass(ActiveInboundClientMessageCommand.class);
        verify(tickets).recordActiveClientMessage(commands.capture());assertThat(commands.getValue().ticketId()).isEqualTo("T-TG");
        assertThat(commands.getValue().providerMessageId()).isEqualTo(5);assertThat(sent).allMatch(request->request.getChatId().equals("1001"));
    }
    @Test void anotherActiveRootBlocksFreshSubmissionAndUnselectedShortcut() {
        var active=new TicketActive();active.setTicketId("OTHER");when(tickets.findActiveTicketForUser(eq(1001L),anyString(),eq(55L))).thenReturn(Optional.of(active));
        complete();verify(tickets,never()).createConversationTicket(any());verify(tickets,never()).recordActiveClientMessage(any());assertThat(state().submissionMessageId).isNull();
    }
    @Test void cancelDeletesPendingMenuAndOldButtonCannotCreateANewSession() {
        receive(1,"/start");String old=choice(0);receive(2,"/cancel");receive(3,old);
        assertThat(store.load(TelegramQuestionnaireHandler.SESSION_PLATFORM,55L,1001L,TelegramQuestionnaireHandler.State.class)).isEmpty();
        verify(runtime,times(1)).questionnaireChoices(channel);
    }
    @Test void newMessageDuringPendingPublicationGetsExplicitRetryInsteadOfBeingConsumed() {
        first();when(tickets.createConversationTicket(any())).thenThrow(new IllegalStateException("test queue failure"))
                .thenReturn(new TicketService.TicketCreationResult("T-TG",null,"queued"));
        assertThatThrownBy(()->receive(4,"Two")).hasMessageContaining("queue failure");
        assertThatThrownBy(()->receive(5,"New followup")).hasMessageContaining("Retry new");
        assertThat(sent).extracting(SendMessage::getText).anyMatch(t->t.contains("Повторите это сообщение"));
        assertThat(state().answers).doesNotContainValue("New followup");
    }
    @Test void selectRouteBackAndOptionalSkipPreserveQuestionnaireSemantics() {
        a=selected(7,"q-a","a",true);when(runtime.selectQuestionnaire(channel,catalog,7L)).thenReturn(a);
        chooseA();receive(3,"1");assertThat(state().index).isEqualTo(2);
        receive(4,"Назад");assertThat(state().index).isZero();assertThat(state().answers).isEmpty();
        receive(5,"нет");assertThat(state().index).isEqualTo(1);receive(6,"Пропустить");assertThat(state().answers).containsEntry("optional","");
        receive(7,"Final");verify(tickets).createConversationTicket(any());
    }
    @Test void malformedChoiceDoesNotConsumeASelectQuestion() {
        a=selected(7,"q-a","a",true);when(runtime.selectQuestionnaire(channel,catalog,7L)).thenReturn(a);chooseA();receive(3,"999");
        assertThat(state().answers).isEmpty();assertThat(state().index).isZero();
    }
    @Test void optimisticConflictCannotOverwriteConcurrentBusinessChoice() {
        receive(1,"/start");String first=choice(0),second=choice(1);var once=new AtomicBoolean(true);
        doAnswer(call->{var state=(TelegramQuestionnaireHandler.State)call.getArgument(4);
            if(state.selected!=null && once.getAndSet(false)) receive(22,second);return call.callRealMethod();})
                .when(store).saveIfUnchanged(eq(TelegramQuestionnaireHandler.SESSION_PLATFORM),eq(55L),eq(1001L),anyString(),any());
        receive(2,first);assertThat(state().selected.businessId()).isEqualTo(8);assertThat(state().answers).isEmpty();
    }
    @Test void exhaustedCasConflictsAreNotAcknowledged() {
        receive(1,"/start");String code=choice(0);clearInvocations(guard);
        doReturn(Optional.empty()).when(store).saveIfUnchanged(eq(TelegramQuestionnaireHandler.SESSION_PLATFORM),eq(55L),eq(1001L),anyString(),any());
        assertThatThrownBy(()->receive(2,code)).isInstanceOf(SessionStateConflictException.class);verify(guard,never()).markProcessed(any());
    }
    @ParameterizedTest @ValueSource(strings={"user","chat","channel","template","progress","marker","version","answer","history"})
    void corruptedStateCannotCrossOwnerOrPublish(String kind) {
        chooseA();var tree=(ObjectNode)mapper.valueToTree(state());
        switch(kind) {
            case "user"->tree.put("userId",9999);case "chat"->tree.put("chatId",9999);case "channel"->tree.put("channelId",999);
            case "template"->((ObjectNode)tree.path("selected")).put("questionTemplateId","foreign");
            case "progress"->tree.put("index",999);case "marker"->tree.put("submittedTicketId","foreign");case "version"->tree.put("version",9);
            case "answer"->((ObjectNode)tree.path("answers")).put("foreign-field","foreign-answer");
            case "history"->tree.set("history",mapper.valueToTree(List.of(new ConversationHistoryEntry(9999L,"foreign","text",null,null,"88",java.time.OffsetDateTime.now()))));
        }
        store.save(TelegramQuestionnaireHandler.SESSION_PLATFORM,55L,1001L,tree);assertThatThrownBy(()->receive(3,"Unsafe")).isInstanceOf(RuntimeException.class);
        verify(tickets,never()).createConversationTicket(any());verify(tickets,never()).recordActiveClientMessage(any());
    }
    @Test void selectedMediaAndEditsCannotReachLegacyStorageOrEditWriter() {
        chooseA();var update=update(3,null);update.getMessage().setDocument(new Document());bot.onUpdateReceived(update);
        var edited=update(4,"Edited answer");edited.setEditedMessage(edited.getMessage());edited.setMessage(null);bot.onUpdateReceived(edited);
        assertThat(state().answers).isEmpty();verifyNoInteractions(attachments);verify(tickets,never()).recordActiveClientMessage(any());
    }
    @Test void blockedClientCannotChooseOrContinue() {
        receive(1,"/start");when(blacklist.getStatus(1001L)).thenReturn(new BlacklistService.BlacklistStatus(true,false));
        receive(2,choice(0));assertThat(state().selected).isNull();verify(runtime,never()).selectQuestionnaire(any(),any(),anyLong());
    }
    @Test void previousInMemoryQuestionnaireDoesNotLeakAnswersIntoNewBootstrap() {
        @SuppressWarnings("unchecked") Map<Long,Object> legacy=(Map<Long,Object>)ReflectionTestUtils.getField(bot,"conversations");
        legacy.put(1001L,new Object());receive(1,"Old private answer");assertThat(state().bootstrap).isNull();assertThat(state().history).isEmpty();assertThat(legacy).isEmpty();
    }
    @Test void legacyAndGeneralOnlyRuntimeAreNotClaimedByNewHandler() {
        doReturn(Optional.empty()).when(runtime).findChannelConfig(channel);
        assertThat(handler.handle(update(1,"/start"),channel,sent::add)).isFalse();
        doReturn(Optional.of(new RuntimeConfigSnapshot(55L,a.botSettings(),Map.of(),Map.of(),"GENERAL","GENERAL_CHANNEL","q-a","a".repeat(64))))
                .when(runtime).findChannelConfig(channel);
        assertThat(handler.handle(update(2,"/start"),channel,sent::add)).isFalse();assertThat(sent).isEmpty();
    }
    @Test void generalOnlyStillRunsThroughRealSupportBotWithoutSelectingBusiness() {
        doReturn(Optional.of(new RuntimeConfigSnapshot(55L,a.botSettings(),Map.of(),Map.of(),"GENERAL","GENERAL_CHANNEL","q-a","a".repeat(64))))
                .when(runtime).findChannelConfig(channel);
        receive(1,"/start");receive(2,"General one");receive(3,"General two");
        var command=org.mockito.ArgumentCaptor.forClass(ConversationTicketCreationCommand.class);verify(tickets).createConversationTicket(command.capture());
        assertThat(command.getValue().scopeKind()).isEqualTo("GENERAL");assertThat(command.getValue().businessId()).isNull();verify(runtime,never()).questionnaireChoices(any());
    }
    @Test void twoCanonicalChannelsWithSameOperatorChatAndClientCannotMixMenus() throws Exception {
        receive(1,"/start");String original=choice(0);
        var second=new Channel();second.setId(66L);second.setPlatform("telegram");second.setActive(true);
        when(channels.ensurePublicIdForToken("second-telegram-token")).thenReturn(second);when(runtime.findChannelConfig(second)).thenThrow(unavailable());
        when(runtime.questionnaireChoices(second)).thenReturn(new ChoiceCatalog(1,66,"d".repeat(64),catalog.choices()));
        properties.setToken("second-telegram-token");rebuild();receive(1,"/start");
        assertThat(store.load(TelegramQuestionnaireHandler.SESSION_PLATFORM,66L,1001L,TelegramQuestionnaireHandler.State.class)).isPresent();
        properties.setToken("test-telegram-token");rebuild();receive(2,original);assertThat(state().selected.businessId()).isEqualTo(7);
        assertThat(store.load(TelegramQuestionnaireHandler.SESSION_PLATFORM,66L,1001L,TelegramQuestionnaireHandler.State.class).orElseThrow().payload().selected).isNull();
    }
    @Test void pageAndGenerationSurviveRestore() throws Exception {
        var choices=new ArrayList<>(catalog.choices());for(long id=100;id<110;id++) choices.add(new Choice("BUSINESS",id,"b"+id,"Business "+id,"q"+id,"a".repeat(64)));
        catalog=new ChoiceCatalog(1,55,"c".repeat(64),choices);when(runtime.questionnaireChoices(channel)).thenReturn(catalog);
        receive(1,"/start");String nonce=state().nonce;receive(2,TelegramQuestionnaireMenu.pageButton(nonce,1,true));resetDeliveryCache();rebuild();receive(1,"/start");
        assertThat(state().page).isEqualTo(1);assertThat(state().nonce).isEqualTo(nonce);verify(runtime,times(1)).questionnaireChoices(channel);
    }
    @Test void selectedCommandCannotBecomeUnscopedTicketListOrClientReply() {
        complete();receive(5,"/mytickets");verify(tickets,never()).recordActiveClientMessage(any());
    }
    @Test void closedRootDoesNotStartAnotherQuestionnaireImplicitly() {
        complete();when(tickets.findByTicketId("T-TG")).thenReturn(Optional.of(new TicketService.TicketWithUser(1001L,"T-TG","closed")));
        receive(5,"Followup");verify(tickets,never()).recordActiveClientMessage(any());verify(runtime,times(1)).questionnaireChoices(channel);
    }
    @Test void invalidStableProviderIdCannotStartBusinessQuestionnaire() {
        var update=update(1,"/start");update.getMessage().setMessageId(0);
        assertThatThrownBy(()->bot.onUpdateReceived(update)).hasMessageContaining("Stable Telegram message ID");
        verify(store,never()).saveIfUnchanged(anyString(),anyLong(),anyLong(),any(),any());
    }
    @ParameterizedTest @ValueSource(strings={"platform","inactive","private-owner"})
    void invalidChannelOrPrivateOwnerFailsClosed(String kind) {
        var update=update(1,"/start");
        if(kind.equals("platform")) channel.setPlatform("max");else if(kind.equals("inactive")) channel.setActive(false);else update.getMessage().getFrom().setId(9999L);
        assertThatThrownBy(()->bot.onUpdateReceived(update)).hasMessageContaining("owner/channel");verify(tickets,never()).createConversationTicket(any());
    }

    private void generalMenu() {
        var payload=selected(7L,"q-general","d",false);
        var general=new SelectedQuestionnaire(2,55,"GENERAL","USER_SELECTION",(Long)null,"q-general","d".repeat(64),"c".repeat(64),payload.botSettings(),Map.of(),Map.of());
        catalog=new ChoiceCatalog(2,55,"c".repeat(64),List.of(new Choice("BUSINESS",7,"a","Business A","q-a","a".repeat(64)),
            new Choice("GENERAL",(Long)null,"GENERAL","Без бизнеса","q-general","d".repeat(64))));
        doReturn(catalog).when(runtime).questionnaireChoices(channel);
        doReturn(general).when(runtime).selectGeneralQuestionnaire(channel,catalog);
    }

    private void chooseGeneral() throws Exception {generalMenu();receive(1,"/start");receive(2,choice(1));}
    @Test void generalChoiceRestartsAndPublishesNullOwnerWithOnlyItsFrozenFreeAnswers() throws Exception {
        chooseGeneral();assertThat(state().selected.businessId()).isNull();rebuild();receive(3,"One");rebuild();receive(4,"Two");
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
        if(boundary.equals("submission")){receive(2,choice(1));receive(3,"One");}
        doThrow(unavailable()).when(runtime).selectGeneralQuestionnaire(channel,catalog);
        if(boundary.equals("choice")){receive(2,choice(1));}else receive(4,"Two");
        verify(tickets,never()).createConversationTicket(any());verify(runtime,times(1)).questionnaireChoices(channel);
    }


    @Test void oldGeneralCommandNeverSelectsAfterExplicitRestart() throws Exception {
        generalMenu();receive(1,"/start");String old="/general "+state().nonce;receive(10,"/start");receive(11,old);assertThat(state().selected).isNull();
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
        r150Menu();chooseA();rebuild();receive(3,"Foreign B");assertThat(state().answers).isEmpty();
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
        var payload=selected(7L,"q-general","d",false);var emptySha=com.example.supportbot.service.SelectedQuestionnaireOptions.resourceSha256(Map.of(),Map.of());
        var general=new SelectedQuestionnaire(3,55,"GENERAL","USER_SELECTION",(Long)null,"q-general","d".repeat(64),"c".repeat(64),payload.botSettings(),Map.of(),Map.of());
        catalog=new ChoiceCatalog(3,55,"c".repeat(64),List.of(
            new Choice("BUSINESS",7L,"a","Business A","q-a","a".repeat(64),"a".repeat(64)),
            new Choice("GENERAL",(Long)null,"GENERAL","Без бизнеса","q-general","d".repeat(64),emptySha)));
        doReturn(catalog).when(runtime).questionnaireChoices(channel);doReturn(general).when(runtime).selectGeneralQuestionnaire(channel,catalog);
        receive(1,"/start");receive(2,choice(1));rebuild();receive(3,"One");rebuild();receive(4,"Two");
        var capture=org.mockito.ArgumentCaptor.forClass(ConversationTicketCreationCommand.class);verify(tickets).createConversationTicket(capture.capture());
        assertThat(capture.getValue().scopeKind()).isEqualTo("GENERAL");assertThat(capture.getValue().businessId()).isNull();
        assertThat(capture.getValue().answers()).containsExactlyInAnyOrderEntriesOf(Map.of("free-first","One","free-second","Two"));verifyNoInteractions(local,feedback);
    }

    @Test void v3PresetRoutesFollowFrozenLabelsAfterRestore() throws Exception {
        r150Menu();
        @SuppressWarnings("unchecked") var template=(Map<String,Object>)((List<?>)a.botSettings().get("question_templates")).get(0);
        @SuppressWarnings("unchecked") var flow=new ArrayList<>((List<Map<String,Object>>)template.get("question_flow"));
        flow.set(0,new HashMap<>(flow.get(0)));flow.get(0).put("routes",List.of(Map.of("value_id","North","next_question_id","team")));
        a=new SelectedQuestionnaire(3,55,"BUSINESS","USER_SELECTION",7L,"q-a","a".repeat(64),"c".repeat(64),
            Map.of("active_template_id","q-a","question_templates",List.of(Map.of("id","q-a","name","A","question_flow",flow))),a.locationTree(),a.presetDefinitions());
        doReturn(a).when(runtime).selectQuestionnaire(channel,catalog,7L);
        chooseA();rebuild();receive(3,"North");rebuild();receive(4,"Kitchen");
        var capture=org.mockito.ArgumentCaptor.forClass(ConversationTicketCreationCommand.class);verify(tickets).createConversationTicket(capture.capture());
        assertThat(capture.getValue().answers()).containsExactlyInAnyOrderEntriesOf(Map.of("city_answer","North","team","Kitchen"));
        assertThat(capture.getValue().attributes()).extracting(TicketService.TicketAttributeInput::questionId).containsExactly("city-q","team");
        verifyNoInteractions(local,feedback);
    }

    private void r152LargeMenu(boolean includeGeneral) {
        r150Menu();a=new SelectedQuestionnaire(4,55,"BUSINESS","USER_SELECTION",7L,"q-a",a.templateSha256(),a.catalogSha256(),a.botSettings(),a.locationTree(),a.presetDefinitions());
        var choices=new ArrayList<Choice>();for(long id=100;id<203;id++)choices.add(new Choice("BUSINESS",id,"b-"+id,"Business "+id,"q-"+id,"a".repeat(64),"b".repeat(64)));
        choices.add(new Choice("BUSINESS",7L,"a","Business A","q-a",a.templateSha256(),SelectedQuestionnaireOptions.resourceSha256(a.locationTree(),a.presetDefinitions())));
        var payload=selected(7L,"q-general","d",false);
        var general=new SelectedQuestionnaire(4,55,"GENERAL","USER_SELECTION",(Long)null,"q-general","d".repeat(64),"c".repeat(64),payload.botSettings(),Map.of(),Map.of());
        if(includeGeneral)choices.add(new Choice("GENERAL",(Long)null,"GENERAL","Без бизнеса","q-general","d".repeat(64),SelectedQuestionnaireOptions.resourceSha256(Map.of(),Map.of())));
        catalog=new ChoiceCatalog(4,55,"c".repeat(64),choices);doReturn(catalog).when(runtime).questionnaireChoices(channel);
        doReturn(a).when(runtime).selectQuestionnaire(channel,catalog,7L);doReturn(general).when(runtime).selectGeneralQuestionnaire(channel,catalog);
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void v4LastPageRestoresThenSelectsItsOwnFrozenBusinessOrGeneral(boolean general) throws Exception {
        r152LargeMenu(general);receive(1,"/start");String nonce=state().nonce;int page=general?13:12;
        receive(2,TelegramQuestionnaireMenu.pageButton(nonce,page,true));rebuild();assertThat(state().page).isEqualTo(page);
        receive(3,choice(general?104:103));rebuild();receive(4,TelegramQuestionnaireMenu.pageButton(nonce,0,false));assertThat(state().answers).isEmpty();
        receive(5,general?"One":"North");rebuild();receive(6,general?"Two":"North site");if(!general){rebuild();receive(7,"Kitchen");}
        var capture=org.mockito.ArgumentCaptor.forClass(ConversationTicketCreationCommand.class);verify(tickets).createConversationTicket(capture.capture());
        assertThat(capture.getValue().scopeKind()).isEqualTo(general?"GENERAL":"BUSINESS");assertThat(capture.getValue().businessId()).isEqualTo(general?null:7L);
        assertThat(capture.getValue().catalogSha256()).isEqualTo(catalog.catalogSha256());verify(runtime,times(1)).questionnaireChoices(channel);verifyNoInteractions(local,feedback);
    }


    private void r153Menu(boolean general,boolean branching) {
        var payload=selected(7L,general?"q-general":"q-a",general?"d":"a",branching);
        var tree=Map.<String,Object>of("businessId",7L,"locations",List.of());var presets=Map.<String,Object>of("businessId",7L,"groups",Map.of());
        a=new SelectedQuestionnaire(4,55,general?"GENERAL":"BUSINESS","USER_SELECTION",general?null:7L,payload.questionTemplateId(),payload.templateSha256(),payload.catalogSha256(),payload.botSettings(),general?Map.of():tree,general?Map.of():presets);
        var choices=new ArrayList<Choice>();
        if(general) choices.add(new Choice("BUSINESS",8L,"b","Business B","q-b","b".repeat(64),"b".repeat(64)));
        choices.add(new Choice(a.scopeKind(),a.businessId(),general?"GENERAL":"a",general?"Без бизнеса":"Business A",a.questionTemplateId(),a.templateSha256(),SelectedQuestionnaireOptions.resourceSha256(a.locationTree(),a.presetDefinitions())));
        catalog=new ChoiceCatalog(4,55,"c".repeat(64),choices);doReturn(catalog).when(runtime).questionnaireChoices(channel);
        if(general)doReturn(a).when(runtime).selectGeneralQuestionnaire(channel,catalog);else doReturn(a).when(runtime).selectQuestionnaire(channel,catalog,7L);
    }
    private void r153Choose(boolean general,boolean branching) {r153Menu(general,branching);receive(1,"/start");receive(2,choice(general?1:0));}
    private Update r153Media(int id,String type,String caption) {
        var update=update(id,null);var tree=(ObjectNode)mapper.valueToTree(update.getMessage());
        var media=mapper.createObjectNode().put("file_id","r153-file").put("file_unique_id","r153-unique").put("file_size",mediaBytes.length);
        if(Set.of("document","audio","video","animation").contains(type))media.put("file_name","../../receipt.TXT");
        tree.set(type,type.equals("photo")?mapper.valueToTree(List.of(media.deepCopy().put("file_id","small").put("file_size",1),media)):media);
        if(caption!=null)tree.put("caption",caption);
        update.setMessage(mapper.convertValue(tree,Message.class));return update;
    }
    @ParameterizedTest @ValueSource(strings={"document","photo","video","voice","audio","animation","sticker","video_note"})
    void v4MediaFreeAnswerStoresOneOwnedObjectAndSurvivesJsonRestore(String type) throws Exception {
        r153Choose(false,false);bot.onUpdateReceived(r153Media(3,type,"Caption for A"));
        var saved=state();assertThat(saved.answers).containsEntry("free-first","Caption for A");assertThat(saved.history).hasSize(1);
        var media=saved.history.get(0);assertThat(media.messageType()).isEqualTo(type);assertThat(media.providerMessageId()).isEqualTo("3");
        assertThat(QuestionnaireMedia.matches(media.attachmentPath(),55,7L,1001,catalog.catalogSha256(),3)).isTrue();
        assertThat(java.nio.file.Files.readAllBytes(mediaRoot.resolve(media.attachmentPath()))).isEqualTo(mediaBytes);
        resetDeliveryCache();rebuild();bot.onUpdateReceived(r153Media(3,type,"Changed duplicate"));assertThat(state().history).hasSize(1);
        receive(4,"Final answer");var capture=org.mockito.ArgumentCaptor.forClass(ConversationTicketCreationCommand.class);verify(tickets).createConversationTicket(capture.capture());
        assertThat(capture.getValue().historyEntries()).containsExactly(media, new ConversationHistoryEntry(1001L,"Final answer","text",null,null,"4",media.occurredAt()));
        assertThat(capture.getValue().businessId()).isEqualTo(7L);verify(attachments,times(1)).storeQuestionnaire(anyLong(),nullable(Long.class),anyLong(),anyString(),anyLong(),anyString(),any(java.io.InputStream.class));
        verifyNoInteractions(local,feedback);assertThat(sent).allMatch(request->request.getChatId().equals("1001"));
    }
    @Test void generalMediaHasNoBusinessNamespaceOrBusinessResources() throws Exception {
        r153Choose(true,false);bot.onUpdateReceived(r153Media(3,"document",null));rebuild();receive(4,"Last");
        var capture=org.mockito.ArgumentCaptor.forClass(ConversationTicketCreationCommand.class);verify(tickets).createConversationTicket(capture.capture());
        assertThat(capture.getValue().businessId()).isNull();assertThat(capture.getValue().scopeKind()).isEqualTo("GENERAL");
        assertThat(capture.getValue().historyEntries().get(0).attachmentPath()).contains("/general/1001/");
        assertThat(capture.getValue().answers()).containsEntry("free-first","Клиент приложил документ");verifyNoInteractions(local,feedback);
    }
    @Test void pendingMenuAndChoiceQuestionCannotDownloadOrStoreMedia() throws Exception {
        r153Menu(false,true);receive(1,"/start");bot.onUpdateReceived(r153Media(2,"photo","Да"));
        receive(3,choice(0));bot.onUpdateReceived(r153Media(4,"document","Да"));
        assertThat(state().answers).isEmpty();verify(bot,never()).fetchFile(anyString());verifyNoInteractions(attachments);verify(tickets,never()).createConversationTicket(any());
    }
    @Test void staleSelectedCatalogFailsBeforeDownloadingMedia() throws Exception {
        r153Choose(false,false);doThrow(unavailable()).when(runtime).selectQuestionnaire(channel,catalog,7L);
        bot.onUpdateReceived(r153Media(3,"photo","Private"));assertThat(state().answers).isEmpty();verify(bot,never()).fetchFile(anyString());verifyNoInteractions(attachments);
    }
    @Test void mediaQueueRetryUsesIdenticalCommandAndNeverDownloadsAgain() throws Exception {
        r153Choose(false,false);receive(3,"One");when(tickets.createConversationTicket(any())).thenThrow(new IllegalStateException("R153 queue failure")).thenReturn(new TicketService.TicketCreationResult("T-TG",null,"queued"));
        assertThatThrownBy(()->bot.onUpdateReceived(r153Media(4,"document","Frozen caption"))).hasMessageContaining("R153 queue failure");
        doThrow(unavailable()).when(runtime).selectQuestionnaire(channel,catalog,7L);rebuild();bot.onUpdateReceived(r153Media(4,"document","Changed retry"));
        var capture=org.mockito.ArgumentCaptor.forClass(ConversationTicketCreationCommand.class);verify(tickets,times(2)).createConversationTicket(capture.capture());
        assertThat(capture.getAllValues().get(0)).isEqualTo(capture.getAllValues().get(1));
        assertThat(capture.getValue().historyEntries().get(1).text()).isEqualTo("Frozen caption");verify(attachments,times(1)).storeQuestionnaire(anyLong(),nullable(Long.class),anyLong(),anyString(),anyLong(),anyString(),any(java.io.InputStream.class));
    }
    @ParameterizedTest @ValueSource(strings={"download","storage"})
    void fileFailureReleasesDeliveryWithoutAdvancingAnswerAndRetrySucceeds(String boundary) throws Exception {
        r153Choose(false,false);
        if(boundary.equals("download")) doThrow(new TelegramApiException("R153 download failure")).when(bot).fetchFile(anyString());
        else doThrow(new java.io.IOException("R153 storage failure")).when(attachments).storeQuestionnaire(anyLong(),nullable(Long.class),anyLong(),anyString(),anyLong(),anyString(),any(java.io.InputStream.class));
        assertThatThrownBy(()->bot.onUpdateReceived(r153Media(3,"document","One"))).isInstanceOf(IllegalStateException.class);
        assertThat(state().answers).isEmpty();assertThat(state().accepted).doesNotContain(3L);verify(guard,atLeastOnce()).release(any());
        rebuild();bot.onUpdateReceived(r153Media(3,"document","One"));assertThat(state().answers).containsEntry("free-first","One");
    }
    @Test void oversizeStreamCannotAdvanceAnswersOrLeaveAnAttachment() throws Exception {
        r153Choose(false,false);mediaBytes=new byte[QuestionnaireMedia.MAX_BYTES+1];
        var update=r153Media(3,"document","Too big");update.getMessage().getDocument().setFileSize(1L);bot.onUpdateReceived(update);
        assertThat(state().answers).isEmpty();assertThat(state().history).isEmpty();
        try(var files=java.nio.file.Files.walk(mediaRoot)){assertThat(files.filter(java.nio.file.Files::isRegularFile).count()).isZero();}
    }
    @Test void completedFlowDoesNotSendMediaThroughUnscopedFollowupOrRawOperatorChat() throws Exception {
        r153Choose(false,false);receive(3,"One");receive(4,"Two");bot.onUpdateReceived(r153Media(5,"photo","More"));
        verify(bot,never()).fetchFile(anyString());verifyNoInteractions(attachments);verify(tickets,never()).recordActiveClientMessage(any());
    }
    @Test void foreignMediaKeyInRestoredStateFailsBeforeAnyWriter() throws Exception {
        r153Choose(false,false);bot.onUpdateReceived(r153Media(3,"document","One"));var tree=(ObjectNode)mapper.valueToTree(state());
        ((ObjectNode)tree.path("history").get(0)).put("attachmentPath",state().history.get(0).attachmentPath().replace("/business-7/","/business-8/"));
        store.save(TelegramQuestionnaireHandler.SESSION_PLATFORM,55L,1001L,tree);rebuild();assertThatThrownBy(()->receive(4,"Two")).hasMessageContaining("identity");
        verify(tickets,never()).createConversationTicket(any());verify(tickets,never()).recordActiveClientMessage(any());
    }

    @Test void failedPromptDeliveryKeepsTheStoredMediaAndAnswerBeforeRetry() throws Exception {
        r153Choose(false,false);doThrow(new TelegramApiException("R153 prompt failure")).when(bot).execute(any(SendMessage.class));
        assertThatThrownBy(()->bot.onUpdateReceived(r153Media(3,"document","One"))).hasMessageContaining("delivery failed");
        assertThat(state().answers).containsEntry("free-first","One");String key=state().history.get(0).attachmentPath();
        rebuild();bot.onUpdateReceived(r153Media(3,"document","Changed retry"));assertThat(state().history).hasSize(1);
        assertThat(state().history.get(0).attachmentPath()).isEqualTo(key);verify(attachments,times(1)).storeQuestionnaire(anyLong(),nullable(Long.class),anyLong(),anyString(),anyLong(),anyString(),any(java.io.InputStream.class));
    }
    @Test void sessionCasRetryReusesOneCompleteFileAndOneAcceptedAnswer() throws Exception {
        r153Choose(false,false);doReturn(Optional.empty()).doCallRealMethod().when(store).saveIfUnchanged(anyString(),anyLong(),anyLong(),any(),any());
        bot.onUpdateReceived(r153Media(3,"document","One"));assertThat(state().history).hasSize(1);assertThat(state().answers).containsEntry("free-first","One");
        assertThat(state().accepted).containsOnlyOnce(3L);
        try(var files=java.nio.file.Files.walk(mediaRoot)){assertThat(files.filter(java.nio.file.Files::isRegularFile).count()).isEqualTo(1);}
        verify(attachments,times(2)).storeQuestionnaire(anyLong(),nullable(Long.class),anyLong(),anyString(),anyLong(),anyString(),any(java.io.InputStream.class));
    }
}
