package com.example.panel.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** HTTP boundary shared by aliases, multipart uploads and operational actions. */
final class SettingsWritePermissionFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication actor = SecurityContextHolder.getContext().getAuthentication();
        if (actor == null || actor instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            chain.doFilter(request, response);
            return;
        }
        String path = requestPath(request);
        boolean write = !java.util.Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod());
        String block = write ? blockFor(path) : null;
        boolean equipmentRead = path.startsWith("/api/settings/it-equipment");
        if ((block != null && !PanelAccessPolicy.canEdit(actor, block))
                || (equipmentRead && !PanelAccessPolicy.has(actor, "PAGE_SETTINGS")
                    && !PanelAccessPolicy.has(actor, "PAGE_OBJECT_PASSPORTS"))) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"error\":\"Нет права изменять этот блок настроек\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    static String requestPath(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path.isEmpty()) path = org.springframework.web.util.UriUtils.decode(
                request.getRequestURI().substring(request.getContextPath().length()), java.nio.charset.StandardCharsets.UTF_8);
        return path.replaceAll(";[^/]*", "");
    }

    static String blockFor(String path) {
        if (path.startsWith("/api/settings/it-equipment")) return "equipment";
        if (path.startsWith("/api/settings/backup")) return "backup";
        if (path.startsWith("/api/settings/netbox-sync")) return "netbox";
        if (path.startsWith("/api/settings/locations-sync")) return "locations";
        if (path.startsWith("/api/settings/notification-routing")) return "notification_routing";
        if (path.startsWith("/api/settings/client-statuses")) return "appearance";
        if (path.startsWith("/api/settings/it-connection-categories")) return "it_connections";
        if (path.startsWith("/api/settings/integration-network")) return "network_profiles";
        if (path.startsWith("/api/settings/") && !path.matches("/api/settings/parameters(?:/[^/]+)?/?")) return "__unclassified";
        if (path.startsWith("/api/auth/org-structure")) return "organization";
        if (path.startsWith("/api/workforce/")) return "workforce";
        if (path.startsWith("/api/channels") || path.startsWith("/api/bot-credentials")
                || path.matches("/api/[^/]+/public-id/regenerate/?")
                || path.matches("/api/bots/[^/]+/(start|stop)/?")) return "channels";
        return null;
    }
}
