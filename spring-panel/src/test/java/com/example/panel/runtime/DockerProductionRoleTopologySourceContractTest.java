package com.example.panel.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DockerProductionRoleTopologySourceContractTest {

    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void productionComposeUsesExplicitScaleReadyBackendRoles() throws IOException {
        String compose = read("docker-compose.production-contour.yml");

        assertThat(compose)
            .contains("\n  db-migrate:\n")
            .contains("\n  ops-worker:\n")
            .contains("\n  bot-runner:\n")
            .contains("\n  panel-web:\n")
            .contains("\n  panel-direct:\n")
            .contains("APP_RUNTIME_ROLE: db-migrate")
            .contains("APP_RUNTIME_ROLE: ops-worker")
            .contains("APP_RUNTIME_ROLE: bot-runner")
            .contains("APP_RUNTIME_ROLE: panel-web")
            .contains("APP_RUNTIME_EXIT_AFTER_MIGRATION: \"true\"")
            .contains("condition: service_completed_successfully")
            .contains("APP_UI_EVENT_FANOUT_MODE: redis")
            .contains("MONITORING_CREDENTIALS_MASTER_KEY:")
            .doesNotContain("container_name:");

        assertThat(compose)
            .contains("minio/minio:RELEASE.2025-07-23T15-54-02Z")
            .contains("minio/mc:RELEASE.2025-07-21T05-28-08Z")
            .doesNotContain("minio/minio:RELEASE.2026-")
            .doesNotContain("minio/mc:RELEASE.2026-");

        assertThat(compose)
            .as("legacy spring-panel service must be removed")
            .doesNotContain("\n  spring-panel:\n");

        String web = section(compose, "  panel-web:", "  panel-direct:");
        assertThat(web)
            .contains("APP_RUNTIME_ROLE: panel-web")
            .contains("expose:")
            .doesNotContain("\n    ports:");

        String worker = section(compose, "  ops-worker:", "  panel-web:");
        assertThat(worker)
            .contains("APP_RUNTIME_ROLE: ops-worker")
            .contains("expose:")
            .doesNotContain("\n    ports:");

        String migrator = section(compose, "  db-migrate:", "  ops-worker:");
        assertThat(migrator)
            .contains("volumes: *db-migrate-volumes")
            .doesNotContain("legacy-sqlite")
            .doesNotContain("APP_DB_PANEL_RUNTIME")
            .doesNotContain("APP_BOT_DATABASE_DIR");

        assertThat(compose)
            .contains("x-db-migrate-volumes: &db-migrate-volumes")
            .doesNotContain("/opt/iguana/legacy-sqlite")
            .doesNotContain("IGUANA_LEGACY_SQLITE_AUTO_IMPORT");

        assertThat(count(compose, "condition: service_completed_successfully"))
            .isGreaterThanOrEqualTo(3);
    }

    @Test
    void botAndIngressServiceDiscoveryTargetsPanelWebOnly() throws IOException {
        String compose = read("docker-compose.production-contour.yml");
        String edge = read("docker-compose.production-edge.yml");
        String httpTemplate = read("docker/nginx/templates/http-only.conf.template");
        String tlsTemplate = read("docker/nginx/templates/tls.conf.template");
        String direct = read("docker/nginx/panel-direct.conf");

        assertThat(compose)
            .contains("APP_PANEL_INTERNAL_API_BASE_URL: http://panel-web:8080")
            .doesNotContain("APP_PANEL_INTERNAL_API_BASE_URL: http://spring-panel:8080");

        for (String bot : new String[] {"bot-telegram:", "bot-vk:", "bot-max:"}) {
            String botSection = section(compose, "  " + bot, nextServiceMarker(compose, "  " + bot));
            assertThat(botSection)
                .as(bot)
                .contains("APP_DB_MODE: postgresql")
                .contains("SPRING_DATASOURCE_URL")
                .contains("SPRING_DATASOURCE_USERNAME")
                .contains("SPRING_DATASOURCE_PASSWORD");
        }

        String botRunner = section(compose, "  bot-runner:", "  panel-web:");
        assertThat(botRunner)
            .contains("APP_RUNTIME_ROLE: bot-runner")
            .contains("APP_INTERNAL_BOT_API_BASE_URL: http://panel-web:8080");

        assertThat(edge)
            .contains("panel-web:")
            .contains("cp \"$$template_dir/tls.conf.template\"")
            .contains("cp \"$$template_dir/http-only.conf.template\"")
            .doesNotContain("cp \"$template_dir/")
            .doesNotContain("spring-panel:");
        assertThat(httpTemplate)
            .contains("server panel-web:8080 resolve;")
            .doesNotContain("spring-panel:8080");
        assertThat(tlsTemplate)
            .contains("server panel-web:8080 resolve;")
            .doesNotContain("spring-panel:8080");
        assertThat(direct)
            .contains("server panel-web:8080 resolve;")
            .contains("proxy_set_header Host $http_host;")
            .contains("proxy_set_header X-Forwarded-Port $forwarded_port;")
            .contains("proxy_redirect off;");
    }

    @Test
    void helperScriptsExposeReplicaControlsAndRequiredSplitRoleSecret() throws IOException {
        String ps = read("scripts/docker-production-up.ps1");
        String sh = read("scripts/docker-production-up.sh");
        String smoke = read("scripts/docker-production-role-smoke.ps1");
        String env = read(".env.example");
        String entrypoint = read("docker/panel-entrypoint.sh");

        assertThat(ps)
            .contains("WebReplicas")
            .contains("WorkerReplicas")
            .contains("panel-web=$resolvedWebReplicas")
            .contains("ops-worker=$resolvedWorkerReplicas")
            .contains("MONITORING_CREDENTIALS_MASTER_KEY")
            .contains("--remove-orphans");

        assertThat(sh)
            .contains("--web-replicas")
            .contains("--worker-replicas")
            .contains("panel-web=${WEB_REPLICAS}")
            .contains("ops-worker=${WORKER_REPLICAS}")
            .contains("MONITORING_CREDENTIALS_MASTER_KEY")
            .contains("--remove-orphans");

        assertThat(smoke)
            .contains("--scale\", \"panel-web=2")
            .contains("--scale\", \"ops-worker=2")
            .contains("01-211 Docker role/scale smoke is GREEN.");

        assertThat(env)
            .contains("IGUANA_PANEL_WEB_REPLICAS=1")
            .contains("IGUANA_OPS_WORKER_REPLICAS=1")
            .contains("MONITORING_CREDENTIALS_MASTER_KEY=change-me")
            .doesNotContain("IGUANA_LEGACY_SQLITE_STAGING_DIR=");

        assertThat(entrypoint)
            .contains("APP_INSTANCE_ID")
            .contains("HOSTNAME")
            .contains("APP_PANEL_LOG_PATH");
    }

@Test
void dockerBuildDoesNotDependOnWindowsCheckoutShellWrappers() throws IOException {
    String dockerfile = read("docker/panel.Dockerfile");
    String dockerignore = read(".dockerignore");

    assertThat(dockerfile)
        .contains("COPY spring-panel/pom.xml ./pom.xml")
        .contains("COPY spring-panel/src/ ./src/")
        .contains("RUN mvn -DskipTests package")
        .doesNotContain("./mvnw")
        .doesNotContain("COPY spring-panel/ ./spring-panel/");

    assertThat(dockerignore)
        .contains("spring-panel/.mvn")
        .contains("spring-panel/mvnw")
        .contains("spring-panel/mvnw.cmd")
        .contains("spring-panel/db-backup-*")
        .contains("spring-panel/recovery-*")
        .contains("spring-panel/.tmp*");
}
@Test
void dockerSmokeUsesDeclaredRuntimeWorkloadIds() throws IOException {
    String heartbeatSource = read(
        "spring-panel/src/main/java/com/example/panel/service/UiEventStreamHeartbeatScheduler.java"
    );
    String smoke = read("scripts/docker-production-role-smoke.ps1");

    assertThat(heartbeatSource)
        .contains("id = \"ui-event-stream-heartbeat\"")
        .contains("roles = {RuntimeRole.WEB}");

    assertThat(smoke)
        .contains("\"ui-event-stream-heartbeat\"")
        .doesNotContain("\"ui-event-stream-heartbeat-scheduler\"");
}
@Test
void dockerSmokeWaitsForIngressReadinessBeforeRoutingAssertions() throws IOException {
    String smoke = read("scripts/docker-production-role-smoke.ps1");

    assertThat(smoke)
        .contains("function Wait-HttpRuntimeRole")
        .contains("\"ps\", \"-q\", \"nginx\"")
        .contains("\"ps\", \"-q\", \"panel-direct\"")
        .contains("[SMOKE] Waiting for ingress proxies health...")
        .contains("Wait-ContainerHealthy -Docker $docker -ContainerId $edgeProxyIds[0] -Timeout 60")
        .contains("Wait-ContainerHealthy -Docker $docker -ContainerId $directProxyIds[0] -Timeout 60")
        .contains("Wait-HttpRuntimeRole `")
        .doesNotContain("$edgeInfoResponse = Invoke-WebRequest")
        .doesNotContain("$directInfoResponse = Invoke-WebRequest");
}
@Test
void edgeNginxCreatesOfficialTemplateDirectoryBeforeSelectingTemplate() throws IOException {
    String edge = read("docker-compose.production-edge.yml");

    String mkdir = "mkdir -p /etc/nginx/templates";
    String tlsCopy = "cp \"$$template_dir/tls.conf.template\" /etc/nginx/templates/default.conf.template";
    String httpCopy = "cp \"$$template_dir/http-only.conf.template\" /etc/nginx/templates/default.conf.template";

    assertThat(edge)
        .contains(mkdir)
        .contains(tlsCopy)
        .contains(httpCopy)
        .contains("exec /docker-entrypoint.sh nginx -g 'daemon off;'");

    assertThat(edge.indexOf(mkdir)).isLessThan(edge.indexOf(tlsCopy));
    assertThat(edge.indexOf(mkdir)).isLessThan(edge.indexOf(httpCopy));
}

@Test
void dockerSmokeDecodesWindowsPowerShellByteArrayHttpBodiesBeforeJsonParsing() throws IOException {
    String smoke = read("scripts/docker-production-role-smoke.ps1");

    assertThat(smoke)
        .contains("function Convert-HttpResponseContentToJson")
        .contains("$content -is [byte[]]")
        .contains("[System.Text.Encoding]::UTF8.GetString([byte[]]$content)")
        .contains("ConvertFrom-Json -InputObject $text")
        .contains("$info = Convert-HttpResponseContentToJson -Response $response")
        .doesNotContain("$response.Content | ConvertFrom-Json")
        .doesNotContain("($response.Content | ConvertFrom-Json)");
}
    private String read(String relativePath) throws IOException {
        return Files.readString(
            REPO_ROOT.resolve(relativePath),
            StandardCharsets.UTF_8
        ).replace("\r\n", "\n");
    }

    private String section(String content, String startMarker, String endMarker) {
        int start = content.indexOf(startMarker);
        assertThat(start).as("start marker " + startMarker).isGreaterThanOrEqualTo(0);
        int end = content.indexOf(endMarker, start + startMarker.length());
        if (end < 0) {
            end = content.length();
        }
        return content.substring(start, end);
    }

    private String nextServiceMarker(String content, String startMarker) {
        int start = content.indexOf(startMarker);
        int searchFrom = start + startMarker.length();
        int next = content.indexOf("\n  ", searchFrom);
        while (next >= 0) {
            int lineEnd = content.indexOf('\n', next + 1);
            String line = lineEnd < 0
                ? content.substring(next + 1)
                : content.substring(next + 1, lineEnd);
            if (line.matches("  [A-Za-z0-9_-]+:")) {
                return line;
            }
            next = content.indexOf("\n  ", next + 3);
        }
        return "\nvolumes:";
    }

    private int count(String content, String token) {
        int count = 0;
        int offset = 0;
        while (true) {
            int index = content.indexOf(token, offset);
            if (index < 0) {
                return count;
            }
            count++;
            offset = index + token.length();
        }
    }
}
