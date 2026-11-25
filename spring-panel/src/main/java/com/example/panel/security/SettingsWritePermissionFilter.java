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
        String block = blockFor(path);
        boolean equipmentRead = path.startsWith("/api/settings/it-equipment");
        // Equipment profile/photo reads retain their existing passport-specific controller checks.
        boolean passportRead = !write && equipmentRead && PanelAccessPolicy.has(actor, "PAGE_OBJECT_PASSPORTS");
        boolean groupedRead = !write && path.startsWith("/api/settings/page-data/");
        boolean parameterRead = !write && path.matches("/api/settings/parameters/?");
        boolean accounts = path.matches("/api/(?:auth/state|users(?:/.*)?|roles(?:/.*)?)/?");
        boolean rosterAggregate = !write && path.equals("/api/auth/state")
                && (PanelAccessPolicy.canEdit(actor, "organization") || PanelAccessPolicy.canEdit(actor, "workforce"));
        boolean denied = accounts && !PanelAccessPolicy.canManageAccounts(actor) && !rosterAggregate;
        if (groupedRead) denied |= !sectionAllowed(actor, path.substring(path.lastIndexOf('/') + 1));
        if (parameterRead) denied |= java.util.stream.Stream.of("parameters", "it_connections", "legal_entities", "equipment")
                .noneMatch(key -> PanelAccessPolicy.canEdit(actor, key));
        if (denied || (!passportRead && !groupedRead && !parameterRead && block != null && !PanelAccessPolicy.canEdit(actor, block))
                || (equipmentRead && !PanelAccessPolicy.has(actor, "PAGE_SETTINGS")
                    && !PanelAccessPolicy.has(actor, "PAGE_OBJECT_PASSPORTS"))) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"error\":\"Нет доступа к этому блоку настроек\"}");
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
        if (path.startsWith("/api/channels") || path.startsWith("/api/bot-credentials") || path.equals("/api/channel-notifications")
                || path.matches("/api/[^/]+/public-id/regenerate/?")
                || path.startsWith("/api/bots/")) return "channels";
        if (path.startsWith("/api/production-readiness") || path.startsWith("/api/admin/storage")) return "backup";
        return null;
    }

    private static boolean sectionAllowed(Authentication actor, String section) {
        String[] blocks = switch (section) {
            case "admin" -> new String[]{"reporting", "manager_bindings"};
            case "channels" -> new String[]{"channels", "bot_templates", "auto_close", "network_profiles"};
            case "locations" -> new String[]{"locations", "netbox"};
            case "parameters" -> new String[]{"parameters", "it_connections", "legal_entities", "equipment", "network_profiles"};
            default -> new String[0];
        };
        return java.util.Arrays.stream(blocks).anyMatch(key -> PanelAccessPolicy.canEdit(actor, key));
    }
}
