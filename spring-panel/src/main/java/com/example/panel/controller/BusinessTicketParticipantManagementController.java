package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.BusinessTicketAssignmentService;
import com.example.panel.service.BusinessTicketParticipantService;
import com.example.panel.service.UiEventTicketScope;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** JSON adapter for the same full-owner, revision-checked workflow used by the business ticket form. */
@RestController
@RequestMapping("/api/business-scope/tickets/{publicId}/participants")
@PreAuthorize("hasAuthority('PAGE_DIALOGS')")
public class BusinessTicketParticipantManagementController {
    private final BusinessTicketAssignmentService assignments;
    private final BusinessTicketParticipantService participants;
    private final MultibusinessFeatureFlags flags;

    public BusinessTicketParticipantManagementController(BusinessTicketAssignmentService assignments,
            BusinessTicketParticipantService participants, MultibusinessFeatureFlags flags) {
        this.assignments = assignments; this.participants = participants; this.flags = flags;
    }

    @GetMapping("/management")
    public ResponseEntity<?> management(@PathVariable String publicId, Authentication authentication) {
        var state = find(publicId, authentication);
        var scope = state.scope();
        long revision = participants.revision(scope, authentication)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Список участников недоступен"));
        List<Map<String, String>> candidates = state.canOperate()
            ? participants.candidates(scope, authentication).stream()
                .map(candidate -> Map.of("userId", Long.toString(candidate.id()), "username", candidate.username())).toList()
            : List.of();
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(Map.of(
            "owner", Map.of("userId", scope.ticketUserId().toString(), "ticketId", scope.ticketId(),
                "ticketPublicId", scope.ticketPublicId(), "businessId", scope.businessId().toString()),
            "revision", Long.toString(revision), "canManage", state.canOperate(), "candidates", candidates));
    }

    @PostMapping
    public ResponseEntity<?> add(@PathVariable String publicId, @RequestBody Change request, Authentication authentication) {
        return change(publicId, request, authentication, true);
    }

    @DeleteMapping
    public ResponseEntity<?> remove(@PathVariable String publicId, @RequestBody Change request, Authentication authentication) {
        return change(publicId, request, authentication, false);
    }

    private ResponseEntity<?> change(String publicId, Change request, Authentication authentication, boolean add) {
        if (request == null || request.ticketId() == null || request.ticketId().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        long memberId = decimal(request.memberId(), false), revision = decimal(request.revision(), true);
        var expected = new UiEventTicketScope(ownerId(request.userId()), request.ticketId(),
            request.ticketPublicId(), "BUSINESS", decimal(request.businessId(), false));
        var state = find(publicId, authentication);
        if (!state.canOperate()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (!publicId.equals(request.ticketPublicId()) || !state.scope().equals(expected)
                || !participants.change(expected, authentication, revision, memberId, add))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Список или права изменились. Обновите участников.");
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }

    private BusinessTicketAssignmentService.State find(String publicId, Authentication authentication) {
        if (!flags.isFoundationEnabled()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return assignments.find(publicId, authentication).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private long decimal(String value, boolean zeroAllowed) {
        try {
            if (value == null || !value.matches(zeroAllowed ? "0|[1-9][0-9]{0,18}" : "[1-9][0-9]{0,18}"))
                throw new NumberFormatException();
            return Long.parseLong(value);
        } catch (NumberFormatException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST); }
    }

    private long ownerId(String value) {
        try {
            if (value == null || !value.matches("0|-?[1-9][0-9]{0,18}")) throw new NumberFormatException();
            return Long.parseLong(value);
        } catch (NumberFormatException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST); }
    }

    public record Change(String userId, String ticketId, String ticketPublicId, String businessId, String revision, String memberId) { }
}
