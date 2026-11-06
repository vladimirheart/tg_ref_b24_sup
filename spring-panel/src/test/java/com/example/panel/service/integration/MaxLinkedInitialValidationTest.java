package com.example.panel.service.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.panel.storage.AttachmentObjectStorageService;
import java.io.ByteArrayInputStream;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class MaxLinkedInitialValidationTest {
    @ParameterizedTest @ValueSource(strings={"missing-source","legacy-scope","missing-catalog","wrong-platform"})
    void legacyScopeCannotBypassOwningLinkedMediaValidation(String mode)throws Exception {
        var fixture=new SelectedQuestionnaireMediaIngestionTest();
        var f=new MaxInitialQuestionnaireMediaValidatorTest();var old=f.entry(f.key(false,1));
        var linked=new ConversationTicketCreatedEvent.ConversationHistoryEntryPayload(old.userId(),old.text(),"max_linked.v1:document",old.attachmentPath(),old.attachmentName(),old.providerMessageId(),old.occurredAt(),new MessageSourceLink("reply","mid.quoted",null,null));
        var mapper=org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build();
        var json=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.valueToTree(SelectedQuestionnaireMediaValidatorTest.event(false,"max",List.of(linked)));
        switch(mode){case "missing-source"->json.putNull("scopeSelectionSource");case "legacy-scope"->json.putNull("scopeKind");case "missing-catalog"->json.putNull("catalogSha256");case "wrong-platform"->json.put("platform","vk");}
        var event=mapper.treeToValue(json,ConversationTicketCreatedEvent.class);
        var service=(ConversationTicketCreationIngestionService)org.springframework.test.util.ReflectionTestUtils.getField(fixture,"service");
        assertThatThrownBy(()->service.ingest(event,"integration.ticket.max")).isInstanceOf(IllegalStateException.class).hasMessageContaining("explicitly selected MAX questionnaire");
        for(String field:List.of("channels","messages","tickets","spans","active","history","metadata","jdbc","storage"))verifyNoInteractions(org.springframework.test.util.ReflectionTestUtils.getField(fixture,field));
    }
    @ParameterizedTest @CsvSource({"false,forward","false,reply","true,forward","true,reply"})
    void initialByteProofKeepsItsActualAuthorAndAcceptsOnlyVersionedSource(boolean general,String type)throws Exception {
        var f=new MaxInitialQuestionnaireMediaValidatorTest();var storage=mock(AttachmentObjectStorageService.class);String key=f.key(general,1);
        when(storage.openDialogAttachmentByStorageKey(key)).thenReturn(new AttachmentObjectStorageService.StoredBinary(key,"application/pdf",3,new ByteArrayInputStream(f.bytes)));
        var old=f.entry(key);var source=new MessageSourceLink(type,"mid.quoted","9007199254740993","Original");
        var entry=new ConversationTicketCreatedEvent.ConversationHistoryEntryPayload(old.userId(),old.text(),"max_linked.v1:document",old.attachmentPath(),old.attachmentName(),old.providerMessageId(),old.occurredAt(),source);
        var event=SelectedQuestionnaireMediaValidatorTest.event(general,"max",List.of(entry));
        assertThat(new SelectedQuestionnaireMediaValidator(storage).validate(event,4)).containsKey(key);
        assertThat(entry.userId()).isEqualTo(1001L);assertThat(entry.sourceLink().authorUserId()).isEqualTo("9007199254740993");
    }
    @ParameterizedTest @ValueSource(strings={"missing-version","missing-link","foreign-platform","legacy-version","no-bytes","conflicting-sources"})
    void invalidContextIsRejectedBeforeOpeningAnyFile(String kind)throws Exception {
        var f=new MaxInitialQuestionnaireMediaValidatorTest();var old=f.entry(f.key(false,1));var source=new MessageSourceLink("forward","mid.quoted",null,"Author");
        var entry=new ConversationTicketCreatedEvent.ConversationHistoryEntryPayload(old.userId(),old.text(),kind.equals("missing-version")?"document":"max_linked.v1:document",kind.equals("no-bytes")?null:old.attachmentPath(),old.attachmentName(),old.providerMessageId(),old.occurredAt(),kind.equals("missing-link")?null:source);
        var entries=new ArrayList<ConversationTicketCreatedEvent.ConversationHistoryEntryPayload>();entries.add(entry);
        if(kind.equals("conflicting-sources"))entries.add(new ConversationTicketCreatedEvent.ConversationHistoryEntryPayload(old.userId(),old.text(),"max_linked.v1:document",f.key(false,2),old.attachmentName(),old.providerMessageId(),old.occurredAt(),new MessageSourceLink("forward","different-mid",null,null)));
        var storage=mock(AttachmentObjectStorageService.class);var event=SelectedQuestionnaireMediaValidatorTest.event(false,kind.equals("foreign-platform")?"vk":"max",entries);
        assertThatThrownBy(()->new SelectedQuestionnaireMediaValidator(storage).validate(event,kind.equals("legacy-version")?3:4)).isInstanceOf(RuntimeException.class);verifyNoInteractions(storage);
    }
}
