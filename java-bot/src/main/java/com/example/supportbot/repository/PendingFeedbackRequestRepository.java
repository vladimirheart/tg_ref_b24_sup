package com.example.supportbot.repository;

import com.example.supportbot.entity.PendingFeedbackRequest;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingFeedbackRequestRepository extends JpaRepository<PendingFeedbackRequest, Long> {
    Optional<PendingFeedbackRequest> findFirstByUserIdAndChannel_IdAndExpiresAtAfterOrderByCreatedAtDesc(
            Long userId, Long channelId, OffsetDateTime now);

    Optional<PendingFeedbackRequest> findFirstByTicketIdOrderByCreatedAtDesc(String ticketId);
}
