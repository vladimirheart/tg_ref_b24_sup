package com.example.panel.controller;

import com.example.panel.security.PanelAccessPolicy;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PanelPermissionsController {
    @GetMapping("/api/auth/permissions")
    public Map<String, Object> permissions(Authentication actor) {
        Map<String, Boolean> blocks = new LinkedHashMap<>();
        Map<String, Boolean> pages = new LinkedHashMap<>();
        PanelAccessPolicy.BLOCKS.keySet().forEach(block -> blocks.put(block, PanelAccessPolicy.canEdit(actor, block)));
        PanelAccessPolicy.PAGES.forEach((key, right) -> pages.put(key, PanelAccessPolicy.has(actor, right)));
        return Map.of("success", true, "settings", blocks, "pages", pages);
    }
}
