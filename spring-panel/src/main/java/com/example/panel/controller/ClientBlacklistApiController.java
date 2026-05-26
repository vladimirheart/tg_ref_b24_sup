package com.example.panel.controller;

import com.example.panel.service.ClientBlacklistService;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/blacklist")
@PreAuthorize("hasAuthority('PAGE_CLIENTS')")
public class ClientBlacklistApiController {

    private final ClientBlacklistService clientBlacklistService;

    public ClientBlacklistApiController(ClientBlacklistService clientBlacklistService) {
        this.clientBlacklistService = clientBlacklistService;
    }

    @PostMapping("/add")
    public Map<String, Object> add(@RequestParam("user_id") String userId,
                                   @RequestParam(value = "reason", required = false) String reason,
                                   Authentication authentication) {
        ClientBlacklistService.BlacklistMutationResult result = clientBlacklistService.blockClient(
                userId,
                reason,
                authentication != null ? authentication.getName() : "system",
                false
        );
        if (!result.ok()) {
            return Map.of("ok", false, "error", result.error());
        }
        return Map.of("ok", true, "message", result.message());
    }

    @PostMapping("/remove")
    public Map<String, Object> remove(@RequestParam("user_id") String userId,
                                      Authentication authentication) {
        ClientBlacklistService.BlacklistMutationResult result = clientBlacklistService.unblockClient(
                userId,
                authentication != null ? authentication.getName() : "system",
                true
        );
        if (!result.ok()) {
            return Map.of("ok", false, "error", result.error());
        }
        return Map.of("ok", true, "message", result.message());
    }
}
