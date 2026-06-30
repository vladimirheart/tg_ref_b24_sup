package com.example.panel.controller;

import com.example.panel.service.AnalyticsMacroGovernancePolicyService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analytics")
public class AnalyticsMacroGovernanceController {

    private final AnalyticsMacroGovernancePolicyService analyticsMacroGovernancePolicyService;

    public AnalyticsMacroGovernanceController(AnalyticsMacroGovernancePolicyService analyticsMacroGovernancePolicyService) {
        this.analyticsMacroGovernancePolicyService = analyticsMacroGovernancePolicyService;
    }

    @PostMapping(value = "/macro-governance/review", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
    public ResponseEntity<?> updateMacroGovernanceReview(
            @RequestBody(required = false) AnalyticsControllerSupport.MacroGovernanceReviewRequest request,
            Authentication authentication) {
        return analyticsMacroGovernancePolicyService.updateMacroGovernanceReview(
                authentication,
                request != null ? request.reviewedBy() : null,
                request != null ? request.reviewedAtUtc() : null,
                request != null ? request.reviewNote() : null,
                request != null ? request.cleanupTicketId() : null,
                request != null ? request.decision() : null
        );
    }

    @PostMapping(value = "/macro-governance/external-catalog-policy", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
    public ResponseEntity<?> updateMacroExternalCatalogPolicy(
            @RequestBody(required = false) AnalyticsControllerSupport.MacroExternalCatalogPolicyRequest request,
            Authentication authentication) {
        return analyticsMacroGovernancePolicyService.updateMacroExternalCatalogPolicy(
                authentication,
                request != null ? request.verifiedBy() : null,
                request != null ? request.verifiedAtUtc() : null,
                request != null ? request.expectedVersion() : null,
                request != null ? request.observedVersion() : null,
                request != null ? request.reviewNote() : null,
                request != null ? request.decision() : null,
                request != null ? request.reviewTtlHours() : null
        );
    }

    @PostMapping(value = "/macro-governance/deprecation-policy", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
    public ResponseEntity<?> updateMacroDeprecationPolicy(
            @RequestBody(required = false) AnalyticsControllerSupport.MacroDeprecationPolicyRequest request,
            Authentication authentication) {
        return analyticsMacroGovernancePolicyService.updateMacroDeprecationPolicy(
                authentication,
                request != null ? request.reviewedBy() : null,
                request != null ? request.reviewedAtUtc() : null,
                request != null ? request.deprecationTicketId() : null,
                request != null ? request.reviewNote() : null,
                request != null ? request.decision() : null,
                request != null ? request.reviewTtlHours() : null
        );
    }
}
