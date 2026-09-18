package com.example.panel.repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

final class JdbcGeneratedKeySupport {

    private JdbcGeneratedKeySupport() {
    }

    static Long extractGeneratedKey(PreparedStatement statement) throws SQLException {
        try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
            if (generatedKeys != null && generatedKeys.next()) {
                return generatedKeys.getLong(1);
            }
        }
        return null;
    }
}
