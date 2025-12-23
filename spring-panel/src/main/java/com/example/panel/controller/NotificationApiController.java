package com.example.panel.controller;

import com.example.panel.model.notification.NotificationDto;
import com.example.panel.model.notification.NotificationSummary;
import com.example.panel.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(NotificationApiController.class);

    private final NotificationService notificationService;

    public NotificationApiController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationDto> list(Authentication authentication) {
        String identity = resolveIdentity(authentication);
        List<NotificationDto> notifications = notificationService.findForUser(identity);
        log.info("Loaded {} notifications for {}", notifications.size(), identity);
        return notifications;
    }

    @GetMapping("/unread_count")
    public Map<String, Object> unreadCount(Authentication authentication) {
        String identity = resolveIdentity(authentication);
        NotificationSummary summary = notificationService.summary(identity);
        log.info("Unread notifications requested by {}: {} unread", identity, summary.unreadCount());
        return Map.of("success", true, "unread", summary.unreadCount());
    }

    @PostMapping("/{id}/read")
    public Map<String, Object> markAsRead(@PathVariable Long id, Authentication authentication) {
        String identity = resolveIdentity(authentication);
        notificationService.markAsRead(identity, id);
        log.info("Notification {} marked as read by {}", id, identity);
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
