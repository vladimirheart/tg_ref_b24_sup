package com.example.panel.controller;

import com.example.panel.service.UnresolvedTicketBusinessSelectionService;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API for an operator's explicit business selection on an unresolved dialog. */
@RestController
@RequestMapping("/api/unresolved-scope")
@PreAuthorize("hasAuthority('PAGE_DIALOGS')")
public class UnresolvedTicketBusinessSelectionController {

    private final UnresolvedTicketBusinessSelectionService selectionService;

    public UnresolvedTicketBusinessSelectionController(UnresolvedTicketBusinessSelectionService selectionService) {
        this.selectionService = selectionService;
    }

    @GetMapping("/businesses")
    public ResponseEntity<List<UnresolvedTicketBusinessSelectionService.BusinessOption>> businesses(
            Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(selectionService.availableBusinesses(authentication));
    }

    @PostMapping("/tickets/{publicId}/business")
    public ResponseEntity<Map<String, Object>> select(@PathVariable String publicId,
                                                       @RequestBody(required = false) SelectionRequest request,
                                                       Authentication authentication) {
        if (request == null || request.businessId() == null || request.businessId() <= 0L) {
            return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                    .body(Map.of("success", false, "error", "Укажите доступный бизнес"));
        }
        return selectionService.select(publicId, request.businessId(), authentication)
                .<ResponseEntity<Map<String, Object>>>map(selection -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore())
                        .body(Map.of(
                                "success", true,
                                "business", selection.businessName(),
                                "managementUrl", "/business-dialog-assignment/" + selection.ticketPublicId())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT).cacheControl(CacheControl.noStore())
                        .body(Map.of(
                                "success", false,
                                "code", "UNRESOLVED_SCOPE_REQUIRED",
                                "error", "Бизнес уже выбран, обращение не найдено или права изменились")));
    }

    public record SelectionRequest(Long businessId) {
    }
}
