package com.example.panel.repository;

import com.example.panel.entity.ClientUnblockRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClientUnblockRequestRepository extends JpaRepository<ClientUnblockRequest, Long> {

    List<ClientUnblockRequest> findByUserId(String userId);
}
