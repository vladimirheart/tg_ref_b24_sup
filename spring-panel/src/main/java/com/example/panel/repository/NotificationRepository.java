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

    String TICKET_VISIBLE_SCOPE = """
        ((:allowLegacy = TRUE AND n.ticket_scope_kind IS NULL)
         OR (n.ticket_scope_kind IS NULL AND n.url ~ '^/(incidents|settings)([/?#]|$)'
             AND EXISTS (SELECT 1 FROM users recipient WHERE lower(recipient.username)=n.user_identity
                 AND recipient.enabled=TRUE AND COALESCE(recipient.is_blocked,FALSE)=FALSE
                 AND 1=(SELECT COUNT(*) FROM users duplicate WHERE lower(duplicate.username)=n.user_identity)))
         OR (n.ticket_scope_kind IN ('BUSINESS', 'UNRESOLVED') AND EXISTS (
             SELECT 1 FROM tickets owner
              WHERE owner.user_id = n.ticket_user_id AND owner.ticket_id = n.ticket_id
                AND owner.ticket_public_id IS NOT DISTINCT FROM n.ticket_public_id
                AND owner.is_general = FALSE
                AND ((n.ticket_scope_kind = 'BUSINESS' AND owner.business_id = n.ticket_business_id AND
        """ + NotificationBusinessAccessSql.CAN_READ_OWNER_BUSINESS + """
                     )
                  OR (:allowLegacy = TRUE AND n.ticket_scope_kind = 'UNRESOLVED' AND owner.business_id IS NULL)))))
        """;

    String VISIBLE_SCOPE = "(n.shared_work_kind IS NULL AND COALESCE(n.url,'') !~ '^/(tasks|projects)([/?#]|$)' AND ("+TICKET_VISIBLE_SCOPE+"))";
    String SHARED_VISIBLE_SCOPE = "("+VISIBLE_SCOPE+"""
        OR (n.shared_recipient_user_id=:sharedUserId AND
             ((n.shared_work_kind='TASK' AND n.shared_work_id IN (:taskIds))
               OR (n.shared_work_kind='PROJECT' AND n.shared_work_id IN (:projectIds)))))
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
    @Query(value="SELECT n.* FROM notifications n WHERE n.user_identity=:userIdentity AND "+SHARED_VISIBLE_SCOPE+" ORDER BY n.created_at DESC,n.id DESC",nativeQuery=true)
    List<Notification> findSharedVisibleForUser(@Param("userIdentity") String userIdentity,@Param("allowLegacy") boolean allowLegacy,@Param("sharedUserId") long sharedUserId,@Param("taskIds") List<Long> taskIds,@Param("projectIds") List<Long> projectIds);

    @Query(value="SELECT n.* FROM notifications n WHERE n.user_identity=:userIdentity AND "+SHARED_VISIBLE_SCOPE+" ORDER BY n.created_at DESC,n.id DESC",nativeQuery=true)
    List<Notification> findSharedVisiblePage(@Param("userIdentity") String userIdentity,@Param("allowLegacy") boolean allowLegacy,@Param("sharedUserId") long sharedUserId,@Param("taskIds") List<Long> taskIds,@Param("projectIds") List<Long> projectIds,Pageable pageable);

    @Query(value="SELECT n.* FROM notifications n WHERE n.user_identity=:userIdentity AND "+SHARED_VISIBLE_SCOPE+" AND (n.created_at<:beforeCreatedAt OR (n.created_at=:beforeCreatedAt AND n.id<:beforeId)) ORDER BY n.created_at DESC,n.id DESC",nativeQuery=true)
    List<Notification> findSharedPageBefore(@Param("userIdentity") String userIdentity,@Param("allowLegacy") boolean allowLegacy,@Param("sharedUserId") long sharedUserId,@Param("taskIds") List<Long> taskIds,@Param("projectIds") List<Long> projectIds,@Param("beforeCreatedAt") OffsetDateTime beforeCreatedAt,@Param("beforeId") Long beforeId,Pageable pageable);

    @Query(value="SELECT COUNT(*) FROM notifications n WHERE n.user_identity=:userIdentity AND n.is_read=FALSE AND "+SHARED_VISIBLE_SCOPE+"",nativeQuery=true)
    long countSharedVisibleUnread(@Param("userIdentity") String userIdentity,@Param("allowLegacy") boolean allowLegacy,@Param("sharedUserId") long sharedUserId,@Param("taskIds") List<Long> taskIds,@Param("projectIds") List<Long> projectIds);

    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query(value="UPDATE notifications n SET is_read=TRUE WHERE n.user_identity=:userIdentity AND n.id=:id AND n.is_read IS DISTINCT FROM TRUE AND "+SHARED_VISIBLE_SCOPE+"",nativeQuery=true)
    int markSharedVisibleAsRead(@Param("userIdentity") String userIdentity,@Param("allowLegacy") boolean allowLegacy,@Param("sharedUserId") long sharedUserId,@Param("taskIds") List<Long> taskIds,@Param("projectIds") List<Long> projectIds,@Param("id") Long id);

    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query(value="UPDATE notifications n SET is_read=TRUE WHERE n.user_identity=:userIdentity AND n.is_read=FALSE AND "+SHARED_VISIBLE_SCOPE+"",nativeQuery=true)
    int markAllSharedVisibleAsRead(@Param("userIdentity") String userIdentity,@Param("allowLegacy") boolean allowLegacy,@Param("sharedUserId") long sharedUserId,@Param("taskIds") List<Long> taskIds,@Param("projectIds") List<Long> projectIds);

}
