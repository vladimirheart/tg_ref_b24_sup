package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.BusinessScopedTicketReadService;
import com.example.panel.service.ManualBusinessTicketCreationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/business-scope/tickets/manual")
@PreAuthorize("hasAuthority('PAGE_DIALOGS')")
public class ManualBusinessTicketController {

    private static final Logger log = LoggerFactory.getLogger(ManualBusinessTicketController.class);
    private final ManualBusinessTicketCreationService service;
    private final BusinessScopedTicketReadService readService;
    private final MultibusinessFeatureFlags flags;

    public ManualBusinessTicketController(ManualBusinessTicketCreationService service,
                                          BusinessScopedTicketReadService readService,
                                          MultibusinessFeatureFlags flags) {
        this.service = service;
        this.readService = readService;
        this.flags = flags;
    }

    @PostMapping
    public ResponseEntity<ManualBusinessTicketCreationService.CreatedTicket> create(
            @RequestBody(required = false) CreateRequest request, Authentication authentication) {
        if (!flags.isFoundationEnabled() || !flags.isManualTicketCreation()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (request == null || request.businessId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Business is required");
        }
        long actorUserId = readService.requireAuthenticatedUserId(
                authentication == null ? null : authentication.getName());
        try {
            var created = service.create(actorUserId, request.businessId(),
                    request.clientName(), request.problem(), request.businessLocationId());
            log.info("Manual business ticket created: actor={}, businessId={}, publicId={}",
                    actorUserId, created.businessId(), created.ticketPublicId());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(created);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    public record CreateRequest(Long businessId, String clientName, String problem,
                                Long businessLocationId) {
    }
}
