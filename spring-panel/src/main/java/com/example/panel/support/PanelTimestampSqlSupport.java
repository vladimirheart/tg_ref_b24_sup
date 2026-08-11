package com.example.panel.support;

import com.example.panel.config.DatabaseMode;
import com.example.panel.config.ExternalDatabaseSettings;
import com.example.panel.config.PanelDatabaseRuntimeMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PanelTimestampSqlSupport {

    private final DatabaseMode databaseMode;

    public PanelTimestampSqlSupport(DatabaseMode databaseMode) {
        this.databaseMode = databaseMode != null ? databaseMode : DatabaseMode.SQLITE;
    }

    @Autowired
    public PanelTimestampSqlSupport(PanelDatabaseRuntimeMode runtimeMode) {
        this(resolveDatabaseMode(runtimeMode));
    }

    private static DatabaseMode resolveDatabaseMode(PanelDatabaseRuntimeMode runtimeMode) {
        if (runtimeMode == null || runtimeMode.isSqliteMode()) {
            return DatabaseMode.SQLITE;
        }
        return runtimeMode.externalSettings()
                .map(ExternalDatabaseSettings::vendor)
                .orElse(runtimeMode.configuredMode());
    }

    public boolean isSqliteMode() {
        return databaseMode == DatabaseMode.SQLITE;
    }

    public String comparableTimestampExpression(String expression) {
        return isSqliteMode()
                ? "datetime(substr(COALESCE(" + expression + ", ''), 1, 19))"
                : expression;
    }

    public String sortableTimestampExpression(String expression) {
        return isSqliteMode()
                ? "substr(COALESCE(" + expression + ", ''), 1, 19)"
                : expression;
    }

    public String orderByTimestampDesc(String expression) {
        return isSqliteMode()
                ? sortableTimestampExpression(expression) + " DESC"
                : expression + " DESC NULLS LAST";
    }

    public String orderByTimestampAsc(String expression) {
        return isSqliteMode()
                ? sortableTimestampExpression(expression) + " ASC"
                : expression + " ASC NULLS LAST";
    }

    public SqlCondition since(String expression, Duration lookback) {
        if (isSqliteMode()) {
            return new SqlCondition(
                    comparableTimestampExpression(expression) + " >= datetime('now', ?)",
                    new Object[]{sqliteLookbackModifier(lookback)}
            );
        }
        return new SqlCondition(
                expression + " >= ?",
                new Object[]{Timestamp.from(Instant.now().minus(safeLookback(lookback)))}
        );
    }

    public SqlCondition between(String expression, Duration olderInclusive, Duration newerExclusive) {
        if (isSqliteMode()) {
            return new SqlCondition(
                    comparableTimestampExpression(expression) + " >= datetime('now', ?)"
                            + " AND "
                            + comparableTimestampExpression(expression) + " < datetime('now', ?)",
                    new Object[]{sqliteLookbackModifier(olderInclusive), sqliteLookbackModifier(newerExclusive)}
            );
        }
        return new SqlCondition(
                expression + " >= ? AND " + expression + " < ?",
                new Object[]{
                        Timestamp.from(Instant.now().minus(safeLookback(olderInclusive))),
                        Timestamp.from(Instant.now().minus(safeLookback(newerExclusive)))
                }
        );
    }

    private Duration safeLookback(Duration lookback) {
        return lookback == null || lookback.isNegative() ? Duration.ZERO : lookback;
    }

    private String sqliteLookbackModifier(Duration lookback) {
        long seconds = safeLookback(lookback).getSeconds();
        if (seconds % 86_400L == 0L) {
            long days = seconds / 86_400L;
            return "-" + days + " " + unitName(days, "day");
        }
        if (seconds % 60L == 0L) {
            long minutes = seconds / 60L;
            return "-" + minutes + " " + unitName(minutes, "minute");
        }
        return "-" + seconds + " " + unitName(seconds, "second");
    }

    private String unitName(long value, String baseName) {
        return Math.abs(value) == 1L ? baseName : baseName + "s";
    }

    public record SqlCondition(String sql, Object[] params) {

        public Object[] bind(Object... leading) {
            Object[] prefix = leading != null ? leading : new Object[0];
            Object[] tail = params != null ? params : new Object[0];
            Object[] combined = Arrays.copyOf(prefix, prefix.length + tail.length);
            System.arraycopy(tail, 0, combined, prefix.length, tail.length);
            return combined;
        }
    }
}
