package com.example.panel.controller;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import javax.sql.DataSource;
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

    public ProfileApiController(@Qualifier("usersJdbcTemplate") JdbcTemplate usersJdbcTemplate,
                                PasswordEncoder passwordEncoder) {
        this.usersJdbcTemplate = usersJdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/profile/password")
    public ResponseEntity<Map<String, Object>> updatePassword(Authentication authentication,
                                                              @RequestBody Map<String, Object> payload) {
        if (authentication == null || !StringUtils.hasText(authentication.getName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("success", false, "error", "Не удалось определить пользователя."));
        }

        String currentPassword = stringValue(payload.get("current_password"));
        String newPassword = stringValue(payload.get("new_password"));
        String confirmPassword = stringValue(payload.get("confirm_password"));

        if (!StringUtils.hasText(currentPassword)) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "error", "Укажите текущий пароль."));
        }
        if (!StringUtils.hasText(newPassword)) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "error", "Новый пароль не может быть пустым."));
        }
        if (!newPassword.equals(confirmPassword)) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "error", "Новый пароль и подтверждение не совпадают."));
        }

        String username = authentication.getName();
        var rows = usersJdbcTemplate.queryForList(
            "SELECT password FROM users WHERE lower(username) = lower(?)",
            username
        );
        if (rows.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("success", false, "error", "Пользователь не найден."));
        }
        String storedPassword = rows.get(0).get("password") != null
            ? rows.get(0).get("password").toString()
            : "";
        if (!passwordEncoder.matches(currentPassword, storedPassword)) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "error", "Текущий пароль введён неверно."));
        }

        String hashed = passwordEncoder.encode(newPassword);
        boolean hasPasswordHash = hasColumn("users", "password_hash");
        if (hasPasswordHash) {
            usersJdbcTemplate.update(
                "UPDATE users SET password = ?, password_hash = ? WHERE lower(username) = lower(?)",
                hashed, hashed, username
            );
        } else {
            usersJdbcTemplate.update(
                "UPDATE users SET password = ? WHERE lower(username) = lower(?)",
                hashed, username
            );
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Пароль успешно обновлён."));
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private boolean hasColumn(String tableName, String columnName) {
        DataSource dataSource = usersJdbcTemplate.getDataSource();
        if (dataSource == null) {
            return false;
        }
        return hasColumn(dataSource, tableName, columnName)
            || hasColumn(dataSource, tableName.toLowerCase(Locale.ROOT), columnName.toLowerCase(Locale.ROOT))
            || hasColumn(dataSource, tableName.toUpperCase(Locale.ROOT), columnName.toUpperCase(Locale.ROOT));
    }

    private boolean hasColumn(DataSource dataSource, String tableName, String columnName) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getColumns(connection.getCatalog(), null, tableName, columnName)) {
                return rs.next();
            }
        } catch (SQLException ex) {
            return false;
        }
    }
}
