package db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V6_1__fix_client_phones_schema extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (!tableExists(connection, "client_phones")) {
            return;
        }

        Set<String> columns = loadColumns(connection, "client_phones");

        if (!columns.contains("user_id") && columns.contains("client_id")) {
            addColumn(connection, "client_phones", "user_id", "BIGINT");
            columns.add("user_id");
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("UPDATE client_phones SET user_id = client_id WHERE user_id IS NULL");
            }
        }

        addColumnIfMissing(connection, columns, "client_phones", "label", "TEXT");
        addColumnIfMissing(connection, columns, "client_phones", "is_active", "BOOLEAN");
        addColumnIfMissing(connection, columns, "client_phones", "created_by", "TEXT");

        if (columns.contains("is_active")) {
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("UPDATE client_phones SET is_active = 1 WHERE is_active IS NULL");
            }
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet rs = metaData.getTables(null, null, tableName, null)) {
            return rs.next();
        }
    }

    private Set<String> loadColumns(Connection connection, String tableName) throws Exception {
        Set<String> columns = new HashSet<>();
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet rs = metaData.getColumns(null, null, tableName, null)) {
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                if (name != null) {
                    columns.add(name.toLowerCase(Locale.ROOT));
                }
            }
        }
        return columns;
    }

    private void addColumnIfMissing(Connection connection, Set<String> columns, String tableName, String columnName, String type)
        throws Exception {
        String normalized = columnName.toLowerCase(Locale.ROOT);
        if (!columns.contains(normalized)) {
            addColumn(connection, tableName, columnName, type);
            columns.add(normalized);
        }
    }

    private void addColumn(Connection connection, String tableName, String columnName, String type) throws Exception {
        String sql = String.format("ALTER TABLE %s ADD COLUMN %s %s", tableName, columnName, type);
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }
}
