package com.example.panel.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class UserRepositoryUserDetailsService implements UserDetailsService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final Map<String, String> PAGE_PERMISSION_TO_AUTHORITY = Map.ofEntries(
            Map.entry("dialogs", "PAGE_DIALOGS"),
            Map.entry("tasks", "PAGE_TASKS"),
            Map.entry("clients", "PAGE_CLIENTS"),
            Map.entry("object_passports", "PAGE_OBJECT_PASSPORTS"),
            Map.entry("knowledge_base", "PAGE_KNOWLEDGE_BASE"),
            Map.entry("dashboard", "PAGE_ANALYTICS"),
            Map.entry("analytics", "PAGE_ANALYTICS"),
            Map.entry("channels", "PAGE_CHANNELS"),
            Map.entry("settings", "PAGE_SETTINGS"),
            Map.entry("user_management", "PAGE_USERS")
    );

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    UserRepositoryUserDetailsService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    javax.sql.DataSource getDataSource() {
        return jdbcTemplate != null ? jdbcTemplate.getDataSource() : null;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (jdbcTemplate == null || jdbcTemplate.getDataSource() == null) {
            throw new UsernameNotFoundException("User store is not configured");
        }
        try {
            Map<String, Object> userRow = jdbcTemplate.queryForMap(
                    "SELECT id, username, password, enabled FROM users WHERE lower(username) = lower(?)",
                    username
            );

            // SQLite может вернуть Integer, Long, BigInteger — приводим через Number
            Number idNumber = (Number) userRow.get("id");
            Long userId = idNumber != null ? idNumber.longValue() : null;

            // enabled может быть и BOOLEAN, и INTEGER (0/1) — аккуратно разбираем
            Object enabledObj = userRow.get("enabled");
            boolean enabled = false;
            if (enabledObj instanceof Boolean b) {
                enabled = b;
            } else if (enabledObj instanceof Number n) {
                enabled = n.intValue() != 0;
            }

            List<GrantedAuthority> authorities = loadAuthorities(userId);

            return User.withUsername((String) userRow.get("username"))
                    .password((String) userRow.get("password"))
                    .authorities(authorities)
                    .disabled(!enabled)
                    .build();
        } catch (EmptyResultDataAccessException ex) {
            throw new UsernameNotFoundException("User not found", ex);
        }
    }

    private List<GrantedAuthority> loadAuthorities(Long userId) {
        Set<String> authorityNames = new LinkedHashSet<>();
        authorityNames.addAll(loadDirectAuthorities(userId));
        authorityNames.addAll(loadRoleAuthorities(userId));

        if (authorityNames.isEmpty()) {
            authorityNames.add("ROLE_USER");
        }
        return authorityNames.stream()
                .map(authority -> (GrantedAuthority) new SimpleGrantedAuthority(authority))
                .toList();
    }

    private Set<String> loadDirectAuthorities(Long userId) {
        try {
            return new LinkedHashSet<>(jdbcTemplate.query(
                    "SELECT authority FROM user_authorities WHERE user_id = ?",
                    (rs, rowNum) -> rs.getString("authority"),
                    userId
            ));
        } catch (DataAccessException ex) {
            return Set.of();
        }
    }

    private Set<String> loadRoleAuthorities(Long userId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT r.name AS role_name, r.permissions AS permissions " +
                            "FROM users u LEFT JOIN roles r ON r.id = u.role_id WHERE u.id = ? LIMIT 1",
                    userId
            );
            if (rows.isEmpty()) {
                return Set.of();
            }
            Map<String, Object> row = rows.get(0);
            Set<String> result = new LinkedHashSet<>(mapPagePermissionsToAuthorities(extractPagePermissions(row.get("permissions"))));
            String roleName = stringValue(row.get("role_name"));
            if ("admin".equalsIgnoreCase(roleName)) {
                result.add("ROLE_ADMIN");
            }
            if (isPortalAdminRole(roleName)) {
                result.add("ROLE_PORTAL_ADMIN");
            }
            return result;
        } catch (DataAccessException ex) {
            return Set.of();
        }
    }

    private boolean isPortalAdminRole(String roleName) {
        if (roleName == null) {
            return false;
        }
        String normalized = roleName.trim().toLowerCase();
        return normalized.equals("portal_admin")
                || normalized.equals("portal admin")
                || normalized.equals("администратор портала");
    }

    static Set<String> mapPagePermissionsToAuthorities(List<String> pages) {
        if (pages == null || pages.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        boolean wildcard = pages.stream().anyMatch(value -> "*".equals(value));
        if (wildcard) {
            result.addAll(PAGE_PERMISSION_TO_AUTHORITY.values());
            return result;
        }
        for (String page : pages) {
            String authority = PAGE_PERMISSION_TO_AUTHORITY.get(page);
            if (authority != null) {
                result.add(authority);
            }
        }
        return result;
    }

    static List<String> extractPagePermissions(Object rawPermissions) {
        if (rawPermissions == null) {
            return List.of();
        }
        String json = rawPermissions.toString().trim();
        if (json.isEmpty()) {
            return List.of();
        }
        try {
            Map<String, Object> permissions = OBJECT_MAPPER.readValue(json, MAP_TYPE);
            Object pagesObj = permissions.get("pages");
            if (!(pagesObj instanceof List<?> pages)) {
                return List.of();
            }
            return pages.stream()
                    .filter(value -> value != null && !value.toString().trim().isEmpty())
                    .map(Object::toString)
                    .toList();
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    void ensureDefaultAdmin(String username, String rawPassword) {
        if (jdbcTemplate == null || jdbcTemplate.getDataSource() == null) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        if (count != null && count > 0) {
            return;
        }

        String hashed = passwordEncoder.encode(rawPassword);
        jdbcTemplate.update(
                "INSERT INTO users(username, password, enabled) VALUES (?, ?, ?)",
                username, hashed, true
        );

        // Тут тоже аккуратно читаем id как Number
        Number idNumber = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?",
                Number.class,
                username
        );

        if (idNumber != null) {
            Long userId = idNumber.longValue();
            jdbcTemplate.update("INSERT INTO user_authorities(user_id, authority) VALUES (?, ?)", userId, "ROLE_ADMIN");
            jdbcTemplate.update("INSERT INTO user_authorities(user_id, authority) VALUES (?, ?)", userId, "PAGE_DIALOGS");
            jdbcTemplate.update("INSERT INTO user_authorities(user_id, authority) VALUES (?, ?)", userId, "PAGE_ANALYTICS");
            jdbcTemplate.update("INSERT INTO user_authorities(user_id, authority) VALUES (?, ?)", userId, "PAGE_KNOWLEDGE_BASE");
            jdbcTemplate.update("INSERT INTO user_authorities(user_id, authority) VALUES (?, ?)", userId, "PAGE_CLIENTS");
            jdbcTemplate.update("INSERT INTO user_authorities(user_id, authority) VALUES (?, ?)", userId, "PAGE_TASKS");
            jdbcTemplate.update("INSERT INTO user_authorities(user_id, authority) VALUES (?, ?)", userId, "PAGE_CHANNELS");
            jdbcTemplate.update("INSERT INTO user_authorities(user_id, authority) VALUES (?, ?)", userId, "PAGE_USERS");
            jdbcTemplate.update("INSERT INTO user_authorities(user_id, authority) VALUES (?, ?)", userId, "PAGE_SETTINGS");
            jdbcTemplate.update("INSERT INTO user_authorities(user_id, authority) VALUES (?, ?)", userId, "PAGE_OBJECT_PASSPORTS");
        }
    }
}
