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
                running_lane_key TEXT,
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
        jdbcTemplate.execute("""
            CREATE UNIQUE INDEX uq_backend_ops_command_running_lane
                ON backend_ops_command(running_lane_key)
             WHERE status = 'running'
               AND running_lane_key IS NOT NULL
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
    void unrelatedExecutionLanesCanRunAtTheSameTime() {
        BackendOpsCommandService.EnqueueResult rms =
            service.enqueueExclusive(
                BackendOpsCommandTypes.RMS_NETWORK_REFRESH,
                "all",
                Map.of(),
                "scheduler"
            );
        BackendOpsCommandService.EnqueueResult locations =
            service.enqueueExclusive(
                BackendOpsCommandTypes.IIKO_LOCATIONS_SYNC,
                "global",
                Map.of("trigger", "manual"),
                "operator"
            );

        BackendOpsCommandService.CommandSnapshot rmsClaim =
            service.claimNextForLane(
                "worker-a",
                BackendOpsCommandTypes.executionLane(
                    BackendOpsCommandTypes.RMS_NETWORK_REFRESH
                )
            ).orElseThrow();

        BackendOpsCommandService.CommandSnapshot locationsClaim =
            service.claimNextForLane(
                "worker-b",
                BackendOpsCommandTypes.executionLane(
                    BackendOpsCommandTypes.IIKO_LOCATIONS_SYNC
                )
            ).orElseThrow();

        assertThat(rmsClaim.commandId())
            .isEqualTo(rms.command().commandId());
        assertThat(locationsClaim.commandId())
            .isEqualTo(locations.command().commandId());
        assertThat(rmsClaim.runningLaneKey())
            .isEqualTo("rms-monitoring");
        assertThat(locationsClaim.runningLaneKey())
            .isEqualTo("iiko-locations");
    }

    @Test
    void conflictingRmsCommandsShareOneExecutionLane() {
        service.enqueueExclusive(
            BackendOpsCommandTypes.RMS_NETWORK_REFRESH,
            "all",
            Map.of(),
            "scheduler"
        );
        service.enqueueExclusive(
            BackendOpsCommandTypes.RMS_LICENSE_REFRESH,
            "all",
            Map.of(),
            "scheduler"
        );

        BackendOpsCommandTypes.ExecutionLane rmsLane =
            BackendOpsCommandTypes.executionLane(
                BackendOpsCommandTypes.RMS_NETWORK_REFRESH
            );

        BackendOpsCommandService.CommandSnapshot first =
            service.claimNextForLane(
                "worker-a",
                rmsLane
            ).orElseThrow();

        assertThat(
            service.claimNextForLane("worker-b", rmsLane)
        ).isEmpty();

        assertThat(
            service.markSucceeded(
                first,
                Map.of("state", "success")
            )
        ).isTrue();

        BackendOpsCommandService.CommandSnapshot second =
            service.claimNextForLane(
                "worker-b",
                rmsLane
            ).orElseThrow();

        assertThat(second.commandId())
            .isNotEqualTo(first.commandId());
        assertThat(second.runningLaneKey())
            .isEqualTo("rms-monitoring");
    }

    @Test
    void staleAttemptCannotOverwriteReclaimedCommand() {
        BackendOpsCommandService.EnqueueResult queued =
            service.enqueueExclusive(
                BackendOpsCommandTypes.IIKO_LOCATIONS_SYNC,
                "global",
                Map.of("trigger", "manual"),
                "operator"
            );

        BackendOpsCommandTypes.ExecutionLane lane =
            BackendOpsCommandTypes.executionLane(
                BackendOpsCommandTypes.IIKO_LOCATIONS_SYNC
            );

        BackendOpsCommandService.CommandSnapshot oldClaim =
            service.claimNextForLane(
                "worker-old",
                lane
            ).orElseThrow();

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
            oldClaim.commandId()
        );

        assertThat(
            service.recoverStaleClaims(
                Duration.ofMinutes(30)
            )
        ).isEqualTo(1);

        BackendOpsCommandService.CommandSnapshot newClaim =
            service.claimNextForLane(
                "worker-new",
                lane
            ).orElseThrow();

        assertThat(newClaim.commandId())
            .isEqualTo(queued.command().commandId());
        assertThat(newClaim.claimedBy())
            .isEqualTo("worker-new");
        assertThat(newClaim.attemptCount()).isEqualTo(2);

        assertThat(
            service.updateProgress(
                oldClaim,
                55,
                "stale progress"
            )
        ).isFalse();
        assertThat(
            service.markSucceeded(
                oldClaim,
                Map.of("state", "stale")
            )
        ).isFalse();

        BackendOpsCommandService.CommandSnapshot stillRunning =
            service.findById(
                newClaim.commandId()
            ).orElseThrow();

        assertThat(stillRunning.running()).isTrue();
        assertThat(stillRunning.claimedBy())
            .isEqualTo("worker-new");
        assertThat(stillRunning.attemptCount()).isEqualTo(2);

        assertThat(
            service.updateProgress(
                newClaim,
                45,
                "half-way"
            )
        ).isTrue();
        assertThat(
            service.markSucceeded(
                newClaim,
                Map.of("state", "success")
            )
        ).isTrue();

        BackendOpsCommandService.CommandSnapshot completed =
            service.findById(
                newClaim.commandId()
            ).orElseThrow();

        assertThat(completed.succeeded()).isTrue();
        assertThat(completed.progressPercent()).isEqualTo(100);
        assertThat(completed.runningLaneKey()).isNull();
    }
}
