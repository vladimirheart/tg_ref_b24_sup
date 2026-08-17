package com.example.supportbot.service;

import com.example.supportbot.config.BotIntegrationTransportMode;
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
    private final BotIntegrationTransportMode integrationTransportMode;
    private final PanelBlacklistClient panelBlacklistClient;

    public UnblockRequestService(ClientUnblockRequestRepository unblockRequestRepository,
                                 BotIntegrationTransportMode integrationTransportMode,
                                 PanelBlacklistClient panelBlacklistClient) {
        this.unblockRequestRepository = unblockRequestRepository;
        this.integrationTransportMode = integrationTransportMode;
        this.panelBlacklistClient = panelBlacklistClient;
    }

    @Transactional(readOnly = true)
    public long countPending() {
        if (integrationTransportMode.isRabbitMqMode() && panelBlacklistClient.isEnabled()) {
            return panelBlacklistClient.pendingSummary(0)
                    .map(PanelBlacklistClient.PendingUnblockSummary::pendingCount)
                    .orElse(0L);
        }
        return unblockRequestRepository.countByStatus(STATUS_PENDING);
    }

    @Transactional(readOnly = true)
    public List<ClientUnblockRequest> findRecentPending(int limit) {
        if (integrationTransportMode.isRabbitMqMode() && panelBlacklistClient.isEnabled()) {
            int safeLimit = Math.max(1, limit);
            return panelBlacklistClient.pendingSummary(safeLimit)
                    .map(PanelBlacklistClient.PendingUnblockSummary::recentRequests)
                    .orElse(List.of());
        }
        int safeLimit = Math.max(1, limit);
        return unblockRequestRepository.findByStatusOrderByCreatedAtDesc(
                STATUS_PENDING,
                PageRequest.of(0, safeLimit)
        );
    }
}
