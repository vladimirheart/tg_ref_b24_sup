package com.example.panel.controller;

import com.example.panel.service.UnblockRequestService;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/unblock-requests")
@PreAuthorize("hasAuthority('PAGE_CLIENTS')")
public class UnblockRequestsApiController {

    private final UnblockRequestService unblockRequestService;

    public UnblockRequestsApiController(UnblockRequestService unblockRequestService) {
        this.unblockRequestService = unblockRequestService;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        return Map.of("count", unblockRequestService.countPendingRequests());
    }
}
