package com.example.panel.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DockerProductionSharedConfigExternalizationSourceContractTest {

    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final String PORTABLE_DEFAULT =
        "${IGUANA_SHARED_CONFIG_DIR:-../iguana-runtime/tg_ref_b24_sup/shared-config}";
    private static final String LEGACY_REPO_DEFAULT =
        "${IGUANA_SHARED_CONFIG_DIR:-./config/shared}";

    @Test
    void productionRuntimeDefaultsOutsideGitAndSeedsOnlyEmptyRuntimeDirectory() throws IOException {
        String contour = read("docker-compose.production-contour.yml");

        assertThat(contour)
            .contains(PORTABLE_DEFAULT + ":/opt/iguana/config/shared")
            .contains(PORTABLE_DEFAULT + ":/opt/iguana/config/shared:ro")
            .contains("\n  shared-config-check:\n")
            .contains(PORTABLE_DEFAULT + ":/target:ro")
            .contains("test -s /target/settings.json")
            .contains("test -s /target/locations.json")
            .contains("test -s /target/org_structure.json")
            .contains("shared-config-check:\n    condition: service_completed_successfully")
            .doesNotContain("cp -a /seed/. /target/")
            .doesNotContain(LEGACY_REPO_DEFAULT);
    }

    @Test
    void backupRecoveryAndHostPolicyResolveTheSamePortableDirectory() throws IOException {
        String backup = read("docker-compose.production-backup.yml");
        String legacy = read("docker-compose.legacy-sqlite-import.yml");
        String psPolicy = read("scripts/lib/backup-config.ps1");
        String shPolicy = read("scripts/lib/backup-config.sh");
        String productionUpPs = read("scripts/docker-production-up.ps1");
        String productionUpSh = read("scripts/docker-production-up.sh");
        String env = read(".env.example");

        assertThat(backup)
            .contains(PORTABLE_DEFAULT)
            .contains("files-backup:\n    depends_on:\n      shared-config-check:\n        condition: service_completed_successfully")
            .doesNotContain(LEGACY_REPO_DEFAULT);
        assertThat(legacy).contains(PORTABLE_DEFAULT).doesNotContain(LEGACY_REPO_DEFAULT);
        assertThat(psPolicy).contains("$configured = \"../iguana-runtime/tg_ref_b24_sup/shared-config\"");
        assertThat(shPolicy).contains("configured=\"../iguana-runtime/tg_ref_b24_sup/shared-config\"");
        assertThat(productionUpPs)
            .contains("Initialize-SharedConfigRuntimeDirectory")
            .contains("Copy-Item -LiteralPath $child.FullName -Destination $runtime -Recurse -Force")
            .contains("Shared config runtime is empty/missing and would be initialized from seed");
        assertThat(productionUpSh)
            .contains("initialize_shared_config_runtime_dir")
            .contains("cp -Rp \"${seed}/.\" \"${runtime}/\"")
            .contains("Shared config runtime is empty/missing and would be initialized from seed");
        assertThat(env)
            .contains("IGUANA_SHARED_CONFIG_DIR=../iguana-runtime/tg_ref_b24_sup/shared-config")
            .doesNotContain("IGUANA_SHARED_CONFIG_DIR=./config/shared");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(REPO_ROOT.resolve(relativePath), StandardCharsets.UTF_8)
            .replace("\r\n", "\n");
    }
}