package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.panel.config.PanelIntegrationTransportMode;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

class BotRuntimeBlacklistServiceTest {

    @Test
    void pendingSummaryReturnsCountAndRecentRows() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UiEventStreamService uiEventStreamService = mock(UiEventStreamService.class);
        when(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM client_unblock_requests WHERE status = 'pending'",
                Long.class))
                .thenReturn(3L);
        when(jdbcTemplate.query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), eq(2)))
                .thenAnswer(invocation -> List.of(
                        new BotRuntimeBlacklistService.PendingUnblockRequestLookup(
                                1001L,
                                "77",
                                12L,
                                "",
                                OffsetDateTime.parse("2026-08-17T10:00:00Z"),
                                "pending"
                        )
                ));

        BotRuntimeBlacklistService service = new BotRuntimeBlacklistService(
            jdbcTemplate,
            new PanelIntegrationTransportMode(new MockEnvironment().withProperty("app.integration.transport.mode", "rabbitmq")),
            uiEventStreamService
        );

        BotRuntimeBlacklistService.PendingUnblockSummaryLookup summary = service.pendingSummary(2);

        assertThat(summary.pendingCount()).isEqualTo(3L);
        assertThat(summary.recentRequests()).hasSize(1);
        assertThat(summary.recentRequests().get(0).userId()).isEqualTo("77");
    }

    @Test
    void expireOldPendingRequestsSkipsOutsideRabbitMode() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UiEventStreamService uiEventStreamService = mock(UiEventStreamService.class);
        BotRuntimeBlacklistService service = new BotRuntimeBlacklistService(
            jdbcTemplate,
            new PanelIntegrationTransportMode(new MockEnvironment().withProperty("app.integration.transport.mode", "jdbc")),
            uiEventStreamService
        );

        service.expireOldPendingRequests();

        verify(jdbcTemplate, never()).query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), any());
    }
}
