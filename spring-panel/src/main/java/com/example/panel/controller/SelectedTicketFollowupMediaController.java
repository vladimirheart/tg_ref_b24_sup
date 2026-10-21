package com.example.panel.controller;

import com.example.panel.security.InternalBotApiRequestGuardService;
import com.example.panel.service.integration.SelectedTicketFollowupMediaService;
import jakarta.servlet.http.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/api/bot/channels/{channelId}/questionnaire-media")
public class SelectedTicketFollowupMediaController {
    private final SelectedTicketFollowupMediaService service;
    private final InternalBotApiRequestGuardService guard;
    public SelectedTicketFollowupMediaController(SelectedTicketFollowupMediaService service,InternalBotApiRequestGuardService guard) {
        this.service=service;this.guard=guard;
    }
    @GetMapping
    public SelectedTicketFollowupMediaService.Admission admission(HttpServletRequest request,HttpServletResponse response,
            @RequestHeader(name="X-Iguana-Bot-Api-Token",required=false) String token,@PathVariable long channelId,
            @RequestParam long userId,@RequestParam String ticketId) {
        response.setHeader("Cache-Control","no-store");guard.authorize(request,token);
        try {return service.admission(channelId,userId,ticketId);}
        catch(IllegalStateException ex) {throw new ResponseStatusException(HttpStatus.CONFLICT);}
    }
}
