package com.example.supportbot.max;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.supportbot.service.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class MaxLinkedQuestionnaireMediaTest {
    @TempDir Path root;
    final ObjectMapper mapper=org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build();
    ObjectNode message(String type) {
        ObjectNode m=mapper.createObjectNode();
        m.set("sender",mapper.valueToTree(Map.of("user_id",1001,"name","Actual client")));
        m.set("recipient",mapper.valueToTree(Map.of("chat_id",2002)));
        var file=Map.of("type","file","name","private.pdf","payload",Map.of("url","https://cdn.max.ru/owned.pdf","size",3));
        m.set("body",mapper.valueToTree(Map.of("mid",Long.toString(Long.MAX_VALUE),"text","Outer caption","attachments",type.equals("reply")?List.of(file):List.of())));
        m.set("link",mapper.valueToTree(Map.of("type",type,"sender",Map.of("user_id",9007199254740993L,"first_name","Original","last_name","Author"),
            "message",Map.of("mid","mid.original:opaque+id","text","Quoted text","attachments",List.of(file)))));
        return m;
    }
    ObjectNode update(String id,ObjectNode message){var u=mapper.createObjectNode();u.put("update_type","message_created");u.put("update_id",id);u.set("message",message);return u;}
    static Stream<Arguments> matrix(){return Stream.of(false,true).flatMap(g->Stream.of("forward","reply").map(t->Arguments.of(g,t)));}
    @ParameterizedTest @MethodSource("matrix")
    void draftCasAndFinalCommandKeepOriginalContextAndActualClient(boolean general,String type)throws Exception {
        var initial=new MaxInitialQuestionnaireMediaTest();initial.root=root;initial.choose(general);var f=initial.f;
        when(f.runtime().confirmMaxLinkedQuestionnaireMedia(any(),any(),any())).thenReturn(true);
        assertThat(initial.base.handleFollowupUpdate(update("linked-draft",message(type))).getStatusCode().value()).isEqualTo(200);
        var state=initial.base.state();assertThat(state.userId()).isEqualTo(1001L);
        var entry=state.history().get(0);assertThat(entry.userId()).isEqualTo(1001L);
        assertThat(entry.sourceLink()).isEqualTo(new MessageSourceLink(type,"mid.original:opaque+id","9007199254740993","Original Author"));
        assertThat(entry.attachmentPath()).contains("/1001/");assertThat(entry.providerMessageId()).isNotEqualTo(entry.sourceLink().providerMessageId());
        String saved=initial.base.r268Raw();initial.base.r268ResetDeliveryCache();var changed=message(type);((ObjectNode)changed.path("link").path("message")).put("mid","changed");
        assertThat(initial.base.handleFollowupUpdate(update("linked-draft",changed)).getBody()).containsEntry("questionnaire_replayed",true);
        assertThat(initial.base.r268Raw()).isEqualTo(saved);verify(f.api(),times(1)).downloadQuestionnaireAttachment(anyString());
        initial.base.send("second","Two");initial.base.send("third","Three");
        var command=org.mockito.ArgumentCaptor.forClass(ConversationTicketCreationCommand.class);verify(f.tickets()).createConversationTicket(command.capture());
        assertThat(command.getValue().userId()).isEqualTo(1001L);assertThat(command.getValue().historyEntries().get(0).sourceLink()).isEqualTo(entry.sourceLink());
    }
    @ParameterizedTest @MethodSource("matrix")
    void publishedBatchKeepsOuterIdentityAndRequiresDistinctAdmission(boolean general,String type)throws Exception {
        var followup=new MaxPublishedFollowupMediaTest();followup.root=root;followup.setup(general,false);var f=followup.f;
        var old=followup.proof;
        when(f.runtime().selectedMaxLinkedMediaAdmission(f.channel(),1001L,"T-MAX")).thenReturn(new PanelRuntimeConfigClient.SelectedMediaAdmission(4,55,1001,"T-MAX",old.businessId(),old.scopeKind(),old.questionTemplateId(),old.templateSha256(),old.catalogSha256(),10));
        assertThat(followup.base.handleFollowupUpdate(update("linked-published",message(type))).getBody()).containsEntry("media_queued",true);
        var command=org.mockito.ArgumentCaptor.forClass(ActiveInboundClientMessageCommand.class);verify(f.tickets()).recordSelectedMediaBatch(command.capture(),any());
        assertThat(command.getValue().sourceLink().type()).isEqualTo(type);assertThat(command.getValue().userId()).isEqualTo(1001L);
        assertThat(command.getValue().ticketId()).isEqualTo("T-MAX");assertThat(command.getValue().providerMessageId()).isEqualTo(Long.MAX_VALUE);
        assertThat(command.getValue().replyToProviderMessageId()).isNull();assertThat(command.getValue().forwardedFrom()).isNull();
        verify(f.runtime(),never()).selectedMaxMediaAdmission(any(),anyLong(),anyString());verify(f.api(),times(1)).downloadQuestionnaireAttachment("https://cdn.max.ru/owned.pdf");
        var accepted=command.getValue().sourceLink();followup.base.r268ResetDeliveryCache();
        when(f.tickets().findRecordedMaxMediaTicket(f.channel(),1001L,Long.MAX_VALUE)).thenReturn(Optional.of("T-MAX"));
        when(f.runtime().selectedMaxLinkedMediaAdmission(any(),anyLong(),anyString())).thenThrow(new IllegalStateException("current binding unavailable"));
        var changed=message(type);((ObjectNode)changed.path("link").path("message")).put("mid","changed-original-id");
        assertThat(followup.base.handleFollowupUpdate(update("linked-published",changed)).getBody()).containsEntry("media_replayed",true);
        assertThat(command.getValue().sourceLink()).isEqualTo(accepted);verify(f.tickets(),times(1)).recordSelectedMediaBatch(any(),any());
        verify(f.api(),times(1)).downloadQuestionnaireAttachment(anyString());
    }
    @ParameterizedTest @ValueSource(strings={"missing-mid","numeric-mid","unsupported-type","alias","nested-body","nested-link","mixed","wrong-author","overflow-author","control-author","long-mid"})
    void ambiguousSourceFailsBeforeProviderIoOrCaptionCas(String change)throws Exception {
        var initial=new MaxInitialQuestionnaireMediaTest();initial.root=root;initial.choose(false);String before=initial.base.r268Raw();var m=message("forward");var link=(ObjectNode)m.path("link");var original=(ObjectNode)link.path("message");
        switch(change) {
            case "missing-mid"->original.remove("mid");case "numeric-mid"->original.put("mid",123);case "unsupported-type"->link.put("type","forwarded");
            case "alias"->link.set("source",original.deepCopy());case "nested-body"->original.set("body",original.deepCopy());case "nested-link"->original.set("link",link.deepCopy());
            case "mixed"->((ObjectNode)m.path("body")).set("attachments",original.path("attachments").deepCopy());
            case "wrong-author"->((ObjectNode)link.path("sender")).put("user_id","123");case "overflow-author"->((ObjectNode)link.path("sender")).put("user_id",new java.math.BigInteger("9223372036854775808"));
            case "control-author"->((ObjectNode)link.path("sender")).put("first_name","bad\u0000name");case "long-mid"->original.put("mid","a".repeat(513));
        }
        assertThat(initial.base.handleFollowupUpdate(update("bad-linked",m)).getBody()).containsEntry("questionnaire_media_unsupported",true);
        assertThat(initial.base.r268Raw()).isEqualTo(before);verify(initial.f.api(),never()).downloadQuestionnaireAttachment(anyString());verify(initial.f.attachments(),never()).storeMaxQuestionnaire(anyLong(),any(),anyLong(),anyString(),anyLong(),anyInt(),anyString(),any());
    }
    @ParameterizedTest @ValueSource(strings={"forward","reply"})
    void oldOrUnavailableCapabilityNeverFallsBackToDirectMedia(String type)throws Exception {
        var followup=new MaxPublishedFollowupMediaTest();followup.root=root;followup.setup(false,false);
        assertThat(followup.base.handleFollowupUpdate(update("old-backend",message(type))).getBody()).containsEntry("questionnaire_media_unsupported",true);
        verify(followup.f.api(),never()).downloadQuestionnaireAttachment(anyString());verify(followup.f.tickets(),never()).recordSelectedMediaBatch(any(),any());
    }
    @Test void replyQuotesAreNotNewlySubmittedFilesEvenWithUntrustedUrls() {
        var m=message("reply");((ObjectNode)m.path("link").path("message")).set("attachments",mapper.valueToTree(List.of(Map.of("type","file","payload",Map.of("url","http://127.0.0.1/secret")))));
        var payload=MaxInboundPayloadSupport.resolveInboundPayload(m,MaxInboundPayloadSupport.resolveClientProfile(m,1001L));
        var batch=MaxQuestionnaireMedia.describeBatch(m,payload.attachments());assertThat(batch.items()).singleElement().satisfies(i->assertThat(i.url()).isEqualTo("https://cdn.max.ru/owned.pdf"));
    }
}
