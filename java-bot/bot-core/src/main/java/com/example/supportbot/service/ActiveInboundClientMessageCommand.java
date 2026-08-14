package com.example.supportbot.service;

import com.example.supportbot.entity.Channel;
import java.time.OffsetDateTime;

public record ActiveInboundClientMessageCommand(Long userId,
                                                String userIdentity,
                                                String username,
                                                String clientName,
                                                Channel channel,
                                                String ticketId,
                                                String text,
                                                String messageType,
                                                String attachmentPath,
                                                String attachmentName,
                                                Long providerMessageId,
                                                Long replyToProviderMessageId,
                                                String forwardedFrom,
                                                OffsetDateTime occurredAt) {
}
