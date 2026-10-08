package com.example.panel.controller;

import com.example.panel.service.WorkforceService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workforce")
@PreAuthorize("hasAuthority('PAGE_SETTINGS') or hasAuthority('PAGE_USERS')")
public class WorkforceAdminApiController {

    private final WorkforceService workforceService;

    public WorkforceAdminApiController(WorkforceService workforceService) {
        this.workforceService = workforceService;
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        return Map.of(
                "success", true,
                "positions", workforceService.listPositions(),
                "notification_targets", List.of("support_chat", "broadcast_channel", "custom_chat"),
                "notification_channels", workforceService.listNotificationChannels()
        );
    }

    @PostMapping("/positions")
    public Map<String, Object> createPosition(@RequestBody Map<String, Object> payload) {
        return Map.of("success", true, "position", workforceService.createPosition(payload));
    }

    @PutMapping("/positions/{positionId}")
    public Map<String, Object> updatePosition(@PathVariable long positionId,
                                               @RequestBody Map<String, Object> payload) {
        return Map.of("success", true, "position", workforceService.updatePosition(positionId, payload));
    }

    @GetMapping("/users/{userId}/settings")
    public Map<String, Object> userSettings(@PathVariable long userId) {
        return Map.of("success", true, "settings", workforceService.getUserSettings(userId));
    }

    @PutMapping("/users/{userId}/settings")
    public Map<String, Object> updateUserSettings(@PathVariable long userId,
                                                   @RequestBody Map<String, Object> payload) {
        return Map.of("success", true, "settings", workforceService.updateUserSettings(userId, payload));
    }

    @GetMapping("/users/{userId}/schedule")
    public Map<String, Object> schedule(@PathVariable long userId) {
        return Map.of("success", true, "schedule", workforceService.getSchedule(userId));
    }

    @PutMapping("/users/{userId}/schedule")
    public Map<String, Object> replaceSchedule(@PathVariable long userId,
                                                @RequestBody Map<String, Object> payload) {
        Object rawRules = payload.get("rules");
        if (!(rawRules instanceof List<?> list)) {
            throw new IllegalArgumentException("Поле rules должно быть массивом");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = (List<Map<String, Object>>) (List<?>) list;
        return Map.of("success", true, "schedule", workforceService.replaceSchedule(userId, rules));
    }

    @GetMapping("/users/{userId}/sessions")
    public Map<String, Object> sessions(@PathVariable long userId,
                                        @RequestParam(name = "limit", defaultValue = "50") int limit) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("sessions", workforceService.listRecentSessions(userId, limit));
        return response;
    }
}
