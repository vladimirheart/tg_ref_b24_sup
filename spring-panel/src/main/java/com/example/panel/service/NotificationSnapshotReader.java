package com.example.panel.service;

import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.security.PanelSessionVersionService;
import com.example.panel.model.notification.NotificationPage;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/** Internal SSE identity only; routing names are never resolved into another account. */
@Service
public class NotificationSnapshotReader {
    private final NotificationService notifications;
    private final PanelSessionVersionService sessions;
    private final SharedSystemAccessService accounts;
    public NotificationSnapshotReader(NotificationService notifications,PanelSessionVersionService sessions,SharedSystemAccessService accounts) {
        this.notifications=notifications;this.sessions=sessions;this.accounts=accounts;
    }
    public record Snapshot(long unread,NotificationPage page) {}
    @Transactional
    public Snapshot read(UiEventRecipient recipient) {
        if(!sessions.isCurrent(recipient.userId(),recipient.username(),recipient.sessionVersion()))
            throw new org.springframework.security.access.AccessDeniedException("Session revoked");
        var principal=new PanelUserPrincipal(recipient.userId(),recipient.username(),"[internal]",true,List.of(),recipient.sessionVersion());
        var authentication=UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities());
        if(!accounts.canAccess(authentication))throw new org.springframework.security.access.AccessDeniedException("Account unavailable");
        var previous=SecurityContextHolder.getContext();var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        try {
            SecurityContextHolder.setContext(context);
            // workScope captures current database role/grants; no cached stream authorities.
            long unread=notifications.summary(recipient.username()).unreadCount();
            return new Snapshot(unread,notifications.findPageForUser(recipient.username(),null,10));
        } finally {SecurityContextHolder.setContext(previous);}
    }
}
