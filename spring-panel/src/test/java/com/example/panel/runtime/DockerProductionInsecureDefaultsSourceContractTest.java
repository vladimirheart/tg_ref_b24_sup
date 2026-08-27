package com.example.panel.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DockerProductionInsecureDefaultsSourceContractTest {

    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void allowInsecureDefaultsMaterializesDocumentedDefaultsOnlyInLauncherProcess() throws IOException {
        String ps = read("scripts/docker-production-up.ps1");
        String sh = read("scripts/docker-production-up.sh");
        String env = read(".env.example");

        assertThat(ps)
            .contains("function Initialize-InsecureDefaults")
            .contains("function Set-InsecureDefaultIfMissing")
            .contains("Resolve-InsecureMonitoringMasterKeyDefault")
            .contains("[Environment]::SetEnvironmentVariable($Name, $DefaultValue, \"Process\")")
            .contains("if ($AllowInsecureDefaults)")
            .contains("IGUANA_POSTGRES_PASSWORD = \"iguana\"")
            .contains("IGUANA_RABBITMQ_PASSWORD = \"iguana\"")
            .contains("IGUANA_REDIS_PASSWORD = \"iguana-redis\"")
            .contains("APP_STORAGE_OBJECT_ACCESS_KEY = \"iguana-minio\"")
            .contains("APP_STORAGE_OBJECT_SECRET_KEY = \"iguana-minio-secret\"")
            .contains("APP_STORAGE_OBJECT_BUCKET = \"iguana\"")
            .contains("IGUANA_GRAFANA_ADMIN_PASSWORD")
            .contains("base64:$encoded")
            .doesNotContain("Set-Content -LiteralPath $dotEnvPath")
            .doesNotContain("WriteAllText($dotEnvPath");

        assertThat(sh)
            .contains("apply_insecure_defaults")
            .contains("set_insecure_default_if_missing")
            .contains("resolve_insecure_monitoring_master_key_default")
            .contains("export \"${name}=${default_value}\"")
            .contains("IGUANA_POSTGRES_PASSWORD\" \"iguana\"")
            .contains("IGUANA_RABBITMQ_PASSWORD\" \"iguana\"")
            .contains("IGUANA_REDIS_PASSWORD\" \"iguana-redis\"")
            .contains("APP_STORAGE_OBJECT_ACCESS_KEY\" \"iguana-minio\"")
            .contains("APP_STORAGE_OBJECT_SECRET_KEY\" \"iguana-minio-secret\"")
            .contains("APP_STORAGE_OBJECT_BUCKET\" \"iguana\"")
            .contains("IGUANA_GRAFANA_ADMIN_PASSWORD")
            .contains("base64:%s")
            .doesNotContain("cat > \"${ENV_FILE}\"")
            .doesNotContain("printf '%s\\n' > \"${ENV_FILE}\"");

        assertThat(env)
            .contains("IGUANA_POSTGRES_PASSWORD=iguana")
            .contains("IGUANA_RABBITMQ_PASSWORD=iguana")
            .contains("IGUANA_REDIS_PASSWORD=iguana-redis")
            .contains("APP_STORAGE_OBJECT_ACCESS_KEY=iguana-minio")
            .contains("APP_STORAGE_OBJECT_SECRET_KEY=iguana-minio-secret")
            .contains("APP_STORAGE_OBJECT_BUCKET=iguana")
            .contains("IGUANA_GRAFANA_ADMIN_PASSWORD=change-me");
    }

    @Test
    void secureProductionPathStillRejectsMissingOrKnownDefaultSecrets() throws IOException {
        String ps = read("scripts/docker-production-up.ps1");
        String sh = read("scripts/docker-production-up.sh");

        assertThat(ps)
            .contains("Assert-NonDefaultSecret")
            .contains("-DisallowedValues @(\"iguana\")")
            .contains("-DisallowedValues @(\"iguana-redis\")")
            .contains("-DisallowedValues @(\"change-me\", \"admin\", \"grafana\")");

        assertThat(sh)
            .contains("assert_non_default_secret")
            .contains("\"iguana\"")
            .contains("\"iguana-redis\"")
            .contains("\"change-me\" \"admin\" \"grafana\"");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(REPO_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
