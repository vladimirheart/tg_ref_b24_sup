package com.example.panel.repository;

import com.example.panel.entity.PendingFeedbackRequest;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PendingFeedbackRequestRepository extends JpaRepository<PendingFeedbackRequest, Long> {

    List<PendingFeedbackRequest> findByTicketId(String ticketId);

    Optional<PendingFeedbackRequest> findFirstByUserIdAndChannel_IdAndExpiresAtAfterOrderByCreatedAtDesc(Long userId,
                                                                                                          Long channelId,
                                                                                                          OffsetDateTime expiresAt);

    Optional<PendingFeedbackRequest> findFirstByUserIdAndExpiresAtAfterOrderByCreatedAtDesc(Long userId,
                                                                                             OffsetDateTime expiresAt);

    List<PendingFeedbackRequest> findTop50BySentAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(OffsetDateTime now);
}
