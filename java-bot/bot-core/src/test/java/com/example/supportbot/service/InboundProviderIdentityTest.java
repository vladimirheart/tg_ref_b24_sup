package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.supportbot.entity.Channel;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InboundProviderIdentityTest {

    @Test
    void explicitProviderEventKeyProducesStableIds() {
        ConversationTicketCreationCommand command = command("message:1001:9001");

        assertThat(InboundProviderIdentity.deterministicId("ticket", command))
            .isEqualTo(InboundProviderIdentity.deterministicId("ticket", command));
        assertThat(InboundProviderIdentity.deterministicId("ticket", command))
            .isNotEqualTo(InboundProviderIdentity.deterministicId("ticket.created.initial_contact", command));
    }

    @Test
    void fallsBackToLastProviderMessageFromHistory() {
        Channel channel = channel();
        ConversationTicketCreationCommand command = new ConversationTicketCreationCommand(
            501L,
            "501",
            "tester",
            "Test User",
            Map.of("problem", "network"),
            List.of(),
            List.of(
                new ConversationHistoryEntry(501L, "first", "text", null, null, "8001", OffsetDateTime.parse("2026-09-21T10:00:00Z")),
                new ConversationHistoryEntry(501L, "second", "text", null, null, "8002", OffsetDateTime.parse("2026-09-21T10:01:00Z"))
            ),
            channel,
            OffsetDateTime.parse("2026-09-21T10:00:00Z")
        );

        assertThat(InboundProviderIdentity.resolveProviderEventKey(command)).isEqualTo("message:8002");
    }

    private ConversationTicketCreationCommand command(String providerEventKey) {
        return new ConversationTicketCreationCommand(
            501L,
            "501",
            "tester",
            "Test User",
            Map.of("problem", "network"),
            List.of(),
            List.of(),
            channel(),
            OffsetDateTime.parse("2026-09-21T10:00:00Z"),
            providerEventKey
        );
    }

    private Channel channel() {
        Channel channel = new Channel();
        channel.setId(3L);
        channel.setPlatform("telegram");
        return channel;
    }
}
