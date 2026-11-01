package com.example.panel.controller;

import com.example.panel.security.InternalBotApiRequestGuardService;
import com.example.panel.service.integration.FeedbackPromptDeliveryAuthorizationService;
import com.example.panel.service.integration.OutboundFeedbackPromptEvent;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FeedbackPromptDeliveryAuthorizationController {
    private final InternalBotApiRequestGuardService guard;
    private final FeedbackPromptDeliveryAuthorizationService authorization;
    public FeedbackPromptDeliveryAuthorizationController(InternalBotApiRequestGuardService guard, FeedbackPromptDeliveryAuthorizationService authorization) {
        this.guard=guard;this.authorization=authorization;
    }
    @PostMapping("/internal/api/bot/feedback-prompts/authorize-delivery")
    public Authorization authorize(HttpServletRequest request,
        @RequestHeader(name=InternalBotApiRequestGuardService.AUTH_HEADER,required=false) String token,
        @RequestBody DeliveryRequest body) {
        // Read-only decision: no write idempotency cache may replay an earlier authorization after revocation.
        guard.authorize(request,token);
        var event=body.event();
        return new Authorization(event!=null?event.eventId():null,authorization.authorize(event,body.routingKey()));
    }
    public record DeliveryRequest(OutboundFeedbackPromptEvent event,String routingKey) {}
    public record Authorization(String eventId,boolean allowed) {}
}
