package com.example.panel.support;

import com.example.panel.config.DatabaseMode;
import com.example.panel.config.ExternalDatabaseSettings;
import com.example.panel.config.PanelDatabaseRuntimeMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
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

    public String dateBucketExpression(String expression) {
        return isSqliteMode()
                ? "substr(" + sortableTimestampExpression(expression) + ", 1, 10)"
                : "to_char(" + expression + ", 'YYYY-MM-DD')";
    }

    public String stringAggregationExpression(String valueExpression, String delimiterLiteral, String orderExpression) {
        if (isSqliteMode()) {
            return "GROUP_CONCAT(" + valueExpression + ", " + delimiterLiteral + ")";
        }
        return "string_agg(" + valueExpression + ", " + delimiterLiteral + " ORDER BY " + orderExpression + ")";
    }

    public Object comparableTimestampParam(String rawValue) {
        if (isSqliteMode()) {
            return normalizeComparableTimestamp(rawValue);
        }
        Instant instant = parseInstant(rawValue);
        return instant != null ? Timestamp.from(instant) : rawValue;
    }

    public String normalizeComparableTimestamp(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().replace(' ', 'T');
        return normalized.length() > 19 ? normalized.substring(0, 19) : normalized;
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

    private Instant parseInstant(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        String raw = rawValue.trim();
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(raw).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(raw.replace(' ', 'T')).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            String compact = raw.replace(' ', 'T');
            if (compact.length() == 19) {
                return LocalDateTime.parse(compact).toInstant(ZoneOffset.UTC);
            }
        } catch (DateTimeParseException ignored) {
        }
        return null;
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
