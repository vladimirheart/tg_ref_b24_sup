package com.example.panel.service;

import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class SidebarStatusWatcher {

    private final UnblockRequestService unblockRequestService;
    private final UiEventStreamService uiEventStreamService;
    private final AtomicLong lastPendingUnblockCount = new AtomicLong(-1L);

    public SidebarStatusWatcher(UnblockRequestService unblockRequestService,
                                UiEventStreamService uiEventStreamService) {
        this.unblockRequestService = unblockRequestService;
        this.uiEventStreamService = uiEventStreamService;
    }

    @PostConstruct
    void initialize() {
        lastPendingUnblockCount.set(safePendingUnblockCount());
    }

    @Scheduled(fixedDelayString = "${panel.sidebar.unblock-watch-interval-ms:10000}")
    void watchPendingUnblockRequests() {
        long current = safePendingUnblockCount();
        long previous = lastPendingUnblockCount.getAndSet(current);
        if (previous != current) {
            uiEventStreamService.publishSidebarUnblockChanged("pending_unblock_requests_changed");
        }
    }

    private long safePendingUnblockCount() {
        try {
            return unblockRequestService.countPendingRequests();
        } catch (RuntimeException ex) {
            return lastPendingUnblockCount.get();
        }
    }
}
