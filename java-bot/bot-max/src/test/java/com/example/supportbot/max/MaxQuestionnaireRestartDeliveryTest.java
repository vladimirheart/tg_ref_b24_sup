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

/** Settings failure must not consume a client event when its recovery instruction was not delivered. */
class MaxQuestionnaireRestartDeliveryTest {
    static final String WARNING="Настройки опроса изменились или недоступны. Отправьте /start для нового выбора.";
    record Scenario(String phase,int version,String scope) {}
    final ObjectMapper mapper=Jackson2ObjectMapperBuilder.json().build();
    final Channel channel=new Channel();
    final ChannelService channels=mock(ChannelService.class);
    final TicketService tickets=mock(TicketService.class);
    final MessagingService messages=mock(MessagingService.class);
    final RuntimeConfigService runtime=mock(RuntimeConfigService.class);
    final BotSettingsService settings=mock(BotSettingsService.class);
    final AttachmentService attachments=mock(AttachmentService.class);
    final MaxApiClient api=mock(MaxApiClient.class);
    final FeedbackService feedback=mock(FeedbackService.class);
    final ChatHistoryService history=mock(ChatHistoryService.class);
    BotSessionStoreService sessions;
    BotWebhookDeliveryGuardService guard;
    MaxWebhookController controller;
    String text="Customer answer";
    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        channel.setId(55L);channel.setPlatform("max");channel.setSupportChatId("unverified-support");
        var properties=new MaxBotProperties();properties.setEnabled(true);properties.setChannelId(55L);properties.setWebhookSecret("fixture-secret");
        when(channels.resolveConfiguredChannel(55L,"","MAX","max")).thenReturn(channel);
        var blacklist=mock(BlacklistService.class);when(blacklist.resolveStatus(eq(1001L),anyString(),any(),eq("2002")))
            .thenReturn(new BlacklistService.ResolvedBlacklistStatus(null,new BlacklistService.BlacklistStatus(false,false)));
        var ingress=mock(BotIngressCoordinationService.class);when(ingress.tryAcquireOrRenew("max",55L)).thenReturn(true);
        var coordination=new BotIngressCoordinationProperties();coordination.setMode("direct");
        ObjectProvider<StringRedisTemplate> redis=mock(ObjectProvider.class);
        sessions=spy(new BotSessionStoreService(coordination,mapper,redis));guard=spy(new BotWebhookDeliveryGuardService(coordination,redis));
        when(messages.sendToUser(eq(channel),eq(1001L),anyString())).thenReturn(true);
        when(runtime.findChannelConfig(channel)).thenThrow(unavailable());when(runtime.questionnaireChoices(channel)).thenThrow(unavailable());
        when(runtime.selectQuestionnaire(eq(channel),any(),eq(7L))).thenThrow(unavailable());
        when(runtime.selectGeneralQuestionnaire(eq(channel),any())).thenThrow(unavailable());
        controller=new MaxWebhookController(properties,blacklist,channels,tickets,history,messages,feedback,settings,
            ingress,guard,sessions,runtime,attachments,api,mock(MaxClientAvatarSyncService.class),mapper);
    }
    ScopedRuntimeConfigUnavailableException unavailable(){return new ScopedRuntimeConfigUnavailableException("private fixture configuration failure");}
    static List<Scenario> scenarios() {
        var result=new ArrayList<Scenario>();result.add(new Scenario("BOOTSTRAP",0,"NONE"));result.add(new Scenario("LEGACY",0,"NONE"));
        for(int version=1;version<=4;version++)for(String phase:List.of("CHOICE","RESTORED_FINAL","LAST_ANSWER")){
            result.add(new Scenario(phase,version,"BUSINESS"));if(version>=2)result.add(new Scenario(phase,version,"GENERAL"));
        }
        return result;
    }
    static Stream<Arguments> failures(){return scenarios().stream().flatMap(s->Stream.of("false","exception","scoped-exception","ack").map(failure->Arguments.of(s,failure)));}
    void prepare(Scenario scenario) {
        if(scenario.phase().equals("BOOTSTRAP"))return;
        var question=new QuestionFlowItemDto();question.setId("free");question.setType("text");question.setText("Original question");
        if(scenario.phase().equals("LEGACY")){
            sessions.save("max",55L,1001L,new MaxConversationSession(1001L,2002L,"client","Client",List.of(question),new BotSettingsDto()).snapshot());
            clearInvocations(sessions);return;
        }
        int version=scenario.version();boolean general=scenario.scope().equals("GENERAL");Long business=general?null:7L;
        Map<String,Object> tree=version>=3?Map.of("businessId",7L,"locations",List.of()):Map.of();
        Map<String,Object> presets=version>=3?Map.of("businessId",7L,"groups",Map.of()):Map.of();
        var businessChoice=new Choice("BUSINESS",7L,"test","Test","q-test","a".repeat(64),SelectedQuestionnaireOptions.resourceSha256(tree,presets));
        var generalChoice=new Choice("GENERAL",(Long)null,"GENERAL","Без бизнеса","q-general","d".repeat(64),SelectedQuestionnaireOptions.resourceSha256(Map.of(),Map.of()));
        var catalog=new ChoiceCatalog(version,55L,"c".repeat(64),general?List.of(businessChoice,generalChoice):List.of(businessChoice));
        var choice=general?generalChoice:businessChoice;
        var pending=MaxConversationSession.pending(1001L,2002L,"client","Client",catalog,"start-event");
        if(scenario.phase().equals("CHOICE"))text=MaxQuestionnaireChoiceSupport.buttonText(catalog,pending.selection().nonce(),general?1:0);
        else {
            var template=Map.of("id",choice.questionTemplateId(),"question_flow",List.of(Map.of("id","free","type","text","text","Original question")));
            var selected=new SelectedQuestionnaire(version,55L,scenario.scope(),"USER_SELECTION",business,choice.questionTemplateId(),choice.templateSha256(),"c".repeat(64),
                Map.of("question_templates",List.of(template)),general?Map.of():tree,general?Map.of():presets);
            pending=pending.choose(selected,new BotSettingsDto(),List.of(question));
            if(scenario.phase().equals("RESTORED_FINAL")){pending.recordAnswer("Saved answer");pending.acceptEvent("saved-final-event");}
        }
        sessions.save("max",55L,1001L,pending.snapshot());clearInvocations(sessions);
    }
    Optional<BotSessionStoreService.StoredBotSession<MaxConversationSession.State>> saved(){return sessions.load("max",55L,1001L,MaxConversationSession.State.class);}
    Optional<String> raw(){return saved().map(BotSessionStoreService.StoredBotSession::rawPayload);}
    ResponseEntity<Map<String,Object>> send(){return controller.handleUpdate(mapper.valueToTree(Map.of("update_type","message_created","update_id","same-retry-event","message",
        Map.of("sender",Map.of("user_id",1001,"username","client","name","Client"),"recipient",Map.of("chat_id",2002),"body",Map.of("text",text)))),"fixture-secret");}
    void assertDurableState(Scenario scenario,Optional<String> before) {
        if(scenario.phase().equals("LAST_ANSWER")){
            var state=saved().orElseThrow().payload();assertThat(state.answers()).containsExactlyEntriesOf(Map.of("free","Customer answer"));
            assertThat(state.history()).hasSize(1);assertThat(state.currentIndex()).isEqualTo(1);
            assertThat(state.selection().acceptedEventKeys()).hasSize(1);assertThat(state.selection().submittedTicketId()).isNull();
            assertThat(state.selection().submissionEventKey()).isNull();
        }else assertThat(raw()).isEqualTo(before);
        verify(tickets,never()).createConversationTicket(any());verify(tickets,never()).recordActiveClientMessage(any());verify(tickets,never()).clearTicketActivity(any());
        verify(messages,never()).sendToSupportChat(any(),anyString());verifyNoInteractions(attachments,api,feedback,history,settings);
        verify(sessions,never()).delete(anyString(),any(),any());verify(sessions,never()).deleteIfUnchanged(anyString(),any(),any(),any());
    }
    @ParameterizedTest @MethodSource("failures")
    void recoveryInstructionOrAcknowledgementFailureLeavesTheSameEventRetryable(Scenario scenario,String failure) {
        prepare(scenario);var before=raw();
        switch(failure){
            case "false"->when(messages.sendToUser(channel,1001L,WARNING)).thenReturn(false);
            case "exception"->when(messages.sendToUser(channel,1001L,WARNING)).thenThrow(new IllegalStateException("provider unavailable"));
            case "scoped-exception"->when(messages.sendToUser(channel,1001L,WARNING)).thenThrow(unavailable());
            case "ack"->doThrow(new IllegalStateException("dedup acknowledgement unavailable")).when(guard).markProcessed(any());
            default->throw new AssertionError(failure);
        }
        assertThatThrownBy(this::send).isInstanceOf(IllegalStateException.class);
        verify(guard).release(any());verify(guard,times(failure.equals("ack")?1:0)).markProcessed(any());
        verify(messages).sendToUser(channel,1001L,WARNING);assertDurableState(scenario,before);var durable=raw();
        doReturn(true).when(messages).sendToUser(channel,1001L,WARNING);doCallRealMethod().when(guard).markProcessed(any());
        var response=send();assertThat(response.getStatusCode().value()).isEqualTo(200);assertThat(response.getBody()).containsEntry("restart_questionnaire",true).doesNotContainKey("ticket_id");
        assertThat(send().getBody()).containsEntry("duplicate",true);assertThat(raw()).isEqualTo(durable);assertDurableState(scenario,before);
        verify(messages,times(2)).sendToUser(channel,1001L,WARNING);verify(guard,times(failure.equals("ack")?2:1)).markProcessed(any());
    }
    @ParameterizedTest @MethodSource("scenarios")
    void confirmedRecoveryInstructionKeepsItsExistingSuccessfulResponse(Scenario scenario) {
        prepare(scenario);var before=raw();var response=send();
        assertThat(response.getStatusCode().value()).isEqualTo(200);assertThat(response.getBody()).containsEntry("restart_questionnaire",true).doesNotContainKey("ticket_id");
        verify(messages).sendToUser(channel,1001L,WARNING);verify(guard).markProcessed(any());verify(guard,never()).release(any());
        assertDurableState(scenario,before);var durable=raw();assertThat(send().getBody()).containsEntry("duplicate",true);assertThat(raw()).isEqualTo(durable);
        verify(messages).sendToUser(channel,1001L,WARNING);
    }
    @Test void channelResolutionFailureWhilePreparingRecoveryAlsoReleasesTheClaim() {
        when(channels.resolveConfiguredChannel(55L,"","MAX","max")).thenReturn(channel).thenThrow(new IllegalStateException("channel unavailable"));
        assertThatThrownBy(this::send).isInstanceOf(IllegalStateException.class);verify(guard).release(any());verify(guard,never()).markProcessed(any());
        verifyNoInteractions(messages,attachments,api,settings,feedback,history);assertThat(raw()).isEmpty();verify(tickets,never()).createConversationTicket(any());
        doReturn(channel).when(channels).resolveConfiguredChannel(55L,"","MAX","max");
        assertThat(send().getBody()).containsEntry("restart_questionnaire",true);assertThat(send().getBody()).containsEntry("duplicate",true);
        verify(messages).sendToUser(channel,1001L,WARNING);
    }
}
