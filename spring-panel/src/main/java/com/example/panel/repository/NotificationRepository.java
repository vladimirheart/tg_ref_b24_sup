package com.example.panel.repository;

import com.example.panel.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    String VISIBLE_SCOPE = """
        ((:allowLegacy = TRUE AND n.ticket_scope_kind IS NULL)
         OR (n.ticket_scope_kind IN ('BUSINESS', 'UNRESOLVED') AND EXISTS (
             SELECT 1 FROM tickets owner
              WHERE owner.user_id = n.ticket_user_id AND owner.ticket_id = n.ticket_id
                AND owner.ticket_public_id IS NOT DISTINCT FROM n.ticket_public_id
                AND owner.is_general = FALSE
                AND ((n.ticket_scope_kind = 'BUSINESS' AND owner.business_id = n.ticket_business_id)
                  OR (:allowLegacy = TRUE AND n.ticket_scope_kind = 'UNRESOLVED' AND owner.business_id IS NULL)))))
        """;

    @Query(value = "SELECT n.* FROM notifications n WHERE n.user_identity = :userIdentity AND "
        + VISIBLE_SCOPE + " ORDER BY n.created_at DESC, n.id DESC", nativeQuery = true)
    List<Notification> findVisibleForUser(@Param("userIdentity") String userIdentity,
        @Param("allowLegacy") boolean allowLegacy);

    @Query(value = "SELECT n.* FROM notifications n WHERE n.user_identity = :userIdentity AND "
        + VISIBLE_SCOPE + " ORDER BY n.created_at DESC, n.id DESC", nativeQuery = true)
    List<Notification> findVisiblePage(@Param("userIdentity") String userIdentity,
        @Param("allowLegacy") boolean allowLegacy, Pageable pageable);

    @Query(value = "SELECT n.* FROM notifications n WHERE n.user_identity = :userIdentity AND "
        + VISIBLE_SCOPE + """
          AND (n.created_at < :beforeCreatedAt OR (n.created_at = :beforeCreatedAt AND n.id < :beforeId))
          ORDER BY n.created_at DESC, n.id DESC
        """, nativeQuery = true)
    List<Notification> findPageBefore(
            @Param("userIdentity") String userIdentity,
            @Param("allowLegacy") boolean allowLegacy,
            @Param("beforeCreatedAt") OffsetDateTime beforeCreatedAt,
            @Param("beforeId") Long beforeId,
            Pageable pageable
    );

    @Query(value = "SELECT COUNT(*) FROM notifications n WHERE n.user_identity = :userIdentity "
        + "AND n.is_read = FALSE AND " + VISIBLE_SCOPE, nativeQuery = true)
    long countVisibleUnread(@Param("userIdentity") String userIdentity, @Param("allowLegacy") boolean allowLegacy);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE notifications n SET is_read = TRUE WHERE n.user_identity = :userIdentity "
        + "AND n.id = :id AND n.is_read IS DISTINCT FROM TRUE AND " + VISIBLE_SCOPE, nativeQuery = true)
    int markVisibleAsRead(@Param("id") Long id, @Param("userIdentity") String userIdentity,
        @Param("allowLegacy") boolean allowLegacy);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE notifications n SET is_read = TRUE WHERE n.user_identity = :userIdentity "
        + "AND n.is_read = FALSE AND " + VISIBLE_SCOPE, nativeQuery = true)
    int markAllVisibleAsRead(@Param("userIdentity") String userIdentity, @Param("allowLegacy") boolean allowLegacy);
}
