package com.example.panel.security;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PortalActivityPressurePostgresqlTest {
    @Test void activitySkipsLockedAccountsThrottlesWritesAndKeepsExactIdentity() throws Exception {
        var jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("activity_pressure_319");
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT,last_portal_activity_at TIMESTAMPTZ)");
        jdbc.update("INSERT INTO users VALUES(7,' reader ',NULL),(8,'reader',NULL)");
        var at=OffsetDateTime.parse("2026-11-25T15:20:00Z");
        jdbc.setQueryTimeout(1);
        try(var lock=jdbc.getDataSource().getConnection()) {
            lock.setAutoCommit(false);
            try(var statement=lock.createStatement()){statement.execute("SELECT id FROM users WHERE id=7 FOR SHARE");}
            assertThat(jdbc.update(UserLastActivityFilter.TOUCH_SQL,at,7L," reader ")).isZero();
            lock.rollback();
        }
        assertThat(jdbc.update(UserLastActivityFilter.TOUCH_SQL,at,7L,"reader")).isZero();
        assertThat(jdbc.update(UserLastActivityFilter.TOUCH_SQL,at,7L," reader ")).isEqualTo(1);
        assertThat(jdbc.update(UserLastActivityFilter.TOUCH_SQL,at.plusSeconds(5),7L," reader ")).isZero();
        assertThat(jdbc.update(UserLastActivityFilter.TOUCH_SQL,at.plusSeconds(31),7L," reader ")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT last_portal_activity_at IS NULL FROM users WHERE id=8",Boolean.class)).isTrue();
    }
}
