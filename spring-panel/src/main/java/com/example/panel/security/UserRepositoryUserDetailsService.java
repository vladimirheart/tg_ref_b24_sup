package com.example.panel.security;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

class UserRepositoryUserDetailsService implements UserDetailsService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    UserRepositoryUserDetailsService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        try {
            Map<String, Object> userRow = jdbcTemplate.queryForMap(
                    "SELECT id, username, password, enabled FROM users WHERE lower(username) = lower(?)",
                    username
            );
            List<GrantedAuthority> authorities = loadAuthorities((Long) userRow.get("id"));
            return User.withUsername((String) userRow.get("username"))
                    .password((String) userRow.get("password"))
                    .authorities(authorities)
                    .disabled(!Boolean.TRUE.equals(userRow.get("enabled")))
                    .build();
        } catch (EmptyResultDataAccessException ex) {
            throw new UsernameNotFoundException("User not found", ex);
        }
    }

    private List<GrantedAuthority> loadAuthorities(Long userId) {
        RowMapper<GrantedAuthority> mapper = (rs, rowNum) -> new SimpleGrantedAuthority(rs.getString("authority"));
        List<GrantedAuthority> authorities = jdbcTemplate.query(
                "SELECT authority FROM user_authorities WHERE user_id = ?",
                mapper,
                userId
        );
        if (authorities.isEmpty()) {
            authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        }
        return authorities;
    }

    void ensureDefaultAdmin(String username, String rawPassword) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        if (count != null && count > 0) {
            return;
        }
        String hashed = passwordEncoder.encode(rawPassword);
        jdbcTemplate.update("INSERT INTO users(username, password, enabled) VALUES (?, ?, ?)", username, hashed, true);
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
        if (userId != null) {
            jdbcTemplate.update("INSERT INTO user_authorities(user_id, authority) VALUES (?, ?)", userId, "ROLE_ADMIN");
            jdbcTemplate.update("INSERT INTO user_authorities(user_id, authority) VALUES (?, ?)", userId, "PAGE_DIALOGS");
            jdbcTemplate.update("INSERT INTO user_authorities(user_id, authority) VALUES (?, ?)", userId, "PAGE_ANALYTICS");
            jdbcTemplate.update("INSERT INTO user_authorities(user_id, authority) VALUES (?, ?)", userId, "PAGE_KNOWLEDGE_BASE");
            jdbcTemplate.update("INSERT INTO user_authorities(user_id, authority) VALUES (?, ?)", userId, "PAGE_CLIENTS");
        }
    }
}
