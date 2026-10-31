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

    String CURRENT_CONTEXT_SQL = """
        SELECT request.id AS "requestId", request.user_id AS "userId", request.channel_id AS "channelId",
               request.ticket_id AS "ticketId", request.ticket_public_id AS "publicId",
               request.ticket_business_id AS "businessId", request.ticket_is_general AS "general",
               request.ticket_context_epoch AS "epoch", request.expires_at AS "expiresAt", request.sent_at AS "sentAt"
          FROM pending_feedback_requests request
          JOIN tickets owner ON owner.ticket_id = request.ticket_id AND owner.user_id = request.user_id
           AND owner.channel_id IS NOT DISTINCT FROM request.channel_id
           AND owner.ticket_public_id IS NOT DISTINCT FROM request.ticket_public_id
           AND owner.business_id IS NOT DISTINCT FROM request.ticket_business_id
           AND owner.is_general IS NOT DISTINCT FROM request.ticket_is_general
           AND owner.feedback_context_epoch = request.ticket_context_epoch
         WHERE request.id = :id AND request.ticket_context_epoch IS NOT NULL
           AND 1 = (SELECT COUNT(*) FROM tickets candidate WHERE candidate.ticket_id = request.ticket_id)
        """;

    @Query(value = CURRENT_CONTEXT_SQL, nativeQuery = true)
    Optional<PendingFeedbackContext> findCurrentFeedbackContext(@Param("id") Long id);

    // The caller first locks the pending row; this ordering is shared by writer and dispatcher.
    @Query(value = CURRENT_CONTEXT_SQL + " FOR UPDATE OF owner", nativeQuery = true)
    Optional<PendingFeedbackContext> lockCurrentFeedbackContext(@Param("id") Long id);

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
