package com.example.supportbot.service;

import com.example.supportbot.config.IntegrationRabbitProperties;
import com.example.supportbot.entity.Channel;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationTicketCreatedPublisherTest {

    @Test
    void preservesExplicitOwnerInEventAndLeavesLegacyCommandUnassigned() {
        IntegrationTransportOutboxService outbox = mock(IntegrationTransportOutboxService.class);
        IntegrationRabbitProperties properties = new IntegrationRabbitProperties();
        when(outbox.enqueueConversationTicketCreated(any(), any())).thenReturn(true);
        ConversationTicketCreatedPublisher publisher = new ConversationTicketCreatedPublisher(outbox, properties);
        Channel channel = new Channel();
        channel.setId(25L);
        channel.setPlatform("telegram");
        OffsetDateTime startedAt = OffsetDateTime.parse("2026-10-15T10:00:00Z");
        ConversationTicketCreationCommand scoped = new ConversationTicketCreationCommand(
            901L, "tg-901", "client", "Client", Map.of("problem", "Help"), List.of(), List.of(),
            channel, startedAt, "message:901:1", 51L);
        ConversationTicketCreationCommand legacy = new ConversationTicketCreationCommand(
            901L, "tg-901", "client", "Client", Map.of("problem", "Help"), List.of(), List.of(),
            channel, startedAt, "message:901:2");
        ConversationTicketCreationCommand general = new ConversationTicketCreationCommand(
            901L, "tg-901", "client", "Client", Map.of("problem", "Help"), List.of(), List.of(),
            channel, startedAt, "message:901:3", null, "GENERAL", "USER_SELECTION");

        assertThat(publisher.publish(scoped, "T-1", "Legacy", "", "", "", "Help")).isTrue();
        assertThat(publisher.publish(legacy, "T-2", "Legacy", "", "", "", "Help")).isTrue();
        assertThat(publisher.publish(general, "T-3", "", "", "", "", "Help")).isTrue();

        ArgumentCaptor<ConversationTicketCreatedEvent> events =
            ArgumentCaptor.forClass(ConversationTicketCreatedEvent.class);
        verify(outbox, times(3)).enqueueConversationTicketCreated(events.capture(), any());
        assertThat(events.getAllValues()).extracting(ConversationTicketCreatedEvent::businessId)
            .containsExactly(51L, null, null);
        assertThat(events.getAllValues()).extracting(ConversationTicketCreatedEvent::business)
            .containsExactly("Legacy", "Legacy", "");
        assertThat(events.getAllValues()).extracting(ConversationTicketCreatedEvent::scopeKind)
            .containsExactly(null, null, "GENERAL");
        assertThat(events.getAllValues()).extracting(ConversationTicketCreatedEvent::scopeSelectionSource)
            .containsExactly(null, null, "USER_SELECTION");
    }
}
