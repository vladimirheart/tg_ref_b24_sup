package com.example.panel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class BackendOpsCommandServiceTest {

    private SingleConnectionDataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private BackendOpsCommandService service;

    @BeforeEach
    void setUp() {
        dataSource = new SingleConnectionDataSource(
            "jdbc:sqlite::memory:",
            true
        );
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
            CREATE TABLE backend_ops_command (
                command_id TEXT PRIMARY KEY,
                command_type TEXT NOT NULL,
                scope_key TEXT NOT NULL,
                active_key TEXT UNIQUE,
                payload_json TEXT NOT NULL DEFAULT '{}',
                status TEXT NOT NULL,
                requested_by TEXT,
                requested_at TIMESTAMP NOT NULL,
                available_at TIMESTAMP NOT NULL,
                claimed_by TEXT,
                claimed_at TIMESTAMP,
                heartbeat_at TIMESTAMP,
                completed_at TIMESTAMP,
                progress_percent INTEGER NOT NULL DEFAULT 0,
                progress_message TEXT,
                result_json TEXT,
                last_error TEXT,
                attempt_count INTEGER NOT NULL DEFAULT 0,
                updated_at TIMESTAMP NOT NULL
            )
            """);
        service = new BackendOpsCommandService(
            jdbcTemplate,
            new ObjectMapper()
        );
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void activeCommandTypeIsDeduplicatedAcrossDifferentScopes() {
        BackendOpsCommandService.EnqueueResult first =
            service.enqueueExclusive(
                BackendOpsCommandTypes.IIKO_API_REFRESH,
                "all",
                Map.of(),
                "web-1"
            );

        BackendOpsCommandService.EnqueueResult second =
            service.enqueueExclusive(
                BackendOpsCommandTypes.IIKO_API_REFRESH,
                "monitor:42",
                Map.of("monitor_id", 42L),
                "web-2"
            );

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.command().commandId())
            .isEqualTo(first.command().commandId());
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM backend_ops_command",
            Integer.class
        )).isEqualTo(1);
    }

    @Test
    void databaseClaimAllowsOnlyOneWorkerAndTerminalStateUnlocksNextCommand() {
        BackendOpsCommandService.EnqueueResult queued =
            service.enqueueExclusive(
                BackendOpsCommandTypes.NETBOX_PASSPORTS_SYNC,
                "global",
                Map.of("trigger", "manual"),
                "operator"
            );

        BackendOpsCommandService.CommandSnapshot claimed =
            service.claimNext("worker-a", Duration.ofHours(2))
                .orElseThrow();

        assertThat(claimed.commandId())
            .isEqualTo(queued.command().commandId());
        assertThat(claimed.running()).isTrue();
        assertThat(claimed.claimedBy()).isEqualTo("worker-a");
        assertThat(service.claimNext("worker-b", Duration.ofHours(2)))
            .isEmpty();

        service.updateProgress(
            claimed.commandId(),
            45,
            "half-way"
        );
        service.markSucceeded(
            claimed.commandId(),
            Map.of("state", "success")
        );

        BackendOpsCommandService.CommandSnapshot completed =
            service.findById(claimed.commandId()).orElseThrow();
        assertThat(completed.succeeded()).isTrue();
        assertThat(completed.progressPercent()).isEqualTo(100);

        BackendOpsCommandService.EnqueueResult next =
            service.enqueueExclusive(
                BackendOpsCommandTypes.NETBOX_PASSPORTS_SYNC,
                "global",
                Map.of("trigger", "schedule"),
                "scheduler"
            );
        assertThat(next.created()).isTrue();
        assertThat(next.command().commandId())
            .isNotEqualTo(claimed.commandId());
    }

    @Test
    void staleRunningClaimReturnsToQueueForWorkerFailover() {
        BackendOpsCommandService.EnqueueResult queued =
            service.enqueueExclusive(
                BackendOpsCommandTypes.IIKO_LOCATIONS_SYNC,
                "global",
                Map.of("trigger", "manual"),
                "operator"
            );
        BackendOpsCommandService.CommandSnapshot claimed =
            service.claimNext("worker-old", Duration.ofHours(2))
                .orElseThrow();

        OffsetDateTime stale =
            OffsetDateTime.now(ZoneOffset.UTC).minusHours(4);
        jdbcTemplate.update("""
                UPDATE backend_ops_command
                   SET heartbeat_at = ?,
                       claimed_at = ?
                 WHERE command_id = ?
                """,
            Timestamp.from(stale.toInstant()),
            Timestamp.from(stale.toInstant()),
            claimed.commandId()
        );

        assertThat(service.recoverStaleClaims(Duration.ofMinutes(30)))
            .isEqualTo(1);

        BackendOpsCommandService.CommandSnapshot reclaimed =
            service.claimNext("worker-new", Duration.ofHours(2))
                .orElseThrow();

        assertThat(reclaimed.commandId())
            .isEqualTo(queued.command().commandId());
        assertThat(reclaimed.claimedBy()).isEqualTo("worker-new");
        assertThat(reclaimed.attemptCount()).isEqualTo(2);
    }
}
