package com.example.panel.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.UncategorizedSQLException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class DialogServiceTest {

    @Test
    void summarizeDataAccessExceptionPrefersRootCauseWithoutSqlText() {
        SQLException sqlException = new SQLException("[SQLITE_ERROR] SQL error or missing database (no such column: t.created_at)");
        UncategorizedSQLException exception = new UncategorizedSQLException(
                "PreparedStatementCallback",
                "SELECT * FROM tickets WHERE created_at IS NOT NULL",
                sqlException
        );

        assertThat(DialogService.summarizeDataAccessException(exception))
                .isEqualTo("[SQLITE_ERROR] SQL error or missing database (no such column: t.created_at)");
    }

    @Test
    void summarizeDataAccessExceptionFallsBackToTrimmedMessage() {
        UncategorizedSQLException exception = new UncategorizedSQLException(
                "PreparedStatementCallback",
                "SELECT * FROM tickets",
                null
        );

        assertThat(DialogService.summarizeDataAccessException(exception))
                .isEqualTo("PreparedStatementCallback; uncategorized SQLException");
    }
}
