package com.example.panel.support;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

public final class JdbcSchemaInspector {

    private JdbcSchemaInspector() {
    }

    public static Set<String> loadColumnNames(JdbcTemplate jdbcTemplate, String tableName) {
        if (jdbcTemplate == null || !StringUtils.hasText(tableName)) {
            return Set.of();
        }
        Set<String> columns = jdbcTemplate.execute((ConnectionCallback<Set<String>>) connection ->
            loadColumnNames(connection, tableName)
        );
        return columns != null ? columns : Set.of();
    }

    private static Set<String> loadColumnNames(Connection connection, String tableName) throws SQLException {
        Set<String> columns = new LinkedHashSet<>();
        DatabaseMetaData metaData = connection.getMetaData();
        collectColumns(metaData, columns, tableName);
        if (!columns.isEmpty()) {
            return columns;
        }
        collectColumns(metaData, columns, tableName.toUpperCase(Locale.ROOT));
        return columns;
    }

    private static void collectColumns(DatabaseMetaData metaData,
                                       Set<String> columns,
                                       String tableName) throws SQLException {
        try (ResultSet resultSet = metaData.getColumns(null, null, tableName, null)) {
            while (resultSet.next()) {
                String columnName = resultSet.getString("COLUMN_NAME");
                if (StringUtils.hasText(columnName)) {
                    columns.add(columnName.toLowerCase(Locale.ROOT));
                }
            }
        }
    }
}
