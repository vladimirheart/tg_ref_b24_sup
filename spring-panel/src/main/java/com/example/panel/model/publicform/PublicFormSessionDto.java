package com.example.panel.model.publicform;

import java.time.OffsetDateTime;

public record PublicFormSessionDto(String token,
                                   String ticketId,
                                   Long channelId,
                                   String channelPublicId,
                                   String clientName,
                                   String clientContact,
                                   String username,
                                   OffsetDateTime createdAt) {
}
