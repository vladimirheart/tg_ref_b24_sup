package com.example.panel.controller;

import com.example.panel.service.UiEventStreamService;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/events")
public class UiEventsController {

    private final UiEventStreamService uiEventStreamService;

    public UiEventsController(UiEventStreamService uiEventStreamService) {
        this.uiEventStreamService = uiEventStreamService;
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication authentication) {
        return uiEventStreamService.connect(resolveIdentity(authentication));
    }

    private String resolveIdentity(Authentication authentication) {
        if (authentication == null) {
            return "anonymous";
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        return authentication.getName();
    }
}
