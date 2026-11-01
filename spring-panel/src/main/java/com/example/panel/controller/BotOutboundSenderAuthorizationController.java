package com.example.panel.controller;

import com.example.panel.security.InternalBotApiRequestGuardService;
import com.example.panel.service.BotOutboundSenderAuthorizationService;
import com.example.panel.service.BotOutboundSenderAuthorizationService.SenderIdentity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BotOutboundSenderAuthorizationController {
    private final InternalBotApiRequestGuardService guard;
    private final BotOutboundSenderAuthorizationService authorization;
    public BotOutboundSenderAuthorizationController(InternalBotApiRequestGuardService guard, BotOutboundSenderAuthorizationService authorization) {
        this.guard = guard; this.authorization = authorization;
    }
    @PostMapping("/internal/api/bot/outbound/authorize-sender")
    public Decision authorize(HttpServletRequest request,
        @RequestHeader(name = InternalBotApiRequestGuardService.AUTH_HEADER, required = false) String token,
        @RequestBody SenderIdentity identity) {
        guard.authorize(request, token);
        return new Decision(identity.channelId(), identity.platform(), authorization.authorize(identity));
    }
    public record Decision(Long channelId, String platform, boolean allowed) { }
}
