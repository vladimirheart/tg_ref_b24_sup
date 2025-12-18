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
        user.setPassword(rs.getString("password"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setRole(rs.getString("role"));

        Long roleId = rs.getObject("role_id", Long.class);
        if (roleId != null) {
            Role roleRef = new Role();
            roleRef.setId(roleId);
            user.setRoleRef(roleRef);
        }

        user.setPhoto(rs.getString("photo"));
        user.setRegistrationDate(parseOffsetDateTime(rs.getString("registration_date")));
        user.setBirthDate(parseLocalDate(rs.getString("birth_date")));
        user.setEmail(rs.getString("email"));
        user.setDepartment(rs.getString("department"));
        user.setPhones(rs.getString("phones"));
        user.setFullName(rs.getString("full_name"));
        Integer blocked = rs.getObject("is_blocked", Integer.class);
        user.setBlocked(blocked != null && blocked == 1);
        return user;
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
