package com.example.panel.service;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusinessAccessServicePostgresIntegrationTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/postgresql/V51__multibusiness_foundation.sql"
    );

    @Test
    void foundationCreatesIndependentBusinessesWithoutAssigningLegacyTickets() throws Exception {
        JdbcTemplate jdbc = freshSchema("business_foundation_v51");
        jdbc.update("INSERT INTO users(id, username) VALUES (1, 'operator')");
        jdbc.update("INSERT INTO tickets(user_id, ticket_id) VALUES (1, 'legacy-ticket')");

        applyMigration(jdbc);

        assertThat(jdbc.queryForList(
                "SELECT code, display_name FROM businesses ORDER BY code"
        )).containsExactly(
                java.util.Map.of("code", "bliny", "display_name", "Блины"),
                java.util.Map.of("code", "sushi", "display_name", "Суши")
        );
        assertThat(jdbc.queryForObject(
                "SELECT business_id FROM tickets WHERE ticket_id = 'legacy-ticket'",
                Long.class
        )).isNull();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM business_legacy_aliases",
                Integer.class
        )).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM business_channel_bindings",
                Integer.class
        )).isZero();
    }

    @Test
    void accessIsDenyByDefaultAndAllBusinessesGrantIncludesFutureBusinessWithoutSystemAdmin() throws Exception {
        JdbcTemplate jdbc = freshSchema("business_access_v51");
        jdbc.update("INSERT INTO users(id, username) VALUES (1, 'operator'), (2, 'all-scope'), (3, 'blocked')");
        applyMigration(jdbc);

        Long sushiId = businessId(jdbc, "sushi");
        Long blinyId = businessId(jdbc, "bliny");
        jdbc.update(
                "INSERT INTO business_memberships(user_id, business_id, role_code) VALUES (?, ?, 'OPERATOR')",
                1L,
                sushiId
        );
        jdbc.update(
                "INSERT INTO business_access_grants(user_id, grant_scope, role_code) VALUES (2, 'ALL_BUSINESSES', 'VIEWER')"
        );

        BusinessAccessService service = new BusinessAccessService(jdbc);

        assertThat(service.resolveAccessibleBusinessIds(3L)).isEmpty();
        assertThat(service.resolveAccessibleBusinessIds(1L)).containsExactly(sushiId);
        assertThat(service.hasCapability(1L, sushiId, BusinessAccessService.BusinessCapability.OPERATE)).isTrue();
        assertThat(service.hasCapability(1L, blinyId, BusinessAccessService.BusinessCapability.READ)).isFalse();
        assertThatThrownBy(() -> service.requireCapability(
                1L,
                blinyId,
                BusinessAccessService.BusinessCapability.READ
        )).isInstanceOf(AccessDeniedException.class);

        assertThat(service.resolveAccessibleBusinessIds(2L)).containsExactlyInAnyOrder(sushiId, blinyId);
        assertThat(service.hasCapability(2L, sushiId, BusinessAccessService.BusinessCapability.READ)).isTrue();
        assertThat(service.hasCapability(2L, sushiId, BusinessAccessService.BusinessCapability.CONFIGURE)).isFalse();

        jdbc.update("INSERT INTO businesses(code, display_name) VALUES ('autoservice', 'Автосервис')");
        Long autoserviceId = businessId(jdbc, "autoservice");
        assertThat(service.resolveAccessibleBusinessIds(2L))
                .containsExactlyInAnyOrder(sushiId, blinyId, autoserviceId);
        assertThat(service.resolveAccessibleBusinessIds(1L)).isEqualTo(Set.of(sushiId));
    }

    @Test
    void selectedBusinessScopeIsFailClosedAndAllMeansOnlyAccessibleBusinesses() throws Exception {
        JdbcTemplate jdbc = freshSchema("business_selected_scope_v51");
        jdbc.update("INSERT INTO users(id, username) VALUES (1, 'member'), (2, 'blocked')");
        applyMigration(jdbc);

        Long sushiId = businessId(jdbc, "sushi");
        Long blinyId = businessId(jdbc, "bliny");
        jdbc.update(
                "INSERT INTO business_memberships(user_id, business_id, role_code) VALUES (?, ?, 'VIEWER')",
                1L,
                sushiId
        );
        BusinessAccessService service = new BusinessAccessService(jdbc);

        assertThat(service.requireSelectedBusiness(
                1L,
                new BusinessAccessService.OneBusiness(sushiId)
        )).isEqualTo(new BusinessAccessService.SelectedBusinessScope(Set.of(sushiId), sushiId));
        assertThat(service.requireSelectedBusiness(
                1L,
                BusinessAccessService.AllAccessibleBusinesses.INSTANCE
        )).isEqualTo(new BusinessAccessService.SelectedBusinessScope(Set.of(sushiId), null));
        assertThat(service.requireSelectedBusiness(
                2L,
                BusinessAccessService.AllAccessibleBusinesses.INSTANCE
        )).isEqualTo(new BusinessAccessService.SelectedBusinessScope(Set.of(), null));
        assertThatThrownBy(() -> service.requireSelectedBusiness(
                1L,
                new BusinessAccessService.OneBusiness(blinyId)
        )).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.requireSelectedBusiness(1L, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    private JdbcTemplate freshSchema(String prefix) {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate(prefix);
        jdbc.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, username TEXT NOT NULL UNIQUE)");
        jdbc.execute("CREATE TABLE channels (id BIGINT PRIMARY KEY)");
        jdbc.execute("""
                CREATE TABLE tickets (
                    user_id BIGINT NOT NULL,
                    ticket_id TEXT NOT NULL,
                    PRIMARY KEY (user_id, ticket_id)
                )
                """);
        return jdbc;
    }

    private void applyMigration(JdbcTemplate jdbc) throws Exception {
        String sql = Files.readString(MIGRATION).replace("\r\n", "\n");
        for (String statement : sql.split(";")) {
            String trimmed = statement.trim();
            if (!trimmed.isEmpty()) {
                jdbc.execute(trimmed);
            }
        }
    }

    private Long businessId(JdbcTemplate jdbc, String code) {
        return jdbc.queryForObject(
                "SELECT id FROM businesses WHERE code = ?",
                Long.class,
                code
        );
    }
}
