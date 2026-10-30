package com.example.panel.service;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusinessAdministrationServicePostgresIntegrationTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/postgresql/V51__multibusiness_foundation.sql");

    @Test
    void createsIndependentBusinessAndManagesDirectAndDynamicAllAccess() throws Exception {
        JdbcTemplate jdbc = freshSchema("business_admin_full_flow");
        jdbc.update("INSERT INTO users(id, username) VALUES (1, 'one'), (2, 'all-reader'), (3, 'other')");
        migrate(jdbc);
        BusinessAdministrationService service = new BusinessAdministrationService(jdbc);
        var adminAuthentication = BusinessAdministratorAccountPostgresTest.fixtureAdministrator(jdbc);
        BusinessAccessService access = new BusinessAccessService(jdbc);
        long sushi = businessId(jdbc, "sushi");
        long bliny = businessId(jdbc, "bliny");

        BusinessAdministrationService.Business created = service.createBusiness(adminAuthentication, "autoservice", "  Автосервис  ");
        assertThat(created.code()).isEqualTo("autoservice");
        assertThat(created.displayName()).isEqualTo("Автосервис");
        assertThat(created.status()).isEqualTo("active");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_channel_bindings", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_locations", Integer.class)).isZero();
        BusinessAdministrationService.Location location = service.createLocation(adminAuthentication,
                created.id(), "  Центр  ", "  магазин  ", "  Москва  ").orElseThrow();
        assertThat(location.locationName()).isEqualTo("Центр");
        assertThat(location.locationType()).isEqualTo("магазин");
        assertThat(location.city()).isEqualTo("Москва");
        assertThat(location.status()).isEqualTo("active");
        BusinessAdministrationService.Location sameNameElsewhere = service.createLocation(adminAuthentication,
                sushi, "Центр", "магазин", "Москва").orElseThrow();
        assertThat(sameNameElsewhere.id()).isNotEqualTo(location.id());
        assertThatThrownBy(() -> service.createLocation(adminAuthentication, created.id(), "цЕНТР", "магазин", "Москва"))
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(service.setLocationStatus(adminAuthentication, sushi, location.id(), "archived")).isFalse();
        assertThat(service.setLocationStatus(adminAuthentication, created.id(), location.id(), "archived")).isTrue();
        assertThat(service.overview(adminAuthentication).locations()).extracting(BusinessAdministrationService.Location::status)
                .contains("archived");
        assertThat(service.setLocationStatus(adminAuthentication, created.id(), location.id(), "active")).isTrue();
        assertThat(service.overview(adminAuthentication).locations()).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_channel_bindings", Integer.class)).isZero();

        assertThat(service.grantMembership(adminAuthentication, created.id(), 1L, "OPERATOR")).isTrue();
        assertThat(access.resolveAccessibleBusinessIds(1L)).containsExactly(created.id());
        assertThat(access.hasCapability(1L, created.id(), BusinessAccessService.BusinessCapability.OPERATE)).isTrue();
        assertThat(access.resolveAccessibleBusinessIds(3L)).isEmpty();

        assertThat(service.grantAllBusinesses(adminAuthentication, 2L, "VIEWER")).isTrue();
        assertThat(access.resolveAccessibleBusinessIds(2L)).containsExactlyInAnyOrder(sushi, bliny, created.id());
        assertThat(access.hasCapability(2L, created.id(), BusinessAccessService.BusinessCapability.CONFIGURE)).isFalse();
        BusinessAdministrationService.Business future = service.createBusiness(adminAuthentication, "flowers", "Цветы");
        assertThat(access.resolveAccessibleBusinessIds(2L)).contains(future.id());
        assertThat(access.resolveAccessibleBusinessIds(1L)).doesNotContain(future.id());

        assertThat(service.grantMembership(adminAuthentication, created.id(), 1L, "MANAGER")).isTrue();
        assertThat(access.hasCapability(1L, created.id(), BusinessAccessService.BusinessCapability.MANAGE)).isTrue();
        assertThat(service.overview(adminAuthentication).memberships()).containsExactly(
                new BusinessAdministrationService.Membership(1L, created.id(), "MANAGER"));
        assertThat(service.overview(adminAuthentication).allGrants()).containsExactly(
                new BusinessAdministrationService.AllGrant(2L, "VIEWER"));

        service.revokeMembership(adminAuthentication, created.id(), 1L);
        assertThat(access.resolveAccessibleBusinessIds(1L)).isEmpty();
        service.revokeAllBusinesses(adminAuthentication, 2L);
        assertThat(access.resolveAccessibleBusinessIds(2L)).isEmpty();
        assertThat(service.overview(adminAuthentication).memberships()).isEmpty();
        assertThat(service.overview(adminAuthentication).allGrants()).isEmpty();
        assertThat(service.grantMembership(adminAuthentication, created.id(), 1L, "VIEWER")).isTrue();
        assertThat(access.hasCapability(1L, created.id(), BusinessAccessService.BusinessCapability.READ)).isTrue();
    }

    @Test
    void rejectsInvalidCodesRolesMissingTargetsAndArchivedBusiness() throws Exception {
        JdbcTemplate jdbc = freshSchema("business_admin_fail_closed");
        jdbc.update("INSERT INTO users(id, username) VALUES (1, 'one')");
        migrate(jdbc);
        BusinessAdministrationService service = new BusinessAdministrationService(jdbc);
        var adminAuthentication = BusinessAdministratorAccountPostgresTest.fixtureAdministrator(jdbc);
        long sushi = businessId(jdbc, "sushi");
        long bliny = businessId(jdbc, "bliny");
        assertThatThrownBy(() -> service.createBusiness(adminAuthentication, "Sushi", "Duplicate name"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createBusiness(adminAuthentication, "bad code", "Name"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createBusiness(adminAuthentication, "valid", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createBusiness(adminAuthentication, "sushi", "Another"))
                .isInstanceOf(DuplicateKeyException.class);
        assertThatThrownBy(() -> service.grantMembership(adminAuthentication, sushi, 1L, "ROLE_ADMIN"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.grantAllBusinesses(adminAuthentication, 1L, "admin"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.grantMembership(adminAuthentication, sushi, 999L, "VIEWER")).isFalse();
        assertThat(service.grantAllBusinesses(adminAuthentication, 999L, "VIEWER")).isFalse();
        jdbc.update("UPDATE businesses SET status = 'archived' WHERE id = ?", sushi);
        assertThat(service.grantMembership(adminAuthentication, sushi, 1L, "VIEWER")).isFalse();
        assertThat(service.createLocation(adminAuthentication, sushi, "Центр", "", "")).isEmpty();
        assertThatThrownBy(() -> service.createLocation(adminAuthentication, 0L, "Центр", "", ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createLocation(adminAuthentication, bliny, " ", "", ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createLocation(adminAuthentication, bliny, "Центр", "x\ny", ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.setLocationStatus(adminAuthentication, bliny, 1L, "deleted"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void renameAndArchivePreserveBusinessIdentityLocationsAndGrants() throws Exception {
        JdbcTemplate jdbc = freshSchema("business_admin_lifecycle");
        jdbc.update("INSERT INTO users(id, username) VALUES (1, 'member'), (2, 'all-reader')");
        migrate(jdbc);
        BusinessAdministrationService service = new BusinessAdministrationService(jdbc);
        var adminAuthentication = BusinessAdministratorAccountPostgresTest.fixtureAdministrator(jdbc);
        BusinessAccessService access = new BusinessAccessService(jdbc);
        var business = service.createBusiness(adminAuthentication, "autoservice", "Автосервис");
        long id = business.id();
        assertThat(service.grantMembership(adminAuthentication, id, 1L, "VIEWER")).isTrue();
        assertThat(service.grantAllBusinesses(adminAuthentication, 2L, "VIEWER")).isTrue();
        var location = service.createLocation(adminAuthentication, id, "Центр", "", "").orElseThrow();

        var renamed = service.renameBusiness(adminAuthentication, id, "  Автосервис 24  ").orElseThrow();
        assertThat(renamed).isEqualTo(new BusinessAdministrationService.Business(
                id, "autoservice", "Автосервис 24", "active"));
        assertThat(service.renameBusiness(adminAuthentication, 999L, "Нет")).isEmpty();
        assertThatThrownBy(() -> service.renameBusiness(adminAuthentication, id, "  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.renameBusiness(adminAuthentication, id, "bad\nname"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createBusiness(adminAuthentication, "bad-name", "bad\nname"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(service.setBusinessStatus(adminAuthentication, id, "archived")).isTrue();
        assertThat(access.resolveAccessibleBusinessIds(1L)).doesNotContain(id);
        assertThat(access.resolveAccessibleBusinessIds(2L)).doesNotContain(id);
        assertThat(service.grantMembership(adminAuthentication, id, 1L, "OPERATOR")).isFalse();
        assertThat(service.createLocation(adminAuthentication, id, "Другая", "", "")).isEmpty();
        assertThat(service.overview(adminAuthentication).locations()).contains(location);
        assertThat(service.overview(adminAuthentication).memberships()).contains(
                new BusinessAdministrationService.Membership(1L, id, "VIEWER"));
        assertThat(service.renameBusiness(adminAuthentication, id, "Архивное название").orElseThrow().code())
                .isEqualTo("autoservice");

        assertThat(service.setBusinessStatus(adminAuthentication, id, "active")).isTrue();
        assertThat(access.resolveAccessibleBusinessIds(1L)).contains(id);
        assertThat(access.resolveAccessibleBusinessIds(2L)).contains(id);
        assertThat(service.overview(adminAuthentication).locations()).contains(location);
        assertThat(service.overview(adminAuthentication).businesses()).contains(
                new BusinessAdministrationService.Business(id, "autoservice", "Архивное название", "active"));
        assertThat(service.setBusinessStatus(adminAuthentication, 999L, "archived")).isFalse();
        assertThatThrownBy(() -> service.setBusinessStatus(adminAuthentication, id, "deleted"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.setBusinessStatus(adminAuthentication, 0L, "archived"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void channelBindingPreviewClassifiesWithoutAssigningOrChangingBindings() throws Exception {
        JdbcTemplate jdbc = freshSchema("business_admin_channel_preview");
        migrate(jdbc);
        BusinessAdministrationService service = new BusinessAdministrationService(jdbc);
        var adminAuthentication = BusinessAdministratorAccountPostgresTest.fixtureAdministrator(jdbc);
        long sushi = businessId(jdbc, "sushi");
        long bliny = businessId(jdbc, "bliny");
        jdbc.update("INSERT INTO channels(id, channel_name, platform, is_active) "
                + "VALUES (10, 'Support', 'telegram', TRUE), (5, 'VK', 'vk', TRUE)");

        assertThat(service.listChannels(adminAuthentication)).containsExactly(
                new BusinessAdministrationService.ChannelSummary(5L, "VK", "vk", true),
                new BusinessAdministrationService.ChannelSummary(10L, "Support", "telegram", true));

        assertThat(service.previewChannelBinding(adminAuthentication, 999L)).isEmpty();
        assertThatThrownBy(() -> service.previewChannelBinding(adminAuthentication, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.previewChannelBinding(adminAuthentication, 10L).orElseThrow().classification())
                .isEqualTo("NO_ACTIVE_BINDING");
        jdbc.update("INSERT INTO business_channel_bindings(channel_id, business_id, resolution_mode) "
                + "VALUES (10, ?, 'SINGLE_BUSINESS')", sushi);
        var single = service.previewChannelBinding(adminAuthentication, 10L).orElseThrow();
        assertThat(single.classification()).isEqualTo("SINGLE_BUSINESS");
        assertThat(single.bindings()).containsExactly(new BusinessAdministrationService.ChannelBinding(
                sushi, "sushi", "active", "SINGLE_BUSINESS", true));

        jdbc.update("INSERT INTO business_channel_bindings(channel_id, business_id, resolution_mode, is_active) "
                + "VALUES (10, ?, 'EXPLICIT_SELECTION', FALSE)", bliny);
        assertThat(service.previewChannelBinding(adminAuthentication, 10L).orElseThrow().classification())
                .isEqualTo("SINGLE_BUSINESS");
        jdbc.update("UPDATE business_channel_bindings SET is_active = TRUE WHERE channel_id = 10 AND business_id = ?", bliny);
        assertThat(service.previewChannelBinding(adminAuthentication, 10L).orElseThrow().classification())
                .isEqualTo("NEEDS_EXPLICIT_DECISION");
        jdbc.update("UPDATE business_channel_bindings SET is_active = FALSE WHERE channel_id = 10 AND business_id = ?", sushi);
        assertThat(service.previewChannelBinding(adminAuthentication, 10L).orElseThrow().classification())
                .isEqualTo("EXPLICIT_SELECTION");
        jdbc.update("UPDATE business_channel_bindings SET resolution_mode = 'SINGLE_BUSINESS' "
                + "WHERE channel_id = 10 AND business_id = ?", bliny);
        assertThat(service.previewChannelBinding(adminAuthentication, 10L).orElseThrow().classification())
                .isEqualTo("SINGLE_BUSINESS");
        jdbc.update("UPDATE businesses SET status = 'archived' WHERE id = ?", bliny);
        assertThat(service.previewChannelBinding(adminAuthentication, 10L).orElseThrow().classification())
                .isEqualTo("NEEDS_EXPLICIT_DECISION");
        jdbc.update("UPDATE channels SET is_active = FALSE WHERE id = 10");
        assertThat(service.previewChannelBinding(adminAuthentication, 10L).orElseThrow().classification())
                .isEqualTo("CHANNEL_INACTIVE");
        assertThat(service.listChannels(adminAuthentication)).contains(
                new BusinessAdministrationService.ChannelSummary(10L, "Support", "telegram", false));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_channel_bindings WHERE channel_id = 10", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void channelBindingWritesPreserveConsistentModesAndNeverAssignTickets() throws Exception {
        JdbcTemplate jdbc = freshSchema("business_admin_channel_bindings");
        migrate(jdbc);
        BusinessAdministrationService service = new BusinessAdministrationService(jdbc);
        var adminAuthentication = BusinessAdministratorAccountPostgresTest.fixtureAdministrator(jdbc);
        long sushi = businessId(jdbc, "sushi");
        long bliny = businessId(jdbc, "bliny");
        jdbc.update("INSERT INTO channels(id, channel_name, platform, is_active) "
                + "VALUES (10, 'Support', 'telegram', TRUE)");

        assertThat(service.saveChannelBinding(adminAuthentication, 10L, sushi, "SINGLE_BUSINESS")).isTrue();
        assertThat(service.saveChannelBinding(adminAuthentication, 10L, sushi, "SINGLE_BUSINESS")).isTrue();
        assertThat(service.previewChannelBinding(adminAuthentication, 10L).orElseThrow().classification())
                .isEqualTo("SINGLE_BUSINESS");
        assertThatThrownBy(() -> service.saveChannelBinding(adminAuthentication, 10L, bliny, "SINGLE_BUSINESS"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.saveChannelBinding(adminAuthentication, 10L, bliny, "EXPLICIT_SELECTION"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(service.previewChannelBinding(adminAuthentication, 10L).orElseThrow().bindings()).hasSize(1);

        assertThat(service.saveChannelBinding(adminAuthentication, 10L, sushi, "EXPLICIT_SELECTION")).isTrue();
        assertThat(service.saveChannelBinding(adminAuthentication, 10L, bliny, "EXPLICIT_SELECTION")).isTrue();
        assertThat(service.previewChannelBinding(adminAuthentication, 10L).orElseThrow().classification())
                .isEqualTo("EXPLICIT_SELECTION");
        assertThatThrownBy(() -> service.saveChannelBinding(adminAuthentication, 10L, sushi, "SINGLE_BUSINESS"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.saveChannelBinding(adminAuthentication, 10L, sushi, "VERIFIED_LOCATION_MAPPING"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.saveChannelBinding(adminAuthentication, 999L, sushi, "EXPLICIT_SELECTION")).isFalse();
        assertThat(service.saveChannelBinding(adminAuthentication, 10L, 999L, "EXPLICIT_SELECTION")).isFalse();

        assertThat(service.deactivateChannelBinding(adminAuthentication, 10L, bliny)).isTrue();
        assertThat(service.deactivateChannelBinding(adminAuthentication, 10L, bliny)).isFalse();
        assertThat(service.saveChannelBinding(adminAuthentication, 10L, sushi, "SINGLE_BUSINESS")).isTrue();
        assertThat(service.previewChannelBinding(adminAuthentication, 10L).orElseThrow().classification())
                .isEqualTo("SINGLE_BUSINESS");
        jdbc.update("UPDATE businesses SET status = 'archived' WHERE id = ?", bliny);
        assertThat(service.saveChannelBinding(adminAuthentication, 10L, bliny, "EXPLICIT_SELECTION")).isFalse();
        assertThatThrownBy(() -> service.deactivateChannelBinding(adminAuthentication, 0L, bliny))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tickets", Integer.class)).isZero();
    }

    private JdbcTemplate freshSchema(String prefix) {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType(prefix);
        jdbc.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, username TEXT NOT NULL UNIQUE)");
        jdbc.execute("CREATE TABLE channels (id BIGINT PRIMARY KEY, channel_name TEXT NOT NULL DEFAULT '', "
                + "platform TEXT DEFAULT 'telegram', is_active BOOLEAN NOT NULL DEFAULT TRUE)");
        jdbc.execute("CREATE TABLE tickets (user_id BIGINT NOT NULL, ticket_id TEXT NOT NULL, PRIMARY KEY (user_id, ticket_id))");
        return jdbc;
    }

    private void migrate(JdbcTemplate jdbc) throws Exception {
        for (String statement : Files.readString(MIGRATION).replace("\r\n", "\n").split(";")) {
            if (!statement.isBlank()) {
                jdbc.execute(statement.trim());
            }
        }
    }

    private long businessId(JdbcTemplate jdbc, String code) {
        return jdbc.queryForObject("SELECT id FROM businesses WHERE code = ?", Long.class, code);
    }

}
