package com.example.supportbot.service;

import com.example.supportbot.entity.ClientUnblockRequest;
import com.example.supportbot.repository.ClientUnblockRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class MaintenanceTasks {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceTasks.class);

    private final ClientUnblockRequestRepository unblockRequestRepository;

    public MaintenanceTasks(ClientUnblockRequestRepository unblockRequestRepository) {
        this.unblockRequestRepository = unblockRequestRepository;
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void expireOldUnblockRequests() {
        OffsetDateTime threshold = OffsetDateTime.now().minusDays(30);
        List<ClientUnblockRequest> requests = unblockRequestRepository.findAll();
        int updated = 0;
        for (ClientUnblockRequest request : requests) {
            if (!"pending".equalsIgnoreCase(request.getStatus())) {
                continue;
            }
            OffsetDateTime createdAt = request.getCreatedAt();
            if (createdAt != null && createdAt.isBefore(threshold)) {
                request.setStatus("expired");
                request.setDecidedAt(OffsetDateTime.now());
                request.setDecisionComment("Auto-expired by scheduler");
                unblockRequestRepository.save(request);
                updated++;
            }
        }
        if (updated > 0) {
            log.info("Marked {} unblock requests as expired", updated);
        }
    }
}
