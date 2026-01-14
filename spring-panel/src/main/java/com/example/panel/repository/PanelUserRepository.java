package com.example.panel.repository;

import com.example.panel.entity.PanelUser;
import com.example.panel.entity.Role;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class PanelUserRepository {

    private final JdbcTemplate usersJdbcTemplate;

    public PanelUserRepository(JdbcTemplate usersJdbcTemplate) {
        this.usersJdbcTemplate = usersJdbcTemplate;
    }

    public List<PanelUser> findAll() {
        return usersJdbcTemplate.query("select * from users", this::mapRow);
    }

    public Optional<PanelUser> findByUsernameIgnoreCase(String username) {
        if (!StringUtils.hasText(username)) {
            return Optional.empty();
        }
        List<PanelUser> result = usersJdbcTemplate.query(
                "select * from users where lower(username) = lower(?) limit 1",
                this::mapRow,
                username
        );
        return result.stream().findFirst();
    }

    private PanelUser mapRow(ResultSet rs, int rowNum) throws SQLException {
        PanelUser user = new PanelUser();
        user.setId(rs.getLong("id"));
        user.setUsername(rs.getString("username"));
        user.setPassword(getStringSafe(rs, "password"));
        user.setPasswordHash(getStringSafe(rs, "password_hash"));
        user.setRole(getStringSafe(rs, "role"));

        Long roleId = getNumberSafe(rs, "role_id", Long.class);
        if (roleId != null) {
            Role roleRef = new Role();
            roleRef.setId(roleId);
            user.setRoleRef(roleRef);
        }

        user.setPhoto(getStringSafe(rs, "photo"));
        user.setRegistrationDate(parseOffsetDateTime(getStringSafe(rs, "registration_date")));
        user.setBirthDate(parseLocalDate(getStringSafe(rs, "birth_date")));
        user.setEmail(getStringSafe(rs, "email"));
        user.setDepartment(getStringSafe(rs, "department"));
        user.setPhones(getStringSafe(rs, "phones"));
        user.setFullName(getStringSafe(rs, "full_name"));
        Integer blocked = getNumberSafe(rs, "is_blocked", Integer.class);
        user.setBlocked(blocked != null && blocked == 1);
        return user;
    }

    private boolean hasColumn(ResultSet rs, String column) {
        try {
            rs.findColumn(column);
            return true;
        } catch (SQLException ex) {
            return false;
        }
    }

    private String getStringSafe(ResultSet rs, String column) throws SQLException {
        return hasColumn(rs, column) ? rs.getString(column) : null;
    }

    private <T extends Number> T getNumberSafe(ResultSet rs, String column, Class<T> type) throws SQLException {
        if (!hasColumn(rs, column)) {
            return null;
        }
        Object value = rs.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return coerceNumber(number, type);
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                if (Long.class.equals(type)) {
                    return type.cast(Long.parseLong(text));
                }
                if (Integer.class.equals(type)) {
                    return type.cast(Integer.parseInt(text));
                }
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private <T extends Number> T coerceNumber(Number number, Class<T> type) {
        if (Long.class.equals(type)) {
            return type.cast(number.longValue());
        }
        if (Integer.class.equals(type)) {
            return type.cast(number.intValue());
        }
        return null;
    }

    private OffsetDateTime parseOffsetDateTime(String value) {
        try {
            return StringUtils.hasText(value) ? OffsetDateTime.parse(value) : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private LocalDate parseLocalDate(String value) {
        try {
            return StringUtils.hasText(value) ? LocalDate.parse(value) : null;
        } catch (Exception ex) {
            return null;
        }
    }
}
