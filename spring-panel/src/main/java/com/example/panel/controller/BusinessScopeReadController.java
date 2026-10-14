package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.BusinessAccessService;
import com.example.panel.service.BusinessScopedTicketReadService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/business-scope")
@PreAuthorize("hasAuthority('PAGE_DIALOGS')")
public class BusinessScopeReadController {

    private final BusinessScopedTicketReadService readService;
    private final MultibusinessFeatureFlags flags;

    public BusinessScopeReadController(BusinessScopedTicketReadService readService,
                                       MultibusinessFeatureFlags flags) {
        this.readService = readService;
        this.flags = flags;
    }

    @GetMapping("/businesses")
    public List<BusinessScopedTicketReadService.BusinessOption> businesses(Authentication authentication) {
        requireFoundation();
        return readService.availableBusinesses(readService.requireAuthenticatedUserId(username(authentication)));
    }

    @GetMapping("/tickets")
    public List<BusinessScopedTicketReadService.TicketRow> tickets(
            Authentication authentication,
            @RequestParam("business_id") String businessId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        requireFoundation();
        if (limit < 1 || limit > 100 || offset < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page bounds");
        }
        Long userId = readService.requireAuthenticatedUserId(username(authentication));
        return readService.listTickets(userId, requestedContext(businessId), limit, offset);
    }

    @GetMapping("/tickets/{publicId}")
    public BusinessScopedTicketReadService.TicketRow ticket(@PathVariable String publicId,
                                                            Authentication authentication) {
        requireFoundation();
        Long userId = readService.requireAuthenticatedUserId(username(authentication));
        return readService.findByPublicId(userId, publicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/tickets/{publicId}/history")
    public BusinessScopedTicketReadService.TicketHistory history(@PathVariable String publicId,
                                                                 Authentication authentication) {
        requireFoundation();
        Long userId = readService.requireAuthenticatedUserId(username(authentication));
        return readService.findHistoryByPublicId(userId, publicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private void requireFoundation() {
        if (!flags.isFoundationEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private String username(Authentication authentication) {
        return authentication != null ? authentication.getName() : null;
    }

    private BusinessAccessService.RequestedBusinessContext requestedContext(String businessId) {
        if ("all".equals(businessId)) {
            return BusinessAccessService.AllAccessibleBusinesses.INSTANCE;
        }
        if (businessId == null || !businessId.matches("[1-9][0-9]*")) {
            throw new AccessDeniedException("Business context is not accessible");
        }
        try {
            return new BusinessAccessService.OneBusiness(Long.parseLong(businessId));
        } catch (NumberFormatException ex) {
            throw new AccessDeniedException("Business context is not accessible", ex);
        }
    }
}
