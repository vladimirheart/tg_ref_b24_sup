package com.example.panel.controller;

import com.example.panel.model.notification.NotificationPage;
import com.example.panel.model.notification.NotificationSummary;
import com.example.panel.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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
    public NotificationPage list(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit,
            Authentication authentication
    ) {
        String identity = resolveIdentity(authentication);
        try {
            NotificationPage page = notificationService.findPageForUser(identity, cursor, limit);
            log.info("Loaded {} notifications for {} (hasMore={})", page.items().size(), identity, page.hasMore());
            return page;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid notification cursor", ex);
        }
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

    @PostMapping("/read-all")
    public Map<String, Object> markAllAsRead(Authentication authentication) {
        String identity = resolveIdentity(authentication);
        long updated = notificationService.markAllAsRead(identity);
        log.info("Marked {} notifications as read for {}", updated, identity);
        return Map.of("success", true, "updated", updated);
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
