package com.example.panel.repository;

import com.example.panel.entity.BotUser;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Repository
public class BotUserRepository {

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    };

    private final JdbcTemplate botJdbcTemplate;

    public BotUserRepository(@Qualifier("botJdbcTemplate") JdbcTemplate botJdbcTemplate) {
        this.botJdbcTemplate = botJdbcTemplate;
    }

    public List<BotUser> findAll() {
        return botJdbcTemplate.query("select * from bot_users", this::mapRow);
    }

    private BotUser mapRow(ResultSet rs, int rowNum) throws SQLException {
        BotUser user = new BotUser();
        user.setUserId(rs.getLong("user_id"));
        user.setUsername(rs.getString("username"));
        user.setFirstName(rs.getString("first_name"));
        user.setLastName(rs.getString("last_name"));
        user.setRegisteredAt(parseOffsetDateTime(rs.getString("registered_at")));
        return user;
    }

    private OffsetDateTime parseOffsetDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception ignored) {
        }
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                LocalDateTime local = LocalDateTime.parse(value, formatter);
                return local.atOffset(ZoneOffset.UTC);
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
