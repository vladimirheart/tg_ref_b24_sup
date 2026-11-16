package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.panel.config.PanelIntegrationTransportMode;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
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
            uiEventStreamService,
            passthroughCoordinationService()
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
            uiEventStreamService,
            passthroughCoordinationService()
        );

        service.expireOldPendingRequests();

        verify(jdbcTemplate, never()).query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), any());
    }

    @Test
    void requestUnblockDoesNotBlockAnActiveClient() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UiEventStreamService uiEventStreamService = mock(UiEventStreamService.class);
        BotRuntimeBlacklistService service = new BotRuntimeBlacklistService(
            jdbcTemplate,
            new PanelIntegrationTransportMode(new MockEnvironment().withProperty("app.integration.transport.mode", "rabbitmq")),
            uiEventStreamService,
            passthroughCoordinationService()
        );

        BotRuntimeBlacklistService.UnblockRequestDecisionLookup result = service.requestUnblock(
            77L,
            "",
            12L,
            java.time.Duration.ofMinutes(10)
        );

        assertThat(result.created()).isFalse();
        assertThat(result.request()).isNull();
        verify(jdbcTemplate, never()).update(any(String.class), any(Object[].class));
        verifyNoInteractions(uiEventStreamService);
    }

    @Test
    void requestUnblockCreatesRequestForLockedBlacklistedClient() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UiEventStreamService uiEventStreamService = mock(UiEventStreamService.class);
        ResultSet blacklistRow = mock(ResultSet.class);
        when(blacklistRow.next()).thenReturn(true);
        when(blacklistRow.getString("user_id")).thenReturn("77");
        when(blacklistRow.getBoolean("is_blacklisted")).thenReturn(true);
        when(blacklistRow.getBoolean("unblock_requested")).thenReturn(false);
        when(jdbcTemplate.query(
            org.mockito.ArgumentMatchers.contains("FROM client_blacklist"),
            any(ResultSetExtractor.class),
            eq("77")
        )).thenAnswer(invocation -> ((ResultSetExtractor<?>) invocation.getArgument(1)).extractData(blacklistRow));
        when(jdbcTemplate.query(
            org.mockito.ArgumentMatchers.contains("FROM client_unblock_requests"),
            any(ResultSetExtractor.class),
            eq("77")
        )).thenReturn(new BotRuntimeBlacklistService.PendingUnblockRequestLookup(
            901L, "77", 12L, "", OffsetDateTime.parse("2026-11-16T10:00:00Z"), "pending"
        ));

        BotRuntimeBlacklistService service = new BotRuntimeBlacklistService(
            jdbcTemplate,
            new PanelIntegrationTransportMode(new MockEnvironment().withProperty("app.integration.transport.mode", "rabbitmq")),
            uiEventStreamService,
            passthroughCoordinationService()
        );

        BotRuntimeBlacklistService.UnblockRequestDecisionLookup result = service.requestUnblock(
            77L,
            "",
            12L,
            java.time.Duration.ofMinutes(10)
        );

        assertThat(result.created()).isTrue();
        assertThat(result.request().id()).isEqualTo(901L);
        verify(jdbcTemplate).query(
            org.mockito.ArgumentMatchers.contains("FOR UPDATE"),
            any(ResultSetExtractor.class),
            eq("77")
        );
        verify(uiEventStreamService).publishSidebarUnblockChanged("runtime_unblock_request_created");
    }

    private RuntimeCoordinationService passthroughCoordinationService() {
        RuntimeCoordinationService coordinationService = mock(RuntimeCoordinationService.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            Runnable action = invocation.getArgument(2);
            action.run();
            return null;
        }).when(coordinationService).runWithLease(any(), any(), any());
        return coordinationService;
    }
}
