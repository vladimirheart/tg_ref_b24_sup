package com.example.panel.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.env.MockEnvironment;

class PostgresRuntimeReadinessVerifierTest {

    @Test
    void postgresModeVerifiesTransportAndIncidentContours() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.datasource.mode", "postgresql")
                .withProperty("server.port", "8080");

        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RowMapper<Object> rowMapper = (RowMapper<Object>) invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getLong("channels_count")).thenReturn(1L);
            when(rs.getLong("tickets_count")).thenReturn(2L);
            when(rs.getLong("messages_count")).thenReturn(3L);
            when(rs.getLong("chat_history_count")).thenReturn(4L);
            when(rs.getLong("tasks_count")).thenReturn(5L);
            when(rs.getLong("clients_count")).thenReturn(6L);
            when(rs.getLong("avatars_count")).thenReturn(7L);
            when(rs.getLong("incidents_count")).thenReturn(8L);
            when(rs.getLong("open_incidents_count")).thenReturn(3L);
            return rowMapper.mapRow(rs, 0);
        });
        com.example.panel.service.RuntimeCoordinationService runtimeCoordinationService = mock(com.example.panel.service.RuntimeCoordinationService.class);
        com.example.panel.storage.AttachmentObjectStorageService attachmentObjectStorageService = mock(com.example.panel.storage.AttachmentObjectStorageService.class);

        PostgresRuntimeReadinessVerifier verifier = new PostgresRuntimeReadinessVerifier(
                jdbcTemplate,
                environment,
                runtimeCoordinationService,
                attachmentObjectStorageService
        );

        assertThatCode(() -> verifier.onApplicationEvent(null))
                .doesNotThrowAnyException();

        verify(jdbcTemplate).queryForList("SELECT id, event_type, ticket_id, channel_id, created_at FROM ui_event_outbox WHERE 1 = 0");
        verify(jdbcTemplate).queryForList("SELECT event_id, event_type, ticket_id, source, status FROM integration_inbound_event_inbox WHERE 1 = 0");
        verify(jdbcTemplate).queryForList("SELECT id, incident_key, status, severity, title, created_at, updated_at FROM incidents WHERE 1 = 0");
        verify(jdbcTemplate).queryForList("SELECT incident_id, relation_type, relation_key, primary_relation FROM incident_relations WHERE 1 = 0");
        verify(jdbcTemplate).queryForList("SELECT incident_id, event_type, actor, created_at FROM incident_events WHERE 1 = 0");
        verify(jdbcTemplate).queryForList("SELECT incident_id, watcher_identity, added_at FROM incident_watchers WHERE 1 = 0");
        verify(jdbcTemplate).queryForList("SELECT incident_id, route_type, route_target, route_status, updated_at FROM incident_routes WHERE 1 = 0");
    }

    @Test
    void postgresModeFailsWhenIncidentSchemaProbeBreaks() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.datasource.mode", "postgresql");

        when(jdbcTemplate.queryForList(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("FROM incidents")) {
                throw new IllegalStateException("incident probe failed");
            }
            return List.of();
        });
        com.example.panel.service.RuntimeCoordinationService runtimeCoordinationService = mock(com.example.panel.service.RuntimeCoordinationService.class);
        com.example.panel.storage.AttachmentObjectStorageService attachmentObjectStorageService = mock(com.example.panel.storage.AttachmentObjectStorageService.class);

        PostgresRuntimeReadinessVerifier verifier = new PostgresRuntimeReadinessVerifier(
                jdbcTemplate,
                environment,
                runtimeCoordinationService,
                attachmentObjectStorageService
        );

        assertThatThrownBy(() -> verifier.onApplicationEvent(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PostgreSQL runtime readiness verification failed");
    }

    @Test
    void sqliteModeSkipsPostgresSpecificProbes() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.datasource.mode", "sqlite");
        com.example.panel.service.RuntimeCoordinationService runtimeCoordinationService = mock(com.example.panel.service.RuntimeCoordinationService.class);
        com.example.panel.storage.AttachmentObjectStorageService attachmentObjectStorageService = mock(com.example.panel.storage.AttachmentObjectStorageService.class);

        PostgresRuntimeReadinessVerifier verifier = new PostgresRuntimeReadinessVerifier(
                jdbcTemplate,
                environment,
                runtimeCoordinationService,
                attachmentObjectStorageService
        );

        assertThatCode(() -> verifier.onApplicationEvent(null))
                .doesNotThrowAnyException();

        verify(jdbcTemplate, never()).queryForList(anyString());
    }
}
