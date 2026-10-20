package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.security.InternalBotApiRequestGuardService;
import com.example.panel.service.BusinessQuestionTemplateRegistryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Read-only selection handshake. Transport/session/ticket writes must still carry the selected scope. */
@RestController
@RequestMapping("/internal/api/bot/channels/{channelId}/questionnaire")
public class SharedQuestionnaireRuntimeController {
    private final BusinessQuestionTemplateRegistryService registry;
    private final InternalBotApiRequestGuardService guard;
    private final MultibusinessFeatureFlags flags;
    public SharedQuestionnaireRuntimeController(BusinessQuestionTemplateRegistryService registry,
            InternalBotApiRequestGuardService guard,MultibusinessFeatureFlags flags) {
        this.registry=registry;this.guard=guard;this.flags=flags;
    }
    @GetMapping("/choices")
    public BusinessQuestionTemplateRegistryService.ChoiceCatalog choices(HttpServletRequest request,HttpServletResponse response,
            @RequestHeader(name="X-Iguana-Bot-Api-Token",required=false) String token,@PathVariable long channelId) {
        authorize(request,response,token);
        try { return registry.choices(channelId).orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT)); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST); }
    }
    @GetMapping("/selection")
    public BusinessQuestionTemplateRegistryService.SelectedRuntime selection(HttpServletRequest request,HttpServletResponse response,
            @RequestHeader(name="X-Iguana-Bot-Api-Token",required=false) String token,@PathVariable long channelId,
            @RequestParam long businessId,@RequestParam String catalogSha256) {
        authorize(request,response,token);
        try { return registry.select(channelId,businessId,catalogSha256).orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT)); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST); }
    }
    private void authorize(HttpServletRequest request,HttpServletResponse response,String token) {
        response.setHeader("Cache-Control","no-store");
        guard.authorize(request,token);
        if (!flags.isFoundationEnabled() || !flags.isSharedChannelResolution()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
