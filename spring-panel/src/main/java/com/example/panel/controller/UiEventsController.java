package com.example.panel.controller;

import com.example.panel.service.UiEventStreamService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
    public ResponseEntity<SseEmitter> stream(Authentication authentication) {
        return ResponseEntity.ok()
            .header("Cache-Control", "no-store")
            .header("X-Accel-Buffering", "no")
            .body(uiEventStreamService.connect(authentication));
    }
}
