package com.example.panel.controller;

import com.example.panel.service.PermissionService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/password-reset-requests")
public class PasswordResetRequestApiController {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";

    private final JdbcTemplate usersJdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final PermissionService permissionService;

    public PasswordResetRequestApiController(@Qualifier("usersJdbcTemplate") JdbcTemplate usersJdbcTemplate,
                                             PasswordEncoder passwordEncoder,
                                             PermissionService permissionService) {
        this.usersJdbcTemplate = usersJdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.permissionService = permissionService;
    }

    @PostMapping("/public")
    public Map<String, Object> createPublicRequest(@RequestBody Map<String, Object> payload,
                                                   HttpServletRequest request) {
        ensureRequestsTable();
        String username = stringValue(payload.get("username"));
        String note = shorten(stringValue(payload.get("comment")), 1000);
        if (!StringUtils.hasText(username)) {
            return Map.of("success", true, "message", "Если пользователь существует, запрос будет обработан администратором.");
        }

        List<Map<String, Object>> rows = usersJdbcTemplate.queryForList(
                "SELECT id, username FROM users WHERE lower(username) = lower(?) LIMIT 1",
                username
        );
        if (!rows.isEmpty()) {
            Map<String, Object> user = rows.get(0);
            Object userId = user.get("id");
            String userNameSnapshot = stringValue(user.get("username"));
            usersJdbcTemplate.update(
                    "INSERT INTO password_reset_requests(" +
                            "user_id, username_snapshot, requested_by_username, requested_by_ip, requested_user_agent, requested_note, status, created_at" +
                            ") VALUES(?, ?, ?, ?, ?, ?, ?, ?)",
                    userId,
                    userNameSnapshot,
                    username,
                    request.getRemoteAddr(),
                    shorten(stringValue(request.getHeader("User-Agent")), 512),
                    note,
                    STATUS_PENDING,
                    OffsetDateTime.now().toString()
            );
        }

        return Map.of(
                "success", true,
                "message", "Если пользователь существует, запрос будет обработан администратором."
        );
    }

    @GetMapping
    public Map<String, Object> listRequests(Authentication authentication) {
        if (!canManageRequests(authentication)) {
            return Map.of("success", false, "error", "Недостаточно прав для просмотра запросов");
        }
        ensureRequestsTable();
        List<Map<String, Object>> rows = usersJdbcTemplate.queryForList(
                "SELECT id, user_id, username_snapshot, requested_by_username, requested_by_ip, status, " +
                        "requested_note, resolution_note, created_at, resolved_at, resolved_by_username " +
                        "FROM password_reset_requests ORDER BY id DESC LIMIT 300"
        );
        return Map.of("success", true, "items", rows);
    }

    @PostMapping("/{requestId}/approve")
    public Map<String, Object> approveRequest(@PathVariable long requestId,
                                              @RequestBody(required = false) Map<String, Object> payload,
                                              Authentication authentication) {
        if (!canManageRequests(authentication)) {
            return Map.of("success", false, "error", "Недостаточно прав для обработки запроса");
        }
        ensureRequestsTable();
        List<Map<String, Object>> rows = usersJdbcTemplate.queryForList(
                "SELECT id, user_id, username_snapshot, status FROM password_reset_requests WHERE id = ? LIMIT 1",
                requestId
        );
        if (rows.isEmpty()) {
            return Map.of("success", false, "error", "Запрос не найден");
        }
        Map<String, Object> row = rows.get(0);
        String status = stringValue(row.get("status"));
        if (!STATUS_PENDING.equalsIgnoreCase(status)) {
            return Map.of("success", false, "error", "Запрос уже обработан");
        }

        Object userId = row.get("user_id");
        if (!(userId instanceof Number)) {
            rejectMissingUserRequest(requestId, authentication, "Пользователь больше не существует");
            return Map.of("success", false, "error", "Пользователь для запроса не найден");
        }

        String temporaryPassword = generateTemporaryPassword();
        String hash = passwordEncoder.encode(temporaryPassword);
        Integer updated = usersJdbcTemplate.update(
                "UPDATE users SET password = ?, password_hash = ? WHERE id = ?",
                hash,
                hash,
                ((Number) userId).longValue()
        );
        if (updated == null || updated == 0) {
            rejectMissingUserRequest(requestId, authentication, "Пользователь больше не существует");
            return Map.of("success", false, "error", "Пользователь для запроса не найден");
        }

        String note = payload == null ? "" : shorten(stringValue(payload.get("note")), 1000);
        usersJdbcTemplate.update(
                "UPDATE password_reset_requests SET status = ?, resolution_note = ?, resolved_at = ?, resolved_by_username = ? WHERE id = ?",
                STATUS_APPROVED,
                note,
                OffsetDateTime.now().toString(),
                authentication != null ? authentication.getName() : "system",
                requestId
        );
        return Map.of(
                "success", true,
                "temporary_password", temporaryPassword,
                "message", "Пароль пользователя сброшен, передайте временный пароль безопасным каналом."
        );
    }

    @PostMapping("/{requestId}/reject")
    public Map<String, Object> rejectRequest(@PathVariable long requestId,
                                             @RequestBody(required = false) Map<String, Object> payload,
                                             Authentication authentication) {
        if (!canManageRequests(authentication)) {
            return Map.of("success", false, "error", "Недостаточно прав для обработки запроса");
        }
        ensureRequestsTable();
        List<Map<String, Object>> rows = usersJdbcTemplate.queryForList(
                "SELECT id, status FROM password_reset_requests WHERE id = ? LIMIT 1",
                requestId
        );
        if (rows.isEmpty()) {
            return Map.of("success", false, "error", "Запрос не найден");
        }
        String status = stringValue(rows.get(0).get("status"));
        if (!STATUS_PENDING.equalsIgnoreCase(status)) {
            return Map.of("success", false, "error", "Запрос уже обработан");
        }
        String note = payload == null ? "" : shorten(stringValue(payload.get("note")), 1000);
        usersJdbcTemplate.update(
                "UPDATE password_reset_requests SET status = ?, resolution_note = ?, resolved_at = ?, resolved_by_username = ? WHERE id = ?",
                STATUS_REJECTED,
                note,
                OffsetDateTime.now().toString(),
                authentication != null ? authentication.getName() : "system",
                requestId
        );
        return Map.of("success", true);
    }

    private boolean canManageRequests(Authentication authentication) {
        return permissionService.isSuperUser(authentication)
                || permissionService.hasAuthority(authentication, "ROLE_PORTAL_ADMIN");
    }

    private void ensureRequestsTable() {
        usersJdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS password_reset_requests (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER,
                username_snapshot TEXT NOT NULL,
                requested_by_username TEXT,
                requested_by_ip TEXT,
                requested_user_agent TEXT,
                requested_note TEXT,
                status TEXT NOT NULL DEFAULT 'PENDING',
                resolution_note TEXT,
                created_at TEXT NOT NULL,
                resolved_at TEXT,
                resolved_by_username TEXT,
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE SET NULL
            );
        """);
        usersJdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_password_reset_requests_status_created_at
                ON password_reset_requests(status, created_at DESC);
        """);
    }

    private void rejectMissingUserRequest(long requestId, Authentication authentication, String note) {
        usersJdbcTemplate.update(
                "UPDATE password_reset_requests SET status = ?, resolution_note = ?, resolved_at = ?, resolved_by_username = ? WHERE id = ?",
                STATUS_REJECTED,
                note,
                OffsetDateTime.now().toString(),
                authentication != null ? authentication.getName() : "system",
                requestId
        );
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String shorten(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String generateTemporaryPassword() {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789@#$%";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(14);
        for (int i = 0; i < 14; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}
