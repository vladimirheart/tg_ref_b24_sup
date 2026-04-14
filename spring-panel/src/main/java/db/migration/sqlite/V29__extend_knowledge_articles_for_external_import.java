package db.migration.sqlite;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class V29__extend_knowledge_articles_for_external_import extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        ensureColumn(context.getConnection(), "knowledge_articles", "external_source", "TEXT");
        ensureColumn(context.getConnection(), "knowledge_articles", "external_id", "TEXT");
        ensureColumn(context.getConnection(), "knowledge_articles", "external_url", "TEXT");
        ensureColumn(context.getConnection(), "knowledge_articles", "external_updated_at", "TEXT");
        ensureIndex(context.getConnection(),
            "idx_knowledge_articles_external_source_id",
            "CREATE INDEX IF NOT EXISTS idx_knowledge_articles_external_source_id "
                + "ON knowledge_articles(external_source, external_id)");
    }

    private void ensureColumn(Connection connection, String tableName, String columnName, String ddlType) throws SQLException {
        if (hasColumn(connection, tableName, columnName)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + ddlType);
        }
    }

    private boolean hasColumn(Connection connection, String tableName, String columnName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (resultSet.next()) {
                if (columnName.equalsIgnoreCase(resultSet.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private void ensureIndex(Connection connection, String indexName, String ddl) throws SQLException {
        if (hasIndex(connection, indexName)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(ddl);
        }
    }

    private boolean hasIndex(Connection connection, String indexName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA index_list(knowledge_articles)")) {
            while (resultSet.next()) {
                if (indexName.equalsIgnoreCase(resultSet.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }
}
