package db.migration.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V11__ensure_channels_platform_columns extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection c = context.getConnection();

        // то, что сейчас прямо ломает запуск
        ensureColumn(c, "channels", "platform",
                "ALTER TABLE channels ADD COLUMN platform TEXT DEFAULT 'telegram'");

        // чтобы не получить следующую ошибку сразу после platform
        ensureColumn(c, "channels", "platform_config",
                "ALTER TABLE channels ADD COLUMN platform_config TEXT DEFAULT '{}'");
        ensureColumn(c, "channels", "public_id",
                "ALTER TABLE channels ADD COLUMN public_id VARCHAR(255)");
        ensureColumn(c, "channels", "bot_username",
                "ALTER TABLE channels ADD COLUMN bot_username TEXT");

        // привести старые строки к дефолтам, если вдруг остались NULL
        try (Statement st = c.createStatement()) {
            st.execute("UPDATE channels SET platform='telegram' WHERE platform IS NULL");
            st.execute("UPDATE channels SET platform_config='{}' WHERE platform_config IS NULL");
        }
    }

    private void ensureColumn(Connection connection, String tableName, String columnName, String ddl) throws SQLException {
        if (columnExists(connection, tableName, columnName)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(ddl);
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        String sql = String.format("SELECT 1 FROM pragma_table_info('%s') WHERE name = ?", tableName);
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
