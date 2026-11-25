package com.example.panel.controller;

import com.example.panel.service.AttentionService;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/attention")
public class AttentionApiController {
    private final AttentionService attention;
    public AttentionApiController(AttentionService attention) { this.attention = attention; }
    @GetMapping
    public ResponseEntity<Map<String, Object>> summary(Authentication actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(attention.summary(actor));
    }
    public record Seen(String kind, List<Long> ids) {}
    @PostMapping("/seen")
    public Map<String, Object> seen(@RequestBody Seen body, Authentication actor) {
        attention.seen(actor, body.kind(), body.ids());
        return Map.of("success", true);
    }
}
