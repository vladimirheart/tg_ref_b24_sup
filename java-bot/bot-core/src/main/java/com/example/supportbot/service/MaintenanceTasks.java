package com.example.supportbot.service;

import com.example.supportbot.entity.ClientUnblockRequest;
import com.example.supportbot.repository.ClientUnblockRequestRepository;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Component
public class MaintenanceTasks {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceTasks.class);
    private static final Duration DEFAULT_AUTO_CLOSE_DURATION = Duration.ofHours(24);

    private final ClientUnblockRequestRepository unblockRequestRepository;
    private final TicketService ticketService;
    private final SharedConfigService sharedConfigService;

    public MaintenanceTasks(ClientUnblockRequestRepository unblockRequestRepository,
                           TicketService ticketService,
                           SharedConfigService sharedConfigService) {
        this.unblockRequestRepository = unblockRequestRepository;
        this.ticketService = ticketService;
        this.sharedConfigService = sharedConfigService;
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

    @Scheduled(cron = "0 */10 * * * *")
    @Transactional
    public void autoCloseInactiveTickets() {
        Duration inactivityLimit = resolveAutoCloseDuration();
        if (inactivityLimit == null) {
            return;
        }
        int closed = ticketService.closeInactiveTickets(inactivityLimit);
        if (closed > 0) {
            log.info("Auto-closed {} inactive tickets after {}", closed, inactivityLimit);
        }
    }

    Duration resolveAutoCloseDuration() {
        Map<String, Object> settings = sharedConfigService.loadSettings();
        int hours = parsePositiveInteger(settings.get("auto_close_hours"));
        if (hours == 0) {
            log.debug("Auto-close is disabled by auto_close_hours=0");
            return null;
        }
        if (hours > 0) {
            return Duration.ofHours(hours);
        }
        return DEFAULT_AUTO_CLOSE_DURATION;
    }

    private int parsePositiveInteger(Object rawValue) {
        if (rawValue == null) {
            return -1;
        }
        if (rawValue instanceof Number number) {
            return number.intValue();
        }
        String normalized = String.valueOf(rawValue).trim();
        if (normalized.isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}
