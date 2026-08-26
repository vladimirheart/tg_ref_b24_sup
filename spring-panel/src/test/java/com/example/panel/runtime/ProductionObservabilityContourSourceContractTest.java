package com.example.panel.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionObservabilityContourSourceContractTest {

    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void productionObservabilityOverlayPinsScaleAwareInternalServices() throws IOException {
        String compose = read("docker-compose.production-observability.yml");
        String prometheus = read("observability/prometheus/prometheus.yml");
        String rabbitPlugins = read("observability/rabbitmq/enabled_plugins");

        assertThat(compose)
            .contains("prom/prometheus:v3.14.0")
            .contains("prom/alertmanager:v0.34.0")
            .contains("grafana/grafana:13.2.0")
            .contains("grafana/loki:3.7.6")
            .contains("grafana/alloy:v1.18.0")
            .contains("quay.io/prometheuscommunity/postgres-exporter:v0.20.1")
            .contains("oliver006/redis_exporter:v1.89.0")
            .contains("MINIO_PROMETHEUS_AUTH_TYPE: public")
            .doesNotContain(":latest");

        assertThat(compose)
            .contains("${IGUANA_GRAFANA_BIND_HOST:-127.0.0.1}")
            .contains("${IGUANA_PROMETHEUS_BIND_HOST:-127.0.0.1}")
            .contains("${IGUANA_ALERTMANAGER_BIND_HOST:-127.0.0.1}")
            .contains("${IGUANA_LOKI_BIND_HOST:-127.0.0.1}")
            .contains("${IGUANA_ALLOY_BIND_HOST:-127.0.0.1}");

        assertThat(prometheus)
            .contains("job_name: iguana-panel-web")
            .contains("job_name: iguana-ops-worker")
            .contains("dns_sd_configs:")
            .contains("- panel-web")
            .contains("- ops-worker")
            .contains("postgres-exporter:9187")
            .contains("redis-exporter:9121")
            .contains("rabbitmq:15692")
            .contains("/minio/v2/metrics/cluster");

        assertThat(rabbitPlugins)
            .contains("rabbitmq_management")
            .contains("rabbitmq_prometheus");
    }

    @Test
    void observabilityConfigKeepsMetricsAndLogsVersionedWithoutDockerSocket() throws IOException {
        String rules = read("observability/prometheus/rules/iguana-alerts.yml");
        String alloy = read("observability/alloy/config.alloy");
        String alertmanager = read("observability/alertmanager/alertmanager.yml");
        String env = read(".env.example");

        assertThat(rules)
            .contains("IguanaPanelWebReplicaDown")
            .contains("IguanaOpsWorkerReplicaDown")
            .contains("IguanaProductionReadinessNotReady")
            .contains("IguanaDlqBacklog")
            .contains("IguanaPanelP95LatencyHigh")
            .contains("IguanaPanel5xxRateHigh");

        assertThat(alloy)
            .contains("/var/log/iguana/**/*.log")
            .contains("loki.write \"local\"")
            .doesNotContain("/var/run/docker.sock")
            .doesNotContain("discovery.docker");

        assertThat(alertmanager)
            .contains("receiver: iguana-local")
            .contains("severity=\"critical\"");

        assertThat(env)
            .contains("IGUANA_GRAFANA_ADMIN_PASSWORD=change-me")
            .contains("IGUANA_PROMETHEUS_RETENTION=30d");
    }

    @Test
    void productionHelpersAndSmokeExposeObservabilityContract() throws IOException {
        String upPs = read("scripts/docker-production-up.ps1");
        String downPs = read("scripts/docker-production-down.ps1");
        String upSh = read("scripts/docker-production-up.sh");
        String downSh = read("scripts/docker-production-down.sh");
        String smoke = read("scripts/docker-production-observability-smoke.ps1");

        assertThat(upPs)
            .contains("[switch]$Observability")
            .contains("docker-compose.production-observability.yml")
            .contains("IGUANA_GRAFANA_ADMIN_PASSWORD");
        assertThat(downPs)
            .contains("[switch]$Observability")
            .contains("docker-compose.production-observability.yml");
        assertThat(upSh)
            .contains("--observability")
            .contains("docker-compose.production-observability.yml")
            .contains("IGUANA_GRAFANA_ADMIN_PASSWORD");
        assertThat(downSh)
            .contains("--observability")
            .contains("docker-compose.production-observability.yml");
        assertThat(smoke)
            .contains("01-194 observability smoke is GREEN")
            .contains("iguana-panel-web")
            .contains("iguana-ops-worker")
            .contains("rabbitmq")
            .contains("minio");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(REPO_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
