package com.example.panel.service.integration;

import java.time.OffsetDateTime;

public record InboundClientMessageEvent(String eventId,
                                        String eventKind,
                                        String platform,
                                        Long channelId,
                                        String ticketId,
                                        Long userId,
                                        String userIdentity,
                                        String username,
                                        String clientName,
                                        String text,
                                        String messageType,
                                        String attachmentPath,
                                        String attachmentName,
                                        String providerMessageId,
                                        String replyToProviderMessageId,
                                        String forwardedFrom,
                                        OffsetDateTime occurredAt) {
}
