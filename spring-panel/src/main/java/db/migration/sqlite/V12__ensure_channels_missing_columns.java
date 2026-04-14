package db.migration.sqlite;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Ensures older SQLite databases have all columns required by current Channel entity.
 * This avoids startup crashes like: "no such column: c1_0.support_chat_id".
 */
public class V12__ensure_channels_missing_columns extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection c = context.getConnection();

        // Columns expected by Channel entity (and used in SELECTs)
        ensureColumn(c, "channels", "platform", "TEXT");
        ensureColumn(c, "channels", "platform_config", "TEXT");

        ensureColumn(c, "channels", "support_chat_id", "BIGINT");
        ensureColumn(c, "channels", "bot_username", "TEXT");

        ensureColumn(c, "channels", "filters", "TEXT");
        ensureColumn(c, "channels", "delivery_settings", "TEXT");
        ensureColumn(c, "channels", "description", "TEXT");

        ensureColumn(c, "channels", "questions_cfg", "TEXT");
        ensureColumn(c, "channels", "max_questions", "INTEGER");

        ensureColumn(c, "channels", "question_template_id", "TEXT");
        ensureColumn(c, "channels", "rating_template_id", "TEXT");
        ensureColumn(c, "channels", "auto_action_template_id", "TEXT");

        ensureColumn(c, "channels", "public_id", "TEXT");

        // These обычно есть в старых схемах, но на всякий случай:
        ensureColumn(c, "channels", "token", "TEXT");
        ensureColumn(c, "channels", "bot_name", "TEXT");
        ensureColumn(c, "channels", "channel_name", "TEXT");
        ensureColumn(c, "channels", "credential_id", "BIGINT");

        ensureColumn(c, "channels", "is_active", "BOOLEAN");
        ensureColumn(c, "channels", "created_at", "DATETIME");
        ensureColumn(c, "channels", "updated_at", "DATETIME");
    }

    private void ensureColumn(Connection connection, String tableName, String columnName, String ddlType) throws Exception {
        if (columnExists(connection, tableName, columnName)) {
            return;
        }
        try (Statement st = connection.createStatement()) {
            st.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + ddlType);
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws Exception {
        String sql = "SELECT 1 FROM pragma_table_info('" + tableName + "') WHERE name = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
