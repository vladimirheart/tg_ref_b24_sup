package com.example.panel.service;

import com.example.panel.model.clients.ClientListItem;
import com.example.panel.model.clients.ClientProfile;
import com.example.panel.repository.ClientUsernameRepository;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientsServiceTest {

    private JdbcTemplate jdbcTemplate;
    private ClientsService service;
    private BlacklistHistoryService blacklistHistoryService;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:clients_service_" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        jdbcTemplate = new JdbcTemplate(dataSource);
        ClientUsernameRepository clientUsernameRepository = mock(ClientUsernameRepository.class);
        when(clientUsernameRepository.findByUserIdOrderBySeenAtDesc(55L)).thenReturn(List.of());
        blacklistHistoryService = mock(BlacklistHistoryService.class);
        when(blacklistHistoryService.historyTableExists()).thenReturn(false);
        service = new ClientsService(jdbcTemplate, clientUsernameRepository, blacklistHistoryService);
        createSchema();
    }

    @Test
    void loadClientsUsesLatestUsernameWithoutViolatingPostgresGroupByRules() {
        jdbcTemplate.update("INSERT INTO channels(id, channel_name) VALUES (?, ?)", 7L, "Telegram");
        jdbcTemplate.update(
                "INSERT INTO tickets(ticket_id, status, resolved_at) VALUES (?, ?, ?)",
                "T-100",
                "resolved",
                "2026-04-21T10:45:00Z"
        );
        jdbcTemplate.update(
                "INSERT INTO tickets(ticket_id, status, resolved_at) VALUES (?, ?, ?)",
                "T-101",
                "pending",
                null
        );
        jdbcTemplate.update(
                """
                INSERT INTO messages(ticket_id, user_id, username, client_name, channel_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                "T-100",
                55L,
                "old-client55",
                "Client 55",
                7L,
                "2026-04-21T09:00:00Z"
        );
        jdbcTemplate.update(
                """
                INSERT INTO messages(ticket_id, user_id, username, client_name, channel_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                "T-101",
                55L,
                "client55",
                "Client 55",
                7L,
                "2026-04-21T11:00:00Z"
        );
        jdbcTemplate.update(
                """
                INSERT INTO chat_history(ticket_id, sender, timestamp)
                VALUES (?, ?, ?)
                """,
                "T-100",
                "operator",
                "2026-04-21T09:15:00Z"
        );
        jdbcTemplate.update(
                "INSERT INTO client_blacklist(user_id, is_blacklisted, unblock_requested) VALUES (?, ?, ?)",
                "55",
                true,
                false
        );

        List<ClientListItem> clients = service.loadClients(null, null);

        assertThat(clients).hasSize(1);
        assertThat(clients.get(0).userId()).isEqualTo(55L);
        assertThat(clients.get(0).username()).isEqualTo("client55");
        assertThat(clients.get(0).ticketCount()).isEqualTo(2L);
        assertThat(clients.get(0).blacklisted()).isTrue();
        assertThat(clients.get(0).totalMinutes()).isEqualTo(90);
        assertThat(clients.get(0).formattedTime()).isEqualTo("1 ч 30 мин");
    }

    @Test
    void loadClientProfileReadsResolvedTicketDataInPostgresMode() {
        jdbcTemplate.update("INSERT INTO channels(id, channel_name, platform) VALUES (?, ?, ?)", 7L, "Telegram", "telegram");
        jdbcTemplate.update(
                "INSERT INTO tickets(ticket_id, status, resolved_at) VALUES (?, ?, ?)",
                "T-200",
                "resolved",
                "2026-04-22T11:30:00Z"
        );
        jdbcTemplate.update(
                """
                INSERT INTO messages(
                    ticket_id, user_id, username, client_name, channel_id,
                    business, city, location_type, location_name, problem, created_at, category, client_status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "T-200",
                55L,
                "client55",
                "Client 55",
                7L,
                "billing",
                "Moscow",
                "office",
                "HQ",
                "Problem",
                "2026-04-22T10:00:00Z",
                "billing",
                "vip"
        );

        Optional<ClientProfile> profile = service.loadClientProfile(55L);

        assertThat(profile).isPresent();
        assertThat(profile.orElseThrow().tickets()).hasSize(1);
        assertThat(profile.orElseThrow().tickets().get(0).ticketId()).isEqualTo("T-200");
        assertThat(profile.orElseThrow().tickets().get(0).resolvedAt()).isEqualTo("2026-04-22T11:30:00Z");
    }

    @Test
    void periodComparisonsDeduplicateTicketsAndCompareEquivalentPeriods() {
        var now = java.time.ZonedDateTime.of(2026, 9, 22, 12, 0, 0, 0, java.time.ZoneId.of("UTC"));
        List<com.example.panel.model.clients.ClientProfileTicket> tickets = List.of(
            trendTicket("W-C1", "2026-09-21T09:00:00Z", "Оплата"),
            trendTicket("W-C1", "2026-09-21T10:00:00Z", "Оплата"),
            trendTicket("W-C2", "2026-09-22T11:00:00Z", "Доставка"),
            trendTicket("W-P1", "2026-09-14T09:00:00Z", "Оплата"),
            trendTicket("W-P2", "2026-09-15T11:00:00Z", "Возврат"),
            trendTicket("M-C1", "2026-09-10T10:00:00Z", "Оплата"),
            trendTicket("M-P1", "2026-08-10T10:00:00Z", "Оплата"),
            trendTicket("M-P2", "2026-08-20T10:00:00Z", "Доставка"),
            trendTicket("Y-C1", "2026-02-10T10:00:00Z", "Прочее"),
            trendTicket("Y-P1", "2025-02-10T10:00:00Z", "Прочее")
        );

        var comparisons = service.buildPeriodComparisons(tickets, now);

        assertThat(comparisons).extracting(comparison -> comparison.key()).containsExactly("wow", "mom", "yoy");
        var wow = comparisons.get(0);
        assertThat(wow.currentCount()).isEqualTo(2);
        assertThat(wow.previousCount()).isEqualTo(2);
        assertThat(wow.deltaLabel()).isEqualTo("0%");
        assertThat(wow.types()).extracting(type -> type.label()).contains("Оплата", "Доставка", "Возврат");
        var mom = comparisons.get(1);
        assertThat(mom.currentCount()).isEqualTo(5);
        assertThat(mom.previousCount()).isEqualTo(2);
        var yoy = comparisons.get(2);
        assertThat(yoy.currentCount()).isEqualTo(8);
        assertThat(yoy.previousCount()).isEqualTo(1);
    }

    private com.example.panel.model.clients.ClientProfileTicket trendTicket(String ticketId, String createdAt, String category) {
        return new com.example.panel.model.clients.ClientProfileTicket(
            ticketId, ticketId, null, null, null, null, null, createdAt, null, null, category, null, null, null
        );
    }

    private void createSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE messages (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    ticket_id VARCHAR(120),
                    group_msg_id VARCHAR(120),
                    user_id BIGINT,
                    username VARCHAR(120),
                    client_name VARCHAR(120),
                    channel_id BIGINT,
                    business VARCHAR(120),
                    city VARCHAR(120),
                    location_type VARCHAR(120),
                    location_name VARCHAR(120),
                    problem VARCHAR(500),
                    created_at VARCHAR(64),
                    category VARCHAR(120),
                    client_status VARCHAR(120)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE tickets (
                    ticket_id VARCHAR(120) PRIMARY KEY,
                    status VARCHAR(32),
                    resolved_at VARCHAR(64)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE chat_history (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    ticket_id VARCHAR(120),
                    sender VARCHAR(120),
                    timestamp VARCHAR(64)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE channels (
                    id BIGINT PRIMARY KEY,
                    channel_name VARCHAR(120),
                    bot_name VARCHAR(120),
                    platform VARCHAR(32)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE client_blacklist (
                    user_id VARCHAR(120) PRIMARY KEY,
                    is_blacklisted BOOLEAN NOT NULL DEFAULT FALSE,
                    reason VARCHAR(255),
                    added_at VARCHAR(64),
                    added_by VARCHAR(120),
                    unblock_requested BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE client_statuses (
                    user_id BIGINT PRIMARY KEY,
                    status VARCHAR(120)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE web_form_sessions (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    ticket_id VARCHAR(120)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE feedbacks (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    user_id BIGINT,
                    rating INTEGER
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE client_phones (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    user_id BIGINT,
                    phone VARCHAR(120),
                    label VARCHAR(120),
                    source VARCHAR(32),
                    is_active BOOLEAN,
                    created_at VARCHAR(64),
                    created_by VARCHAR(120)
                )
                """);
    }
}
