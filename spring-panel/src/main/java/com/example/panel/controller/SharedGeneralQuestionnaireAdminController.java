package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.SharedGeneralQuestionnaireService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/business-admin/shared-channels/{channelId}/general-question-template")
@PreAuthorize("hasAuthority('PAGE_SETTINGS') and hasAuthority('ROLE_ADMIN')")
public class SharedGeneralQuestionnaireAdminController {
    private static final Logger log = LoggerFactory.getLogger(SharedGeneralQuestionnaireAdminController.class);
    private final SharedGeneralQuestionnaireService service;
    private final MultibusinessFeatureFlags flags;

    public SharedGeneralQuestionnaireAdminController(SharedGeneralQuestionnaireService service, MultibusinessFeatureFlags flags) {
        this.service = service; this.flags = flags;
    }

    @GetMapping
    public SharedGeneralQuestionnaireService.Binding binding(@PathVariable long channelId) {
        enabled();
        try { return service.findBinding(channelId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)); }
        catch (IllegalArgumentException ex) { throw failure(HttpStatus.BAD_REQUEST, ex); }
    }

    @PutMapping
    public SharedGeneralQuestionnaireService.Binding bind(@PathVariable long channelId,
            @RequestBody(required = false) BindRequest request, Authentication authentication) {
        enabled();
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Template ID is required");
        try {
            var binding = service.bind(channelId, request.templateId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            log.info("Shared general template bound: actor={}, channelId={}, templateId={}",
                authentication.getName(), channelId, binding.templateId());
            return binding;
        } catch (IllegalArgumentException ex) { throw failure(HttpStatus.BAD_REQUEST, ex); }
        catch (IllegalStateException | DuplicateKeyException ex) { throw failure(HttpStatus.CONFLICT, ex); }
    }

    @DeleteMapping
    public ResponseEntity<Void> deactivate(@PathVariable long channelId, Authentication authentication) {
        enabled();
        try {
            if (!service.deactivate(channelId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (IllegalArgumentException ex) { throw failure(HttpStatus.BAD_REQUEST, ex); }
        log.info("Shared general template deactivated: actor={}, channelId={}", authentication.getName(), channelId);
        return ResponseEntity.noContent().build();
    }

    private void enabled() {
        if (!flags.isFoundationEnabled()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    private ResponseStatusException failure(HttpStatus status, RuntimeException ex) {
        return new ResponseStatusException(status, ex.getMessage(), ex);
    }
    public record BindRequest(String templateId) {}
}
