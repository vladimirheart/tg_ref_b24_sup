package com.example.panel.repository;

import com.example.panel.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface ChannelRepository extends JpaRepository<Channel, Long> {

    // Scalar projection and an independent short transaction: never return an
    // already managed/cached Channel or reuse a caller's repeatable-read snapshot.
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    @Query(value = """
            SELECT id, is_active AS "active", platform, token,
                   credential_id AS "credentialId", support_chat_id AS "supportChatId",
                   delivery_settings AS "deliverySettings", platform_config AS "platformConfig"
              FROM channels WHERE id = :id
            """, nativeQuery = true)
    Optional<TransportState> findTransportStateById(@Param("id") Long id);

    interface TransportState {
        Long getId();
        Boolean getActive();
        String getPlatform();
        String getToken();
        Long getCredentialId();
        String getSupportChatId();
        String getDeliverySettings();
        String getPlatformConfig();
    }

    Optional<Channel> findByToken(String token);

    Optional<Channel> findByPublicId(String publicId);

    Optional<Channel> findByPublicIdIgnoreCase(String publicId);
}
