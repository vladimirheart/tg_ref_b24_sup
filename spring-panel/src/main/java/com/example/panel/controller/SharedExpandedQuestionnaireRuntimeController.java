package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.security.InternalBotApiRequestGuardService;
import com.example.panel.service.SharedGeneralQuestionnaireService;
import jakarta.servlet.http.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Version 4 start requires the matching writer and a current explicit selection. */
@org.springframework.transaction.annotation.Transactional
@RestController
@RequestMapping("/internal/api/bot/channels/{channelId}/questionnaire/v4")
public class SharedExpandedQuestionnaireRuntimeController {
    private final SharedGeneralQuestionnaireService service;
    private final InternalBotApiRequestGuardService guard;
    private final MultibusinessFeatureFlags flags;
    private com.example.panel.service.integration.SelectedTicketFollowupMediaService linkedMedia;
    @org.springframework.beans.factory.annotation.Autowired
    public void setLinkedMedia(com.example.panel.service.integration.SelectedTicketFollowupMediaService linkedMedia) { this.linkedMedia=linkedMedia; }

    public SharedExpandedQuestionnaireRuntimeController(SharedGeneralQuestionnaireService service,
            InternalBotApiRequestGuardService guard, MultibusinessFeatureFlags flags) {
        this.service = service; this.guard = guard; this.flags = flags;
    }

    @GetMapping("/choices")
    public SharedGeneralQuestionnaireService.Catalog choices(HttpServletRequest request, HttpServletResponse response,
            @RequestHeader(name="X-Iguana-Bot-Api-Token", required=false) String token, @PathVariable long channelId,
            @RequestParam(defaultValue="false") boolean forStart) {
        authorize(request, response, token, channelId, forStart);
        try { return service.choicesExpanded(channelId).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT)); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST); }
    }

    @GetMapping("/selection")
    public SharedGeneralQuestionnaireService.Selected selection(HttpServletRequest request, HttpServletResponse response,
            @RequestHeader(name="X-Iguana-Bot-Api-Token", required=false) String token, @PathVariable long channelId,
            @RequestParam String scopeKind, @RequestParam(required=false) Long businessId,
            @RequestParam String catalogSha256, @RequestParam(defaultValue="false") boolean forStart) {
        authorize(request, response, token, channelId, forStart);
        try { return service.selectExpanded(channelId, scopeKind, businessId, catalogSha256)
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
        if (forStart) {
            response.setHeader("X-Iguana-Questionnaire-Writer-Version", "4");
            response.setHeader("X-Iguana-Questionnaire-Media-Version", "1");
            response.setHeader("X-Iguana-Questionnaire-VK-Media-Version", "1");
        }
    }
    @GetMapping("/max-linked-media-selection")
    public SharedGeneralQuestionnaireService.Selected maxLinkedMediaSelection(HttpServletRequest request,HttpServletResponse response,
            @RequestHeader(name="X-Iguana-Bot-Api-Token",required=false) String token,@PathVariable long channelId,
            @RequestParam String scopeKind,@RequestParam(required=false) Long businessId,
            @RequestParam String catalogSha256,@RequestParam(defaultValue="false") boolean forStart) {
        var selected=maxMediaSelection(request,response,token,channelId,scopeKind,businessId,catalogSha256,forStart);
        if(linkedMedia==null)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        linkedMedia.requireMaxLinkedMedia(channelId);
        return selected;
    }

    @GetMapping("/max-media-selection")
    public SharedGeneralQuestionnaireService.Selected maxMediaSelection(HttpServletRequest request,HttpServletResponse response,
            @RequestHeader(name="X-Iguana-Bot-Api-Token",required=false) String token,@PathVariable long channelId,
            @RequestParam String scopeKind,@RequestParam(required=false) Long businessId,
            @RequestParam String catalogSha256,@RequestParam(defaultValue="false") boolean forStart) {
        var selected=selection(request,response,token,channelId,scopeKind,businessId,catalogSha256,forStart);
        if(!forStart)throw new ResponseStatusException(HttpStatus.CONFLICT);
        response.setHeader("X-Iguana-Questionnaire-MAX-Media-Version","1");
        return selected;
    }
}
