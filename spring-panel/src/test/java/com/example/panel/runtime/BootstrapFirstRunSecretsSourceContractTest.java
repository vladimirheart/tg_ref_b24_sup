package com.example.panel.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BootstrapFirstRunSecretsSourceContractTest {

    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void freshBootstrapGeneratesProductionLikeSecretsWithoutHardcodedInfraPasswords() throws IOException {
        String ps = read("scripts/bootstrap-first-run.ps1");
        String sh = read("scripts/bootstrap-first-run.sh");
        String compose = read("docker-compose.local-postgres.yml");
        String envExample = read(".env.example");

        assertThat(ps)
            .contains("IGUANA_POSTGRES_PASSWORD=$postgresPassword")
            .contains("IGUANA_RABBITMQ_PASSWORD=$rabbitPassword")
            .contains("IGUANA_REDIS_PASSWORD=$redisPassword")
            .contains("APP_STORAGE_OBJECT_ACCESS_KEY=$objectAccessKey")
            .contains("APP_STORAGE_OBJECT_SECRET_KEY=$objectSecretKey")
            .contains("APP_INTERNAL_BOT_API_TOKEN=$internalBotApiToken")
            .contains("APP_SECURITY_REMEMBER_ME_KEY=$rememberMeKey")
            .contains("MONITORING_CREDENTIALS_MASTER_KEY=$monitoringMasterKey")
            .contains("IGUANA_GRAFANA_ADMIN_PASSWORD=$grafanaAdminPassword")
            .contains("return \"base64:$")
            .contains("Existing persisted infrastructure credentials are not rotated automatically.");

        assertThat(sh)
            .contains("IGUANA_POSTGRES_PASSWORD=${postgres_password}")
            .contains("IGUANA_RABBITMQ_PASSWORD=${rabbit_password}")
            .contains("IGUANA_REDIS_PASSWORD=${redis_password}")
            .contains("APP_STORAGE_OBJECT_ACCESS_KEY=${object_access_key}")
            .contains("APP_STORAGE_OBJECT_SECRET_KEY=${object_secret_key}")
            .contains("APP_INTERNAL_BOT_API_TOKEN=${internal_bot_api_token}")
            .contains("APP_SECURITY_REMEMBER_ME_KEY=${remember_me_key}")
            .contains("MONITORING_CREDENTIALS_MASTER_KEY=${monitoring_master_key}")
            .contains("IGUANA_GRAFANA_ADMIN_PASSWORD=${grafana_admin_password}")
            .contains("printf 'base64:%s'")
            .contains("Existing persisted infrastructure credentials are not rotated automatically.");

        assertThat(compose)
            .contains("POSTGRES_PASSWORD: ${IGUANA_POSTGRES_PASSWORD:-iguana}")
            .contains("RABBITMQ_DEFAULT_PASS: ${IGUANA_RABBITMQ_PASSWORD:-iguana}");

        assertThat(envExample)
            .contains("bootstrap generates random secret values automatically")
            .contains("must not be treated as a production-ready secret source");
    }

    @Test
    void localLaunchersHealOnlySafeAppSideSecretsForBootstrapContour() throws IOException {
        String psHelper = read("scripts/ensure-local-bootstrap-secrets.ps1");
        String shHelper = read("scripts/ensure-local-bootstrap-secrets.sh");
        String runWindows = read("spring-panel/run-windows.bat");
        String runLinux = read("spring-panel/run-linux.sh");

        assertThat(psHelper)
            .contains("APP_INTERNAL_BOT_API_TOKEN")
            .contains("APP_SECURITY_REMEMBER_ME_KEY")
            .contains("MONITORING_CREDENTIALS_MASTER_KEY")
            .doesNotContain("IGUANA_POSTGRES_PASSWORD")
            .doesNotContain("IGUANA_RABBITMQ_PASSWORD");

        assertThat(shHelper)
            .contains("maybe_set_secret \"APP_INTERNAL_BOT_API_TOKEN\"")
            .contains("maybe_set_secret \"APP_SECURITY_REMEMBER_ME_KEY\"")
            .contains("maybe_set_secret \"MONITORING_CREDENTIALS_MASTER_KEY\"")
            .doesNotContain("maybe_set_secret \"IGUANA_POSTGRES_PASSWORD\"")
            .doesNotContain("maybe_set_secret \"IGUANA_RABBITMQ_PASSWORD\"");

        assertThat(runWindows).contains("ensure-local-bootstrap-secrets.ps1");
        assertThat(runLinux).contains("ensure-local-bootstrap-secrets.sh");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(REPO_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
