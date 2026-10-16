package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.security.InternalBotApiRequestGuardService;
import com.example.panel.service.BusinessQuestionTemplateRegistryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** New explicit-selection runtime path; legacy channel runtime remains unchanged. */
@RestController
@RequestMapping("/internal/api/bot")
public class BusinessQuestionTemplateRuntimeController {

    private final BusinessQuestionTemplateRegistryService registry;
    private final MultibusinessFeatureFlags flags;
    private final InternalBotApiRequestGuardService guard;

    public BusinessQuestionTemplateRuntimeController(BusinessQuestionTemplateRegistryService registry,
            MultibusinessFeatureFlags flags, InternalBotApiRequestGuardService guard) {
        this.registry = registry;
        this.flags = flags;
        this.guard = guard;
    }

    @GetMapping("/channels/{channelId}/businesses/{businessId}/runtime-config")
    public BusinessQuestionTemplateRegistryService.ScopedRuntimeConfig runtimeConfig(HttpServletRequest request,
            @RequestHeader(name = "X-Iguana-Bot-Api-Token", required = false) String token,
            @PathVariable long channelId, @PathVariable long businessId) {
        guard.authorize(request, token);
        if (!flags.isFoundationEnabled() || !flags.isSharedChannelResolution()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        try {
            return registry.resolveExplicitChannel(channelId, businessId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }
}
