package com.example.panel.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DockerProductionStorageCutoverSourceContractTest {

    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void storageBackfillUsesCanonicalRuntimeKeysAndVerifiedDatabaseMappings() throws IOException {
        String ps = read("scripts/docker-production-storage-backfill.ps1");

        assertThat(ps)
            .contains("[switch]$ValidateOnly")
            .contains("APP_STORAGE_OBJECT_KEY_PREFIX")
            .contains("Get-PanelRuntimeObjectKeyPrefix")
            .contains("Object key prefix mismatch")
            .contains("json_build_object('id', id, 'storage_key', storage_key, 'legacy_attachment_ref', legacy_attachment_ref)")
            .contains("Join-ObjectKey -Prefix $objectKeyPrefix -Domain \"attachments\" -LogicalKey $storageKey")
            .contains("mc cp /source/file \"local/$IGUANA_BACKFILL_BUCKET/$IGUANA_BACKFILL_OBJECT_KEY\"")
            .contains("mc stat \"local/$IGUANA_BACKFILL_BUCKET/$IGUANA_BACKFILL_OBJECT_KEY\"")
            .contains("metadata_rows_verified_s3=")
            .contains("missing_s3_dialog_objects=")
            .contains("missing_metadata_rows=")
            .contains("without deleting local source files")
            .contains("\"run\", \"--rm\", \"--no-deps\", \"-T\"")
            .contains("\"minio-init\"")
            .contains("IGUANA_BACKFILL_ACCESS_KEY")
            .contains("IGUANA_BACKFILL_SECRET_KEY")
            .doesNotContain("mc cp --overwrite")
            .doesNotContain("$LASTEXITCODE:")
            .doesNotContain("--network $NetworkName")
            .doesNotContain(".Replace(\"__ACCESS_KEY__\"")
            .doesNotContain("Remove-Item")
            .doesNotContain("rm -rf");
    }

    @Test
    void cutoverAuditIsReadOnlyAndChecksAttachmentAndPanelAvatarMappings() throws IOException {
        String ps = read("scripts/docker-production-storage-cutover-audit.ps1");

        assertThat(ps)
            .contains("[switch]$ValidateOnly")
            .contains("SELECT json_build_object('id', id, 'storage_key', storage_key)::text FROM chat_attachment_metadata")
            .contains("SELECT json_build_object('id', id, 'photo', photo)::text FROM users")
            .contains("Join-ObjectKey -Prefix $objectKeyPrefix -Domain \"attachments\"")
            .contains("Join-ObjectKey -Prefix $objectKeyPrefix -Domain \"avatars\"")
            .contains("missing_s3_dialog_objects=")
            .contains("missing_s3_panel_avatars=")
            .contains("missing_metadata_rows=")
            .contains("STORAGE CUTOVER AUDIT PASSED")
            .contains("Keep APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED=true")
            .contains("mc stat")
            .doesNotContain("UPDATE ")
            .doesNotContain("DELETE ")
            .doesNotContain("mc cp")
            .doesNotContain("mc mirror")
            .doesNotContain("Remove-Item")
            .doesNotContain("rm -rf")
            .doesNotContain("$LASTEXITCODE:");
    }

    @Test
    void clientAvatarCutoverAuditUsesRuntimeNamesAndIsReadOnly() throws IOException {
        String ps = read("scripts/docker-production-client-avatar-cutover-audit.ps1");

        assertThat(ps)
            .contains("[switch]$ValidateOnly")
            .contains("SELECT DISTINCT user_id FROM client_avatar_history")
            .contains("\"${userId}.jpg\"")
            .contains("\"${userId}_full.jpg\"")
            .contains("missing_s3_client_avatars=")
            .contains("CLIENT AVATAR CUTOVER AUDIT PASSED")
            .contains("Keep APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED=true")
            .contains("mc stat")
            .doesNotContain("UPDATE ")
            .doesNotContain("DELETE ")
            .doesNotContain("mc cp")
            .doesNotContain("mc mirror")
            .doesNotContain("Remove-Item")
            .doesNotContain("rm -rf")
            .doesNotContain("$LASTEXITCODE:");
    }

    @Test
    void productionContourPropagatesObjectPrefixAndDocumentsCutoverSwitch() throws IOException {
        String compose = read("docker-compose.production-contour.yml");
        String env = read(".env.example");

        assertThat(compose)
            .contains("APP_STORAGE_OBJECT_KEY_PREFIX: ${APP_STORAGE_OBJECT_KEY_PREFIX:-iguana}")
            .contains("APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED: ${APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED:-true}");

        assertThat(env)
            .contains("APP_STORAGE_OBJECT_KEY_PREFIX=iguana")
            .contains("APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED=true");
    }

    @Test
    void javaFallbackTestsAssertExactCanonicalObjectKeys() throws IOException {
        String javaTest = read("spring-panel/src/test/java/com/example/panel/storage/AttachmentObjectStorageServiceLegacyFallbackTest.java");

        assertThat(javaTest)
            .contains("iguana/attachments/ticket-1/file.txt")
            .contains("iguana/avatars/380742186.jpg")
            .contains("ArgumentCaptor<PutObjectRequest>");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(REPO_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
