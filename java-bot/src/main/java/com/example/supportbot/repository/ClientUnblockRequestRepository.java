package com.example.supportbot.repository;

import com.example.supportbot.entity.ClientUnblockRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClientUnblockRequestRepository extends JpaRepository<ClientUnblockRequest, Long> {
    Optional<ClientUnblockRequest> findFirstByUserIdAndStatusOrderByIdDesc(String userId, String status);
}
