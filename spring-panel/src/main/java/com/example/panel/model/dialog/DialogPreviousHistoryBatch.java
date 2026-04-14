package com.example.panel.model.dialog;

import java.util.List;

public record DialogPreviousHistoryBatch(String ticketId,
                                         String status,
                                         String createdAt,
                                         String problem,
                                         String channelName,
                                         String sourceKey,
                                         String sourceLabel,
                                         List<ChatMessageDto> messages) {
}
