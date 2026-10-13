package com.example.panel.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.panel.service.LegacyTicketIdJdbcGuard;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class TicketAttachmentOwnerJdbcGuardTest {
    private record Fixture(JdbcTemplate jdbc, TicketAttachmentOwnerJdbcGuard guard) { }

    private Fixture fixture() {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate("attachment_owner");
        jdbc.execute("CREATE TABLE tickets(user_id BIGINT, ticket_id TEXT, PRIMARY KEY(user_id, ticket_id))");
        jdbc.execute("CREATE TABLE chat_history(id BIGINT PRIMARY KEY, ticket_id TEXT)");
        jdbc.execute("""
                CREATE TABLE chat_attachment_metadata(
                    chat_history_id BIGINT UNIQUE REFERENCES chat_history(id), ticket_id TEXT,
                    storage_key TEXT, storage_provider TEXT DEFAULT 'local_fs',
                    storage_class TEXT DEFAULT 'dialog_attachment', normalization_status TEXT DEFAULT 'normalized',
                    deleted_at TIMESTAMPTZ)
                """);
        return new Fixture(jdbc, new TicketAttachmentOwnerJdbcGuard(jdbc, new LegacyTicketIdJdbcGuard(jdbc)));
    }

    private void ticket(Fixture fixture, long userId, String ticketId) {
        fixture.jdbc().update("INSERT INTO tickets VALUES (?,?)", userId, ticketId);
    }

    private void metadata(Fixture fixture, long historyId, String historyTicketId, String metadataTicketId, String key) {
        fixture.jdbc().update("INSERT INTO chat_history VALUES (?,?)", historyId, historyTicketId);
        fixture.jdbc().update("INSERT INTO chat_attachment_metadata(chat_history_id,ticket_id,storage_key) VALUES (?,?,?)",
                historyId, metadataTicketId, key);
    }

    @ParameterizedTest
    @ValueSource(strings = {"T-EXACT", " T-EXACT ", " T/100?&%#+ Ю ", " null ", "T%2F+", "../opaque"})
    void metadataPreservesExactOwnerAndDoesNotUseChannelNamespaceOrTrimmedSibling(String owner) {
        Fixture fixture = fixture();
        ticket(fixture, 1, owner);
        if (!owner.equals(owner.trim())) ticket(fixture, 2, owner.trim());
        ticket(fixture, 3, "channel");
        metadata(fixture, 11, owner, owner, "channel/2026/photo.jpg");

        assertThat(fixture.guard().resolveUniqueOwner("channel/2026/photo.jpg")).isEqualTo(owner);
    }

    @Test
    void multipleMessagesWithSameExactOwnerRemainReadable() {
        Fixture fixture = fixture();
        ticket(fixture, 1, "T");
        metadata(fixture, 11, "T", "T", "channel/photo.jpg");
        metadata(fixture, 12, "T", "T", "channel/photo.jpg");
        assertThat(fixture.guard().resolveUniqueOwner("channel/photo.jpg")).isEqualTo("T");
    }

    @Test
    void differentMetadataOwnersDoNotFallBackToAnExistingPrefixTicket() {
        Fixture fixture = fixture();
        ticket(fixture, 1, "T"); ticket(fixture, 2, "U");
        metadata(fixture, 11, "T", "T", "T/photo.jpg");
        metadata(fixture, 12, "U", "U", "T/photo.jpg");
        assertThat(fixture.guard().resolveUniqueOwner("T/photo.jpg")).isNull();
    }

    @Test
    void metadataAndHistoryMismatchIsDeniedWithoutPrefixFallback() {
        Fixture fixture = fixture();
        ticket(fixture, 1, "T"); ticket(fixture, 2, "U");
        metadata(fixture, 11, "T", "U", "T/photo.jpg");
        assertThat(fixture.guard().resolveUniqueOwner("T/photo.jpg")).isNull();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void unknownMetadataOwnerCannotUseHistoryOrDirectoryAsFallback(String owner) {
        Fixture fixture = fixture();
        ticket(fixture, 1, "T");
        metadata(fixture, 11, "T", owner, "T/photo.jpg");
        assertThat(fixture.guard().resolveUniqueOwner("T/photo.jpg")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"deleted", "provider", "class", "normalization"})
    void invalidOrDeletedMetadataNeverReactivatesLegacyPrefixFallback(String invalid) {
        Fixture fixture = fixture();
        ticket(fixture, 1, "T");
        metadata(fixture, 11, "T", "T", "T/photo.jpg");
        switch (invalid) {
            case "deleted" -> fixture.jdbc().update("UPDATE chat_attachment_metadata SET deleted_at=CURRENT_TIMESTAMP");
            case "provider" -> fixture.jdbc().update("UPDATE chat_attachment_metadata SET storage_provider='external_url'");
            case "class" -> fixture.jdbc().update("UPDATE chat_attachment_metadata SET storage_class='knowledge_base'");
            case "normalization" -> fixture.jdbc().update("UPDATE chat_attachment_metadata SET normalization_status='unresolved'");
            default -> throw new IllegalArgumentException(invalid);
        }
        assertThat(fixture.guard().resolveUniqueOwner("T/photo.jpg")).isNull();
    }

    @Test
    void oneDeletedReferenceCannotHideBehindAnotherActiveReference() {
        Fixture fixture = fixture();
        ticket(fixture, 1, "T");
        metadata(fixture, 11, "T", "T", "T/photo.jpg");
        metadata(fixture, 12, "T", "T", "T/photo.jpg");
        fixture.jdbc().update("UPDATE chat_attachment_metadata SET deleted_at=CURRENT_TIMESTAMP WHERE chat_history_id=12");
        assertThat(fixture.guard().resolveUniqueOwner("T/photo.jpg")).isNull();
    }

    @Test
    void duplicateLegacyOwnerIsDeniedEvenWithMatchingMetadata() {
        Fixture fixture = fixture();
        ticket(fixture, 1, "T"); ticket(fixture, 2, "T");
        metadata(fixture, 11, "T", "T", "channel/photo.jpg");
        assertThat(fixture.guard().resolveUniqueOwner("channel/photo.jpg")).isNull();
    }

    @Test
    void absentMetadataOwnerIsNotReplacedWithExistingPrefixTicket() {
        Fixture fixture = fixture();
        ticket(fixture, 1, "T");
        metadata(fixture, 11, "ABSENT", "ABSENT", "T/photo.jpg");
        assertThat(fixture.guard().resolveUniqueOwner("T/photo.jpg")).isNull();
    }

    @Test
    void exactLegacyTicketDirectoryStillWorksWithoutMetadata() {
        Fixture fixture = fixture();
        ticket(fixture, 1, "T");
        assertThat(fixture.guard().resolveUniqueOwner("T/2026/10/photo.jpg")).isEqualTo("T");
        assertThat(fixture.guard().hasDirectOwner("T", "2026/10/photo.jpg")).isTrue();
    }

    @Test
    void nestedTicketPrefixCollisionIsDeniedWithoutMetadata() {
        Fixture fixture = fixture();
        ticket(fixture, 1, "T"); ticket(fixture, 2, "T/opaque");
        assertThat(fixture.guard().resolveUniqueOwner("T/opaque/photo.jpg")).isNull();
    }

    @Test
    void nestedOpaqueIdIsNotGuessedWhenFirstLegacyDirectoryOwnerIsAbsent() {
        Fixture fixture = fixture();
        ticket(fixture, 1, "T/opaque");
        assertThat(fixture.guard().resolveUniqueOwner("T/opaque/photo.jpg")).isNull();
    }

    @Test
    void duplicateLegacyDirectoryOwnerIsDeniedWithoutMetadata() {
        Fixture fixture = fixture();
        ticket(fixture, 1, "T"); ticket(fixture, 2, "T");
        assertThat(fixture.guard().resolveUniqueOwner("T/photo.jpg")).isNull();
    }

    @Test
    void absentLegacyDirectoryOwnerIsDenied() {
        Fixture fixture = fixture();
        assertThat(fixture.guard().resolveUniqueOwner("T/photo.jpg")).isNull();
    }

    @Test
    void percentEncodedLookingOwnerIsComparedLiterallyNotDecoded() {
        Fixture fixture = fixture();
        ticket(fixture, 1, "T%2F"); ticket(fixture, 2, "T");
        assertThat(fixture.guard().resolveUniqueOwner("T%2F/photo.jpg")).isEqualTo("T%2F");
    }

    @Test
    void directDownloadRequiresSameExactOwnerAsPhysicalKey() {
        Fixture fixture = fixture();
        ticket(fixture, 1, " T "); ticket(fixture, 2, "T");
        metadata(fixture, 11, " T ", " T ", "T/photo.jpg");
        assertThat(fixture.guard().hasDirectOwner(" T ", "photo.jpg")).isTrue();
        assertThat(fixture.guard().hasDirectOwner("T", "photo.jpg")).isFalse();
    }

    @Test
    void directPaddedIdCannotAliasPlainDirectoryWithoutMetadata() {
        Fixture fixture = fixture();
        ticket(fixture, 1, " T "); ticket(fixture, 2, "T");
        assertThat(fixture.guard().hasDirectOwner(" T ", "photo.jpg")).isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "file.jpg", "T/", "T//photo.jpg", "T/../U/photo.jpg", "T/./photo.jpg",
            "T /photo.jpg", "T/ folder/photo.jpg", "T/photo.", "T/\nphoto.jpg", "T/\u0000photo.jpg"})
    void unsafeOrUnscopedKeyIsRejectedBeforeJdbc(String key) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacyTicketIdJdbcGuard tickets = mock(LegacyTicketIdJdbcGuard.class);
        assertThat(new TicketAttachmentOwnerJdbcGuard(jdbc, tickets).resolveUniqueOwner(key)).isNull();
        verifyNoInteractions(jdbc, tickets);
    }

    @Test
    void failedMetadataLookupDoesNotEnableDirectoryFallback() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacyTicketIdJdbcGuard tickets = mock(LegacyTicketIdJdbcGuard.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenThrow(new DataAccessResourceFailureException("offline"));
        assertThat(new TicketAttachmentOwnerJdbcGuard(jdbc, tickets).resolveUniqueOwner("T/photo.jpg")).isNull();
        verifyNoInteractions(tickets);
    }
}
