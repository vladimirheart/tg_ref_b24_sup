package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.panel.config.IntegrationRabbitProperties;
import com.example.panel.config.PanelIntegrationTransportMode;
import com.example.panel.config.RuntimeCoordinationProperties;
import com.example.panel.storage.AttachmentObjectStorageService;
import com.example.panel.storage.ObjectStorageProperties;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.jdbc.core.JdbcTemplate;

class ProductionReadinessServiceTest {

    @Test
    void overallStatusRequiresAllProductionComponentsHealthy() {
        ProductionReadinessService service = new ProductionReadinessService(
            null, null, null, null, null, null, null, null, null,
            "postgresql",
            "docs/runbooks/postgresql-production-contour.md"
        );

        assertThat(service.overallStatus(true, List.of(
            component("healthy"),
            component("healthy")
        ))).isEqualTo("ready");
        assertThat(service.overallStatus(true, List.of(
            component("healthy"),
            component("degraded")
        ))).isEqualTo("degraded");
        assertThat(service.overallStatus(true, List.of(
            component("unavailable")
        ))).isEqualTo("degraded");
    }

    @Test
    void nonProductionContourIsReportedAsCompatibilityWhenNoProbeIsDegraded() {
        ProductionReadinessService service = new ProductionReadinessService(
            null, null, null, null, null, null, null, null, null,
            "postgresql",
            "docs/runbooks/postgresql-production-contour.md"
        );

        assertThat(service.overallStatus(false, List.of(
            component("compatibility"),
            component("healthy")
        ))).isEqualTo("compatibility");
    }

    @Test
    void buildSnapshotMarksRedisAndObjectStorageAsCompatibilityWhenBootstrapFlagsDisableThem() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);

        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");

        RuntimeCoordinationProperties coordinationProperties = new RuntimeCoordinationProperties();
        coordinationProperties.setMode("direct");
        coordinationProperties.setRequiredForPostgresql(false);
        RuntimeCoordinationService coordinationService = mock(RuntimeCoordinationService.class);

        PanelIntegrationTransportMode transportMode = mock(PanelIntegrationTransportMode.class);
        when(transportMode.mode()).thenReturn("rabbitmq");
        when(transportMode.isRabbitMqMode()).thenReturn(true);

        IntegrationRabbitProperties rabbitProperties = new IntegrationRabbitProperties();
        rabbitProperties.setInboundQueue("iguana.integration.inbound.panel");
        rabbitProperties.setTicketCreatedQueue("iguana.integration.ticket-created.panel");
        rabbitProperties.setInboundDlq("iguana.integration.inbound.panel.dlq");
        rabbitProperties.setTicketCreatedDlq("iguana.integration.ticket-created.panel.dlq");

        RabbitAdmin rabbitAdmin = mock(RabbitAdmin.class);
        QueueInformation inboundQueue = queueInfo(0);
        QueueInformation ticketCreatedQueue = queueInfo(0);
        QueueInformation inboundDlq = queueInfo(0);
        QueueInformation ticketCreatedDlq = queueInfo(0);
        when(rabbitAdmin.getQueueInfo("iguana.integration.inbound.panel")).thenReturn(inboundQueue);
        when(rabbitAdmin.getQueueInfo("iguana.integration.ticket-created.panel")).thenReturn(ticketCreatedQueue);
        when(rabbitAdmin.getQueueInfo("iguana.integration.inbound.panel.dlq")).thenReturn(inboundDlq);
        when(rabbitAdmin.getQueueInfo("iguana.integration.ticket-created.panel.dlq")).thenReturn(ticketCreatedDlq);

        AttachmentObjectStorageService objectStorageService = mock(AttachmentObjectStorageService.class);
        when(objectStorageService.providerLabel()).thenReturn("local_fs");

        ObjectStorageProperties objectStorageProperties = new ObjectStorageProperties();
        objectStorageProperties.setMode("local_fs");
        objectStorageProperties.setRequiredForPostgresql(false);

        ProductionReadinessService service = new ProductionReadinessService(
            jdbcTemplate,
            dataSource,
            coordinationProperties,
            coordinationService,
            transportMode,
            rabbitProperties,
            rabbitAdmin,
            objectStorageService,
            objectStorageProperties,
            "postgresql",
            "docs/runbooks/postgresql-production-contour.md"
        );

        Map<String, Object> snapshot = service.buildSnapshot();

        assertThat(snapshot.get("overall")).isEqualTo("compatibility");
        assertThat(snapshot.get("contour")).isEqualTo("compatibility");
        assertThat(component(snapshot, "postgresql")).containsEntry("status", "healthy");
        assertThat(component(snapshot, "rabbitmq")).containsEntry("status", "healthy");
        assertThat(component(snapshot, "redis"))
            .containsEntry("status", "compatibility")
            .containsEntry("required", false);
        assertThat(component(snapshot, "object_storage"))
            .containsEntry("status", "compatibility")
            .containsEntry("required", false);

        verify(coordinationService, never()).verifyAvailable();
        verify(objectStorageService, never()).verifyAvailable();
    }

    private static QueueInformation queueInfo(int messages) {
        QueueInformation info = mock(QueueInformation.class);
        when(info.getMessageCount()).thenReturn(messages);
        return info;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> component(Map<String, Object> snapshot, String key) {
        Object value = snapshot.get("components");
        if (!(value instanceof List<?> components)) {
            throw new AssertionError("components were not present");
        }
        return components.stream()
            .filter(Map.class::isInstance)
            .map(Map.class::cast)
            .filter(component -> key.equals(component.get("key")))
            .map(component -> (Map<String, Object>) component)
            .findFirst()
            .orElseThrow(() -> new AssertionError("component not found: " + key));
    }

    private static Map<String, Object> component(String status) {
        return Map.of("status", status);
    }
}
