package com.example.panel.controller;

import com.example.panel.security.PublicAuthenticationGuard;
import com.example.panel.service.AccessRecoveryService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/password-reset-requests")
public class PasswordResetRequestApiController {
    private final AccessRecoveryService recovery;
    private final PublicAuthenticationGuard guard;
    public PasswordResetRequestApiController(AccessRecoveryService recovery, PublicAuthenticationGuard guard) {
        this.recovery = recovery; this.guard = guard;
    }
    @PostMapping("/public")
    public ResponseEntity<Map<String, Object>> createPublicRequest(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        long started = System.nanoTime();
        try { recovery.submit(payload, request); }
        finally { guard.finish(started); }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("success", true, "message", AccessRecoveryService.PUBLIC_MESSAGE));
    }
    @GetMapping
    public ResponseEntity<Map<String, Object>> listRequests(Authentication actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("success", true, "items", recovery.list(actor)));
    }
    @GetMapping("/{id}/events")
    public ResponseEntity<Map<String, Object>> history(@PathVariable long id, Authentication actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("success", true, "items", recovery.history(id, actor)));
    }
    @PostMapping("/{id}/link")
    public Map<String, Object> link(@PathVariable long id, @RequestBody Map<String, Object> payload, Authentication actor) {
        return recovery.link(id, payload, actor);
    }
    @PostMapping("/{id}/approve")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable long id, @RequestBody Map<String, Object> payload, Authentication actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(recovery.approve(id, payload, actor));
    }
    @PostMapping("/{id}/reject")
    public Map<String, Object> reject(@PathVariable long id, @RequestBody Map<String, Object> payload, Authentication actor) {
        return recovery.reject(id, payload, actor);
    }
}
