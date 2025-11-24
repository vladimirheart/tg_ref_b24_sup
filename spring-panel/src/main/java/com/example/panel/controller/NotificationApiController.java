package com.example.panel.controller;

import com.example.panel.model.notification.NotificationDto;
import com.example.panel.model.notification.NotificationSummary;
import com.example.panel.service.NotificationService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationApiController {

    private final NotificationService notificationService;

    public NotificationApiController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationDto> list(Authentication authentication) {
        return notificationService.findForUser(resolveIdentity(authentication));
    }

    @GetMapping("/unread_count")
    public Map<String, Object> unreadCount(Authentication authentication) {
        NotificationSummary summary = notificationService.summary(resolveIdentity(authentication));
        return Map.of("success", true, "unread", summary.unreadCount());
    }

    @PostMapping("/{id}/read")
    public Map<String, Object> markAsRead(@PathVariable Long id, Authentication authentication) {
        notificationService.markAsRead(resolveIdentity(authentication), id);
        return Map.of("success", true);
    }

    private String resolveIdentity(Authentication authentication) {
        if (authentication == null) {
            return "all";
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        return authentication.getName();
    }
}