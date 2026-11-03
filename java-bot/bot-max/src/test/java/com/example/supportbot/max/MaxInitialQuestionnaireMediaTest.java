package com.example.supportbot.max;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.supportbot.service.*;
import com.example.supportbot.config.ObjectStorageProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class MaxInitialQuestionnaireMediaTest {
    @TempDir Path root;
    protected MaxQuestionnaireSelectionTest fixture(){return new MaxQuestionnaireSelectionTest();}
    final MaxQuestionnaireSelectionTest base=fixture();
    final ObjectMapper mapper=org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build();
    MaxQuestionnaireSelectionTest.FollowupFixture f;
    void choose(boolean general) throws Exception {
        base.setup();base.r268Choose(4,general);f=base.followupFixture();
        when(f.runtime().confirmMaxQuestionnaireMedia(any(),any(),any())).thenReturn(true);
        when(f.api().downloadQuestionnaireAttachment(anyString())).thenReturn(new MaxApiClient.DownloadedQuestionnaireAttachment(new byte[]{1,2,3},"application/pdf"));
        var actual=new AttachmentService(root,new ObjectStorageProperties());
        when(f.attachments().storeMaxQuestionnaire(anyLong(),any(),anyLong(),anyString(),anyLong(),anyInt(),anyString(),any())).thenAnswer(c->
            actual.storeMaxQuestionnaire(c.getArgument(0),c.getArgument(1),c.getArgument(2),c.getArgument(3),c.getArgument(4),c.getArgument(5),c.getArgument(6),c.getArgument(7)));
        clearInvocations(f.runtime(),f.api(),f.attachments(),f.messages(),f.tickets(),f.store());
    }
    org.springframework.http.ResponseEntity<Map<String,Object>> media(String id,int count,String caption,String shape) {
        var list=new ArrayList<Map<String,Object>>();
        for(int n=0;n<count;n++)list.add(Map.of("type",shape.equals("unknown")?"unknown":"file","name","private.pdf","payload",shape.equals("opaque")||(shape.equals("mixed")&&n>0)?Map.of("token","private"):Map.of("url",shape.equals("foreign")?"https://example.invalid/file":"https://cdn.max.ru/private?slot="+n)));
        var body=Map.of("text",caption,"attachments",list);
        var message=Map.<String,Object>of("sender",Map.of("user_id",1001,"username","client","name","Client"),"recipient",Map.of("chat_id",2002),"body",body);
        return base.handleFollowupUpdate(mapper.valueToTree(Map.of("update_type","message_created","update_id",id,"message",message)));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void batchIsCasSavedWithFrozenScopeAndSubmittedWithOwnedMetadata(boolean general) throws Exception {
        choose(general);var scope=base.state().scope();
        assertThat(media("media-first",2,"Private caption","ok").getStatusCode().value()).isEqualTo(200);
        assertThat(base.state().currentIndex()).isEqualTo(1);assertThat(base.state().scope()).isEqualTo(scope);
        assertThat(base.state().answers()).containsEntry("free-first","Private caption");
        assertThat(base.state().history()).hasSize(2);
        for(var entry:base.state().history()) {
            assertThat(entry.attachmentPath()).startsWith("questionnaires/max/55/"+(general?"general":"business-7")+"/1001/");
            assertThat(Files.readAllBytes(root.resolve(entry.attachmentPath()))).containsExactly((byte)1,(byte)2,(byte)3);
            assertThat(entry.occurredAt()).isNotNull();assertThat(entry.providerMessageId()).matches("[1-9][0-9]*");
        }
        String raw=base.r268Raw();base.r268ResetDeliveryCache();
        assertThat(media("media-first",2,"Changed caption","ok").getBody()).containsEntry("questionnaire_replayed",true);assertThat(base.r268Raw()).isEqualTo(raw);
        verify(f.api(),times(2)).downloadQuestionnaireAttachment(anyString());
        base.send("second","Two");base.send("last","Three");
        var command=org.mockito.ArgumentCaptor.forClass(ConversationTicketCreationCommand.class);verify(f.tickets()).createConversationTicket(command.capture());
        assertThat(command.getValue().scopeKind()).isEqualTo(general?"GENERAL":"BUSINESS");assertThat(command.getValue().historyEntries().stream().filter(e->e.attachmentPath()!=null).count()).isEqualTo(2);
        verify(f.messages(),never()).sendToSupportChat(any(),anyString());verify(f.api(),never()).downloadAttachment(anyString());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void finalMediaCasCommitAndLostAckCannotChangeSubmissionIdentity(boolean general) throws Exception {
        choose(general);base.send("first","One");base.send("second","Two");
        var once=new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(c->{var value=(MaxConversationSession.State)c.getArgument(4);Object result=c.callRealMethod();if(value.currentIndex()==3&&value.selection().submissionEventKey()==null&&once.getAndSet(false))throw new IllegalStateException("saved media before ack");return result;})
            .when(f.store()).saveIfUnchanged(eq("max"),eq(55L),eq(1001L),anyString(),any());
        assertThatThrownBy(()->media("final-media",1,"","ok")).hasMessageContaining("saved media");
        assertThat(base.state().currentIndex()).isEqualTo(3);base.r268ResetDeliveryCache();
        assertThat(media("final-media",1,"changed","ok").getBody()).containsEntry("ticket_id","T-MAX");
        assertThat(base.state().selection().submissionEventKey()).isEqualTo("update:final-media");
        verify(f.api(),times(1)).downloadQuestionnaireAttachment(anyString());verify(f.tickets(),times(1)).createConversationTicket(any());
    }
    @ParameterizedTest @ValueSource(strings={"foreign","opaque","unknown","mixed"})
    void unsupportedBatchDoesNotDownloadOrMutate(String shape) throws Exception {
        choose(false);String before=base.r268Raw();
        assertThat(media("bad-media",2,"Do not consume",shape).getBody()).containsEntry("questionnaire_media_unsupported",true);
        assertThat(base.r268Raw()).isEqualTo(before);verify(f.api(),never()).downloadQuestionnaireAttachment(anyString());verifyNoInteractions(f.attachments());
    }
    @ParameterizedTest @ValueSource(strings={"handshake","download","second-item","cas"})
    void failuresLeaveQuestionUnadvancedAndTheSameEventCanRetry(String kind) throws Exception {
        choose(false);String before=base.r268Raw();
        if(kind.equals("handshake"))when(f.runtime().confirmMaxQuestionnaireMedia(any(),any(),any())).thenReturn(false);
        if(kind.equals("download"))when(f.api().downloadQuestionnaireAttachment(anyString())).thenThrow(new IOException("fixture"));
        if(kind.equals("second-item"))when(f.api().downloadQuestionnaireAttachment(anyString())).thenReturn(new MaxApiClient.DownloadedQuestionnaireAttachment(new byte[]{1},null)).thenThrow(new IOException("fixture"));
        if(kind.equals("cas"))doReturn(Optional.empty()).when(f.store()).saveIfUnchanged(eq("max"),eq(55L),eq(1001L),anyString(),any());
        if(kind.equals("handshake"))assertThat(media("retry-media",2,"Caption","ok").getBody()).containsEntry("restart_questionnaire",true);
        else if(kind.equals("cas"))assertThat(media("retry-media",2,"Caption","ok").getStatusCode().value()).isEqualTo(503);
        else assertThatThrownBy(()->media("retry-media",2,"Caption","ok")).hasMessageContaining("storage failed");
        assertThat(base.r268Raw()).isEqualTo(before);verify(f.tickets(),never()).createConversationTicket(any());
        if(kind.equals("handshake"))base.r268ResetDeliveryCache();
        when(f.runtime().confirmMaxQuestionnaireMedia(any(),any(),any())).thenReturn(true);
        doReturn(new MaxApiClient.DownloadedQuestionnaireAttachment(new byte[]{1,2,3},null)).when(f.api()).downloadQuestionnaireAttachment(anyString());
        doCallRealMethod().when(f.store()).saveIfUnchanged(eq("max"),eq(55L),eq(1001L),anyString(),any());
        assertThat(media("retry-media",2,"Caption","ok").getStatusCode().value()).isEqualTo(200);assertThat(base.state().currentIndex()).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"owner","slot","version","timestamp","journal"})
    void corruptedRestoredMediaDoesNotReachDownload(String kind) throws Exception {
        choose(false);media("stored",1,"Caption","ok");
        var tree=mapper.readTree(base.r268Raw());var entry=(com.fasterxml.jackson.databind.node.ObjectNode)tree.path("history").get(0);
        switch(kind){case "owner"->entry.put("attachmentPath",entry.path("attachmentPath").asText().replace("/1001/","/1002/"));case "slot"->entry.put("attachmentPath",entry.path("attachmentPath").asText().replace("/1/","/11/"));case "version"->((com.fasterxml.jackson.databind.node.ObjectNode)tree.path("selection").path("catalog")).put("version",3);case "timestamp"->entry.remove("occurredAt");case "journal"->((com.fasterxml.jackson.databind.node.ObjectNode)tree.path("selection")).remove("acceptedEventKeys");}
        assertThatThrownBy(()->new MaxConversationSession(mapper.treeToValue(tree,MaxConversationSession.State.class))).isInstanceOf(Exception.class);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void lossOfBackendCapabilityBeforeSubmissionDoesNotPublishMediaToAnOldWorker(boolean general) throws Exception {
        choose(general);media("media-first",1,"Caption","ok");base.send("second","Two");
        when(f.runtime().confirmMaxQuestionnaireMedia(any(),any(),any())).thenReturn(false);
        assertThat(base.send("final-answer","Three").getBody()).containsEntry("restart_questionnaire",true);
        verify(f.tickets(),never()).createConversationTicket(any());assertThat(base.state().selection().submissionEventKey()).isNull();
        when(f.runtime().confirmMaxQuestionnaireMedia(any(),any(),any())).thenReturn(true);base.r268ResetDeliveryCache();
        assertThat(base.send("final-answer","Changed").getBody()).containsEntry("ticket_id","T-MAX");
        verify(f.tickets(),times(1)).createConversationTicket(any());verify(f.api(),times(1)).downloadQuestionnaireAttachment(anyString());
    }
    @ParameterizedTest @ValueSource(strings={"limit","active-root"})
    void limitAndExistingRootAreCheckedBeforeAnyDownload(String boundary) throws Exception {
        choose(false);String before=base.r268Raw();
        if(boundary.equals("active-root")){var active=new com.example.supportbot.entity.TicketActive();active.setTicketId("OTHER");when(f.tickets().findActiveTicketForUser(eq(1001L),anyString(),eq(55L))).thenReturn(Optional.of(active));}
        assertThat(media("blocked",boundary.equals("limit")?11:1,"Caption","ok").getBody()).containsEntry("questionnaire_media_unsupported",true);
        assertThat(base.r268Raw()).isEqualTo(before);verify(f.api(),never()).downloadQuestionnaireAttachment(anyString());
    }
    @org.junit.jupiter.api.Test void oldTextHistoryRetainsItsThreeFieldJsonShape() throws Exception {
        var node=mapper.readTree(mapper.writeValueAsBytes(new MaxConversationSession.HistoryEvent(1001L,"Text","text")));
        assertThat(node.size()).isEqualTo(3);assertThat(node.has("attachmentPath")).isFalse();assertThat(node.path("messageType").asText()).isEqualTo("text");
    }
    @org.junit.jupiter.api.Test void actualSizeRejectionIsAcknowledgedOnlyAfterWarningWithoutAdvancing() throws Exception {
        choose(false);String before=base.r268Raw();
        doThrow(new AttachmentService.MediaSizeException()).when(f.api()).downloadQuestionnaireAttachment(anyString());
        assertThat(media("oversize",1,"Caption","ok").getBody()).containsEntry("questionnaire_media_unsupported",true);
        assertThat(base.r268Raw()).isEqualTo(before);verify(f.messages()).sendToUser(eq(f.channel()),eq(1001L),contains("10 МиБ"));
        verify(f.tickets(),never()).createConversationTicket(any());
    }
}
