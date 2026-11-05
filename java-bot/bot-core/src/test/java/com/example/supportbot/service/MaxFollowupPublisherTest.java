package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.supportbot.config.IntegrationRabbitProperties;
import com.example.supportbot.entity.Channel;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MaxFollowupPublisherTest {
    @ParameterizedTest @ValueSource(longs={33,Long.MAX_VALUE})
    void maxBatchUsesOneFrozenEnvelopeAndMaxRoute(long provider) {
        var outbox=mock(IntegrationTransportOutboxService.class);var properties=new IntegrationRabbitProperties();var publisher=new InboundClientMessagePublisher(outbox,properties);
        var channel=new Channel();channel.setId(55L);channel.setPlatform("max");var time=OffsetDateTime.parse("2026-11-05T08:00:00Z");
        var command=new ActiveInboundClientMessageCommand(901L,"identity","client","Client",channel," T:? root ","Caption","selected_media_batch.v1",null,null,provider,null,null,time);
        var items=List.of(new InboundClientMessageEvent.MediaItem("document","key/1/a.pdf","a.pdf"),new InboundClientMessageEvent.MediaItem("audio","key/2/a.ogg","a.ogg"));
        publisher.publishSelectedMediaBatch(command,items);publisher.publishSelectedMediaBatch(command,items);
        var capture=org.mockito.ArgumentCaptor.forClass(InboundClientMessageEvent.class);verify(outbox,times(2)).enqueueInboundClientMessage(capture.capture(),eq(properties.routingKeyForPlatform("max")),same(properties));
        var event=capture.getValue();assertThat(capture.getAllValues().get(0)).isEqualTo(event);assertThat(event.platform()).isEqualTo("max");assertThat(event.ticketId()).isEqualTo(" T:? root ");assertThat(event.occurredAt()).isEqualTo(time);assertThat(event.mediaBatch()).containsExactlyElementsOf(items);assertThat(event.eventId()).isEqualTo(InboundClientMessageEvent.mediaBatchId(55,901," T:? root ",Long.toString(provider)));assertThat(event.eventKind()).isEqualTo(InboundClientMessageEvent.MEDIA_BATCH_KIND);
    }
    @ParameterizedTest @ValueSource(strings={"telegram","unknown","zero-provider","forward","reply","missing-time","empty","many"})
    void unstableOrUnsupportedMaxEnvelopeNeverFallsBack(String kind) {
        var outbox=mock(IntegrationTransportOutboxService.class);var publisher=new InboundClientMessagePublisher(outbox,new IntegrationRabbitProperties());var channel=new Channel();channel.setId(55L);channel.setPlatform(Set.of("telegram","unknown").contains(kind)?kind:"max");
        var command=new ActiveInboundClientMessageCommand(901L,"i","u","C",channel,"T","Caption","selected_media_batch.v1",null,null,kind.equals("zero-provider")?0L:33L,kind.equals("reply")?9L:null,kind.equals("forward")?"forward":null,kind.equals("missing-time")?null:OffsetDateTime.now());
        var item=new InboundClientMessageEvent.MediaItem("document","key","name");var items=kind.equals("empty")?List.<InboundClientMessageEvent.MediaItem>of():Collections.nCopies(kind.equals("many")?11:1,item);
        assertThatThrownBy(()->publisher.publishSelectedMediaBatch(command,items)).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(outbox);
    }
}
