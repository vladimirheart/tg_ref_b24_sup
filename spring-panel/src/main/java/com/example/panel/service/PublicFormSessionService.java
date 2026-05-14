package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.model.publicform.PublicFormSessionDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Service
public class PublicFormSessionService {

    private final JdbcTemplate jdbcTemplate;
    private final PublicFormRuntimeConfigService runtimeConfigService;

    public PublicFormSessionService(JdbcTemplate jdbcTemplate,
                                    PublicFormRuntimeConfigService runtimeConfigService) {
        this.jdbcTemplate = jdbcTemplate;
        this.runtimeConfigService = runtimeConfigService;
    }

    @Transactional(readOnly = true)
    public Optional<PublicFormSessionDto> findSession(Channel channel, String token) {
        if (channel == null || !StringUtils.hasText(token)) {
            return Optional.empty();
        }
        return loadSessionRow(token, channel.getId())
                .filter(this::isSessionActive)
                .map(session -> {
                    PublicSessionRow persistedSession = maybeRotateSessionToken(session);
                    return new PublicFormSessionDto(
                            persistedSession.token(),
                            persistedSession.ticketId(),
                            channel.getId(),
                            channel.getPublicId(),
                            persistedSession.clientName(),
                            persistedSession.clientContact(),
                            persistedSession.username(),
                            persistedSession.createdAt()
                    );
                });
    }

    private PublicSessionRow maybeRotateSessionToken(PublicSessionRow session) {
        if (!runtimeConfigService.readDialogConfigBoolean("public_form_session_token_rotate_on_read", false)) {
            return session;
        }
        String nextToken = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update("""
                UPDATE web_form_sessions
                   SET token = ?, last_active_at = ?
                 WHERE id = ?
                """,
                nextToken,
                Timestamp.from(now.toInstant()),
                session.id()
        );
        return new PublicSessionRow(
                session.id(),
                nextToken,
                session.ticketId(),
                session.channelId(),
                session.userId(),
                session.clientName(),
                session.clientContact(),
                session.username(),
                session.createdAt(),
                now
        );
    }

    private boolean isSessionActive(PublicSessionRow session) {
        int ttlHours = runtimeConfigService.readDialogConfigInt("public_form_session_ttl_hours", 72, 1, 24 * 30);
        OffsetDateTime createdAt = session.createdAt();
        if (createdAt == null) {
            return true;
        }
        return createdAt.plusHours(ttlHours).isAfter(OffsetDateTime.now(ZoneOffset.UTC));
    }

    private Optional<PublicSessionRow> loadSessionRow(String token, Long channelId) {
        if (!StringUtils.hasText(token) || channelId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                        SELECT id, token, ticket_id, channel_id, user_id, client_name, client_contact, username, created_at, last_active_at
                          FROM web_form_sessions
                         WHERE token = ? AND channel_id = ?
                         LIMIT 1
                        """,
                (rs, rowNum) -> new PublicSessionRow(
                        rs.getLong("id"),
                        rs.getString("token"),
                        rs.getString("ticket_id"),
                        rs.getLong("channel_id"),
                        rs.getLong("user_id"),
                        rs.getString("client_name"),
                        rs.getString("client_contact"),
                        rs.getString("username"),
                        parseOffsetDateTimeValue(rs.getObject("created_at")),
                        parseOffsetDateTimeValue(rs.getObject("last_active_at"))
                ),
                token,
                channelId
        ).stream().findFirst();
    }

    private OffsetDateTime parseOffsetDateTimeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.withOffsetSameInstant(ZoneOffset.UTC);
        }
        if (value instanceof Timestamp timestamp) {
            return OffsetDateTime.ofInstant(timestamp.toInstant(), ZoneOffset.UTC);
        }
        if (value instanceof java.util.Date date) {
            return OffsetDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC);
        }
        if (value instanceof Number number) {
            return OffsetDateTime.ofInstant(Instant.ofEpochMilli(number.longValue()), ZoneOffset.UTC);
        }
        String raw = value.toString().trim();
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw).withOffsetSameInstant(ZoneOffset.UTC);
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(raw.replace(' ', 'T')).atOffset(ZoneOffset.UTC);
        } catch (Exception ignored) {
        }
        try {
            return OffsetDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(raw)), ZoneOffset.UTC);
        } catch (Exception ignored) {
        }
        return null;
    }

    private record PublicSessionRow(Long id,
                                    String token,
                                    String ticketId,
                                    Long channelId,
                                    Long userId,
                                    String clientName,
                                    String clientContact,
                                    String username,
                                    OffsetDateTime createdAt,
                                    OffsetDateTime lastActiveAt) {
    }
}
