package com.example.panel.service;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import static com.example.panel.support.GeneralTicketAdminTestSupport.administrator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static com.example.panel.support.AiControlIdentityFixture.auth;
import static com.example.panel.support.AiControlIdentityFixture.login;

class GeneralTicketReadServicePostgresIntegrationTest {
    private static JdbcTemplate jdbc;
    private static GeneralTicketAccessService access;
    private static GeneralTicketReadService service;
    private static final AtomicLong IDS = new AtomicLong(1000);

    @BeforeAll
    static void migrateFromExistingTickets() {
        jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate("general_queue_v59");
        Flyway.configure().dataSource(jdbc.getDataSource())
            .locations("filesystem:src/main/resources/db/migration/postgresql").target("58").load().migrate();
        jdbc.update("INSERT INTO tickets(user_id, ticket_id) VALUES (900, 'legacy-unresolved-v58')");
        jdbc.update("INSERT INTO tickets(user_id, ticket_id, is_general, general_selection_source) "
            + "VALUES (901, 'general-v58', TRUE, 'GENERAL_CHANNEL')");
        Flyway.configure().dataSource(jdbc.getDataSource())
            .locations("filesystem:src/main/resources/db/migration/postgresql").load().migrate();
        access = new GeneralTicketAccessService(jdbc);
        service = new GeneralTicketReadService(jdbc, access);
    }

    @Test
    void migrationDoesNotGrantAccessOrConvertLegacyNullOwners() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '59' AND success",
            Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT is_general FROM tickets WHERE ticket_id = 'legacy-unresolved-v58'",
            Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("SELECT is_general FROM tickets WHERE ticket_id = 'general-v58'", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT question_template_id FROM tickets WHERE ticket_id = 'general-v58'", String.class)).isNull();
        long userId = account();
        assertThat(access.canRead(userId)).isFalse();
        assertThat(access.canRead(null)).isFalse();
        assertThat(access.canRead(-1L)).isFalse();
        assertThat(access.accounts(administrator(jdbc))).filteredOn(a -> a.userId() == userId)
            .singleElement().extracting(GeneralTicketAccessService.AccountAccess::canRead).isEqualTo(false);
        assertThatThrownBy(() -> access.requireAuthenticatedUserId(login(jdbc, "missing-user")))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void businessGrantDoesNotAuthorizeGeneralAndRevocationAppliesInsideEveryQuery() {
        long reader = account();
        jdbc.update("INSERT INTO business_access_grants(user_id, grant_scope, role_code) "
            + "VALUES (?, 'ALL_BUSINESSES', 'ADMIN')", reader);
        String token = token();
        String id = ticket(token, 101, true, null, "open", "Client", "Help");
        assertThat(access.canRead(reader)).isFalse();
        assertThat(service.find(reader, id)).isEmpty();
        assertThatThrownBy(() -> service.page(reader, 50, 0, null, token)).isInstanceOf(AccessDeniedException.class);
        assertThat(access.grantRead(administrator(jdbc),reader)).isTrue();
        assertThat(access.grantRead(administrator(jdbc),reader)).isTrue();
        assertThat(access.requireAuthenticatedUserId(login(jdbc, "USER-" + reader))).isEqualTo(reader);
        assertThat(service.find(reader, id)).isPresent();
        assertThat(access.revokeRead(administrator(jdbc),reader)).isTrue();
        assertThat(access.revokeRead(administrator(jdbc),reader)).isTrue();
        assertThat(service.find(reader, id)).isEmpty();
        assertThat(service.history(reader, id, null, 50)).isEmpty();
        assertThat(service.answers(reader, id)).isEmpty();
        assertThat(service.attachment(reader, id, 1)).isEmpty();
        // Simulates revocation after an early permission check: SQL itself must still deny data.
        GeneralTicketReadService racing = new GeneralTicketReadService(jdbc, mock(GeneralTicketAccessService.class));
        assertThat(racing.page(reader, 50, 0, null, token).tickets()).isEmpty();
        assertThat(access.grantRead(administrator(jdbc),Long.MAX_VALUE)).isFalse();
        assertThatThrownBy(() -> access.grantRead(administrator(jdbc),0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(access.grantRead(administrator(jdbc),reader)).isTrue();
        assertThat(service.find(reader, id)).isPresent();
        jdbc.update("UPDATE users SET enabled = FALSE WHERE id = ?", reader);
        assertThat(access.canRead(reader)).isFalse();
        assertThat(service.find(reader, id)).isEmpty();
        assertThatThrownBy(() -> access.requireAuthenticatedUserId(auth(reader, "user-" + reader)))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void queueFiltersCanonicalGeneralTicketsWithStablePagination() {
        long reader = account();
        access.grantRead(administrator(jdbc),reader);
        String token = token();
        String first = ticket(token + "-a", 201, true, null, "open", "Иван", "Проблема");
        String second = ticket(token + "-b", 202, true, null, "closed", "Иван", "Решена");
        String unresolved = ticket(token + "-unresolved", 203, false, null, "open", "Секрет", "private");
        long business = jdbc.queryForObject("SELECT id FROM businesses WHERE code = 'sushi'", Long.class);
        String businessId = ticket(token + "-business", 204, false, business, "open", "Секрет", "private");
        var page = service.page(reader, 1, 0, null, token);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.tickets()).extracting(GeneralTicketReadService.TicketRow::ticketPublicId).containsExactly(first);
        assertThat(service.page(reader, 1, 1, null, token).tickets())
            .extracting(GeneralTicketReadService.TicketRow::ticketPublicId).containsExactly(second);
        assertThat(service.page(reader, 1, 1, null, token).hasMore()).isFalse();
        assertThat(service.page(reader, 50, 0, "closed", token).tickets()).hasSize(1);
        assertThat(service.find(reader, unresolved)).isEmpty();
        assertThat(service.find(reader, businessId)).isEmpty();
        assertThat(service.history(reader, businessId, null, 50)).isEmpty();
        assertThat(service.answers(reader, unresolved)).isEmpty();
        jdbc.update("INSERT INTO business_access_grants(user_id, grant_scope, role_code) "
            + "VALUES (?, 'ALL_BUSINESSES', 'VIEWER')", reader);
        BusinessScopedTicketReadService businessRead = new BusinessScopedTicketReadService(jdbc, new BusinessAccessService(jdbc));
        assertThat(businessRead.findByPublicId(reader, first)).isEmpty();
        assertThat(businessRead.pageTickets(reader, BusinessAccessService.AllAccessibleBusinesses.INSTANCE,
            50, 0, null, token + "-business", null).tickets()).extracting(BusinessScopedTicketReadService.TicketRow::ticketPublicId)
            .containsExactly(businessId);
        assertThat(service.find(reader, token)).isEmpty();
        assertThatThrownBy(() -> service.page(reader, 101, 0, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.page(reader, 50, -1, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.page(reader, 50, 0, "invalid", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.page(reader, 50, 0, null, "\n")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void historyUsesCompositeOwnerIgnoresHistoricalChannelAndPaginatesWithoutDeletedRows() {
        long reader = account();
        access.grantRead(administrator(jdbc),reader);
        String raw = token();
        String id = ticket(raw, 301, true, null, "open", "Client", "Help");
        long old = history(raw, 301L, "old", null);
        long middle = history(raw, 301L, "middle", null);
        long last = history(raw, 301L, "last", null);
        long deleted = history(raw, 301L, "deleted", null);
        jdbc.update("UPDATE chat_history SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", deleted);
        history(raw, null, "unresolved history", null);
        jdbc.update("INSERT INTO tickets(user_id, ticket_id) VALUES (302, ?)", raw);
        history(raw, 302L, "different owner", null);
        jdbc.update("INSERT INTO channels(id, token, channel_name, platform) OVERRIDING SYSTEM VALUE "
            + "VALUES (8301, 'general-test-channel', 'Old channel', 'telegram')");
        jdbc.update("UPDATE chat_history SET channel_id = 8301 WHERE id = ?", last);
        var page = service.history(reader, id, null, 2).orElseThrow();
        assertThat(page.entries()).extracting(GeneralTicketReadService.HistoryEntry::message).containsExactly("middle", "last");
        assertThat(page.nextBeforeId()).isEqualTo(middle);
        assertThat(page.hasMore()).isTrue();
        var older = service.history(reader, id, page.nextBeforeId(), 2).orElseThrow();
        assertThat(older.entries()).extracting(GeneralTicketReadService.HistoryEntry::id).containsExactly(old);
        assertThat(older.hasMore()).isFalse();
        assertThatThrownBy(() -> service.history(reader, id, 0L, 50)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void answersAndAttachmentsRequireExactUnambiguousRelationsAndCurrentGrant() {
        long reader = account();
        access.grantRead(administrator(jdbc),reader);
        String raw = token();
        String id = ticket(raw, 401, true, null, "open", "Client", "Help");
        jdbc.update("INSERT INTO ticket_attributes(ticket_id, question_id, attribute_key, input_type, question_text, value_text) "
            + "VALUES (?, 'q1', 'q1', 'text', 'Вопрос', '<script>answer</script>')", raw);
        long historyId = history(raw, 401L, "file", "file.bin");
        jdbc.update("INSERT INTO chat_attachment_metadata(chat_history_id, ticket_id, storage_key, original_name, "
            + "availability_status) VALUES (?, ?, ?, 'file.bin', 'available')", historyId, raw, raw + "/file.bin");
        assertThat(service.answers(reader, id).orElseThrow().answers()).singleElement()
            .extracting(GeneralTicketReadService.AnswerEntry::valueText).isEqualTo("<script>answer</script>");
        assertThat(service.attachment(reader, id, historyId)).get()
            .extracting(GeneralTicketReadService.AttachmentReference::storageKey).isEqualTo(raw + "/file.bin");
        jdbc.update("UPDATE chat_attachment_metadata SET storage_key = ? WHERE chat_history_id = ?", raw + "/../other", historyId);
        assertThat(service.attachment(reader, id, historyId)).isEmpty();
        jdbc.update("UPDATE chat_attachment_metadata SET storage_key = ?, archived_at = CURRENT_TIMESTAMP "
            + "WHERE chat_history_id = ?", raw + "/file.bin", historyId);
        assertThat(service.attachment(reader, id, historyId)).isEmpty();
        jdbc.update("UPDATE chat_attachment_metadata SET archived_at = NULL WHERE chat_history_id = ?", historyId);
        long duplicate = history(raw, 401L, "duplicate file", "file.bin");
        jdbc.update("INSERT INTO chat_attachment_metadata(chat_history_id, ticket_id, storage_key, availability_status) "
            + "VALUES (?, ?, ?, 'available')", duplicate, raw, raw + "/file.bin");
        assertThat(service.attachment(reader, id, historyId)).isEmpty();
        jdbc.update("DELETE FROM chat_attachment_metadata WHERE chat_history_id = ?", duplicate);
        jdbc.update("INSERT INTO tickets(user_id, ticket_id) VALUES (402, ?)", raw);
        assertThat(service.answers(reader, id).orElseThrow().relationVerified()).isFalse();
        assertThat(service.answers(reader, id).orElseThrow().answers()).isEmpty();
        assertThat(service.attachment(reader, id, historyId)).isEmpty();
        access.revokeRead(administrator(jdbc),reader);
        assertThat(service.answers(reader, id)).isEmpty();
        assertThat(service.attachment(reader, id, historyId)).isEmpty();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"blocked", "duplicate"})
    void blockedOrAmbiguousAccountCannotReadAnyGeneralSurface(String mutation) {
        long reader = account();
        access.grantRead(administrator(jdbc),reader);
        String raw = token();
        String id = ticket(raw, reader, true, null, "open", "Client", "Help");
        assertThat(service.find(reader, id)).isPresent();
        if (mutation.equals("blocked")) jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=?", reader);
        else jdbc.update("INSERT INTO users(username,password,enabled) VALUES(?,'unused',FALSE)", "USER-" + reader);
        assertThat(access.canRead(reader)).isFalse();
        assertThatThrownBy(() -> access.requireAuthenticatedUserId(auth(reader, "user-" + reader))).isInstanceOf(AccessDeniedException.class);
        assertThat(service.find(reader, id)).isEmpty();
        assertThat(service.history(reader, id, null, 50)).isEmpty();
        assertThat(service.answers(reader, id)).isEmpty();
        assertThat(service.attachment(reader, id, 1)).isEmpty();
        GeneralTicketReadService late = new GeneralTicketReadService(jdbc, mock(GeneralTicketAccessService.class));
        assertThat(late.page(reader, 50, 0, null, raw).tickets()).isEmpty();
    }

    private long account() {
        long id = IDS.incrementAndGet();
        jdbc.update("INSERT INTO users(id, username, password) OVERRIDING SYSTEM VALUE VALUES (?, ?, 'unused')", id, "user-" + id);
        return id;
    }

    private String token() { return "T-" + UUID.randomUUID().toString().replace("-", ""); }

    private String ticket(String raw, long owner, boolean general, Long business, String status, String client, String problem) {
        String publicId = UUID.randomUUID().toString().replace("-", "");
        long groupId = IDS.incrementAndGet();
        jdbc.update("INSERT INTO messages(group_msg_id, user_id, ticket_id, client_name, problem) VALUES (?, ?, ?, ?, ?)",
            groupId, owner, raw, client, problem);
        jdbc.update("INSERT INTO tickets(user_id, ticket_id, group_msg_id, ticket_public_id, business_id, is_general, "
            + "general_selection_source, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, '2026-10-16T08:00:00Z')",
            owner, raw, groupId, publicId, business, general, general ? "GENERAL_CHANNEL" : null, status);
        return publicId;
    }

    private long history(String raw, Long owner, String message, String attachment) {
        return jdbc.queryForObject("INSERT INTO chat_history(ticket_id, ticket_owner_user_id, message, attachment) "
            + "VALUES (?, ?, ?, ?) RETURNING id", Long.class, raw, owner, message, attachment);
    }
}
