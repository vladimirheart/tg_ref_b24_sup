package com.example.supportbot.service;

import com.example.supportbot.entity.Channel;
import com.example.supportbot.entity.TicketActive;
import com.example.supportbot.entity.Ticket;
import com.example.supportbot.repository.TicketActiveRepository;
import com.example.supportbot.repository.TicketRepository;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PublicFormConversationLinkService {

    private final JdbcTemplate jdbcTemplate;
    private final TicketRepository ticketRepository;
    private final TicketActiveRepository ticketActiveRepository;
    private final ChatHistoryService chatHistoryService;

    public PublicFormConversationLinkService(JdbcTemplate jdbcTemplate,
                                             TicketRepository ticketRepository,
                                             TicketActiveRepository ticketActiveRepository,
                                             ChatHistoryService chatHistoryService) {
        this.jdbcTemplate = jdbcTemplate;
        this.ticketRepository = ticketRepository;
        this.ticketActiveRepository = ticketActiveRepository;
        this.chatHistoryService = chatHistoryService;
    }

    @Transactional
    public LinkResult bindSessionToChannel(String token, Long userId, String username, Channel channel) {
        if (!StringUtils.hasText(token)) {
            return LinkResult.error("Укажите токен продолжения диалога.");
        }
        if (userId == null || channel == null || channel.getId() == null) {
            return LinkResult.error("Не удалось определить канал продолжения.");
        }
        Optional<SessionRow> sessionOpt = loadSessionByToken(token.trim());
        if (sessionOpt.isEmpty()) {
            return LinkResult.error("Токен внешней формы не найден или уже недействителен.");
        }
        SessionRow session = sessionOpt.get();
        Optional<Ticket> ticketOpt = ticketRepository.findByIdTicketId(session.ticketId());
        if (ticketOpt.isEmpty()) {
            return LinkResult.error("Диалог по токену не найден.");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String identity = StringUtils.hasText(username) ? username.trim() : userId.toString();

        jdbcTemplate.update("""
                UPDATE messages
                   SET user_id = ?,
                       username = ?,
                       channel_id = ?,
                       updated_at = ?,
                       updated_by = ?
                 WHERE ticket_id = ?
                """,
                userId,
                identity,
                channel.getId(),
                Timestamp.from(now.toInstant()),
                "public_form_continue",
                session.ticketId()
        );

        jdbcTemplate.update("""
                UPDATE web_form_sessions
                   SET user_id = ?,
                       username = ?,
                       last_active_at = ?
                 WHERE id = ?
                """,
                userId,
                identity,
                Timestamp.from(now.toInstant()),
                session.id()
        );

        Ticket ticket = ticketOpt.get();
        ticket.setChannel(channel);
        ticketRepository.save(ticket);

        TicketActive active = ticketActiveRepository.findById(session.ticketId()).orElseGet(() -> {
            TicketActive created = new TicketActive();
            created.setTicketId(session.ticketId());
            return created;
        });
        active.setUser(identity);
        active.setLastSeen(now);
        ticketActiveRepository.save(active);

        chatHistoryService.storeSystemEvent(userId, session.ticketId(), channel,
                "Клиент продолжил диалог через " + normalizePlatformLabel(channel.getPlatform()) + ".");

        boolean closed = isClosedStatus(ticket.getStatus());
        return LinkResult.success(session.ticketId(), closed, session.clientName());
    }

    private Optional<SessionRow> loadSessionByToken(String token) {
        return jdbcTemplate.query("""
                        SELECT id, ticket_id, user_id, client_name
                          FROM web_form_sessions
                         WHERE token = ?
                         LIMIT 1
                        """,
                (rs, rowNum) -> new SessionRow(
                        rs.getLong("id"),
                        rs.getString("ticket_id"),
                        rs.getLong("user_id"),
                        rs.getString("client_name")
                ),
                token
        ).stream().findFirst();
    }

    private boolean isClosedStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        String normalized = status.trim().toLowerCase();
        return "closed".equals(normalized) || "resolved".equals(normalized);
    }

    private String normalizePlatformLabel(String platform) {
        String normalized = platform == null ? "" : platform.trim().toLowerCase();
        return switch (normalized) {
            case "vk" -> "VK";
            case "max" -> "MAX";
            default -> "Telegram";
        };
    }

    public record LinkResult(boolean success, String error, String ticketId, boolean closed, String clientName) {
        public static LinkResult success(String ticketId, boolean closed, String clientName) {
            return new LinkResult(true, null, ticketId, closed, clientName);
        }

        public static LinkResult error(String error) {
            return new LinkResult(false, error, null, false, null);
        }
    }

    private record SessionRow(Long id, String ticketId, Long userId, String clientName) {
    }
}
