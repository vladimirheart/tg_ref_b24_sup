package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.GeneralChannelQuestionTemplateService;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/business-admin/general-channels")
@PreAuthorize("hasAuthority('PAGE_SETTINGS') and hasAuthority('ROLE_ADMIN')")
public class GeneralChannelQuestionTemplateAdminController {

    private static final Logger log = LoggerFactory.getLogger(GeneralChannelQuestionTemplateAdminController.class);
    private final GeneralChannelQuestionTemplateService service;
    private final MultibusinessFeatureFlags flags;

    public GeneralChannelQuestionTemplateAdminController(GeneralChannelQuestionTemplateService service,
            MultibusinessFeatureFlags flags) {
        this.service = service;
        this.flags = flags;
    }

    @GetMapping("/{channelId}/question-template")
    public GeneralChannelQuestionTemplateService.Binding binding(@PathVariable long channelId) {
        requireFoundation();
        try {
            return service.findBinding(channelId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PutMapping("/{channelId}/question-template")
    public GeneralChannelQuestionTemplateService.Binding bind(@PathVariable long channelId,
            @RequestBody(required = false) BindRequest request, Authentication authentication) {
        requireFoundation();
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Template ID is required");
        }
        try {
            Optional<GeneralChannelQuestionTemplateService.Binding> binding =
                    service.bind(channelId, request.templateId());
            GeneralChannelQuestionTemplateService.Binding result = binding
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            log.info("General channel template bound: actor={}, channelId={}, templateId={}",
                    actor(authentication), channelId, result.templateId());
            return result;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }
    }

    @DeleteMapping("/{channelId}/question-template")
    public ResponseEntity<Void> deactivate(@PathVariable long channelId, Authentication authentication) {
        requireFoundation();
        try {
            if (!service.deactivate(channelId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
        log.info("General channel template deactivated: actor={}, channelId={}", actor(authentication), channelId);
        return ResponseEntity.noContent().build();
    }

    private void requireFoundation() {
        if (!flags.isFoundationEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private String actor(Authentication authentication) {
        return authentication == null ? "unknown" : authentication.getName();
    }

    public record BindRequest(String templateId) {
    }
}
