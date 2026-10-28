package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.BusinessTicketCurrentAssignmentReadService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/business-scope/tickets/{publicId}/assignments/current")
@PreAuthorize("hasAuthority('PAGE_DIALOGS')")
public class BusinessTicketCurrentAssignmentReadController {
    private final BusinessTicketCurrentAssignmentReadService service;
    private final MultibusinessFeatureFlags flags;
    public BusinessTicketCurrentAssignmentReadController(BusinessTicketCurrentAssignmentReadService service,
                                                          MultibusinessFeatureFlags flags) {
        this.service = service;
        this.flags = flags;
    }
    @GetMapping
    public ResponseEntity<BusinessTicketCurrentAssignmentReadService.State> current(
            @PathVariable String publicId, Authentication authentication) {
        if (!flags.isFoundationEnabled()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        try {
            var result = service.current(publicId, authentication)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header("X-Content-Type-Options", "nosniff").body(result);
        } catch (BusinessTicketCurrentAssignmentReadService.ReadUnavailableException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Current assignment is unavailable", ex);
        }
    }
}
