package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.service.integration.LegacyTicketIdConcurrencyGuard;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

class UnresolvedTicketBusinessSelectionPostgresTest {

    private static final String PUBLIC_ID = "a".repeat(32);

    private JdbcTemplate jdbc;
    private UnresolvedTicketBusinessSelectionService service;

    @BeforeEach
    void setUp() throws Exception {
        jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("unresolved_business_selection");
        jdbc.execute("""
                CREATE TABLE users(id BIGINT PRIMARY KEY, username TEXT, enabled BOOLEAN DEFAULT TRUE, is_blocked BOOLEAN DEFAULT FALSE);
                CREATE TABLE businesses(id BIGINT PRIMARY KEY, display_name TEXT NOT NULL, status TEXT DEFAULT 'active');
                CREATE TABLE business_memberships(user_id BIGINT, business_id BIGINT, role_code TEXT, is_active BOOLEAN DEFAULT TRUE);
                CREATE TABLE business_access_grants(user_id BIGINT, grant_scope TEXT, role_code TEXT, is_active BOOLEAN DEFAULT TRUE);
                CREATE TABLE tickets(
                    user_id BIGINT, ticket_id TEXT, ticket_public_id TEXT UNIQUE, business_id BIGINT,
                    is_general BOOLEAN DEFAULT FALSE, channel_id BIGINT, status TEXT DEFAULT 'pending',
                    PRIMARY KEY(user_id, ticket_id)
                );
                CREATE TABLE ui_event_outbox(
                    id BIGINT PRIMARY KEY, event_type TEXT, ticket_id TEXT, channel_id BIGINT, created_at TIMESTAMPTZ,
                    ticket_user_id BIGINT, ticket_public_id TEXT, ticket_scope_kind TEXT, ticket_business_id BIGINT
                );
                INSERT INTO users(id,username) VALUES(7,'operator'),(8,'viewer');
                INSERT INTO businesses(id,display_name) VALUES(11,'Первый бизнес'),(22,'Чужой бизнес');
                INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(7,11,'OPERATOR'),(8,11,'VIEWER');
                """);
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql/V81__business_ticket_manual_work.sql")));
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql/V96__operator_business_ticket_selection.sql")));
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,channel_id) VALUES(42,'raw-1',?,9)", PUBLIC_ID);
        MultibusinessFeatureFlags flags = new MultibusinessFeatureFlags();
        flags.getFoundation().setEnabled(true);
        flags.setUiSelector(true);
        service = new UnresolvedTicketBusinessSelectionService(
                jdbc, flags, new UiEventOutboxAppendService(jdbc, new LegacyTicketIdJdbcGuard(jdbc)));
    }

    @Test
    void explicitOperatorSelectionMovesOnlyAnUnresolvedOwnerToTheAuthorizedBusiness() {
        assertThat(service.availableBusinesses(actor(7, "operator")))
                .extracting(UnresolvedTicketBusinessSelectionService.BusinessOption::id)
                .containsExactly(11L);

        var selection = service.select(PUBLIC_ID, 11L, actor(7, "operator"));

        assertThat(selection).hasValueSatisfying(value -> assertThat(value.businessName()).isEqualTo("Первый бизнес"));
        assertThat(jdbc.queryForMap("SELECT business_id,business_work_revision,is_general FROM tickets WHERE ticket_public_id=?", PUBLIC_ID))
                .containsEntry("business_id", 11L).containsEntry("business_work_revision", 1L).containsEntry("is_general", false);
        assertThat(jdbc.queryForMap("SELECT ticket_public_id,business_id,actor_user_id,actor,source FROM business_ticket_selection_events"))
                .containsEntry("ticket_public_id", PUBLIC_ID).containsEntry("business_id", 11L)
                .containsEntry("actor_user_id", 7L).containsEntry("actor", "operator").containsEntry("source", "OPERATOR_DIALOG");
        assertThat(jdbc.queryForMap("SELECT event_type,ticket_public_id,ticket_scope_kind,ticket_business_id FROM ui_event_outbox"))
                .containsEntry("event_type", "business_scope_selected").containsEntry("ticket_public_id", PUBLIC_ID)
                .containsEntry("ticket_scope_kind", "BUSINESS").containsEntry("ticket_business_id", 11L);
        assertThat(service.select(PUBLIC_ID, 11L, actor(7, "operator"))).isEmpty();
    }

    @Test
    void doesNotReclassifyGeneralOrUseAViewerOrForeignBusiness() {
        jdbc.update("UPDATE tickets SET is_general=TRUE WHERE ticket_public_id=?", PUBLIC_ID);
        assertThat(service.select(PUBLIC_ID, 11L, actor(7, "operator"))).isEmpty();
        jdbc.update("UPDATE tickets SET is_general=FALSE WHERE ticket_public_id=?", PUBLIC_ID);
        assertThat(service.select(PUBLIC_ID, 22L, actor(7, "operator"))).isEmpty();
        assertThat(service.select(PUBLIC_ID, 11L, actor(8, "viewer"))).isEmpty();
        assertThat(jdbc.queryForObject("SELECT business_id FROM tickets WHERE ticket_public_id=?", Long.class, PUBLIC_ID)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_ticket_selection_events", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ui_event_outbox", Long.class)).isZero();
    }

    private Authentication actor(long id, String username) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new PanelUserPrincipal(id, username, "unused", true, List.of()), null, List.of());
    }
}
