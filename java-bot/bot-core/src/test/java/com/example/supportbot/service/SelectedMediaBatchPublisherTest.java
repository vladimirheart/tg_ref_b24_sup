package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.example.supportbot.config.IntegrationRabbitProperties;
import com.example.supportbot.entity.Channel;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;

class SelectedMediaBatchPublisherTest {
    @Test void oneProviderBatchUsesOneStableEventAndCannotAliasTheSingleMessageOrAnotherOwner() {
        var outbox=mock(IntegrationTransportOutboxService.class);var properties=new IntegrationRabbitProperties();
        var publisher=new InboundClientMessagePublisher(outbox,properties);var channel=new Channel();channel.setId(55L);channel.setPlatform("vk");
        var time=OffsetDateTime.parse("2026-10-21T10:00:00Z");
        var command=new ActiveInboundClientMessageCommand(901L,"identity","client","Client",channel,"T:? root","Caption","selected_media_batch.v1",null,null,88L,null,null,time);
        var items=List.of(new InboundClientMessageEvent.MediaItem("photo","key/1/a.jpg","a.jpg"),
            new InboundClientMessageEvent.MediaItem("document","key/2/a.bin","a.bin"));
        publisher.publishSelectedMediaBatch(command,items);publisher.publishSelectedMediaBatch(command,items);
        var capture=org.mockito.ArgumentCaptor.forClass(InboundClientMessageEvent.class);
        verify(outbox,times(2)).enqueueInboundClientMessage(capture.capture(),eq(properties.routingKeyForPlatform("vk")),same(properties));
        var first=capture.getAllValues().get(0);assertThat(first).isEqualTo(capture.getValue());
        assertThat(first.mediaBatch()).containsExactlyElementsOf(items);assertThat(first.eventKind()).isEqualTo(InboundClientMessageEvent.MEDIA_BATCH_KIND);
        assertThat(first.providerMessageId()).isEqualTo("88");assertThat(first.occurredAt()).isEqualTo(time);
        assertThat(first.eventId()).isNotEqualTo(InboundClientMessageEvent.mediaBatchId(55,902,"T:? root","88"))
            .isNotEqualTo(UUID.nameUUIDFromBytes("client_message.active_ticket:55:T:? root:88".getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());
    }
    @Test void unstableOrLegacyBatchNeverFallsBackToTheSingleMessageWriter() {
        var outbox=mock(IntegrationTransportOutboxService.class);var publisher=new InboundClientMessagePublisher(outbox,new IntegrationRabbitProperties());
        assertThatThrownBy(()->publisher.publishSelectedMediaBatch(null,List.of())).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(outbox);
    }
}
