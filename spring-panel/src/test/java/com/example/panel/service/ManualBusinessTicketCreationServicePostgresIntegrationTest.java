package com.example.panel.service;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ManualBusinessTicketCreationServicePostgresIntegrationTest {

    private static final Path FOUNDATION = Path.of(
            "src/main/resources/db/migration/postgresql/V51__multibusiness_foundation.sql");
    private static final Path MANUAL_CLIENTS = Path.of(
            "src/main/resources/db/migration/postgresql/V54__manual_business_clients.sql");

    @Test
    void createsDistinctLocalClientsAndBusinessOwnedTicketsWithoutChannels() throws Exception {
        JdbcTemplate jdbc = freshSchema("manual_ticket_create");
        long sushi = businessId(jdbc, "sushi");
        long bliny = businessId(jdbc, "bliny");
        jdbc.update("INSERT INTO business_memberships(user_id, business_id, role_code) VALUES (1, ?, 'OPERATOR')", sushi);
        jdbc.update("INSERT INTO business_memberships(user_id, business_id, role_code) VALUES (1, ?, 'OPERATOR')", bliny);
        jdbc.update("""
                INSERT INTO business_locations(business_id, location_name, normalized_location_name,
                                               location_type, city)
                VALUES (?, 'Центр', 'центр', 'кафе', 'Москва')
                """, sushi);
        long locationId = jdbc.queryForObject("SELECT id FROM business_locations", Long.class);
        ManualBusinessTicketCreationService service = new ManualBusinessTicketCreationService(
                jdbc, new BusinessAccessService(jdbc));

        var first = service.create(1L, sushi, "  Анна  ", "  Нужна помощь\nСрочно  ", locationId);
        var second = service.create(1L, sushi, "Борис", "Второе обращение", null);
        var third = service.create(1L, bliny, "Анна", "Другое направление", null);

        assertThat(first.clientUserId()).isNegative().isNotEqualTo(second.clientUserId());
        assertThat(second.clientUserId()).isNotEqualTo(third.clientUserId());
        assertThat(first.ticketId()).startsWith("manual-");
        assertThat(first.ticketPublicId()).matches("[0-9a-f]{32}");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM channels", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM manual_business_clients", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT display_name FROM manual_business_clients WHERE user_id = ?",
                String.class, first.clientUserId())).isEqualTo("Анна");
        assertThat(jdbc.queryForObject("SELECT business_id FROM tickets WHERE user_id = ? AND ticket_id = ?",
                Long.class, first.clientUserId(), first.ticketId())).isEqualTo(sushi);
        assertThat(jdbc.queryForObject("SELECT group_msg_id FROM tickets WHERE user_id = ? AND ticket_id = ?",
                Long.class, first.clientUserId(), first.ticketId())).isEqualTo(first.clientUserId());
        assertThat(jdbc.queryForObject("SELECT channel_id FROM tickets WHERE ticket_id = ?",
                Long.class, first.ticketId())).isNull();
        assertThat(jdbc.queryForObject("SELECT location_name FROM messages WHERE ticket_id = ?",
                String.class, first.ticketId())).isEqualTo("Центр");
        assertThat(jdbc.queryForObject("SELECT problem FROM messages WHERE ticket_id = ?",
                String.class, first.ticketId())).isEqualTo("Нужна помощь\nСрочно");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ticket_spans WHERE ticket_id = ?",
                Integer.class, first.ticketId())).isEqualTo(1);
        assertThat(new TicketLocatorService(jdbc).findByCompositeKey(first.clientUserId(), first.ticketId()))
                .contains(new TicketLocatorService.TicketReference(
                        first.clientUserId(), first.ticketId(), first.ticketPublicId(), sushi));

        BusinessScopedTicketReadService read = new BusinessScopedTicketReadService(
                jdbc, new BusinessAccessService(jdbc));
        assertThat(read.listTickets(1L, new BusinessAccessService.OneBusiness(sushi), 50, 0))
                .extracting(BusinessScopedTicketReadService.TicketRow::ticketId)
                .containsExactlyInAnyOrder(first.ticketId(), second.ticketId());
        assertThat(read.listTickets(1L, new BusinessAccessService.OneBusiness(bliny), 50, 0))
                .extracting(BusinessScopedTicketReadService.TicketRow::ticketId)
                .containsExactly(third.ticketId());
    }

    @Test
    void rejectsViewerForeignLocationAndInvalidInputBeforeWriting() throws Exception {
        JdbcTemplate jdbc = freshSchema("manual_ticket_reject");
        long sushi = businessId(jdbc, "sushi");
        long bliny = businessId(jdbc, "bliny");
        jdbc.update("INSERT INTO business_memberships(user_id, business_id, role_code) VALUES (1, ?, 'VIEWER')", sushi);
        jdbc.update("INSERT INTO business_memberships(user_id, business_id, role_code) VALUES (2, ?, 'OPERATOR')", sushi);
        jdbc.update("""
                INSERT INTO business_locations(business_id, location_name, normalized_location_name)
                VALUES (?, 'Чужая', 'чужая')
                """, bliny);
        long foreignLocation = jdbc.queryForObject("SELECT id FROM business_locations", Long.class);
        ManualBusinessTicketCreationService service = new ManualBusinessTicketCreationService(
                jdbc, new BusinessAccessService(jdbc));

        assertThatThrownBy(() -> service.create(1L, sushi, "Клиент", "Проблема", null))
                .isInstanceOf(AccessDeniedException.class);
        ManualBusinessTicketCreationService staleAccessService = new ManualBusinessTicketCreationService(
                jdbc, mock(BusinessAccessService.class));
        assertThatThrownBy(() -> staleAccessService.create(1L, sushi, "Клиент", "Проблема", null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.create(2L, sushi, "Клиент", "Проблема", foreignLocation))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(2L, sushi, " ", "Проблема", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM manual_business_clients", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tickets", Integer.class)).isZero();
    }

    private JdbcTemplate freshSchema(String prefix) throws Exception {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType(prefix);
        jdbc.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, username TEXT NOT NULL UNIQUE)");
        jdbc.execute("CREATE TABLE channels (id BIGINT PRIMARY KEY)");
        jdbc.execute("""
                CREATE TABLE tickets (
                    user_id BIGINT NOT NULL, ticket_id TEXT NOT NULL, group_msg_id BIGINT,
                    ticket_public_id VARCHAR(32), status TEXT, channel_id BIGINT REFERENCES channels(id),
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    reopen_count INTEGER, closed_count INTEGER, work_time_total_sec BIGINT,
                    last_reopen_at TIMESTAMP WITH TIME ZONE,
                    resolved_at TIMESTAMP WITH TIME ZONE, resolved_by TEXT,
                    PRIMARY KEY (user_id, ticket_id), UNIQUE (ticket_public_id)
                )
                """);
        jdbc.execute("""
                CREATE TABLE messages (
                    group_msg_id BIGINT PRIMARY KEY, user_id BIGINT, ticket_id TEXT UNIQUE,
                    client_name TEXT, problem TEXT, location_type TEXT, city TEXT,
                    location_name TEXT, created_at TIMESTAMP WITH TIME ZONE,
                    created_date TEXT, created_time TEXT
                )
                """);
        jdbc.execute("CREATE TABLE bot_users (user_id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE ticket_spans (ticket_id TEXT NOT NULL, span_no INTEGER NOT NULL, "
                + "started_at TIMESTAMP WITH TIME ZONE NOT NULL, UNIQUE (ticket_id, span_no))");
        jdbc.update("INSERT INTO users(id, username) VALUES (1, 'viewer'), (2, 'operator')");
        for (Path migration : new Path[] {FOUNDATION, MANUAL_CLIENTS}) {
            for (String statement : Files.readString(migration).replace("\r\n", "\n").split(";")) {
                if (!statement.isBlank()) {
                    jdbc.execute(statement.trim());
                }
            }
        }
        return jdbc;
    }

    private long businessId(JdbcTemplate jdbc, String code) {
        return jdbc.queryForObject("SELECT id FROM businesses WHERE code = ?", Long.class, code);
    }
}
