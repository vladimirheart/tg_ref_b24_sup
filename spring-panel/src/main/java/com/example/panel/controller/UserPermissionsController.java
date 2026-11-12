package com.example.panel.controller;

import com.example.panel.security.PanelAccessPolicy;
import com.example.panel.security.PanelUserPrincipal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/users/{userId}/permissions")
@PreAuthorize("hasAnyAuthority('PAGE_SETTINGS','PAGE_USERS')")
public class UserPermissionsController {
    private final JdbcTemplate jdbc;

    public UserPermissionsController(@Qualifier("usersJdbcTemplate") JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public Map<String, Object> read(@PathVariable long userId, Authentication actor) {
        Map<String, Object> account = account(userId, false);
        Set<String> inherited = inherited(account);
        Set<String> direct = direct(userId);
        Set<String> effective = PanelAccessPolicy.effective(inherited, direct);
        List<Map<String, Object>> rows = new ArrayList<>();
        PanelAccessPolicy.PAGES.forEach((key, authority) -> rows.add(row("page", key, key, authority, inherited, direct, effective)));
        PanelAccessPolicy.BLOCKS.forEach((key, label) -> rows.add(row("settings", key, label,
                PanelAccessPolicy.editAuthority("settings." + key), inherited, direct, effective)));
        PanelAccessPolicy.USER_FIELDS.forEach(key -> rows.add(row("action", key, key,
                PanelAccessPolicy.editAuthority(key), inherited, direct, effective)));
        boolean canEdit = PanelAccessPolicy.canEdit(actor, "access") && !protectedAccount(account, direct)
                && !(actor.getPrincipal() instanceof PanelUserPrincipal principal && principal.getUserId() == userId);
        rows.forEach(row -> row.put("can_override", canEdit && PanelAccessPolicy.has(actor, row.get("authority").toString())));
        return Map.of("success", true, "username", account.get("username"), "rows", rows, "can_edit", canEdit);
    }

    @PutMapping
    @Transactional
    public Map<String, Object> write(@PathVariable long userId, @RequestBody Map<String, String> overrides,
                                     Authentication actor) {
        PanelAccessPolicy.requireBlock("access");
        if (!(actor.getPrincipal() instanceof PanelUserPrincipal principal) || principal.getUserId() == userId)
            throw new AccessDeniedException("Нельзя менять собственные индивидуальные права");
        Map<String, Object> account = account(userId, true);
        Set<String> direct = direct(userId);
        if (protectedAccount(account, direct)) throw new AccessDeniedException("Права системного администратора защищены");
        Set<String> managed = PanelAccessPolicy.managedAuthorities();
        for (var entry : overrides.entrySet()) {
            if (!managed.contains(entry.getKey()) || entry.getValue() == null || !Set.of("inherit", "allow", "deny").contains(entry.getValue()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестное разрешение или режим");
            if (!PanelAccessPolicy.has(actor, entry.getKey()))
                throw new AccessDeniedException("Нельзя управлять правом, которого нет у текущего пользователя");
        }
        // Replace only explicitly supplied managed entries; unrelated authorities stay intact.
        for (var entry : overrides.entrySet()) {
            jdbc.update("DELETE FROM user_authorities WHERE user_id = ? AND authority IN (?, ?)",
                    userId, entry.getKey(), "DENY_" + entry.getKey());
            if (!"inherit".equals(entry.getValue())) jdbc.update(
                    "INSERT INTO user_authorities(user_id, authority) VALUES (?, ?)", userId,
                    ("deny".equals(entry.getValue()) ? "DENY_" : "") + entry.getKey());
        }
        return read(userId, actor);
    }

    private Map<String, Object> account(long id, boolean lock) {
        var rows = jdbc.queryForList("SELECT u.id, u.username, r.name AS role_name, r.permissions FROM users u "
                + "LEFT JOIN roles r ON r.id = u.role_id WHERE u.id = ?" + (lock ? " FOR UPDATE OF u" : ""), id);
        if (rows.size() != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден");
        return rows.get(0);
    }

    private Set<String> direct(long id) {
        return new LinkedHashSet<>(jdbc.query("SELECT authority FROM user_authorities WHERE user_id = ?",
                (rs, row) -> rs.getString(1), id));
    }

    private boolean protectedAccount(Map<String, Object> account, Set<String> direct) {
        return systemUsername(account.get("username")) || protectedRole(account.get("role_name"))
                || direct.contains("ROLE_ADMIN") || direct.contains("ROLE_PORTAL_ADMIN");
    }

    public static boolean systemUsername(Object name) { return "admin".equalsIgnoreCase(String.valueOf(name)); }

    public static boolean protectedRole(Object name) {
        if ("admin".equalsIgnoreCase(String.valueOf(name).trim())) return true;
        return Set.of("admin", "portal_admin", "portal admin", "администратор портала")
                .contains(String.valueOf(name).trim().toLowerCase(java.util.Locale.ROOT));
    }

    private Set<String> inherited(Map<String, Object> account) {
        Set<String> result = PanelAccessPolicy.roleAuthorities(account.get("permissions"));
        if (protectedRole(account.get("role_name")))
            PanelAccessPolicy.managedAuthorities().stream().filter(right -> !right.startsWith("PAGE_")).forEach(result::add);
        return result;
    }

    private Map<String, Object> row(String kind, String key, String label, String authority, Set<String> inherited,
                                    Set<String> direct, Set<String> effective) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("kind", kind); row.put("key", key); row.put("label", label); row.put("authority", authority);
        row.put("inherited", inherited.contains(authority)); row.put("effective", effective.contains(authority));
        row.put("override", direct.contains("DENY_" + authority) ? "deny" : direct.contains(authority) ? "allow" : "inherit");
        return row;
    }
}
