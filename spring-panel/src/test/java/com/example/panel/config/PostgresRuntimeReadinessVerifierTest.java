package com.example.panel.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
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
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"FROM general_ticket_participants","FROM general_ticket_participant_events"})
    void missingGeneralParticipantSchemaCannotReportReady(String fragment) {
        var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForList(anyString())).thenReturn(List.of());
        when(jdbc.queryForList(contains(fragment))).thenThrow(new IllegalStateException("V80 is missing"));
        var verifier=new PostgresRuntimeReadinessVerifier(jdbc,new MockEnvironment().withProperty("app.datasource.mode","postgresql"),
            mock(com.example.panel.service.RuntimeCoordinationService.class),mock(com.example.panel.storage.AttachmentObjectStorageService.class));
        assertThatThrownBy(()->verifier.onApplicationEvent(null)).isInstanceOf(IllegalStateException.class).hasRootCauseMessage("V80 is missing");
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"FROM business_ticket_participants","FROM business_ticket_participant_events"})
    void missingBusinessParticipantSchemaCannotReportReady(String fragment) {
        var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForList(anyString())).thenReturn(List.of());
        when(jdbc.queryForList(contains(fragment))).thenThrow(new IllegalStateException("V79 is missing"));
        var verifier=new PostgresRuntimeReadinessVerifier(jdbc,new MockEnvironment().withProperty("app.datasource.mode","postgresql"),
            mock(com.example.panel.service.RuntimeCoordinationService.class),mock(com.example.panel.storage.AttachmentObjectStorageService.class));
        assertThatThrownBy(()->verifier.onApplicationEvent(null)).isInstanceOf(IllegalStateException.class).hasRootCauseMessage("V79 is missing");
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"SELECT responsibility_revision","FROM scoped_sla_assignment_events"})
    void missingScopedSlaSchemaCannotReportReady(String fragment) {
        var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForList(anyString())).thenReturn(List.of());
        when(jdbc.queryForList(contains(fragment))).thenThrow(new IllegalStateException("V77 is missing"));
        var verifier=new PostgresRuntimeReadinessVerifier(jdbc,new MockEnvironment().withProperty("app.datasource.mode","postgresql"),
            mock(com.example.panel.service.RuntimeCoordinationService.class),mock(com.example.panel.storage.AttachmentObjectStorageService.class));
        assertThatThrownBy(()->verifier.onApplicationEvent(null)).isInstanceOf(IllegalStateException.class).hasRootCauseMessage("V77 is missing");
    }

    @Test void missingBusinessManualAssignmentSchemaCannotReportReady() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);when(jdbc.queryForList(anyString())).thenReturn(List.of());
        when(jdbc.queryForList(contains("FROM business_ticket_assignment_events"))).thenThrow(new IllegalStateException("V78 is missing"));
        var verifier=new PostgresRuntimeReadinessVerifier(jdbc,new MockEnvironment().withProperty("app.datasource.mode","postgresql"),
            mock(com.example.panel.service.RuntimeCoordinationService.class),mock(com.example.panel.storage.AttachmentObjectStorageService.class));
        assertThatThrownBy(()->verifier.onApplicationEvent(null)).isInstanceOf(IllegalStateException.class).hasRootCauseMessage("V78 is missing");
    }

    @Test void missingFrozenQuestionnaireVersionCannotReportReady() {
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of());
        when(jdbc.queryForList(contains("SELECT questionnaire_version FROM tickets"))).thenThrow(new IllegalStateException("V76 is missing"));
        var verifier=new PostgresRuntimeReadinessVerifier(jdbc,new MockEnvironment().withProperty("app.datasource.mode","postgresql"),
            mock(com.example.panel.service.RuntimeCoordinationService.class),mock(com.example.panel.storage.AttachmentObjectStorageService.class));
        assertThatThrownBy(()->verifier.onApplicationEvent(null)).isInstanceOf(IllegalStateException.class).hasRootCauseMessage("V76 is missing");
    }

    @Test
    void missingSharedGeneralBindingCannotReportReady() {
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of());
        when(jdbc.queryForList(contains("FROM shared_channel_general_question_template_bindings")))
            .thenThrow(new IllegalStateException("V74 is missing"));
        var verifier=new PostgresRuntimeReadinessVerifier(jdbc,
            new MockEnvironment().withProperty("app.datasource.mode","postgresql"),
            mock(com.example.panel.service.RuntimeCoordinationService.class),
            mock(com.example.panel.storage.AttachmentObjectStorageService.class));
        assertThatThrownBy(()->verifier.onApplicationEvent(null)).isInstanceOf(IllegalStateException.class)
            .hasRootCauseMessage("V74 is missing");
    }

    @Test
    void missingBusinessSelectionProvenanceCannotReportReady() {
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of());
        when(jdbc.queryForList(contains("questionnaire_catalog_sha256"))).thenThrow(new IllegalStateException("V73 is missing"));
        var verifier=new PostgresRuntimeReadinessVerifier(jdbc,
            new MockEnvironment().withProperty("app.datasource.mode","postgresql"),
            mock(com.example.panel.service.RuntimeCoordinationService.class),
            mock(com.example.panel.storage.AttachmentObjectStorageService.class));
        assertThatThrownBy(()->verifier.onApplicationEvent(null)).isInstanceOf(IllegalStateException.class)
            .hasRootCauseMessage("V73 is missing");
    }

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

        verify(jdbcTemplate).queryForList("SELECT id, event_type, ticket_id, channel_id, created_at, ticket_user_id, ticket_public_id, "
            + "ticket_scope_kind, ticket_business_id FROM ui_event_outbox WHERE 1 = 0");
        verify(jdbcTemplate).queryForList(contains("FROM integration_inbound_event_inbox"));
        verify(jdbcTemplate).queryForList("SELECT ticket_id, created_at, general_work_revision FROM tickets WHERE 1 = 0");
        verify(jdbcTemplate).queryForList("SELECT business_selection_source,questionnaire_catalog_sha256 FROM tickets WHERE 1 = 0");
        verify(jdbcTemplate).queryForList("SELECT user_id, is_active FROM general_ticket_operate_grants WHERE 1 = 0");
        verify(jdbcTemplate).queryForList("SELECT ticket_public_id, revision, actor_user_id FROM general_ticket_work_events WHERE 1 = 0");
        verify(jdbcTemplate).queryForList("SELECT general_ticket_transition_allowed('open','closed') WHERE 1 = 0");
        verify(jdbcTemplate).queryForList(contains("FROM general_ticket_work_intervals WHERE 1 = 0"));
        verify(jdbcTemplate).queryForList(contains("FROM general_ticket_assignment_events WHERE 1 = 0"));
        verify(jdbcTemplate).queryForList(contains("FROM manual_general_clients WHERE 1 = 0"));
        verify(jdbcTemplate).queryForList(contains("FROM general_manual_ticket_requests WHERE 1 = 0"));
        verify(jdbcTemplate).queryForList("SELECT general_ticket_selection_source_allowed('MANUAL') WHERE 1 = 0");
        verify(jdbcTemplate).queryForList(contains("SELECT record_general_work_interval("));
        verify(jdbcTemplate).queryForList("SELECT request_key, ticket_public_id, destination_sha256, state, chat_history_id FROM general_ticket_text_replies WHERE 1 = 0");
        verify(jdbcTemplate).queryForList("SELECT request_key, state, actor_user_id FROM general_ticket_reply_events WHERE 1 = 0");
        verify(jdbcTemplate).queryForList(contains("FROM general_ticket_reply_changes WHERE 1 = 0"));
        verify(jdbcTemplate).queryForList(contains("FROM general_ticket_reply_change_events WHERE 1 = 0"));
        verify(jdbcTemplate).queryForList("SELECT ticket_public_id FROM chat_history WHERE 1 = 0");
        verify(jdbcTemplate).queryForList("SELECT worker_key, cursor_text, updated_at FROM runtime_worker_checkpoints WHERE 1 = 0");
        verify(jdbcTemplate).queryForList(contains("FROM integration_transport_outbox"));
        verify(jdbcTemplate).queryForList("SELECT id, incident_key, status, severity, title, created_at, updated_at FROM incidents WHERE 1 = 0");
        verify(jdbcTemplate).queryForList("SELECT incident_id, relation_type, relation_key, primary_relation FROM incident_relations WHERE 1 = 0");
        verify(jdbcTemplate).queryForList("SELECT incident_id, event_type, actor, created_at FROM incident_events WHERE 1 = 0");
        verify(jdbcTemplate).queryForList("SELECT incident_id, watcher_identity, added_at FROM incident_watchers WHERE 1 = 0");
        verify(jdbcTemplate).queryForList("SELECT incident_id, route_type, route_target, route_status, updated_at FROM incident_routes WHERE 1 = 0");
        verify(jdbcTemplate).queryForList("SELECT id, project_key, name, status, lead_identity FROM projects WHERE 1 = 0");
        verify(jdbcTemplate).queryForList("SELECT task_id, project_id, added_at, added_by FROM task_project_memberships WHERE 1 = 0");
        verify(jdbcTemplate).queryForList("SELECT id, name, normalized_name, color FROM tags WHERE 1 = 0");
        verify(jdbcTemplate).queryForList("SELECT task_id, tag_id, added_at, added_by FROM task_tags WHERE 1 = 0");
        verify(jdbcTemplate).queryForList("SELECT task_id, project_id, event_type, actor, occurred_at, field_name FROM task_events WHERE 1 = 0");
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
    void missingOutboxScopeSchemaFailsReadiness() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of());
        when(jdbc.queryForList(contains("ticket_scope_kind"))).thenThrow(new IllegalStateException("V61 is missing"));
        var verifier = new PostgresRuntimeReadinessVerifier(jdbc,
            new MockEnvironment().withProperty("app.datasource.mode", "postgresql"),
            mock(com.example.panel.service.RuntimeCoordinationService.class),
            mock(com.example.panel.storage.AttachmentObjectStorageService.class));

        assertThatThrownBy(() -> verifier.onApplicationEvent(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PostgreSQL runtime readiness verification failed")
            .hasRootCauseMessage("V61 is missing");
    }

    @Test
    void missingGeneralOperateSchemaFailsReadiness() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of());
        when(jdbc.queryForList(contains("general_ticket_operate_grants"))).thenThrow(new IllegalStateException("V65 is missing"));
        var verifier = new PostgresRuntimeReadinessVerifier(jdbc,
            new MockEnvironment().withProperty("app.datasource.mode", "postgresql"),
            mock(com.example.panel.service.RuntimeCoordinationService.class),
            mock(com.example.panel.storage.AttachmentObjectStorageService.class));
        assertThatThrownBy(() -> verifier.onApplicationEvent(null)).isInstanceOf(IllegalStateException.class)
            .hasRootCauseMessage("V65 is missing");
    }

    @Test
    void missingGeneralReplySchemaFailsReadiness() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of());
        when(jdbc.queryForList(contains("general_ticket_text_replies"))).thenThrow(new IllegalStateException("V66 is missing"));
        var verifier=new PostgresRuntimeReadinessVerifier(jdbc,
            new MockEnvironment().withProperty("app.datasource.mode","postgresql"),
            mock(com.example.panel.service.RuntimeCoordinationService.class),
            mock(com.example.panel.storage.AttachmentObjectStorageService.class));
        assertThatThrownBy(()->verifier.onApplicationEvent(null)).isInstanceOf(IllegalStateException.class).hasRootCauseMessage("V66 is missing");
    }

    @Test
    void missingGeneralLifecycleSchemaFailsReadiness() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of());
        when(jdbc.queryForList(contains("general_ticket_transition_allowed"))).thenThrow(new IllegalStateException("V67 is missing"));
        var verifier = new PostgresRuntimeReadinessVerifier(jdbc,
            new MockEnvironment().withProperty("app.datasource.mode", "postgresql"),
            mock(com.example.panel.service.RuntimeCoordinationService.class),
            mock(com.example.panel.storage.AttachmentObjectStorageService.class));
        assertThatThrownBy(() -> verifier.onApplicationEvent(null)).isInstanceOf(IllegalStateException.class).hasRootCauseMessage("V67 is missing");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"FROM general_ticket_work_intervals","SELECT record_general_work_interval("})
    void missingGeneralIntervalSchemaOrWriterFailsReadiness(String missing) {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of());
        when(jdbc.queryForList(contains(missing))).thenThrow(new IllegalStateException("V68 is missing"));
        var verifier=new PostgresRuntimeReadinessVerifier(jdbc,
            new MockEnvironment().withProperty("app.datasource.mode","postgresql"),
            mock(com.example.panel.service.RuntimeCoordinationService.class),
            mock(com.example.panel.storage.AttachmentObjectStorageService.class));
        assertThatThrownBy(()->verifier.onApplicationEvent(null)).isInstanceOf(IllegalStateException.class).hasRootCauseMessage("V68 is missing");
    }

    @Test void missingGeneralAssignmentSchemaFailsReadiness() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of());
        when(jdbc.queryForList(contains("general_ticket_assignment_events"))).thenThrow(new IllegalStateException("V69 is missing"));
        var verifier=new PostgresRuntimeReadinessVerifier(jdbc,new MockEnvironment().withProperty("app.datasource.mode","postgresql"),
            mock(com.example.panel.service.RuntimeCoordinationService.class),mock(com.example.panel.storage.AttachmentObjectStorageService.class));
        assertThatThrownBy(()->verifier.onApplicationEvent(null)).isInstanceOf(IllegalStateException.class).hasRootCauseMessage("V69 is missing");
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"manual_general_clients","general_manual_ticket_requests","general_ticket_selection_source_allowed"})
    void missingGeneralManualSchemaFailsReadiness(String missing) {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of());
        when(jdbc.queryForList(contains(missing))).thenThrow(new IllegalStateException("V70 is missing"));
        var verifier=new PostgresRuntimeReadinessVerifier(jdbc,new MockEnvironment().withProperty("app.datasource.mode","postgresql"),
            mock(com.example.panel.service.RuntimeCoordinationService.class),mock(com.example.panel.storage.AttachmentObjectStorageService.class));
        assertThatThrownBy(()->verifier.onApplicationEvent(null)).isInstanceOf(IllegalStateException.class).hasRootCauseMessage("V70 is missing");
    }
    @Test
    void sqliteModeFailsReadiness() {
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

        assertThatThrownBy(() -> verifier.onApplicationEvent(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SQLite runtime mode is retired");

        verify(jdbcTemplate, never()).queryForList(anyString());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"FROM general_ticket_reply_changes","FROM general_ticket_reply_change_events"})
    void missingGeneralReplyChangeSchemaFailsReadiness(String missing) {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of());
        when(jdbc.queryForList(contains(missing))).thenThrow(new IllegalStateException("V71 is missing"));
        var verifier=new PostgresRuntimeReadinessVerifier(jdbc,new MockEnvironment().withProperty("app.datasource.mode","postgresql"),
            mock(com.example.panel.service.RuntimeCoordinationService.class),mock(com.example.panel.storage.AttachmentObjectStorageService.class));
        assertThatThrownBy(()->verifier.onApplicationEvent(null)).isInstanceOf(IllegalStateException.class).hasRootCauseMessage("V71 is missing");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"FROM general_ticket_document_replies","FROM general_ticket_document_events"})
    void missingGeneralDocumentSchemaFailsReadiness(String missing){
        JdbcTemplate jdbc=mock(JdbcTemplate.class);when(jdbc.queryForList(anyString())).thenReturn(List.of());
        when(jdbc.queryForList(contains(missing))).thenThrow(new IllegalStateException("V72 is missing"));
        var verifier=new PostgresRuntimeReadinessVerifier(jdbc,new MockEnvironment().withProperty("app.datasource.mode","postgresql"),
            mock(com.example.panel.service.RuntimeCoordinationService.class),mock(com.example.panel.storage.AttachmentObjectStorageService.class));
        assertThatThrownBy(()->verifier.onApplicationEvent(null)).isInstanceOf(IllegalStateException.class).hasRootCauseMessage("V72 is missing");
    }
    @Test void missingSharedGeneralSelectionWriterFailsReadiness() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);when(jdbc.queryForList(anyString())).thenReturn(List.of());
        when(jdbc.queryForList(contains("prevent_ticket_shared_general_selection_change"))).thenThrow(new IllegalStateException("V75 missing"));
        var verifier=new PostgresRuntimeReadinessVerifier(jdbc,new MockEnvironment().withProperty("app.datasource.mode","postgresql"),
            mock(com.example.panel.service.RuntimeCoordinationService.class),mock(com.example.panel.storage.AttachmentObjectStorageService.class));
        assertThatThrownBy(()->verifier.onApplicationEvent(null)).isInstanceOf(IllegalStateException.class).hasRootCauseMessage("V75 missing");
    }
}
