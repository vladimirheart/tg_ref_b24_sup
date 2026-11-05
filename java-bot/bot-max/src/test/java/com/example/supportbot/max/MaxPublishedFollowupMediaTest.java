package com.example.supportbot.max;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.supportbot.service.*;
import com.example.supportbot.entity.TicketActive;
import com.example.supportbot.config.ObjectStorageProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MaxPublishedFollowupMediaTest {
    @TempDir Path root;
    final MaxQuestionnaireSelectionTest base=new MaxQuestionnaireSelectionTest();
    final ObjectMapper mapper=org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build();
    MaxQuestionnaireSelectionTest.FollowupFixture f;
    PanelRuntimeConfigClient.SelectedMediaAdmission proof;
    void stub(Object target,String method,Class<?>[] types,Object value,Object... args) throws Exception {
        try{target.getClass().getMethod(method,types).invoke(doReturn(value).when(target),args);}catch(NoSuchMethodException baseline){}
    }
    void setup(boolean general,boolean session) throws Exception {
        base.setup();base.r268Choose(4,general);f=base.followupFixture();f.channel().setActive(true);
        base.send("answer1","One");base.send("answer2","Two");base.send("answer3","Three");
        if(!session)f.store().delete("max",55L,1001L);
        var active=new TicketActive();active.setTicketId("T-MAX");when(f.tickets().findActiveTicketForUser(eq(1001L),anyString(),eq(55L))).thenReturn(Optional.of(active));
        when(f.tickets().findByTicketId("T-MAX")).thenReturn(Optional.of(new TicketService.TicketWithUser(1001L,"T-MAX","open",55L,4,1)));
        proof=new PanelRuntimeConfigClient.SelectedMediaAdmission(3,55,1001,"T-MAX",general?null:7L,general?"GENERAL":"BUSINESS",general?"q-general":"q-a","a".repeat(64),"c".repeat(64),10);
        admission(proof);
        when(f.api().downloadQuestionnaireAttachment(anyString())).thenReturn(new MaxApiClient.DownloadedQuestionnaireAttachment(new byte[]{1,2,3},"application/pdf"));
        var actual=new AttachmentService(root,new ObjectStorageProperties());when(f.attachments().storeMaxQuestionnaire(anyLong(),any(),anyLong(),anyString(),anyLong(),anyInt(),anyString(),any())).thenAnswer(c->actual.storeMaxQuestionnaire(c.getArgument(0),c.getArgument(1),c.getArgument(2),c.getArgument(3),c.getArgument(4),c.getArgument(5),c.getArgument(6),c.getArgument(7)));
        clearInvocations(f.tickets(),f.runtime(),f.api(),f.attachments(),f.messages(),f.store());
    }
    void admission(PanelRuntimeConfigClient.SelectedMediaAdmission p) throws Exception {stub(f.runtime(),"selectedMaxMediaAdmission",new Class<?>[]{com.example.supportbot.entity.Channel.class,long.class,String.class},p,f.channel(),1001L,"T-MAX");}
    org.springframework.http.ResponseEntity<Map<String,Object>> media(String caption,int count,String shape) {
        var list=new ArrayList<Map<String,Object>>();for(int n=0;n<count;n++)list.add(Map.of("type",shape.equals("unknown")?"unknown":"file","name","proof.pdf","payload",shape.equals("opaque")||(shape.equals("mixed")&&n>0)?Map.of("token","private"):Map.of("url",shape.equals("foreign")?"https://example.invalid/file":"https://cdn.max.ru/private?slot="+n)));
        var message=new LinkedHashMap<String,Object>();message.put("message_id",Long.MAX_VALUE);message.put("sender",Map.of("user_id",1001,"username","client","name","Client"));message.put("recipient",Map.of("chat_id",shape.equals("chat")?2003:2002));message.put("body",Map.of("text",caption,"attachments",list));if(Set.of("forward","reply").contains(shape))message.put("link",Map.of("type",shape));
        return base.handleFollowupUpdate(mapper.valueToTree(Map.of("update_type","message_created","update_id","published-media","message",message)));
    }
    @ParameterizedTest @ValueSource(strings={"business-session","general-session","business-missing","general-missing"})
    void publishedRootUsesCanonicalOwnerWithoutCurrentCatalogOrQuestionnaireMutation(String mode) throws Exception {
        boolean general=mode.startsWith("general"),session=mode.endsWith("session");setup(general,session);String before=session?base.r268Raw():null;
        assertThat(media("Private caption",2,"ok").getBody()).containsEntry("ticket_id","T-MAX").containsEntry("media_queued",true);
        var command=org.mockito.ArgumentCaptor.forClass(ActiveInboundClientMessageCommand.class);
        @SuppressWarnings("unchecked") var items=org.mockito.ArgumentCaptor.forClass((Class<List<InboundClientMessageEvent.MediaItem>>)(Class<?>)List.class);
        verify(f.tickets()).recordSelectedMediaBatch(command.capture(),items.capture());assertThat(command.getValue().providerMessageId()).isEqualTo(Long.MAX_VALUE);assertThat(command.getValue().text()).isEqualTo("Private caption");assertThat(command.getValue().occurredAt()).isNotNull();assertThat(items.getValue()).hasSize(2);
        for(int i=0;i<2;i++)assertThat(items.getValue().get(i).attachmentPath()).startsWith("questionnaires/max/55/"+(general?"general":"business-7")+"/1001/"+"c".repeat(64)+"/"+Long.MAX_VALUE+"/"+(i+1)+"/");
        if(session)assertThat(base.r268Raw()).isEqualTo(before);else assertThat(f.store().load("max",55L,1001L,MaxConversationSession.State.class)).isEmpty();
        verify(f.tickets(),never()).recordActiveClientMessage(any());verify(f.tickets(),never()).createConversationTicket(any());verify(f.runtime(),never()).questionnaireChoices(any());verify(f.runtime(),never()).findChannelConfig(any());verify(f.api(),never()).downloadAttachment(anyString());verify(f.messages(),never()).sendToSupportChat(any(),anyString());
    }
    @ParameterizedTest @ValueSource(strings={"forward","reply","opaque","mixed","unknown","foreign","quota","admission","session-owner","root-owner"})
    void unsupportedOrUnprovedBatchDoesNotConsumeCaptionOrDownload(String kind) throws Exception {
        setup(false,true);String before=base.r268Raw();
        if(kind.equals("quota"))admission(new PanelRuntimeConfigClient.SelectedMediaAdmission(3,55,1001,"T-MAX",7L,"BUSINESS","q-a","a".repeat(64),"c".repeat(64),1));
        if(kind.equals("admission"))admission(null);
        if(kind.equals("session-owner"))admission(new PanelRuntimeConfigClient.SelectedMediaAdmission(3,55,1001,"T-MAX",8L,"BUSINESS","q-b","b".repeat(64),"d".repeat(64),10));
        if(kind.equals("root-owner"))when(f.tickets().findByTicketId("T-MAX")).thenReturn(Optional.of(new TicketService.TicketWithUser(1002L,"T-MAX","open",55L,4,1)));
        var response=media("Do not consume",2,kind);assertThat(response.getStatusCode().value()).isIn(200,503);assertThat(response.getBody()).doesNotContainEntry("media_queued",true);assertThat(base.r268Raw()).isEqualTo(before);verify(f.api(),never()).downloadQuestionnaireAttachment(anyString());verify(f.tickets(),never()).recordSelectedMediaBatch(any(),any());verify(f.tickets(),never()).recordActiveClientMessage(any());
    }
    @ParameterizedTest @ValueSource(strings={"storage","outbox"})
    void failureBeforeDurableAcceptanceReleasesClaimAndRetriesWholeBatch(String kind) throws Exception {
        setup(false,false);
        if(kind.equals("storage"))doThrow(new java.io.IOException("failed")).doReturn(new MaxApiClient.DownloadedQuestionnaireAttachment(new byte[]{1,2,3},null)).when(f.api()).downloadQuestionnaireAttachment(anyString());
        else doThrow(new IllegalStateException("outbox failed")).doNothing().when(f.tickets()).recordSelectedMediaBatch(any(),any());
        assertThatThrownBy(()->media("Caption",2,"ok")).isInstanceOf(IllegalStateException.class);
        assertThat(media("Caption",2,"ok").getBody()).containsEntry("media_queued",true);
    }
    @ParameterizedTest @ValueSource(strings={"followup","initial-media","changed-caption","new-active-root"})
    void durableOutboxWinsAfterLostAckAndSessionEviction(String kind) throws Exception {
        setup(false,false);
        stub(f.tickets(),"findRecordedMaxMediaTicket",new Class<?>[]{com.example.supportbot.entity.Channel.class,long.class,long.class},Optional.of("OLD-ROOT"),f.channel(),1001L,Long.MAX_VALUE);
        when(f.tickets().findActiveTicketForUser(anyLong(),anyString(),anyLong())).thenThrow(new IllegalStateException("must not recapture active root"));
        var response=media(kind.equals("changed-caption")?"Changed":"Caption",2,"ok");assertThat(response.getBody()).containsEntry("ticket_id","OLD-ROOT").containsEntry("media_replayed",true);
        verify(f.api(),never()).downloadQuestionnaireAttachment(anyString());verify(f.tickets(),never()).recordSelectedMediaBatch(any(),any());verify(f.runtime(),never()).findChannelConfig(any());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void realOutboxPayloadSurvivesCommitBeforeExceptionAndEvictedSession(boolean general) throws Exception {
        setup(general,false);
        var rows=new LinkedHashMap<String,Map<String,Object>>();
        var jdbc=new org.springframework.jdbc.core.JdbcTemplate() {
            @Override public void execute(String sql) {}
            @Override public int update(String sql,Object... args) {
                if(!sql.contains("INSERT INTO integration_transport_outbox"))throw new AssertionError(sql);
                var row=Map.<String,Object>of("event_id",args[0],"event_kind",args[2],"payload_json",args[5],"ticket_id",args[8],"status",args[10]);
                return rows.putIfAbsent((String)args[0],row)==null?1:0;
            }
            @Override public List<Map<String,Object>> queryForList(String sql,Object... args) {return new ArrayList<>(rows.values());}
        };
        var outbox=new IntegrationTransportOutboxService(jdbc,mapper,mock(org.springframework.amqp.rabbit.core.RabbitTemplate.class));
        var rabbit=new com.example.supportbot.config.IntegrationRabbitProperties();rabbit.setInboundExchange("isolated.inbound");rabbit.setRoutingMax("isolated.max");var publisher=new InboundClientMessagePublisher(outbox,rabbit);
        var once=new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(c->{publisher.publishSelectedMediaBatch(c.getArgument(0),c.getArgument(1));if(once.getAndSet(false))throw new IllegalStateException("committed before reply lost");return null;}).when(f.tickets()).recordSelectedMediaBatch(any(),any());
        try {
            var lookup=f.tickets().getClass().getMethod("findRecordedMaxMediaTicket",com.example.supportbot.entity.Channel.class,long.class,long.class);
            lookup.invoke(doAnswer(c->outbox.getClass().getMethod("findRecordedMaxMediaTicket",long.class,long.class,long.class).invoke(outbox,c.<com.example.supportbot.entity.Channel>getArgument(0).getId(),c.getArgument(1),c.getArgument(2))).when(f.tickets()),f.channel(),1001L,Long.MAX_VALUE);
        }catch(NoSuchMethodException baseline){}
        assertThatThrownBy(()->media("Original caption",2,"ok")).hasMessageContaining("committed before reply lost");
        assertThat(rows).hasSize(1);String original=(String)rows.values().iterator().next().get("payload_json");
        base.r268ResetDeliveryCache();f.store().delete("max",55L,1001L);
        when(f.tickets().findActiveTicketForUser(anyLong(),anyString(),anyLong())).thenThrow(new IllegalStateException("current binding unavailable"));
        assertThat(media("Changed caption",2,"ok").getBody()).containsEntry("media_replayed",true).containsEntry("ticket_id","T-MAX");
        assertThat(rows.values().iterator().next().get("payload_json")).isEqualTo(original);
        verify(f.api(),times(2)).downloadQuestionnaireAttachment(anyString());verify(f.tickets(),times(1)).recordSelectedMediaBatch(any(),any());
    }
    @Test void sessionChatMismatchRemainsRejectedBeforeProviderIo() throws Exception {setup(false,true);var saved=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(base.r268Raw());saved.put("chatId",2003);f.store().save("max",55L,1001L,mapper.treeToValue(saved,MaxConversationSession.State.class));assertThat(media("Caption",1,"ok").getStatusCode().value()).isEqualTo(409);verify(f.api(),never()).downloadQuestionnaireAttachment(anyString());}
}
