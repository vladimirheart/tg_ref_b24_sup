package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.security.InternalBotApiRequestGuardService;
import com.example.panel.service.SharedGeneralQuestionnaireService;
import jakarta.servlet.http.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Version 2 start requires the matching writer and a current explicit selection. */
@RestController
@RequestMapping("/internal/api/bot/channels/{channelId}/questionnaire/v2")
public class SharedGeneralQuestionnaireRuntimeController {
    private final SharedGeneralQuestionnaireService service;
    private final InternalBotApiRequestGuardService guard;
    private final MultibusinessFeatureFlags flags;

    public SharedGeneralQuestionnaireRuntimeController(SharedGeneralQuestionnaireService service,
            InternalBotApiRequestGuardService guard, MultibusinessFeatureFlags flags) {
        this.service = service; this.guard = guard; this.flags = flags;
    }

    @GetMapping("/choices")
    public SharedGeneralQuestionnaireService.Catalog choices(HttpServletRequest request, HttpServletResponse response,
            @RequestHeader(name="X-Iguana-Bot-Api-Token", required=false) String token, @PathVariable long channelId,
            @RequestParam(defaultValue="false") boolean forStart) {
        authorize(request, response, token, channelId, forStart);
        try { return service.choices(channelId).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT)); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST); }
    }

    @GetMapping("/selection")
    public SharedGeneralQuestionnaireService.Selected selection(HttpServletRequest request, HttpServletResponse response,
            @RequestHeader(name="X-Iguana-Bot-Api-Token", required=false) String token, @PathVariable long channelId,
            @RequestParam String scopeKind, @RequestParam(required=false) Long businessId,
            @RequestParam String catalogSha256, @RequestParam(defaultValue="false") boolean forStart) {
        authorize(request, response, token, channelId, forStart);
        try { return service.select(channelId, scopeKind, businessId, catalogSha256)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT)); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST); }
    }

    private void authorize(HttpServletRequest request, HttpServletResponse response, String token, long channelId, boolean forStart) {
        response.setHeader("Cache-Control", "no-store");
        guard.authorize(request, token);
        if (!flags.isFoundationEnabled() || !flags.isSharedChannelResolution()
                || (!flags.isGeneralChannelResolution() && (!forStart || service.findBinding(channelId)
                    .filter(SharedGeneralQuestionnaireService.Binding::active).isPresent()))
                || (forStart && !flags.isTicketWriteRequiresBusiness()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (forStart) response.setHeader("X-Iguana-Questionnaire-Writer-Version", "2");
    }
}
