package com.example.panel.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.service.UiPreferenceService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProfileApiController {

    private final JdbcTemplate usersJdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final UiPreferenceService uiPreferenceService;

    public ProfileApiController(@Qualifier("usersJdbcTemplate") JdbcTemplate usersJdbcTemplate,
                                PasswordEncoder passwordEncoder,
                                UiPreferenceService uiPreferenceService) {
        this.usersJdbcTemplate = usersJdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.uiPreferenceService = uiPreferenceService;
    }

    @org.springframework.web.bind.annotation.GetMapping("/profile/ui-preferences")
    public ResponseEntity<Map<String, Object>> loadUiPreferences(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("success", false, "error", "Не удалось определить пользователя."));
        }
        return ResponseEntity.ok(Map.of(
            "success", true,
            "preferences", uiPreferenceService.loadForUser(authentication)
        ));
    }

    @org.springframework.web.bind.annotation.PutMapping("/profile/ui-preferences")
    public ResponseEntity<Map<String, Object>> updateUiPreferences(Authentication authentication,
                                                                   @RequestBody Map<String, Object> payload) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("success", false, "error", "Не удалось определить пользователя."));
        }
        Map<String, Object> saved = uiPreferenceService.saveForUser(authentication, payload);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "preferences", saved
        ));
    }

    @PostMapping("/profile/password")
    public ResponseEntity<Map<String, Object>> updatePassword(Authentication authentication,
                                                              @RequestBody Map<String, Object> payload) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof PanelUserPrincipal principal)
                || principal.getUserId() <= 0 || !StringUtils.hasText(principal.getUsername())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("success", false, "error", "Не удалось определить пользователя."));
        }

        String currentPassword = rawPassword(payload.get("current_password"));
        String newPassword = rawPassword(payload.get("new_password"));
        String confirmPassword = rawPassword(payload.get("confirm_password"));

        if (!StringUtils.hasText(currentPassword)) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "error", "Укажите текущий пароль."));
        }
        if (!StringUtils.hasText(newPassword)) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "error", "Новый пароль не может быть пустым."));
        }
        String passwordError = com.example.panel.security.CredentialPasswordPolicy.error(newPassword);
        if (!passwordError.isEmpty()) return ResponseEntity.badRequest().body(Map.of("success", false, "field", "new_password", "error", passwordError));
        if (!newPassword.equals(confirmPassword)) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "error", "Новый пароль и подтверждение не совпадают."));
        }

        var rows = usersJdbcTemplate.queryForList(
            "SELECT * FROM users WHERE id = ?", principal.getUserId()
        );
        if (rows.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("success", false, "error", "Пользователь не найден."));
        }
        if (rows.size() != 1) return accountChanged();
        Map<String, Object> account = rows.get(0);
        if (!(account.get("id") instanceof Number id) || id.longValue() != principal.getUserId()
                || !principal.getUsername().equals(account.get("username"))
                || !booleanValue(account.get("enabled"), true) || booleanValue(account.get("is_blocked"), false)) {
            return accountChanged();
        }
        String storedPassword = resolveStoredCredential(account);
        if (!passwordEncoder.matches(currentPassword, storedPassword)) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "error", "Текущий пароль введён неверно."));
        }

        String hashed = passwordEncoder.encode(newPassword);
        boolean hasPasswordHash = account.containsKey("password_hash");
        StringBuilder sql = new StringBuilder("UPDATE users SET password = ?");
        List<Object> values = new ArrayList<>();
        values.add(hashed);
        if (hasPasswordHash) {
            sql.append(", password_hash = ?");
            values.add(hashed);
        }
        sql.append(" WHERE id = ? AND username = ? AND password IS NOT DISTINCT FROM ?");
        values.add(principal.getUserId());
        values.add(principal.getUsername());
        values.add(account.get("password"));
        // Compare the snapshot used to verify the current password; a concurrent change must win.
        for (String column : List.of("password_hash", "enabled", "is_blocked")) {
            if (account.containsKey(column)) {
                sql.append(" AND ").append(column).append(" IS NOT DISTINCT FROM ?");
                values.add(account.get(column));
            }
        }
        if (usersJdbcTemplate.update(sql.toString(), values.toArray()) != 1) return accountChanged();

        return ResponseEntity.ok(Map.of("success", true, "message", "Пароль успешно обновлён."));
    }

    private String rawPassword(Object value) {
        return value instanceof String text ? text : "";
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String resolveStoredCredential(Map<String, Object> row) {
        String password = stringValue(row.get("password"));
        if (StringUtils.hasText(password)) {
            return password;
        }
        return stringValue(row.get("password_hash"));
    }

    private ResponseEntity<Map<String, Object>> accountChanged() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("success", false, "error", "Учётная запись изменилась. Повторите вход."));
    }

    private boolean booleanValue(Object raw, boolean defaultValue) {
        if (raw == null) return defaultValue;
        if (raw instanceof Boolean value) return value;
        if (raw instanceof Number value) return value.intValue() != 0;
        String text = raw.toString().trim();
        return text.isEmpty() ? defaultValue : "1".equals(text) || "true".equalsIgnoreCase(text);
    }
}
