package com.example.panel.repository;

import com.example.panel.entity.PendingFeedbackRequest;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PendingFeedbackRequestRepository extends JpaRepository<PendingFeedbackRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT request FROM PendingFeedbackRequest request WHERE request.id = :id")
    Optional<PendingFeedbackRequest> findByIdForUpdate(@Param("id") Long id);

    List<PendingFeedbackRequest> findByTicketId(String ticketId);

    Optional<PendingFeedbackRequest> findFirstByUserIdAndChannel_IdAndExpiresAtAfterOrderByCreatedAtDesc(Long userId,
                                                                                                          Long channelId,
                                                                                                          OffsetDateTime expiresAt);

    Optional<PendingFeedbackRequest> findFirstByUserIdAndExpiresAtAfterOrderByCreatedAtDesc(Long userId,
                                                                                             OffsetDateTime expiresAt);

    List<PendingFeedbackRequest> findTop50BySentAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(OffsetDateTime now);
}
