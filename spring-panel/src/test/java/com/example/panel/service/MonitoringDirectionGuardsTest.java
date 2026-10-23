package com.example.panel.service;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MonitoringDirectionGuardsTest {
    @Test void pausedSchedulersDoNotAcquireLeasesOrLogProbeFailures() throws Exception {
        for (String name : List.of("ProviderHealthMonitoring", "PublicIngressMonitoring", "SmtpNotificationMonitoring",
                "SslCertificateMonitoring", "BackupReadinessMonitoring", "CredentialRotationRegistry",
                "ProviderDeliveryAlerting", "IikoApiMonitoring", "RmsLicenseMonitoring")) {
            Class<?> serviceType = Class.forName("com.example.panel.service." + name + "Service");
            Object monitor = mock(serviceType);
            when((Boolean) serviceType.getMethod("isMonitoringPaused").invoke(monitor)).thenReturn(true);
            Class<?> schedulerType = Class.forName("com.example.panel.service." + name + "Scheduler");
            var constructor = schedulerType.getConstructors()[0];
            Object[] dependencies = java.util.Arrays.stream(constructor.getParameterTypes()).map(type -> type == serviceType ? monitor : mock(type)).toArray();
            Object scheduler = constructor.newInstance(dependencies);
            int ticks = 0;
            for (var method : schedulerType.getDeclaredMethods()) {
                if (java.lang.reflect.Modifier.isPublic(method.getModifiers()) && method.getReturnType() == void.class && method.getParameterCount() == 0) {
                    method.invoke(scheduler); ticks++;
                }
            }
            assertThat(mockingDetails(monitor).getInvocations().stream().filter(call -> call.getMethod().getName().equals("isMonitoringPaused")).count()).isEqualTo(ticks);
            for (Object dependency : dependencies) if (dependency != monitor) verifyNoInteractions(dependency);
        }
    }

    @Test void pausedServicesDoNotReachRepositoriesNetworkOrQueues() throws Exception {
        var policy = mock(IncidentMonitoringPolicyService.class);
        when(policy.isSuppressed(anyString())).thenReturn(true);
        for (Class<?> type : List.of(ProviderHealthMonitoringService.class, SslCertificateMonitoringService.class,
                SmtpNotificationMonitoringService.class, PublicIngressMonitoringService.class,
                BackupReadinessMonitoringService.class, CredentialRotationRegistryService.class,
                ProviderDeliveryAlertingService.class, IikoApiMonitoringService.class, RmsLicenseMonitoringService.class)) {
            // CALLS_REAL_METHODS + no constructor: any accidental dependency access is an immediate failure.
            Object service = mock(type, CALLS_REAL_METHODS);
            ReflectionTestUtils.setField(service, "incidentMonitoringPolicy", policy);
            String method = type == IikoApiMonitoringService.class ? "requestRefresh" : type == RmsLicenseMonitoringService.class ? "requestNetworkRefresh" : "refreshAll";
            if (type == SslCertificateMonitoringService.class) {
                assertThatThrownBy(() -> ((SslCertificateMonitoringService) service).refreshAll(true)).isInstanceOf(ResponseStatusException.class);
            } else {
                assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, method)).isInstanceOf(ResponseStatusException.class);
            }
            var entryNames = java.util.Set.of("refreshAll", "refreshById", "requestRefresh", "requestRefreshForMonitor",
                "requestLicenseRefresh", "requestLicenseRefreshForMonitor", "requestNetworkRefresh", "requestNetworkRefreshForMonitor",
                "executeBackendOpsRefresh", "executeBackendOpsLicenseRefresh", "executeBackendOpsNetworkRefresh");
            for (var entry : type.getDeclaredMethods()) {
                if (!entryNames.contains(entry.getName())) continue;
                Object[] arguments = java.util.Arrays.stream(entry.getParameterTypes()).map(parameter ->
                    parameter == boolean.class ? (Object) true : parameter == long.class ? 1L : java.util.Map.of()).toArray();
                assertThatThrownBy(() -> entry.invoke(service, arguments))
                    .isInstanceOf(java.lang.reflect.InvocationTargetException.class).hasCauseInstanceOf(ResponseStatusException.class);
            }
        }
        var provider = mock(ProviderHealthMonitoringService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(provider, "incidentMonitoringPolicy", policy);
        assertThatThrownBy(provider::buildOverview).isInstanceOf(ResponseStatusException.class);
        var transport = new com.example.panel.service.integration.IntegrationTransportIncidentMonitor(null, null, null);
        ReflectionTestUtils.setField(transport, "monitoringPolicy", policy);
        assertThatCode(transport::monitor).doesNotThrowAnyException();
    }
}
