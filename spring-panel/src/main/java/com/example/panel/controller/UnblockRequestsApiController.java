package com.example.panel.controller;

import com.example.panel.service.UnblockRequestService;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @PostMapping("/decision")
    public Map<String, Object> decide(@RequestParam("request_id") long requestId,
                                      @RequestParam("decision") String decision,
                                      @RequestParam(value = "comment", required = false) String comment,
                                      Authentication authentication) {
        boolean approve = "approve".equalsIgnoreCase(decision) || "approved".equalsIgnoreCase(decision);
        boolean reject = "reject".equalsIgnoreCase(decision) || "rejected".equalsIgnoreCase(decision);
        if (!approve && !reject) {
            return Map.of("ok", false, "error", "Неизвестное решение");
        }
        var outcome = unblockRequestService.decideRequest(
                requestId,
                approve,
                comment,
                authentication != null ? authentication.getName() : "system"
        );
        if (!outcome.success()) {
            return Map.of("ok", false, "error", outcome.message());
        }
        return Map.of("ok", true, "message", outcome.message());
    }
}
