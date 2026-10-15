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
        BusinessAccessService access = new BusinessAccessService(jdbc);
        long sushi = businessId(jdbc, "sushi");
        long bliny = businessId(jdbc, "bliny");

        BusinessAdministrationService.Business created = service.createBusiness("autoservice", "  Автосервис  ");
        assertThat(created.code()).isEqualTo("autoservice");
        assertThat(created.displayName()).isEqualTo("Автосервис");
        assertThat(created.status()).isEqualTo("active");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_channel_bindings", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_locations", Integer.class)).isZero();
        BusinessAdministrationService.Location location = service.createLocation(
                created.id(), "  Центр  ", "  магазин  ", "  Москва  ").orElseThrow();
        assertThat(location.locationName()).isEqualTo("Центр");
        assertThat(location.locationType()).isEqualTo("магазин");
        assertThat(location.city()).isEqualTo("Москва");
        assertThat(location.status()).isEqualTo("active");
        BusinessAdministrationService.Location sameNameElsewhere = service.createLocation(
                sushi, "Центр", "магазин", "Москва").orElseThrow();
        assertThat(sameNameElsewhere.id()).isNotEqualTo(location.id());
        assertThatThrownBy(() -> service.createLocation(created.id(), "цЕНТР", "магазин", "Москва"))
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(service.setLocationStatus(sushi, location.id(), "archived")).isFalse();
        assertThat(service.setLocationStatus(created.id(), location.id(), "archived")).isTrue();
        assertThat(service.overview().locations()).extracting(BusinessAdministrationService.Location::status)
                .contains("archived");
        assertThat(service.setLocationStatus(created.id(), location.id(), "active")).isTrue();
        assertThat(service.overview().locations()).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_channel_bindings", Integer.class)).isZero();

        assertThat(service.grantMembership(created.id(), 1L, "OPERATOR")).isTrue();
        assertThat(access.resolveAccessibleBusinessIds(1L)).containsExactly(created.id());
        assertThat(access.hasCapability(1L, created.id(), BusinessAccessService.BusinessCapability.OPERATE)).isTrue();
        assertThat(access.resolveAccessibleBusinessIds(3L)).isEmpty();

        assertThat(service.grantAllBusinesses(2L, "VIEWER")).isTrue();
        assertThat(access.resolveAccessibleBusinessIds(2L)).containsExactlyInAnyOrder(sushi, bliny, created.id());
        assertThat(access.hasCapability(2L, created.id(), BusinessAccessService.BusinessCapability.CONFIGURE)).isFalse();
        BusinessAdministrationService.Business future = service.createBusiness("flowers", "Цветы");
        assertThat(access.resolveAccessibleBusinessIds(2L)).contains(future.id());
        assertThat(access.resolveAccessibleBusinessIds(1L)).doesNotContain(future.id());

        assertThat(service.grantMembership(created.id(), 1L, "MANAGER")).isTrue();
        assertThat(access.hasCapability(1L, created.id(), BusinessAccessService.BusinessCapability.MANAGE)).isTrue();
        assertThat(service.overview().memberships()).containsExactly(
                new BusinessAdministrationService.Membership(1L, created.id(), "MANAGER"));
        assertThat(service.overview().allGrants()).containsExactly(
                new BusinessAdministrationService.AllGrant(2L, "VIEWER"));

        service.revokeMembership(created.id(), 1L);
        assertThat(access.resolveAccessibleBusinessIds(1L)).isEmpty();
        service.revokeAllBusinesses(2L);
        assertThat(access.resolveAccessibleBusinessIds(2L)).isEmpty();
        assertThat(service.overview().memberships()).isEmpty();
        assertThat(service.overview().allGrants()).isEmpty();
        assertThat(service.grantMembership(created.id(), 1L, "VIEWER")).isTrue();
        assertThat(access.hasCapability(1L, created.id(), BusinessAccessService.BusinessCapability.READ)).isTrue();
    }

    @Test
    void rejectsInvalidCodesRolesMissingTargetsAndArchivedBusiness() throws Exception {
        JdbcTemplate jdbc = freshSchema("business_admin_fail_closed");
        jdbc.update("INSERT INTO users(id, username) VALUES (1, 'one')");
        migrate(jdbc);
        BusinessAdministrationService service = new BusinessAdministrationService(jdbc);
        long sushi = businessId(jdbc, "sushi");
        long bliny = businessId(jdbc, "bliny");
        assertThatThrownBy(() -> service.createBusiness("Sushi", "Duplicate name"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createBusiness("bad code", "Name"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createBusiness("valid", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createBusiness("sushi", "Another"))
                .isInstanceOf(DuplicateKeyException.class);
        assertThatThrownBy(() -> service.grantMembership(sushi, 1L, "ROLE_ADMIN"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.grantAllBusinesses(1L, "admin"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.grantMembership(sushi, 999L, "VIEWER")).isFalse();
        assertThat(service.grantAllBusinesses(999L, "VIEWER")).isFalse();
        jdbc.update("UPDATE businesses SET status = 'archived' WHERE id = ?", sushi);
        assertThat(service.grantMembership(sushi, 1L, "VIEWER")).isFalse();
        assertThat(service.createLocation(sushi, "Центр", "", "")).isEmpty();
        assertThatThrownBy(() -> service.createLocation(0L, "Центр", "", ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createLocation(bliny, " ", "", ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createLocation(bliny, "Центр", "x\ny", ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.setLocationStatus(bliny, 1L, "deleted"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void renameAndArchivePreserveBusinessIdentityLocationsAndGrants() throws Exception {
        JdbcTemplate jdbc = freshSchema("business_admin_lifecycle");
        jdbc.update("INSERT INTO users(id, username) VALUES (1, 'member'), (2, 'all-reader')");
        migrate(jdbc);
        BusinessAdministrationService service = new BusinessAdministrationService(jdbc);
        BusinessAccessService access = new BusinessAccessService(jdbc);
        var business = service.createBusiness("autoservice", "Автосервис");
        long id = business.id();
        assertThat(service.grantMembership(id, 1L, "VIEWER")).isTrue();
        assertThat(service.grantAllBusinesses(2L, "VIEWER")).isTrue();
        var location = service.createLocation(id, "Центр", "", "").orElseThrow();

        var renamed = service.renameBusiness(id, "  Автосервис 24  ").orElseThrow();
        assertThat(renamed).isEqualTo(new BusinessAdministrationService.Business(
                id, "autoservice", "Автосервис 24", "active"));
        assertThat(service.renameBusiness(999L, "Нет")).isEmpty();
        assertThatThrownBy(() -> service.renameBusiness(id, "  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.renameBusiness(id, "bad\nname"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createBusiness("bad-name", "bad\nname"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(service.setBusinessStatus(id, "archived")).isTrue();
        assertThat(access.resolveAccessibleBusinessIds(1L)).doesNotContain(id);
        assertThat(access.resolveAccessibleBusinessIds(2L)).doesNotContain(id);
        assertThat(service.grantMembership(id, 1L, "OPERATOR")).isFalse();
        assertThat(service.createLocation(id, "Другая", "", "")).isEmpty();
        assertThat(service.overview().locations()).contains(location);
        assertThat(service.overview().memberships()).contains(
                new BusinessAdministrationService.Membership(1L, id, "VIEWER"));
        assertThat(service.renameBusiness(id, "Архивное название").orElseThrow().code())
                .isEqualTo("autoservice");

        assertThat(service.setBusinessStatus(id, "active")).isTrue();
        assertThat(access.resolveAccessibleBusinessIds(1L)).contains(id);
        assertThat(access.resolveAccessibleBusinessIds(2L)).contains(id);
        assertThat(service.overview().locations()).contains(location);
        assertThat(service.overview().businesses()).contains(
                new BusinessAdministrationService.Business(id, "autoservice", "Архивное название", "active"));
        assertThat(service.setBusinessStatus(999L, "archived")).isFalse();
        assertThatThrownBy(() -> service.setBusinessStatus(id, "deleted"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.setBusinessStatus(0L, "archived"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void channelBindingPreviewClassifiesWithoutAssigningOrChangingBindings() throws Exception {
        JdbcTemplate jdbc = freshSchema("business_admin_channel_preview");
        migrate(jdbc);
        BusinessAdministrationService service = new BusinessAdministrationService(jdbc);
        long sushi = businessId(jdbc, "sushi");
        long bliny = businessId(jdbc, "bliny");
        jdbc.update("INSERT INTO channels(id, channel_name, platform, is_active) "
                + "VALUES (10, 'Support', 'telegram', TRUE), (5, 'VK', 'vk', TRUE)");

        assertThat(service.listChannels()).containsExactly(
                new BusinessAdministrationService.ChannelSummary(5L, "VK", "vk", true),
                new BusinessAdministrationService.ChannelSummary(10L, "Support", "telegram", true));

        assertThat(service.previewChannelBinding(999L)).isEmpty();
        assertThatThrownBy(() -> service.previewChannelBinding(0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.previewChannelBinding(10L).orElseThrow().classification())
                .isEqualTo("NO_ACTIVE_BINDING");
        jdbc.update("INSERT INTO business_channel_bindings(channel_id, business_id, resolution_mode) "
                + "VALUES (10, ?, 'SINGLE_BUSINESS')", sushi);
        var single = service.previewChannelBinding(10L).orElseThrow();
        assertThat(single.classification()).isEqualTo("SINGLE_BUSINESS");
        assertThat(single.bindings()).containsExactly(new BusinessAdministrationService.ChannelBinding(
                sushi, "sushi", "active", "SINGLE_BUSINESS", true));

        jdbc.update("INSERT INTO business_channel_bindings(channel_id, business_id, resolution_mode, is_active) "
                + "VALUES (10, ?, 'EXPLICIT_SELECTION', FALSE)", bliny);
        assertThat(service.previewChannelBinding(10L).orElseThrow().classification())
                .isEqualTo("SINGLE_BUSINESS");
        jdbc.update("UPDATE business_channel_bindings SET is_active = TRUE WHERE channel_id = 10 AND business_id = ?", bliny);
        assertThat(service.previewChannelBinding(10L).orElseThrow().classification())
                .isEqualTo("NEEDS_EXPLICIT_DECISION");
        jdbc.update("UPDATE business_channel_bindings SET is_active = FALSE WHERE channel_id = 10 AND business_id = ?", sushi);
        assertThat(service.previewChannelBinding(10L).orElseThrow().classification())
                .isEqualTo("NEEDS_EXPLICIT_DECISION");
        jdbc.update("UPDATE business_channel_bindings SET resolution_mode = 'SINGLE_BUSINESS' "
                + "WHERE channel_id = 10 AND business_id = ?", bliny);
        assertThat(service.previewChannelBinding(10L).orElseThrow().classification())
                .isEqualTo("SINGLE_BUSINESS");
        jdbc.update("UPDATE businesses SET status = 'archived' WHERE id = ?", bliny);
        assertThat(service.previewChannelBinding(10L).orElseThrow().classification())
                .isEqualTo("NEEDS_EXPLICIT_DECISION");
        jdbc.update("UPDATE channels SET is_active = FALSE WHERE id = 10");
        assertThat(service.previewChannelBinding(10L).orElseThrow().classification())
                .isEqualTo("CHANNEL_INACTIVE");
        assertThat(service.listChannels()).contains(
                new BusinessAdministrationService.ChannelSummary(10L, "Support", "telegram", false));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_channel_bindings WHERE channel_id = 10", Integer.class))
                .isEqualTo(2);
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
