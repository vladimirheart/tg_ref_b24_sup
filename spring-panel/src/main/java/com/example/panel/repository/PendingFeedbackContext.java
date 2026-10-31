package com.example.panel.repository;

import com.example.panel.entity.PendingFeedbackRequest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/** Immutable projection of persisted provenance, admitted only for the current root generation. */
public interface PendingFeedbackContext {
    Long getRequestId();
    Long getUserId();
    Long getChannelId();
    String getTicketId();
    String getPublicId();
    Long getBusinessId();
    Boolean getGeneral();
    UUID getEpoch();
    Instant getExpiresAt();
    Instant getSentAt();

    default boolean matches(PendingFeedbackRequest request) {
        return request != null && getEpoch() != null
            && getUserId() != null && getChannelId() != null
            && Objects.equals(getRequestId(), request.getId())
            && Objects.equals(getUserId(), request.getUserId())
            && Objects.equals(getTicketId(), request.getTicketId())
            && request.getChannel() != null
            && Objects.equals(getChannelId(), request.getChannel().getId());
    }

    default boolean activeAt(OffsetDateTime now) {
        return getExpiresAt() != null && getExpiresAt().isAfter(now.toInstant());
    }
}
