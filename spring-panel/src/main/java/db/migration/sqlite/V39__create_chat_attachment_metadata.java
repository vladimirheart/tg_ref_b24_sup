package db.migration.sqlite;

import com.example.panel.storage.AttachmentStorageKeyResolver;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;

public class V39__create_chat_attachment_metadata extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS chat_attachment_metadata (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        chat_history_id BIGINT NOT NULL UNIQUE REFERENCES chat_history(id) ON DELETE CASCADE,
                        ticket_id TEXT,
                        channel_id BIGINT,
                        storage_key TEXT,
                        storage_provider TEXT NOT NULL DEFAULT 'local_fs',
                        storage_class TEXT NOT NULL DEFAULT 'dialog_attachment',
                        original_name TEXT,
                        mime_type TEXT,
                        size BIGINT,
                        content_hash TEXT,
                        legacy_attachment_ref TEXT,
                        normalization_status TEXT NOT NULL DEFAULT 'normalized',
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TEXT,
                        archived_at TEXT,
                        deleted_at TEXT,
                        CHECK (normalization_status IN ('normalized', 'unresolved'))
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_chat_attachment_metadata_ticket
                    ON chat_attachment_metadata(ticket_id, chat_history_id)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_chat_attachment_metadata_storage_key
                    ON chat_attachment_metadata(storage_key)
                    """);
        }

        boolean hasFileName = hasColumn(connection, "chat_history", "file_name");
        boolean hasMessageType = hasColumn(connection, "chat_history", "message_type");
        String fileNameColumn = hasFileName ? "file_name" : "NULL AS file_name";
        String messageTypeColumn = hasMessageType ? "message_type" : "NULL AS message_type";
        String selectSql = """
                SELECT id, ticket_id, channel_id, attachment, %s, %s
                  FROM chat_history
                 WHERE attachment IS NOT NULL
                   AND TRIM(attachment) <> ''
                   AND NOT EXISTS (
                       SELECT 1
                         FROM chat_attachment_metadata cam
                        WHERE cam.chat_history_id = chat_history.id
                   )
                """.formatted(fileNameColumn, messageTypeColumn);

        String insertSql = """
                INSERT INTO chat_attachment_metadata (
                    chat_history_id,
                    ticket_id,
                    channel_id,
                    storage_key,
                    storage_provider,
                    storage_class,
                    original_name,
                    mime_type,
                    size,
                    content_hash,
                    legacy_attachment_ref,
                    normalization_status,
                    created_at,
                    updated_at,
                    archived_at,
                    deleted_at
                ) VALUES (?, ?, ?, ?, 'local_fs', 'dialog_attachment', ?, ?, NULL, NULL, ?, ?, ?, ?, NULL, NULL)
                """;

        try (PreparedStatement select = connection.prepareStatement(selectSql);
             ResultSet resultSet = select.executeQuery();
             PreparedStatement insert = connection.prepareStatement(insertSql)) {
            while (resultSet.next()) {
                long chatHistoryId = resultSet.getLong("id");
                String ticketId = trim(resultSet.getString("ticket_id"));
                Long channelId = resultSet.getObject("channel_id") == null ? null : resultSet.getLong("channel_id");
                String attachment = trim(resultSet.getString("attachment"));
                String originalName = AttachmentStorageKeyResolver.resolveOriginalName(
                        trim(resultSet.getString("file_name")),
                        attachment,
                        AttachmentStorageKeyResolver.normalizeStorageKey(ticketId, attachment)
                );
                String storageKey = AttachmentStorageKeyResolver.normalizeStorageKey(ticketId, attachment);
                String mimeType = AttachmentStorageKeyResolver.guessMimeType(
                        null,
                        originalName,
                        storageKey,
                        trim(resultSet.getString("message_type"))
                );
                String timestamp = OffsetDateTime.now().toString();

                insert.setLong(1, chatHistoryId);
                insert.setString(2, ticketId);
                if (channelId == null) {
                    insert.setObject(3, null);
                } else {
                    insert.setLong(3, channelId);
                }
                insert.setString(4, trim(storageKey));
                insert.setString(5, trim(originalName));
                insert.setString(6, trim(mimeType));
                insert.setString(7, attachment);
                insert.setString(8, StringUtils.hasText(storageKey) ? "normalized" : "unresolved");
                insert.setString(9, timestamp);
                insert.setString(10, timestamp);
                insert.executeUpdate();
            }
        }
    }

    private boolean hasColumn(Connection connection, String table, String column) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info('" + table + "')")) {
            while (resultSet.next()) {
                if (column.equalsIgnoreCase(resultSet.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private String trim(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
