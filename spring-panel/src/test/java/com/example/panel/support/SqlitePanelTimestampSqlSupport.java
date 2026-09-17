package com.example.panel.support;

import java.time.Duration;

/** Test-fixture SQL dialect shim. SQLite is not a spring-panel production runtime mode. */
public class SqlitePanelTimestampSqlSupport extends PanelTimestampSqlSupport {

    @Override
    public String comparableTimestampExpression(String expression) {
        return "datetime(substr(COALESCE(" + expression + ", ''), 1, 19))";
    }

    @Override
    public String sortableTimestampExpression(String expression) {
        return "substr(COALESCE(" + expression + ", ''), 1, 19)";
    }

    @Override
    public String orderByTimestampDesc(String expression) {
        return sortableTimestampExpression(expression) + " DESC";
    }

    @Override
    public String orderByTimestampAsc(String expression) {
        return sortableTimestampExpression(expression) + " ASC";
    }

    @Override
    public String dateBucketExpression(String expression) {
        return "substr(" + sortableTimestampExpression(expression) + ", 1, 10)";
    }

    @Override
    public String stringAggregationExpression(String valueExpression, String delimiterLiteral, String orderExpression) {
        return "GROUP_CONCAT(" + valueExpression + ", " + delimiterLiteral + ")";
    }

    @Override
    public Object comparableTimestampParam(String rawValue) {
        return normalizeComparableTimestamp(rawValue);
    }

    @Override
    public SqlCondition since(String expression, Duration lookback) {
        return new SqlCondition(
                comparableTimestampExpression(expression) + " >= datetime('now', ?)",
                new Object[]{sqliteLookbackModifier(lookback)}
        );
    }

    @Override
    public SqlCondition between(String expression, Duration olderInclusive, Duration newerExclusive) {
        return new SqlCondition(
                comparableTimestampExpression(expression) + " >= datetime('now', ?)"
                        + " AND "
                        + comparableTimestampExpression(expression) + " < datetime('now', ?)",
                new Object[]{sqliteLookbackModifier(olderInclusive), sqliteLookbackModifier(newerExclusive)}
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
}
