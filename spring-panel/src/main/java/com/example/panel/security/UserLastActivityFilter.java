package com.example.panel.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final JdbcTemplate usersJdbcTemplate;
    private volatile boolean columnMissing = false;

    public UserLastActivityFilter(@Qualifier("usersJdbcTemplate") JdbcTemplate usersJdbcTemplate) {
        this.usersJdbcTemplate = usersJdbcTemplate;
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
        if (columnMissing) {
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return;
        }
        String username = authentication.getName();
        if (!StringUtils.hasText(username)) {
            return;
        }
        try {
            usersJdbcTemplate.update(
                    "UPDATE users SET last_portal_activity_at = ? WHERE lower(username) = lower(?)",
                    OffsetDateTime.now(ZoneOffset.UTC).toString(),
                    username.trim()
            );
        } catch (DataAccessException ex) {
            if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("last_portal_activity_at")) {
                columnMissing = true;
            }
            throw ex;
        }
    }
}

