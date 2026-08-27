package com.example.panel.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProductionAlertmanagerDeliverySourceContractTest {

    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void alertmanagerUsesInternalBearerCredentialFileAndNoProviderSecrets() throws IOException {
        String config = read("observability/alertmanager/alertmanager.yml");
        String compose = read("docker-compose.production-observability.yml");
        String prometheusRules = read("observability/prometheus/rules/iguana-alerts.yml");

        assertThat(config)
            .contains("receiver: iguana-internal")
            .contains("severity=~\"critical|high\"")
            .contains("severity=\"critical\"")
            .contains("severity=\"high\"")
            .contains("repeat_interval: 30m")
            .contains("repeat_interval: 2h")
            .contains("http://panel-web:8080/internal/observability/alertmanager")
            .contains("send_resolved: true")
            .contains("credentials_file: /run/secrets/iguana-alertmanager-ingestion.token")
            .doesNotContain("telegram_configs")
            .doesNotContain("smtp_auth_password")
            .doesNotContain("APP_INTERNAL_BOT_API_TOKEN");

        assertThat(compose)
            .contains("APP_ALERTMANAGER_INGESTION_ENABLED: \"true\"")
            .contains("APP_ALERTMANAGER_INGESTION_TOKEN_FILE: /run/secrets/iguana-alertmanager-ingestion.token")
            .contains("alertmanager-ingestion.token")
            .contains("/run/secrets/iguana-alertmanager-ingestion.token")
            .contains("read_only: true");

        assertThat(prometheusRules)
            .contains("IguanaAlertmanagerDeliveryFailed")
            .contains("alertmanager_notifications_failed_total")
            .contains("integration=\"webhook\"");
    }

    @Test
    void ingestionIsBlockedByPublicNginxAndUsesIncidentOutboxBoundary() throws IOException {
        String direct = read("docker/nginx/panel-direct.conf");
        String http = read("docker/nginx/templates/http-only.conf.template");
        String tls = read("docker/nginx/templates/tls.conf.template");
        String security = read("spring-panel/src/main/java/com/example/panel/security/SecurityConfig.java");
        String controller = read("spring-panel/src/main/java/com/example/panel/controller/AlertmanagerIngestionController.java");
        String service = read("spring-panel/src/main/java/com/example/panel/service/AlertmanagerIngestionService.java");
        String incident = read("spring-panel/src/main/java/com/example/panel/service/IncidentService.java");

        for (String nginx : new String[] {direct, http, tls}) {
            assertThat(nginx)
                .contains("location ^~ /internal/observability/")
                .contains("return 404;");
        }

        assertThat(security)
            .contains("/internal/observability/alertmanager");

        assertThat(controller)
            .contains("@RequestMapping(\"/internal/observability\")")
            .contains("@PostMapping(\"/alertmanager\")")
            .contains("guardService.authorize");

        assertThat(service)
            .contains("SIGNAL_TYPE = \"alertmanager\"")
            .contains("runWithLease")
            .contains("listIncidentSummariesForSignal")
            .contains("openOrRefreshSignalIncident")
            .contains("resolveSignalIncident")
            .contains("\"route_type\", routeType")
            .contains("\"route_target\", routeTarget");

        assertThat(incident)
            .contains("List<Map<String, Object>> initialRoutes")
            .contains("syncRoutes(incident, extractRoutes(routePayload), now)")
            .contains("incidentRouteDeliveryOutboxService.enqueueIncidentRoutes");
    }

    @Test
    void secretBootstrapAndE2eSmokeCoverFiringResolvedAndDeliveredOutbox() throws IOException {
        String ensurePs = read("scripts/ensure-alertmanager-ingestion-token.ps1");
        String ensureSh = read("scripts/ensure-alertmanager-ingestion-token.sh");
        String smoke = read("scripts/docker-alertmanager-delivery-smoke.ps1");
        String upPs = read("scripts/docker-production-up.ps1");
        String upSh = read("scripts/docker-production-up.sh");
        String gitignore = read(".gitignore");

        assertThat(ensurePs)
            .contains("RandomNumberGenerator")
            .contains("BitConverter")
            .contains("alertmanager-ingestion.token")
            .doesNotContain("ToHexString")
            .doesNotContain("Write-Host $token");

        assertThat(ensureSh)
            .contains("/dev/urandom")
            .contains("alertmanager-ingestion.token")
            .contains("umask 077")
            .contains("chmod 700")
            .contains("chmod 644");

        assertThat(upPs)
            .contains("Resolve-RepoPathFromSetting")
            .contains("-SecretsDir $alertmanagerSecretsDir");

        assertThat(upSh)
            .contains("get_setting_value \"IGUANA_SECRETS_DIR\"")
            .contains("IGUANA_SECRETS_DIR=\"${ALERTMANAGER_SECRETS_DIR}\" bash");

        assertThat(gitignore)
            .contains("/config/secrets/")
            .contains("**/alertmanager-ingestion.token");

        assertThat(smoke)
            .contains("zz-iguana-alertmanager-delivery-smoke.yml")
            .contains("vector(1)")
            .contains("vector(0) == 1")
            .contains("/-/reload")
            .contains("/api/v1/alerts")
            .contains("/api/v2/alerts")
            .contains("signal_type='alertmanager'")
            .contains("incident_signal_updated")
            .contains("incident_signal_resolved")
            .contains("status='delivered'");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(REPO_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
