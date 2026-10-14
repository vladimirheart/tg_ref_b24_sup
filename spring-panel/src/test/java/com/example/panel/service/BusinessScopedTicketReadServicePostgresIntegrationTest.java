package com.example.panel.service;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BusinessScopedTicketReadServicePostgresIntegrationTest {

    private static final Path FOUNDATION = Path.of(
            "src/main/resources/db/migration/postgresql/V51__multibusiness_foundation.sql");
    private static final Path LOCATOR = Path.of(
            "src/main/resources/db/migration/postgresql/V52__ticket_public_locator.sql");

    @Test
    void listsOnlyCanonicalOwnersWithinDirectOrAllGrantedScope() throws Exception {
        JdbcTemplate jdbc = freshSchema("business_scoped_ticket_read");
        jdbc.update("INSERT INTO users(id, username) VALUES (1, 'sushi-reader'), (2, 'all-reader'), (3, 'no-grant')");
        migrate(jdbc);
        long sushi = businessId(jdbc, "sushi");
        long bliny = businessId(jdbc, "bliny");
        jdbc.update("INSERT INTO businesses(code, display_name) VALUES ('autoservice', 'Автосервис')");
        long autoservice = businessId(jdbc, "autoservice");
        jdbc.update("INSERT INTO business_memberships(user_id, business_id, role_code) VALUES (1, ?, 'VIEWER')", sushi);
        jdbc.update("INSERT INTO business_access_grants(user_id, grant_scope, role_code) VALUES (2, 'ALL_BUSINESSES', 'VIEWER')");
        jdbc.update("INSERT INTO tickets(user_id, ticket_id, business_id, ticket_public_id) VALUES (1, 'same', ?, '0123456789abcdef0123456789abcdef')", sushi);
        jdbc.update("INSERT INTO tickets(user_id, ticket_id, business_id, ticket_public_id) VALUES (2, 'same', ?, 'fedcba9876543210fedcba9876543210')", bliny);
        jdbc.update("INSERT INTO tickets(user_id, ticket_id, business_id) VALUES (3, 'new-direction', ?)", autoservice);
        jdbc.update("INSERT INTO tickets(user_id, ticket_id) VALUES (3, 'unresolved')");

        BusinessScopedTicketReadService service = new BusinessScopedTicketReadService(
                jdbc, new BusinessAccessService(jdbc));
        assertThat(service.requireAuthenticatedUserId("SUSHI-READER")).isEqualTo(1L);
        assertThat(service.availableBusinesses(1L)).extracting(
                BusinessScopedTicketReadService.BusinessOption::code).containsExactly("sushi");
        assertThat(service.listTickets(1L, BusinessAccessService.AllAccessibleBusinesses.INSTANCE, 50, 0))
                .extracting(BusinessScopedTicketReadService.TicketRow::ticketPublicId)
                .containsExactly("0123456789abcdef0123456789abcdef");
        assertThat(service.findByPublicId(1L, "0123456789abcdef0123456789abcdef"))
                .get().extracting(BusinessScopedTicketReadService.TicketRow::businessId).isEqualTo(sushi);
        assertThat(service.findByPublicId(1L, "fedcba9876543210fedcba9876543210")).isEmpty();
        assertThat(service.findByPublicId(1L, "same")).isEmpty();
        assertThatThrownBy(() -> service.listTickets(
                1L, new BusinessAccessService.OneBusiness(bliny), 50, 0))
                .isInstanceOf(AccessDeniedException.class);

        List<BusinessScopedTicketReadService.TicketRow> all = service.listTickets(
                2L, BusinessAccessService.AllAccessibleBusinesses.INSTANCE, 50, 0);
        assertThat(all).extracting(BusinessScopedTicketReadService.TicketRow::businessId)
                .containsExactly(sushi, bliny, autoservice);
        assertThat(all).extracting(BusinessScopedTicketReadService.TicketRow::ticketId)
                .containsExactly("same", "same", "new-direction");
        assertThat(service.availableBusinesses(2L)).extracting(
                BusinessScopedTicketReadService.BusinessOption::code)
                .containsExactly("sushi", "bliny", "autoservice");
        assertThat(service.listTickets(3L, BusinessAccessService.AllAccessibleBusinesses.INSTANCE, 50, 0))
                .isEmpty();
        assertThat(service.listTickets(2L, new BusinessAccessService.OneBusiness(bliny), 50, 0))
                .extracting(BusinessScopedTicketReadService.TicketRow::userId).containsExactly(2L);
        assertThat(service.findByPublicId(2L, "fedcba9876543210fedcba9876543210"))
                .get().extracting(BusinessScopedTicketReadService.TicketRow::businessId).isEqualTo(bliny);
    }

    @Test
    void revocationAndAmbiguousUsernameFailClosed() throws Exception {
        JdbcTemplate jdbc = freshSchema("business_scoped_read_revoke");
        jdbc.update("INSERT INTO users(id, username) VALUES (1, 'member'), (2, 'MEMBER')");
        migrate(jdbc);
        long sushi = businessId(jdbc, "sushi");
        jdbc.update("INSERT INTO business_memberships(user_id, business_id, role_code) VALUES (1, ?, 'VIEWER')", sushi);
        jdbc.update("INSERT INTO tickets(user_id, ticket_id, business_id) VALUES (1, 'ticket', ?)", sushi);
        BusinessScopedTicketReadService service = new BusinessScopedTicketReadService(
                jdbc, new BusinessAccessService(jdbc));

        assertThatThrownBy(() -> service.requireAuthenticatedUserId("member"))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(service.listTickets(1L, new BusinessAccessService.OneBusiness(sushi), 50, 0))
                .hasSize(1);
        jdbc.update("UPDATE business_memberships SET is_active = FALSE WHERE user_id = 1");
        assertThatThrownBy(() -> service.listTickets(
                1L, new BusinessAccessService.OneBusiness(sushi), 50, 0))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(service.availableBusinesses(1L)).isEmpty();
        assertThat(service.findByPublicId(1L, "0123456789abcdef0123456789abcdef")).isEmpty();

        BusinessAccessService staleAccess = mock(BusinessAccessService.class);
        when(staleAccess.resolveAccessibleBusinessIds(1L)).thenReturn(Set.of(sushi));
        when(staleAccess.requireSelectedBusiness(eq(1L), any()))
                .thenReturn(new BusinessAccessService.SelectedBusinessScope(Set.of(sushi), sushi));
        BusinessScopedTicketReadService staleService = new BusinessScopedTicketReadService(jdbc, staleAccess);
        assertThat(staleService.availableBusinesses(1L)).isEmpty();
        assertThat(staleService.listTickets(
                1L, new BusinessAccessService.OneBusiness(sushi), 50, 0)).isEmpty();
    }

    private JdbcTemplate freshSchema(String prefix) {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType(prefix);
        jdbc.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, username TEXT NOT NULL UNIQUE)");
        jdbc.execute("CREATE TABLE channels (id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE tickets (user_id BIGINT NOT NULL, ticket_id TEXT NOT NULL, PRIMARY KEY (user_id, ticket_id))");
        return jdbc;
    }

    private void migrate(JdbcTemplate jdbc) throws Exception {
        for (String statement : Files.readString(FOUNDATION).replace("\r\n", "\n").split(";")) {
            if (!statement.isBlank()) {
                jdbc.execute(statement.trim());
            }
        }
        jdbc.execute(Files.readString(LOCATOR).replace("\r\n", "\n"));
    }

    private long businessId(JdbcTemplate jdbc, String code) {
        return jdbc.queryForObject("SELECT id FROM businesses WHERE code = ?", Long.class, code);
    }
}
