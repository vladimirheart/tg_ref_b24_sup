package com.example.panel.repository;

import com.example.panel.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdentityOrderByCreatedAtDesc(String userIdentity);

    List<Notification> findByUserIdentityOrderByCreatedAtDescIdDesc(String userIdentity, Pageable pageable);

    @Query("""
            select n from Notification n
            where n.userIdentity = :userIdentity
              and (n.createdAt < :beforeCreatedAt
                   or (n.createdAt = :beforeCreatedAt and n.id < :beforeId))
            order by n.createdAt desc, n.id desc
            """)
    List<Notification> findPageBefore(
            @Param("userIdentity") String userIdentity,
            @Param("beforeCreatedAt") OffsetDateTime beforeCreatedAt,
            @Param("beforeId") Long beforeId,
            Pageable pageable
    );

    List<Notification> findByUserIdentityAndIsReadFalseOrderByCreatedAtDesc(String userIdentity);

    long countByUserIdentityAndIsReadFalse(String userIdentity);

    Optional<Notification> findByIdAndUserIdentity(Long id, String userIdentity);
}
