package com.example.supportbot.repository;

import com.example.supportbot.entity.ClientAvatarHistory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientAvatarHistoryRepository extends JpaRepository<ClientAvatarHistory, Long> {

    Optional<ClientAvatarHistory> findByUserIdAndFingerprint(Long userId, String fingerprint);
}
