package com.example.supportbot.service;

import com.example.supportbot.entity.ClientUnblockRequest;
import com.example.supportbot.repository.ClientUnblockRequestRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UnblockRequestService {

    private static final String STATUS_PENDING = "pending";

    private final ClientUnblockRequestRepository unblockRequestRepository;

    public UnblockRequestService(ClientUnblockRequestRepository unblockRequestRepository) {
        this.unblockRequestRepository = unblockRequestRepository;
    }

    @Transactional(readOnly = true)
    public long countPending() {
        return unblockRequestRepository.countByStatus(STATUS_PENDING);
    }

    @Transactional(readOnly = true)
    public List<ClientUnblockRequest> findRecentPending(int limit) {
        int safeLimit = Math.max(1, limit);
        return unblockRequestRepository.findByStatusOrderByCreatedAtDesc(
                STATUS_PENDING,
                PageRequest.of(0, safeLimit)
        );
    }
}
