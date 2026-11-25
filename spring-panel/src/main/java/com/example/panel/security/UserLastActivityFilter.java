package com.example.panel.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
public class UserLastActivityFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(UserLastActivityFilter.class);
    static final String TOUCH_SQL = """
            WITH touch AS (SELECT CAST(? AS TIMESTAMPTZ) AS at),
            unlocked AS (
                SELECT account.id FROM users account CROSS JOIN touch
                 WHERE account.id=? AND account.username=?
                   AND (account.last_portal_activity_at IS NULL
                        OR account.last_portal_activity_at < touch.at-INTERVAL '30 seconds')
                 FOR UPDATE OF account SKIP LOCKED
            )
            UPDATE users account SET last_portal_activity_at=touch.at FROM touch,unlocked
             WHERE account.id=unlocked.id
            """;

    private final JdbcTemplate usersJdbcTemplate;
    private volatile boolean columnMissing = false;

    public UserLastActivityFilter(@Qualifier("usersJdbcTemplate") ObjectProvider<JdbcTemplate> usersJdbcTemplate) {
        this.usersJdbcTemplate = usersJdbcTemplate.getIfAvailable();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            touchCurrentUser();
        } catch (Exception ex) {
            log.debug("Unable to update portal activity: {}", ex.getMessage());
        }
        filterChain.doFilter(request, response);
    }

    private void touchCurrentUser() {
        if (columnMissing || usersJdbcTemplate == null) {
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return;
        }
        if (!(authentication.getPrincipal() instanceof PanelUserPrincipal principal)
                || principal.getUserId() <= 0) {
            return;
        }
        String username = principal.getUsername();
        if (!StringUtils.hasText(username)) {
            return;
        }
        try {
            usersJdbcTemplate.update(
                    TOUCH_SQL,
                    OffsetDateTime.now(ZoneOffset.UTC),
                    principal.getUserId(),
                    username
            );
        } catch (DataAccessException ex) {
            if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("last_portal_activity_at")) {
                columnMissing = true;
            }
            throw ex;
        }
    }
}

