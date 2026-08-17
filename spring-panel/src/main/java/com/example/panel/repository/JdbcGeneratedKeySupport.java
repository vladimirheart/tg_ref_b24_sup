package com.example.panel.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

final class JdbcGeneratedKeySupport {

    private JdbcGeneratedKeySupport() {
    }

    static Long extractGeneratedKey(PreparedStatement statement, Connection connection) throws SQLException {
        try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
            if (generatedKeys != null && generatedKeys.next()) {
                return generatedKeys.getLong(1);
            }
        } catch (SQLFeatureNotSupportedException ex) {
            // Fall through to SQLite-compatible last_insert_rowid() fallback.
        }

        try (PreparedStatement fallback = connection.prepareStatement("SELECT last_insert_rowid()");
             ResultSet rs = fallback.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        return null;
    }
}
