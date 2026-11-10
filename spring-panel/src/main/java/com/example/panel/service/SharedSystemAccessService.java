package com.example.panel.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** Incidents and the notification inbox are common to current panel accounts; no business/module grant. */
@Service
public class SharedSystemAccessService {
    public static final String REQUIRE = "@sharedSystemAccessService.canAccess(authentication)";
    private final JdbcTemplate jdbc;

    public SharedSystemAccessService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean canAccess(Authentication authentication) {
        var actor = GeneralTicketActor.from(authentication);
        if (actor == null) return false;
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS (SELECT 1 FROM users account
              WHERE account.id = ? AND account.username = ?
                AND account.enabled = TRUE AND COALESCE(account.is_blocked, FALSE) = FALSE)
            """, Boolean.class, actor.userId(), actor.username()));
    }

    public void requireCurrentAccount() {
        if (!canAccess(SecurityContextHolder.getContext().getAuthentication()))
            throw new AccessDeniedException("Требуется действующая учётная запись панели.");
    }
}
