package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.supportbot.config.IntegrationRabbitProperties;
import com.example.supportbot.entity.Channel;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MaxLinkedPublicationTest {
    @ParameterizedTest @ValueSource(booleans={false,true})
    void initialWireMarksLinkedItemsWithoutChangingSubmissionIdentity(boolean general) {
        var outbox=mock(IntegrationTransportOutboxService.class);var publisher=new ConversationTicketCreatedPublisher(outbox,new IntegrationRabbitProperties());
        var channel=new Channel();channel.setId(55L);channel.setPlatform("max");var now=OffsetDateTime.now();
        var source=new MessageSourceLink("forward","mid.original",null,"Author");
        var entries=List.of(new ConversationHistoryEntry(1001L,"Caption","document","owned/key","file.pdf","33",now,source));
        var command=new ConversationTicketCreationCommand(1001L,"actual","actual","Actual",Map.of(),List.of(),entries,channel,now,"same-submission",general?null:7L,general?"GENERAL":"BUSINESS","USER_SELECTION","frozen","a".repeat(64),"c".repeat(64));
        publisher.publish(command," raw-root ",null,null,null,null,"Caption");
        var captured=org.mockito.ArgumentCaptor.forClass(ConversationTicketCreatedEvent.class);verify(outbox).enqueueConversationTicketCreated(captured.capture(),any());
        var event=captured.getValue();assertThat(event.historyEntries()).singleElement().satisfies(entry->{assertThat(entry.sourceLink()).isEqualTo(source);assertThat(entry.messageType()).isEqualTo("max_linked.v1:document");assertThat(entry.userId()).isEqualTo(1001L);});
        assertThat(event.eventId()).isEqualTo(InboundProviderIdentity.deterministicId("ticket.created.initial_contact",command));
        assertThat(command.historyEntries().get(0).messageType()).isEqualTo("document");
    }
    @ParameterizedTest @ValueSource(strings={"forward","reply"})
    void immutableLinkedOutboxIsRecognizedWithoutCurrentApiOrNewPublication(String type)throws Exception {
        var fixture=new MaxFollowupOutboxTest();fixture.setup();fixture.payload.put("messageType",MessageSourceLink.BATCH_TYPE);
        fixture.payload.set("sourceLink",fixture.mapper.valueToTree(new MessageSourceLink(type,"mid.original","9007199254740993","Author")));
        fixture.rows.add(fixture.row("published"));String saved=fixture.mapper.writeValueAsString(fixture.rows);
        assertThat(fixture.recorded()).contains(" T-MAX ");assertThat(fixture.mapper.writeValueAsString(fixture.rows)).isEqualTo(saved);
        verify(fixture.jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void missingVersionOnStoredLinkFailsClosed()throws Exception {
        var fixture=new MaxFollowupOutboxTest();fixture.setup();fixture.payload.set("sourceLink",fixture.mapper.valueToTree(new MessageSourceLink("reply","mid.original",null,null)));
        fixture.rows.add(fixture.row("published"));assertThatThrownBy(fixture::recorded).isInstanceOf(IllegalStateException.class);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void signedLinkedAdmissionIsUncachedAndUsesItsDistinctVersionedRoute(boolean general)throws Exception {
        var fixture=new MaxFollowupAdmissionTest();fixture.setup();
        try {
            fixture.proof.put("version",4);if(general)fixture.proof.put("scopeKind","GENERAL").putNull("businessId");
            var proof=fixture.client.selectedMaxLinkedMediaAdmission(8,901," T? /& ");assertThat(proof.version()).isEqualTo(4);assertThat(fixture.signed).isTrue();
            assertThat(fixture.path).isEqualTo("/internal/api/bot/channels/8/questionnaire-media/max-linked-batch?userId=901&ticketId=+T%3F+%2F%26+");
            fixture.proof.put("remainingFiles",1);assertThat(fixture.client.selectedMaxLinkedMediaAdmission(8,901," T? /& ").remainingFiles()).isEqualTo(1);
        } finally {fixture.stop();}
    }
    @ParameterizedTest @ValueSource(ints={0,1,2,3,5,404,503})
    void oldUnavailableOrWrongCapabilityCannotBecomeDirectAdmission(int mode)throws Exception {
        var fixture=new MaxFollowupAdmissionTest();fixture.setup();
        try {
            if(mode>=400)fixture.status=mode;else fixture.proof.put("version",mode);
            assertThatThrownBy(()->fixture.client.selectedMaxLinkedMediaAdmission(8,901," T? /& ")).isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);
            assertThat(fixture.path).contains("/max-linked-batch?");
        } finally {fixture.stop();}
    }
}
