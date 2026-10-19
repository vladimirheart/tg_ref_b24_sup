package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.GeneralTicketRealtimeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Controller
public class GeneralTicketStreamController {
    private final GeneralTicketRealtimeService realtime;
    private final MultibusinessFeatureFlags flags;
    public GeneralTicketStreamController(GeneralTicketRealtimeService realtime, MultibusinessFeatureFlags flags) {
        this.realtime = realtime; this.flags = flags;
    }
    @GetMapping(value="/api/general-tickets/events/stream", produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('PAGE_DIALOGS')")
    public ResponseEntity<SseEmitter> stream(Authentication auth) {
        if (!flags.isFoundationEnabled() || !flags.isGeneralTicketQueue()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return ResponseEntity.ok().header("Cache-Control", "no-store").header("X-Accel-Buffering", "no")
            .body(realtime.connect(auth == null ? null : auth.getName()));
    }
}
