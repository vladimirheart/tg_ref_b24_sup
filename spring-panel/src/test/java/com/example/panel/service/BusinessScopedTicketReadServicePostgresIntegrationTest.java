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
    private static final Path HISTORY_OWNER = Path.of(
            "src/main/resources/db/migration/postgresql/V53__chat_history_ticket_owner.sql");

    @Test
    void historyUsesOnlyExplicitCompositeOwnerEvenWhenRawTicketIdCollides() throws Exception {
        JdbcTemplate jdbc = freshSchema("business_scoped_history");
        jdbc.update("INSERT INTO users(id, username) VALUES (1, 'sushi-reader'), (2, 'all-reader')");
        migrate(jdbc);
        jdbc.execute("""
                CREATE TABLE chat_history (
                    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    user_id BIGINT, ticket_id TEXT, sender TEXT, message TEXT,
                    timestamp TIMESTAMP WITH TIME ZONE, message_type TEXT,
                    attachment TEXT, deleted_at TIMESTAMP WITH TIME ZONE
                )
                """);
        jdbc.execute(Files.readString(HISTORY_OWNER));
        long sushi = businessId(jdbc, "sushi");
        long bliny = businessId(jdbc, "bliny");
        jdbc.update("INSERT INTO business_memberships(user_id, business_id, role_code) VALUES (1, ?, 'VIEWER')", sushi);
        jdbc.update("INSERT INTO business_access_grants(user_id, grant_scope, role_code) VALUES (2, 'ALL_BUSINESSES', 'VIEWER')");
        String sushiId = "0123456789abcdef0123456789abcdef";
        String blinyId = "fedcba9876543210fedcba9876543210";
        jdbc.update("INSERT INTO tickets(user_id, ticket_id, business_id, ticket_public_id) VALUES (11, 'same', ?, ?)", sushi, sushiId);
        jdbc.update("INSERT INTO tickets(user_id, ticket_id, business_id, ticket_public_id) VALUES (22, 'same', ?, ?)", bliny, blinyId);
        jdbc.update("UPDATE tickets SET group_msg_id = 101 WHERE user_id = 11");
        jdbc.update("UPDATE tickets SET group_msg_id = 202 WHERE user_id = 22");
        jdbc.update("INSERT INTO messages(group_msg_id, user_id, ticket_id, client_name, problem) VALUES "
                + "(101, 11, 'same', 'Иван', 'Нет света'), (202, 22, 'same', 'Анна', 'Нет сети')");
        jdbc.update("INSERT INTO chat_history(user_id, ticket_id, ticket_owner_user_id, sender, message) VALUES (11, 'same', 11, 'client', 'sushi')");
        jdbc.update("INSERT INTO chat_history(user_id, ticket_id, ticket_owner_user_id, sender, message) VALUES (22, 'same', 22, 'client', 'bliny')");
        jdbc.update("INSERT INTO chat_history(user_id, ticket_id, sender, message) VALUES (11, 'same', 'client', 'legacy-unlinked')");
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO chat_history(ticket_id, ticket_owner_user_id, message) VALUES ('missing', 11, 'invalid')"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        BusinessScopedTicketReadService service = new BusinessScopedTicketReadService(
                jdbc, new BusinessAccessService(jdbc));
        assertThat(service.findHistoryByPublicId(1L, sushiId, null, 50)).get()
                .extracting(snapshot -> snapshot.entries().get(0).message()).isEqualTo("sushi");
        assertThat(service.findHistoryByPublicId(1L, sushiId, null, 50).orElseThrow().ticket().clientName())
                .isEqualTo("Иван");
        assertThat(service.findHistoryByPublicId(1L, blinyId, null, 50)).isEmpty();
        assertThat(service.findHistoryByPublicId(2L, blinyId, null, 50)).get()
                .extracting(snapshot -> snapshot.entries().get(0).message()).isEqualTo("bliny");
        assertThat(service.findHistoryByPublicId(1L, "same", null, 50)).isEmpty();
        jdbc.update("UPDATE business_memberships SET is_active = FALSE WHERE user_id = 1");
        assertThat(service.findHistoryByPublicId(1L, sushiId, null, 50)).isEmpty();
    }

    @Test
    void historyPagesRemainOrderedAndRecheckGrantAfterRevocation() throws Exception {
        JdbcTemplate jdbc = freshSchema("business_scoped_history_pages");
        jdbc.update("INSERT INTO users(id, username) VALUES (1, 'reader')");
        migrate(jdbc);
        jdbc.execute("""
                CREATE TABLE chat_history (
                    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    user_id BIGINT, ticket_id TEXT, sender TEXT, message TEXT,
                    timestamp TIMESTAMP WITH TIME ZONE, message_type TEXT,
                    attachment TEXT, deleted_at TIMESTAMP WITH TIME ZONE
                )
                """);
        jdbc.execute(Files.readString(HISTORY_OWNER));
        long business = businessId(jdbc, "sushi");
        String publicId = "0123456789abcdef0123456789abcdef";
        jdbc.update("INSERT INTO business_memberships(user_id, business_id, role_code) VALUES (1, ?, 'VIEWER')", business);
        jdbc.update("INSERT INTO tickets(user_id, ticket_id, business_id, ticket_public_id) VALUES (11, 'same', ?, ?)", business, publicId);
        jdbc.update("INSERT INTO tickets(user_id, ticket_id, business_id) VALUES (22, 'same', ?)", business);
        for (String message : List.of("a", "b", "c", "d", "e")) {
            jdbc.update("INSERT INTO chat_history(ticket_id, ticket_owner_user_id, message) VALUES ('same', 11, ?)", message);
        }
        jdbc.update("INSERT INTO chat_history(ticket_id, ticket_owner_user_id, message, deleted_at) VALUES ('same', 11, 'deleted', CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO chat_history(ticket_id, message) VALUES ('same', 'unlinked')");
        jdbc.update("INSERT INTO chat_history(ticket_id, ticket_owner_user_id, message) VALUES ('same', 22, 'other-owner')");

        BusinessScopedTicketReadService service = new BusinessScopedTicketReadService(
                jdbc, new BusinessAccessService(jdbc));
        var first = service.findHistoryByPublicId(1L, publicId, null, 2).orElseThrow();
        assertThat(first.entries()).extracting(BusinessScopedTicketReadService.HistoryEntry::message)
                .containsExactly("d", "e");
        assertThat(first.hasMore()).isTrue();
        assertThat(first.nextBeforeId()).isEqualTo(first.entries().get(0).id());
        jdbc.update("INSERT INTO chat_history(ticket_id, ticket_owner_user_id, message) VALUES ('same', 11, 'late')");
        var second = service.findHistoryByPublicId(1L, publicId, first.nextBeforeId(), 2).orElseThrow();
        assertThat(second.entries()).extracting(BusinessScopedTicketReadService.HistoryEntry::message)
                .containsExactly("b", "c");
        assertThat(second.hasMore()).isTrue();
        var third = service.findHistoryByPublicId(1L, publicId, second.nextBeforeId(), 2).orElseThrow();
        assertThat(third.entries()).extracting(BusinessScopedTicketReadService.HistoryEntry::message)
                .containsExactly("a");
        assertThat(third.hasMore()).isFalse();
        assertThat(third.nextBeforeId()).isNull();
        assertThat(service.findHistoryByPublicId(1L, publicId, third.entries().get(0).id(), 2)
                .orElseThrow().entries()).isEmpty();
        jdbc.update("UPDATE business_memberships SET is_active = FALSE WHERE user_id = 1");
        assertThat(service.findHistoryByPublicId(1L, publicId, first.nextBeforeId(), 2)).isEmpty();
    }

    @Test
    void attachmentRequiresExactHistoryMetadataAndUnambiguousRawNamespace() throws Exception {
        JdbcTemplate jdbc = freshSchema("business_scoped_attachment");
        jdbc.update("INSERT INTO users(id, username) VALUES (1, 'reader'), (2, 'outsider')");
        migrate(jdbc);
        jdbc.execute("""
                CREATE TABLE chat_history (
                    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    user_id BIGINT, ticket_id TEXT, sender TEXT, message TEXT,
                    timestamp TIMESTAMP WITH TIME ZONE, message_type TEXT,
                    attachment TEXT, deleted_at TIMESTAMP WITH TIME ZONE
                )
                """);
        jdbc.execute(Files.readString(HISTORY_OWNER));
        jdbc.execute("""
                CREATE TABLE chat_attachment_metadata (
                    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    chat_history_id BIGINT NOT NULL UNIQUE REFERENCES chat_history(id),
                    ticket_id TEXT, storage_key TEXT, original_name TEXT,
                    storage_provider TEXT, storage_class TEXT,
                    normalization_status TEXT, availability_status TEXT,
                    archived_at TIMESTAMP WITH TIME ZONE, deleted_at TIMESTAMP WITH TIME ZONE
                )
                """);
        long sushi = businessId(jdbc, "sushi");
        long bliny = businessId(jdbc, "bliny");
        String publicId = "0123456789abcdef0123456789abcdef";
        jdbc.update("INSERT INTO business_memberships(user_id, business_id, role_code) VALUES (1, ?, 'VIEWER')", sushi);
        jdbc.update("INSERT INTO tickets(user_id, ticket_id, business_id, ticket_public_id) VALUES (11, 'same', ?, ?)", sushi, publicId);
        jdbc.update("INSERT INTO chat_history(ticket_id, ticket_owner_user_id, message, attachment) VALUES ('same', 11, 'file', 'file.bin')");
        long historyId = jdbc.queryForObject("SELECT id FROM chat_history WHERE message = 'file'", Long.class);
        jdbc.update("""
                INSERT INTO chat_attachment_metadata(
                    chat_history_id, ticket_id, storage_key, original_name,
                    storage_provider, storage_class, normalization_status, availability_status
                ) VALUES (?, 'same', 'same/file.bin', 'invoice.pdf',
                          'local_fs', 'dialog_attachment', 'normalized', 'available')
                """, historyId);
        BusinessScopedTicketReadService service = new BusinessScopedTicketReadService(
                jdbc, new BusinessAccessService(jdbc));
        assertThat(service.findAttachmentByPublicId(1L, publicId, historyId))
                .get().extracting(BusinessScopedTicketReadService.AttachmentReference::storageKey)
                .isEqualTo("same/file.bin");
        assertThat(service.findAttachmentByPublicId(2L, publicId, historyId)).isEmpty();
        assertThat(service.findAttachmentByPublicId(1L, publicId, historyId + 100)).isEmpty();

        jdbc.update("UPDATE chat_attachment_metadata SET storage_key = 'other/file.bin'");
        assertThat(service.findAttachmentByPublicId(1L, publicId, historyId)).isEmpty();
        jdbc.update("UPDATE chat_attachment_metadata SET storage_key = 'same/file.bin', storage_provider = 'external_url'");
        assertThat(service.findAttachmentByPublicId(1L, publicId, historyId)).isEmpty();
        jdbc.update("UPDATE chat_attachment_metadata SET storage_provider = 'local_fs', deleted_at = CURRENT_TIMESTAMP");
        assertThat(service.findAttachmentByPublicId(1L, publicId, historyId)).isEmpty();
        jdbc.update("UPDATE chat_attachment_metadata SET deleted_at = NULL");

        jdbc.update("INSERT INTO chat_history(ticket_id, ticket_owner_user_id, message, attachment) VALUES ('same', 11, 'duplicate', 'file.bin')");
        long duplicateId = jdbc.queryForObject("SELECT id FROM chat_history WHERE message = 'duplicate'", Long.class);
        jdbc.update("""
                INSERT INTO chat_attachment_metadata(
                    chat_history_id, ticket_id, storage_key, original_name,
                    storage_provider, storage_class, normalization_status, availability_status
                ) VALUES (?, 'same', 'same/file.bin', 'other.pdf',
                          'local_fs', 'dialog_attachment', 'normalized', 'available')
                """, duplicateId);
        assertThat(service.findAttachmentByPublicId(1L, publicId, historyId)).isEmpty();
        jdbc.update("DELETE FROM chat_attachment_metadata WHERE chat_history_id = ?", duplicateId);

        jdbc.update("INSERT INTO tickets(user_id, ticket_id, business_id) VALUES (22, 'same', ?)", bliny);
        assertThat(service.findAttachmentByPublicId(1L, publicId, historyId)).isEmpty();
        jdbc.update("DELETE FROM tickets WHERE user_id = 22 AND ticket_id = 'same'");
        jdbc.update("UPDATE business_memberships SET is_active = FALSE WHERE user_id = 1");
        assertThat(service.findAttachmentByPublicId(1L, publicId, historyId)).isEmpty();
    }

    @Test
    void answersRequireCanonicalScopeAndUnambiguousLegacyTicketId() throws Exception {
        JdbcTemplate jdbc = freshSchema("business_scoped_ticket_answers");
        jdbc.update("INSERT INTO users(id, username) VALUES (1, 'direct'), (2, 'all-reader')");
        migrate(jdbc);
        jdbc.execute("""
                CREATE TABLE ticket_attributes (
                    ticket_id TEXT NOT NULL, question_id TEXT NOT NULL,
                    question_text TEXT, input_type TEXT NOT NULL,
                    value_label TEXT, value_text TEXT,
                    PRIMARY KEY (ticket_id, question_id)
                )
                """);
        long sushi = businessId(jdbc, "sushi");
        long bliny = businessId(jdbc, "bliny");
        String publicId = "0123456789abcdef0123456789abcdef";
        String manyId = "fedcba9876543210fedcba9876543210";
        String blinyId = "11111111111111111111111111111111";
        jdbc.update("INSERT INTO business_memberships(user_id, business_id, role_code) VALUES (1, ?, 'VIEWER')", sushi);
        jdbc.update("INSERT INTO business_access_grants(user_id, grant_scope, role_code) VALUES (2, 'ALL_BUSINESSES', 'VIEWER')");
        jdbc.update("INSERT INTO tickets(user_id, ticket_id, business_id, ticket_public_id) VALUES (11, 'same', ?, ?)", sushi, publicId);
        jdbc.update("UPDATE tickets SET group_msg_id = 101 WHERE user_id = 11");
        jdbc.update("INSERT INTO messages(group_msg_id, user_id, ticket_id, client_name, problem) "
                + "VALUES (101, 11, 'same', 'Иван', 'Нет света')");
        jdbc.update("INSERT INTO tickets(user_id, ticket_id, business_id, ticket_public_id) VALUES (22, 'other', ?, ?)", bliny, blinyId);
        jdbc.update("INSERT INTO ticket_attributes(ticket_id, question_id, question_text, input_type, value_label, value_text) "
                + "VALUES ('same', 'q2', 'Адрес', 'text', NULL, '<script>адрес</script>'), "
                + "('same', 'q1', 'Причина', 'select', 'Доставка', NULL)");
        BusinessScopedTicketReadService service = new BusinessScopedTicketReadService(
                jdbc, new BusinessAccessService(jdbc));

        var readable = service.findAnswersByPublicId(1L, publicId).orElseThrow();
        assertThat(readable.relationVerified()).isTrue();
        assertThat(readable.ticket().clientName()).isEqualTo("Иван");
        assertThat(readable.answers()).extracting(BusinessScopedTicketReadService.AnswerEntry::questionId)
                .containsExactly("q1", "q2");
        assertThat(readable.answers().get(1).valueText()).isEqualTo("<script>адрес</script>");
        assertThat(service.findAnswersByPublicId(1L, manyId)).isEmpty();
        assertThat(service.findAnswersByPublicId(1L, blinyId)).isEmpty();
        assertThat(service.findAnswersByPublicId(2L, blinyId).orElseThrow().relationVerified()).isTrue();
        assertThat(service.findAnswersByPublicId(1L, "bad-id")).isEmpty();

        jdbc.update("INSERT INTO tickets(user_id, ticket_id, business_id) VALUES (22, 'same', ?)", bliny);
        var ambiguous = service.findAnswersByPublicId(1L, publicId).orElseThrow();
        assertThat(ambiguous.relationVerified()).isFalse();
        assertThat(ambiguous.answers()).isEmpty();
        assertThat(service.findAnswersByPublicId(2L, publicId).orElseThrow().answers()).isEmpty();

        jdbc.update("INSERT INTO tickets(user_id, ticket_id, business_id, ticket_public_id) VALUES (33, 'many', ?, ?)", sushi, manyId);
        jdbc.update("INSERT INTO ticket_attributes(ticket_id, question_id, input_type) "
                + "SELECT 'many', 'q-' || n, 'text' FROM generate_series(1, 101) n");
        assertThatThrownBy(() -> service.findAnswersByPublicId(1L, manyId))
                .isInstanceOf(BusinessScopedTicketReadService.AnswerLimitExceededException.class);
        jdbc.update("DELETE FROM ticket_attributes WHERE ticket_id = 'many' AND question_id = 'q-101'");
        assertThat(service.findAnswersByPublicId(1L, manyId).orElseThrow().answers()).hasSize(100);

        jdbc.update("UPDATE business_memberships SET is_active = FALSE WHERE user_id = 1");
        assertThat(service.findAnswersByPublicId(1L, publicId)).isEmpty();
        BusinessAccessService staleAccess = mock(BusinessAccessService.class);
        when(staleAccess.resolveAccessibleBusinessIds(1L)).thenReturn(Set.of(sushi));
        assertThat(new BusinessScopedTicketReadService(jdbc, staleAccess)
                .findAnswersByPublicId(1L, publicId)).isEmpty();
        jdbc.update("UPDATE businesses SET status = 'archived' WHERE id = ?", sushi);
        assertThat(service.findAnswersByPublicId(2L, publicId)).isEmpty();
    }

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
        jdbc.update("UPDATE tickets SET status = 'open' WHERE user_id = 1");
        jdbc.update("UPDATE tickets SET status = 'resolved' WHERE user_id = 2");
        jdbc.update("UPDATE tickets SET status = 'closed' WHERE user_id = 3 AND ticket_id = 'new-direction'");

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
        assertThat(all).extracting(BusinessScopedTicketReadService.TicketRow::status)
                .containsExactly("open", "resolved", "closed");
        var firstPage = service.pageTickets(2L,
                BusinessAccessService.AllAccessibleBusinesses.INSTANCE, 2, 0, null, null);
        assertThat(firstPage.tickets()).extracting(BusinessScopedTicketReadService.TicketRow::businessId)
                .containsExactly(sushi, bliny);
        assertThat(firstPage.hasMore()).isTrue();
        var lastPage = service.pageTickets(2L,
                BusinessAccessService.AllAccessibleBusinesses.INSTANCE, 2, 2, null, null);
        assertThat(lastPage.tickets()).extracting(BusinessScopedTicketReadService.TicketRow::businessId)
                .containsExactly(autoservice);
        assertThat(lastPage.hasMore()).isFalse();
        assertThat(service.pageTickets(2L,
                BusinessAccessService.AllAccessibleBusinesses.INSTANCE, 3, 0, null, null).hasMore()).isFalse();
        assertThat(service.pageTickets(2L,
                BusinessAccessService.AllAccessibleBusinesses.INSTANCE, 1, 0, "open", null).hasMore()).isFalse();
        assertThat(service.pageTickets(1L,
                BusinessAccessService.AllAccessibleBusinesses.INSTANCE, 1, 0, null,
                "fedcba9876543210fedcba9876543210").tickets()).isEmpty();
        assertThat(service.listTickets(2L, BusinessAccessService.AllAccessibleBusinesses.INSTANCE,
                50, 0, "open", null)).extracting(BusinessScopedTicketReadService.TicketRow::businessId)
                .containsExactly(sushi);
        assertThat(service.listTickets(2L, BusinessAccessService.AllAccessibleBusinesses.INSTANCE,
                50, 0, null, "same")).extracting(BusinessScopedTicketReadService.TicketRow::businessId)
                .containsExactly(sushi, bliny);
        assertThat(service.listTickets(2L, BusinessAccessService.AllAccessibleBusinesses.INSTANCE,
                50, 0, null, "fedcba9876543210fedcba9876543210"))
                .extracting(BusinessScopedTicketReadService.TicketRow::businessId).containsExactly(bliny);
        assertThat(service.listTickets(1L, BusinessAccessService.AllAccessibleBusinesses.INSTANCE,
                50, 0, null, "fedcba9876543210fedcba9876543210")).isEmpty();
        assertThatThrownBy(() -> service.listTickets(2L,
                BusinessAccessService.AllAccessibleBusinesses.INSTANCE, 50, 0, "invalid", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.listTickets(2L,
                BusinessAccessService.AllAccessibleBusinesses.INSTANCE, 50, 0, null, "same\n"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.availableBusinesses(2L)).extracting(
                BusinessScopedTicketReadService.BusinessOption::code)
                .containsExactly("sushi", "bliny", "autoservice");
        assertThat(service.listTickets(3L, BusinessAccessService.AllAccessibleBusinesses.INSTANCE, 50, 0))
                .isEmpty();
        assertThat(service.listTickets(2L, new BusinessAccessService.OneBusiness(bliny), 50, 0))
                .extracting(BusinessScopedTicketReadService.TicketRow::userId).containsExactly(2L);
        assertThat(service.findByPublicId(2L, "fedcba9876543210fedcba9876543210"))
                .get().extracting(BusinessScopedTicketReadService.TicketRow::businessId).isEqualTo(bliny);
        for (int i = 0; i < 98; i++) {
            jdbc.update("INSERT INTO tickets(user_id, ticket_id, business_id) VALUES (2, ?, ?)",
                    "bulk-" + i, sushi);
        }
        assertThat(service.pageTickets(2L,
                BusinessAccessService.AllAccessibleBusinesses.INSTANCE, 100, 0, null, null).hasMore()).isTrue();
        var maxLastPage = service.pageTickets(2L,
                BusinessAccessService.AllAccessibleBusinesses.INSTANCE, 100, 100, null, null);
        assertThat(maxLastPage.tickets()).hasSize(1);
        assertThat(maxLastPage.hasMore()).isFalse();

        BusinessAdministrationService admin = new BusinessAdministrationService(jdbc);
        assertThat(admin.setBusinessStatus(sushi, "archived")).isTrue();
        assertThat(service.availableBusinesses(1L)).isEmpty();
        assertThat(service.listTickets(1L,
                BusinessAccessService.AllAccessibleBusinesses.INSTANCE, 50, 0)).isEmpty();
        assertThat(service.findByPublicId(1L, "0123456789abcdef0123456789abcdef")).isEmpty();
        assertThat(service.pageTickets(2L,
                BusinessAccessService.AllAccessibleBusinesses.INSTANCE, 50, 0, null, null).tickets())
                .extracting(BusinessScopedTicketReadService.TicketRow::businessId)
                .containsExactly(bliny, autoservice);
        assertThat(admin.setBusinessStatus(sushi, "active")).isTrue();
        assertThat(service.availableBusinesses(1L)).extracting(
                BusinessScopedTicketReadService.BusinessOption::code).containsExactly("sushi");
        assertThat(service.findByPublicId(1L, "0123456789abcdef0123456789abcdef")).isPresent();
    }

    @Test
    void textSearchUsesOnlyExactRootTupleInsideCurrentBusinessScope() throws Exception {
        JdbcTemplate jdbc = freshSchema("business_scoped_root_search");
        jdbc.update("INSERT INTO users(id, username) VALUES (1, 'direct'), (2, 'all-reader')");
        migrate(jdbc);
        long sushi = businessId(jdbc, "sushi");
        long bliny = businessId(jdbc, "bliny");
        jdbc.update("INSERT INTO business_memberships(user_id, business_id, role_code) VALUES (1, ?, 'VIEWER')", sushi);
        jdbc.update("INSERT INTO business_access_grants(user_id, grant_scope, role_code) VALUES (2, 'ALL_BUSINESSES', 'VIEWER')");
        jdbc.update("INSERT INTO tickets(user_id, ticket_id, group_msg_id, business_id, status) VALUES "
                + "(11, 'same', 101, ?, 'open'), (22, 'same', 202, ?, 'open'), "
                + "(33, 'other', 303, ?, 'resolved')", sushi, bliny, sushi);
        jdbc.update("INSERT INTO tickets(user_id, ticket_id, group_msg_id, status) VALUES (44, 'unresolved', 404, 'open')");
        jdbc.update("INSERT INTO tickets(user_id, ticket_id, group_msg_id, business_id) VALUES "
                + "(55, 'wrong-user', 606, ?), (77, 'wrong-ticket', 707, ?)", sushi, sushi);
        jdbc.update("INSERT INTO messages(group_msg_id, user_id, ticket_id, client_name, problem) VALUES "
                + "(101, 11, 'same', 'Иван', 'Нет света'), "
                + "(202, 22, 'same', 'Иван', 'Нет интернета'), "
                + "(303, 33, 'other', 'Мария', 'Скидка 100%_'), "
                + "(404, 44, 'unresolved', 'Иван', 'Нет света'), "
                + "(505, 11, 'same', 'Посторонний', 'секрет'), "
                + "(606, 66, 'wrong-user', 'Посторонний', 'секрет-user'), "
                + "(707, 77, 'other-ticket', 'Посторонний', 'секрет-ticket')");
        BusinessScopedTicketReadService service = new BusinessScopedTicketReadService(
                jdbc, new BusinessAccessService(jdbc));
        var all = BusinessAccessService.AllAccessibleBusinesses.INSTANCE;

        jdbc.update("UPDATE tickets SET ticket_public_id = '0123456789abcdef0123456789abcdef' WHERE user_id = 11");
        assertThat(service.findByPublicId(1L, "0123456789abcdef0123456789abcdef")
                .orElseThrow().clientName()).isEqualTo("Иван");
        assertThat(service.listTickets(1L, all, 50, 0).stream()
                .filter(row -> row.userId() == 11L).findFirst().orElseThrow().problemPreview())
                .isEqualTo("Нет света");
        assertThat(service.listTickets(1L, all, 50, 0).stream()
                .filter(row -> row.userId() == 55L).findFirst().orElseThrow().clientName()).isNull();
        assertThat(service.listTickets(1L, all, 50, 0).stream()
                .filter(row -> row.userId() == 77L).findFirst().orElseThrow().problemPreview()).isNull();

        assertThat(service.listTickets(1L, all, 50, 0, null, null, " иВан "))
                .extracting(BusinessScopedTicketReadService.TicketRow::userId).containsExactly(11L);
        assertThat(service.listTickets(1L, all, 50, 0, null, null, "интернета")).isEmpty();
        assertThat(service.listTickets(2L, all, 50, 0, null, null, "секрет")).isEmpty();
        assertThat(service.exportTickets(1L, all, "open", null, " иВан "))
                .extracting(BusinessScopedTicketReadService.TicketRow::userId).containsExactly(11L);
        assertThat(service.exportTickets(2L, all, "open", "same", "Иван"))
                .extracting(BusinessScopedTicketReadService.TicketRow::userId).containsExactly(11L, 22L);
        assertThat(service.exportTickets(2L, all, null, null, "секрет")).isEmpty();
        assertThat(service.listTickets(2L, all, 50, 0, null, null, "%_"))
                .extracting(BusinessScopedTicketReadService.TicketRow::userId).containsExactly(33L);
        var first = service.pageTickets(2L, all, 1, 0, "open", null, "Иван");
        assertThat(first.tickets()).extracting(BusinessScopedTicketReadService.TicketRow::userId)
                .containsExactly(11L);
        assertThat(first.hasMore()).isTrue();
        var second = service.pageTickets(2L, all, 1, 1, "open", null, "Иван");
        assertThat(second.tickets()).extracting(BusinessScopedTicketReadService.TicketRow::userId)
                .containsExactly(22L);
        assertThat(second.hasMore()).isFalse();
        assertThat(service.pageTickets(2L, all, 1, 0, "resolved", "other", "Скидка").tickets())
                .extracting(BusinessScopedTicketReadService.TicketRow::userId).containsExactly(33L);
        assertThatThrownBy(() -> service.pageTickets(2L, all, 1, 0, null, null, "  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.pageTickets(2L, all, 1, 0, null, null, "bad\n"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.pageTickets(2L, all, 1, 0, null, null, "x".repeat(101)))
                .isInstanceOf(IllegalArgumentException.class);

        jdbc.update("UPDATE business_memberships SET is_active = FALSE WHERE user_id = 1");
        BusinessAccessService staleAccess = mock(BusinessAccessService.class);
        when(staleAccess.requireSelectedBusiness(eq(1L), any()))
                .thenReturn(new BusinessAccessService.SelectedBusinessScope(Set.of(sushi), sushi));
        assertThat(new BusinessScopedTicketReadService(jdbc, staleAccess)
                .listTickets(1L, all, 50, 0, null, null, "Иван")).isEmpty();
        assertThat(new BusinessScopedTicketReadService(jdbc, staleAccess)
                .exportTickets(1L, all, null, null, "Иван")).isEmpty();
        jdbc.update("UPDATE businesses SET status = 'archived' WHERE id = ?", bliny);
        assertThat(service.listTickets(2L, all, 50, 0, null, null, "Иван"))
                .extracting(BusinessScopedTicketReadService.TicketRow::userId).containsExactly(11L);
        assertThat(service.exportTickets(2L, all, null, null, "Иван"))
                .extracting(BusinessScopedTicketReadService.TicketRow::userId).containsExactly(11L);
        jdbc.update("UPDATE messages SET client_name = ?, problem = ? WHERE group_msg_id = 101",
                "И".repeat(205), "П".repeat(505) + "TAIL");
        var fullTextMatch = service.exportTickets(2L, all, null, null, "tail");
        assertThat(fullTextMatch).hasSize(1);
        assertThat(fullTextMatch.get(0).clientName()).hasSize(200);
        assertThat(fullTextMatch.get(0).problemPreview()).hasSize(500);
        assertThat(service.findByPublicId(2L, "0123456789abcdef0123456789abcdef")
                .orElseThrow().problemPreview()).hasSize(500);
    }

    @Test
    void exportRejectsOversizedSnapshotWithoutTruncationAndRechecksScope() throws Exception {
        JdbcTemplate jdbc = freshSchema("business_scoped_ticket_export");
        jdbc.update("INSERT INTO users(id, username) VALUES (1, 'direct'), (2, 'all-reader')");
        migrate(jdbc);
        long sushi = businessId(jdbc, "sushi");
        long bliny = businessId(jdbc, "bliny");
        jdbc.update("INSERT INTO business_memberships(user_id, business_id, role_code) VALUES (1, ?, 'VIEWER')", sushi);
        jdbc.update("INSERT INTO business_access_grants(user_id, grant_scope, role_code) VALUES (2, 'ALL_BUSINESSES', 'VIEWER')");
        jdbc.update("INSERT INTO tickets(user_id, ticket_id, business_id) "
                + "SELECT 11, 'ticket-' || n, ? FROM generate_series(1, 1000) n", sushi);
        jdbc.update("INSERT INTO tickets(user_id, ticket_id, business_id) VALUES (22, 'other', ?)", bliny);
        BusinessScopedTicketReadService service = new BusinessScopedTicketReadService(
                jdbc, new BusinessAccessService(jdbc));
        var all = BusinessAccessService.AllAccessibleBusinesses.INSTANCE;

        assertThat(service.exportTickets(1L, all, null, null, null)).hasSize(1000);
        assertThat(service.exportTickets(1L, new BusinessAccessService.OneBusiness(sushi),
                "pending", "ticket-1", null)).hasSize(1);
        assertThatThrownBy(() -> service.exportTickets(1L,
                new BusinessAccessService.OneBusiness(bliny), null, null, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.exportTickets(2L, all, null, null, null))
                .isInstanceOf(BusinessScopedTicketReadService.ExportTooLargeException.class);
        assertThat(service.exportTickets(2L, new BusinessAccessService.OneBusiness(bliny),
                null, null, null)).extracting(BusinessScopedTicketReadService.TicketRow::ticketId)
                .containsExactly("other");
        assertThatThrownBy(() -> service.exportTickets(1L, all, "invalid", null, null))
                .isInstanceOf(IllegalArgumentException.class);

        jdbc.update("UPDATE business_memberships SET is_active = FALSE WHERE user_id = 1");
        assertThat(service.exportTickets(1L, all, null, null, null)).isEmpty();
        assertThatThrownBy(() -> service.exportTickets(1L,
                new BusinessAccessService.OneBusiness(sushi), null, null, null))
                .isInstanceOf(AccessDeniedException.class);
        BusinessAccessService staleAccess = mock(BusinessAccessService.class);
        when(staleAccess.requireSelectedBusiness(eq(1L), any()))
                .thenReturn(new BusinessAccessService.SelectedBusinessScope(Set.of(sushi), sushi));
        assertThat(new BusinessScopedTicketReadService(jdbc, staleAccess)
                .exportTickets(1L, all, null, null, null)).isEmpty();
    }

    @Test
    void summaryCountsOnlyCurrentScopeAndIncludesEmptyBusinesses() throws Exception {
        JdbcTemplate jdbc = freshSchema("business_scoped_summary");
        jdbc.update("INSERT INTO users(id, username) VALUES (1, 'direct'), (2, 'all-reader')");
        migrate(jdbc);
        long sushi = businessId(jdbc, "sushi");
        long bliny = businessId(jdbc, "bliny");
        jdbc.update("INSERT INTO businesses(code, display_name) VALUES ('flowers', 'Цветы')");
        long flowers = businessId(jdbc, "flowers");
        jdbc.update("INSERT INTO business_memberships(user_id, business_id, role_code) VALUES (1, ?, 'VIEWER')", sushi);
        jdbc.update("INSERT INTO business_access_grants(user_id, grant_scope, role_code) VALUES (2, 'ALL_BUSINESSES', 'VIEWER')");
        jdbc.update("INSERT INTO tickets(user_id, ticket_id, business_id, status) VALUES (1, 'same', ?, 'open')", sushi);
        jdbc.update("INSERT INTO tickets(user_id, ticket_id, business_id, status) VALUES (2, 'same', ?, 'unexpected')", bliny);
        jdbc.update("INSERT INTO tickets(user_id, ticket_id, status) VALUES (1, 'unresolved', 'closed')");
        BusinessAccessService access = new BusinessAccessService(jdbc);
        BusinessScopedTicketReadService service = new BusinessScopedTicketReadService(jdbc, access);

        var direct = service.summarizeTickets(1L, new BusinessAccessService.OneBusiness(sushi));
        assertThat(direct.total()).isEqualTo(1);
        assertThat(direct.open()).isEqualTo(1);
        assertThat(direct.businesses()).extracting(BusinessScopedTicketReadService.BusinessTicketCounts::businessId)
                .containsExactly(sushi);
        assertThatThrownBy(() -> service.summarizeTickets(1L, new BusinessAccessService.OneBusiness(bliny)))
                .isInstanceOf(AccessDeniedException.class);

        var all = service.summarizeTickets(2L, BusinessAccessService.AllAccessibleBusinesses.INSTANCE);
        assertThat(all.total()).isEqualTo(2);
        assertThat(all.open()).isEqualTo(1);
        assertThat(all.other()).isEqualTo(1);
        assertThat(all.pending()).isZero();
        assertThat(all.businesses()).extracting(BusinessScopedTicketReadService.BusinessTicketCounts::businessId)
                .containsExactly(sushi, bliny, flowers);
        assertThat(all.businesses().get(2).total()).isZero();
        assertThat(all.businesses().get(1).other()).isEqualTo(1);

        BusinessAdministrationService admin = new BusinessAdministrationService(jdbc);
        assertThat(admin.setBusinessStatus(bliny, "archived")).isTrue();
        var archived = service.summarizeTickets(2L, BusinessAccessService.AllAccessibleBusinesses.INSTANCE);
        assertThat(archived.total()).isEqualTo(1);
        assertThat(archived.businesses()).extracting(BusinessScopedTicketReadService.BusinessTicketCounts::businessId)
                .containsExactly(sushi, flowers);
        jdbc.update("UPDATE business_memberships SET is_active = FALSE WHERE user_id = 1");
        assertThat(service.summarizeTickets(1L, BusinessAccessService.AllAccessibleBusinesses.INSTANCE))
                .isEqualTo(new BusinessScopedTicketReadService.TicketSummary(0, 0, 0, 0, 0, 0, List.of()));
        BusinessAccessService staleAccess = mock(BusinessAccessService.class);
        when(staleAccess.requireSelectedBusiness(eq(1L), any()))
                .thenReturn(new BusinessAccessService.SelectedBusinessScope(Set.of(sushi), sushi));
        assertThat(new BusinessScopedTicketReadService(jdbc, staleAccess).summarizeTickets(
                1L, new BusinessAccessService.OneBusiness(sushi)).total()).isZero();
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
        assertThatThrownBy(() -> service.pageTickets(
                1L, new BusinessAccessService.OneBusiness(sushi), 50, 0, null, null))
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
        assertThat(staleService.listTickets(
                1L, new BusinessAccessService.OneBusiness(sushi), 50, 0, "pending", "ticket")).isEmpty();
        assertThat(staleService.pageTickets(
                1L, new BusinessAccessService.OneBusiness(sushi), 50, 0, null, null))
                .isEqualTo(new BusinessScopedTicketReadService.TicketPage(List.of(), false));
    }

    private JdbcTemplate freshSchema(String prefix) {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType(prefix);
        jdbc.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, username TEXT NOT NULL UNIQUE)");
        jdbc.execute("CREATE TABLE channels (id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE tickets (user_id BIGINT NOT NULL, ticket_id TEXT NOT NULL, "
                + "group_msg_id BIGINT, status TEXT DEFAULT 'pending', PRIMARY KEY (user_id, ticket_id))");
        jdbc.execute("CREATE TABLE messages (group_msg_id BIGINT PRIMARY KEY, user_id BIGINT, "
                + "ticket_id TEXT, client_name TEXT, problem TEXT)");
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
