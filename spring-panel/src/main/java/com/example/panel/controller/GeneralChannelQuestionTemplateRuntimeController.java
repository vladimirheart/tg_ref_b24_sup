package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.security.InternalBotApiRequestGuardService;
import com.example.panel.service.GeneralChannelQuestionTemplateService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** General-only runtime path; legacy channel runtime is not reclassified. */
@RestController
@RequestMapping("/internal/api/bot")
public class GeneralChannelQuestionTemplateRuntimeController {

    private final GeneralChannelQuestionTemplateService service;
    private final MultibusinessFeatureFlags flags;
    private final InternalBotApiRequestGuardService guard;

    public GeneralChannelQuestionTemplateRuntimeController(GeneralChannelQuestionTemplateService service,
            MultibusinessFeatureFlags flags, InternalBotApiRequestGuardService guard) {
        this.service = service;
        this.flags = flags;
        this.guard = guard;
    }

    @GetMapping("/channels/{channelId}/general/runtime-config")
    public GeneralChannelQuestionTemplateService.RuntimeConfig runtimeConfig(HttpServletRequest request,
            @RequestHeader(name = "X-Iguana-Bot-Api-Token", required = false) String token,
            @PathVariable long channelId) {
        guard.authorize(request, token);
        if (!flags.isFoundationEnabled() || !flags.isGeneralChannelResolution()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        try {
            return service.resolve(channelId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }
}
