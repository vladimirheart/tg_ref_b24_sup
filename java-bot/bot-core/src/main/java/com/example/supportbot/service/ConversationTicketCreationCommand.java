package com.example.supportbot.service;

import com.example.supportbot.entity.Channel;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record ConversationTicketCreationCommand(Long userId,
                                                String userIdentity,
                                                String username,
                                                String clientName,
                                                Map<String, String> answers,
                                                List<TicketService.TicketAttributeInput> attributes,
                                                List<ConversationHistoryEntry> historyEntries,
                                                Channel channel,
                                                OffsetDateTime startedAt) {
}
