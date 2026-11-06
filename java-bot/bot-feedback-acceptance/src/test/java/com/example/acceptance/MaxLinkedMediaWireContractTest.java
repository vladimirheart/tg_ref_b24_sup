package com.example.acceptance;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.supportbot.service.*;
import com.example.supportbot.config.IntegrationRabbitProperties;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class MaxLinkedMediaWireContractTest {
    static Stream<Arguments> matrix(){return Stream.of(false,true).flatMap(g->Stream.of("forward","reply").map(type->Arguments.of(g,type)));}
    final com.fasterxml.jackson.databind.ObjectMapper mapper=org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build();
    @ParameterizedTest @MethodSource("matrix")
    void actualPublisherWireDeserializesAsActualBackendBatch(boolean general,String type)throws Exception {
        var outbox=mock(IntegrationTransportOutboxService.class);var channel=new com.example.supportbot.entity.Channel();channel.setId(9007199254740995L);channel.setPlatform("max");
        var routes=new IntegrationRabbitProperties();routes.setRoutingMax("isolated.max");var source=new MessageSourceLink(type,"mid.original:<opaque>","9223372036854775807","Original author");
        var command=new ActiveInboundClientMessageCommand(9007199254740993L,"actual","actual","Client",channel," raw/Ю%?' ","Caption","selected_media_batch.v1",null,null,Long.MAX_VALUE,null,null,OffsetDateTime.parse("2026-11-06T06:00:00Z"),source);
        String key="questionnaires/max/"+channel.getId()+"/"+(general?"general":"business-7")+"/"+command.userId()+"/"+"c".repeat(64)+"/"+Long.MAX_VALUE+"/1/"+"a".repeat(64)+".pdf";
        new InboundClientMessagePublisher(outbox,routes).publishSelectedMediaBatch(command,List.of(new InboundClientMessageEvent.MediaItem("document",key,"proof.pdf")));
        var captured=org.mockito.ArgumentCaptor.forClass(InboundClientMessageEvent.class);verify(outbox).enqueueInboundClientMessage(captured.capture(),eq("isolated.max"),same(routes));
        var event=mapper.readValue(mapper.writeValueAsBytes(captured.getValue()),com.example.panel.service.integration.InboundClientMessageEvent.class);
        assertThat(event.userId()).isEqualTo(command.userId());assertThat(event.ticketId()).isEqualTo(command.ticketId());assertThat(event.sourceLink().authorUserId()).isEqualTo("9223372036854775807");
        assertThat(event.sourceLink().providerMessageId()).isEqualTo(source.providerMessageId());assertThat(event.sourceLink().type()).isEqualTo(type);
        assertThat(com.example.panel.service.integration.MessageSourceLink.batchType(event.messageType(),event.sourceLink())).isTrue();
        assertThat(event.mediaBatch().get(0).attachmentPath()).isEqualTo(key);
    }
    @ParameterizedTest @MethodSource("matrix")
    void actualInitialPublisherKeepsLogicalSessionDataAndVersionedBackendHistory(boolean general,String type)throws Exception {
        var outbox=mock(IntegrationTransportOutboxService.class);var channel=new com.example.supportbot.entity.Channel();channel.setId(55L);channel.setPlatform("max");var now=OffsetDateTime.parse("2026-11-06T06:00:00Z");
        var source=new MessageSourceLink(type,"mid.original+opaque",null,"Author");var history=List.of(new ConversationHistoryEntry(1001L,"Caption","document","owned/key","proof.pdf","33",now,source));
        var command=new ConversationTicketCreationCommand(1001L,"actual","actual","Client",Map.of(),List.of(),history,channel,now,"submission",general?null:7L,general?"GENERAL":"BUSINESS","USER_SELECTION","q","a".repeat(64),"c".repeat(64));
        new ConversationTicketCreatedPublisher(outbox,new IntegrationRabbitProperties()).publish(command,"raw",null,null,null,null,"Caption");
        var captured=org.mockito.ArgumentCaptor.forClass(ConversationTicketCreatedEvent.class);verify(outbox).enqueueConversationTicketCreated(captured.capture(),any());
        var event=mapper.readValue(mapper.writeValueAsBytes(captured.getValue()),com.example.panel.service.integration.ConversationTicketCreatedEvent.class);var entry=event.historyEntries().get(0);
        assertThat(entry.sourceLink().providerMessageId()).isEqualTo(source.providerMessageId());assertThat(entry.userId()).isEqualTo(1001L);
        assertThat(com.example.panel.service.integration.MessageSourceLink.mediaType(event.platform(),entry.messageType(),entry.sourceLink())).isEqualTo("document");
        assertThat(command.historyEntries().get(0).messageType()).isEqualTo("document");
    }
}
