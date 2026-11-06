package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.supportbot.config.IntegrationRabbitProperties;
import com.example.supportbot.entity.Channel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MessageSourceLinkTransportTest {
    final MessageSourceLink link=new MessageSourceLink("forward","mid.opaque:<safe>","9007199254740993","Original author");
    final ObjectMapper mapper=(ObjectMapper)org.springframework.test.util.ReflectionTestUtils.getField(
        new IntegrationTransportOutboxService(mock(org.springframework.jdbc.core.JdbcTemplate.class),new ObjectMapper(),mock(org.springframework.amqp.rabbit.core.RabbitTemplate.class)),"transportObjectMapper");
    @ParameterizedTest @ValueSource(strings={"forward","reply"})
    void batchWireRoundTripIsVersionedAndHasTheSameOuterDedupIdentity(String type)throws Exception {
        var source=new MessageSourceLink(type,link.providerMessageId(),link.authorUserId(),link.authorName());
        var outbox=mock(IntegrationTransportOutboxService.class);var routes=new IntegrationRabbitProperties();routes.setRoutingMax("isolated.max");
        var publisher=new InboundClientMessagePublisher(outbox,routes);var channel=new Channel();channel.setId(55L);channel.setPlatform("max");
        var command=new ActiveInboundClientMessageCommand(1001L,"actual","actual","Actual",channel," raw-root ","Caption","selected_media_batch.v1",null,null,Long.MAX_VALUE,null,null,OffsetDateTime.now(),source);
        publisher.publishSelectedMediaBatch(command,List.of(new InboundClientMessageEvent.MediaItem("document","questionnaires/owned","file.pdf")));
        var captured=org.mockito.ArgumentCaptor.forClass(InboundClientMessageEvent.class);verify(outbox).enqueueInboundClientMessage(captured.capture(),eq("isolated.max"),same(routes));
        var event=captured.getValue();assertThat(event.messageType()).isEqualTo(MessageSourceLink.BATCH_TYPE);assertThat(event.sourceLink()).isEqualTo(source);
        assertThat(event.eventId()).isEqualTo(InboundClientMessageEvent.mediaBatchId(55,1001," raw-root ",Long.toString(Long.MAX_VALUE)));
        assertThat(mapper.readValue(mapper.writeValueAsBytes(event),InboundClientMessageEvent.class)).isEqualTo(event);
        assertThat(event.userId()).isEqualTo(1001L);assertThat(event.replyToProviderMessageId()).isNull();
    }
    @Test void oldWireAndConstructorDoNotInventSourceMetadata()throws Exception {
        var entry=new ConversationHistoryEntry(1L,"text","text",null,null,null,null);
        assertThat(mapper.writeValueAsString(entry)).doesNotContain("sourceLink");
        assertThat(mapper.readValue("{\"userId\":1,\"text\":\"old\",\"messageType\":\"text\"}",ConversationHistoryEntry.class).sourceLink()).isNull();
    }
    @ParameterizedTest @ValueSource(strings={"type","missing-mid","space-mid","long-mid","control-mid","zero-author","rounded-author","overflow-author","control-name","long-name"})
    void invalidContextCannotEnterDurableWire(String change) {
        assertThatThrownBy(()->new MessageSourceLink(change.equals("type")?"forwarded":"forward",
            switch(change){case "missing-mid"->null;case "space-mid"->" mid ";case "long-mid"->"a".repeat(513);case "control-mid"->"mid\u0000bad";default->"mid.original";},
            switch(change){case "zero-author"->"0";case "rounded-author"->"1.2";case "overflow-author"->"9223372036854775808";default->"9007199254740993";},
            change.equals("control-name")?"bad\u0000name":change.equals("long-name")?"a".repeat(256):"Author")).isInstanceOf(IllegalArgumentException.class);
    }
    @ParameterizedTest @ValueSource(strings={"telegram","vk","max"})
    void linkRequiresItsExactWireProtocol(String platform) {
        assertThatThrownBy(()->MessageSourceLink.mediaType(platform,"document",link)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->MessageSourceLink.mediaType(platform,"max_linked.v1:document",null)).isInstanceOf(IllegalArgumentException.class);
        if(platform.equals("max"))assertThat(MessageSourceLink.mediaType(platform,"max_linked.v1:document",link)).isEqualTo("document");
        else assertThatThrownBy(()->MessageSourceLink.mediaType(platform,"max_linked.v1:document",link)).isInstanceOf(IllegalArgumentException.class);
    }
}
