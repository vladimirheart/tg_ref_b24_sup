package com.example.panel.support;

import com.example.panel.config.DatabaseMode;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PanelTimestampSqlSupportTest {

    @Test
    void sqliteModeBuildsDatetimePredicateWithRelativeModifier() {
        PanelTimestampSqlSupport support = new PanelTimestampSqlSupport(DatabaseMode.SQLITE);

        PanelTimestampSqlSupport.SqlCondition condition = support.since("created_at", Duration.ofMinutes(60));

        assertThat(condition.sql()).isEqualTo("datetime(substr(COALESCE(created_at, ''), 1, 19)) >= datetime('now', ?)");
        assertThat(condition.params()).containsExactly("-60 minutes");
        assertThat(support.orderByTimestampDesc("created_at")).isEqualTo("substr(COALESCE(created_at, ''), 1, 19) DESC");
    }

    @Test
    void postgresModeBuildsTypedPredicateWithNullSafeOrdering() {
        PanelTimestampSqlSupport support = new PanelTimestampSqlSupport(DatabaseMode.POSTGRESQL);

        PanelTimestampSqlSupport.SqlCondition condition = support.between("COALESCE(updated_at, created_at)", Duration.ofDays(14), Duration.ofDays(7));

        assertThat(condition.sql()).isEqualTo("COALESCE(updated_at, created_at) >= ? AND COALESCE(updated_at, created_at) < ?");
        assertThat(condition.params()).hasSize(2);
        assertThat(support.orderByTimestampDesc("event_at")).isEqualTo("event_at DESC NULLS LAST");
    }
}
