package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.BusinessQuestionTemplateRegistryService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
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
@RequestMapping("/api/business-admin/question-templates")
@PreAuthorize("hasAuthority('PAGE_SETTINGS') and hasAuthority('ROLE_ADMIN')")
public class BusinessQuestionTemplateAdminController {

    private static final Logger log = LoggerFactory.getLogger(BusinessQuestionTemplateAdminController.class);
    private final BusinessQuestionTemplateRegistryService registry;
    private final MultibusinessFeatureFlags flags;

    public BusinessQuestionTemplateAdminController(BusinessQuestionTemplateRegistryService registry,
            MultibusinessFeatureFlags flags) {
        this.registry = registry;
        this.flags = flags;
    }

    @GetMapping
    public List<BusinessQuestionTemplateRegistryService.TemplateSummary> templates() {
        requireFoundation();
        return registry.availableTemplates();
    }

    @GetMapping("/businesses/{businessId}")
    public BusinessQuestionTemplateRegistryService.Binding binding(@PathVariable long businessId) {
        requireFoundation();
        try {
            return registry.findBinding(businessId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PutMapping("/businesses/{businessId}")
    public BusinessQuestionTemplateRegistryService.Binding bind(@PathVariable long businessId,
            @RequestBody(required = false) BindRequest request, Authentication authentication) {
        requireFoundation();
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Template ID is required");
        }
        try {
            BusinessQuestionTemplateRegistryService.Binding result = registry.bind(businessId,
                    request.templateId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            log.info("Business question template bound: actor={}, businessId={}, templateId={}",
                    actor(authentication), businessId, result.templateId());
            return result;
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Template is bound to another business", ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @DeleteMapping("/businesses/{businessId}")
    public ResponseEntity<Void> deactivate(@PathVariable long businessId, Authentication authentication) {
        requireFoundation();
        try {
            if (!registry.deactivate(businessId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
        log.info("Business question template deactivated: actor={}, businessId={}",
                actor(authentication), businessId);
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
