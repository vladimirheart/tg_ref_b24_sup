package com.example.supportbot.repository;

import com.example.supportbot.entity.ClientUnblockRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ClientUnblockRequestRepository extends JpaRepository<ClientUnblockRequest, Long> {
    Optional<ClientUnblockRequest> findFirstByUserIdAndStatusOrderByIdDesc(String userId, String status);

    long countByStatus(String status);

    List<ClientUnblockRequest> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
}
