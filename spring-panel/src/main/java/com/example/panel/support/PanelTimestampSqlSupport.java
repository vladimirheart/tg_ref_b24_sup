package com.example.panel.support;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PanelTimestampSqlSupport {

    public String comparableTimestampExpression(String expression) {
        return expression;
    }

    public String sortableTimestampExpression(String expression) {
        return expression;
    }

    public String orderByTimestampDesc(String expression) {
        return expression + " DESC NULLS LAST";
    }

    public String orderByTimestampAsc(String expression) {
        return expression + " ASC NULLS LAST";
    }

    public String dateBucketExpression(String expression) {
        return "to_char(" + expression + ", 'YYYY-MM-DD')";
    }

    public String stringAggregationExpression(String valueExpression, String delimiterLiteral, String orderExpression) {
        return "string_agg(" + valueExpression + ", " + delimiterLiteral + " ORDER BY " + orderExpression + ")";
    }

    public Object comparableTimestampParam(String rawValue) {
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
        return new SqlCondition(
                expression + " >= ?",
                new Object[]{Timestamp.from(Instant.now().minus(safeLookback(lookback)))}
        );
    }

    public SqlCondition between(String expression, Duration olderInclusive, Duration newerExclusive) {
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
