package db.migration;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V6__add_auto_action_template_id_to_channels extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        if (columnExists(context, "channels", "auto_action_template_id")) {
            return;
        }
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("ALTER TABLE channels ADD COLUMN auto_action_template_id TEXT");
        }
    }

    private boolean columnExists(Context context, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metaData = context.getConnection().getMetaData();
        try (ResultSet columns = metaData.getColumns(null, null, tableName, null)) {
            while (columns.next()) {
                String existing = columns.getString("COLUMN_NAME");
                if (existing != null && existing.equalsIgnoreCase(columnName)) {
                    return true;
                }
            }
        }
        return false;
    }
}
