package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.supportbot.config.ObjectStorageProperties;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AttachmentServiceClientAvatarTest {

    @TempDir
    Path tempDir;

    @Test
    void storesCanonicalClientAvatarAndRemovesObsoleteVariant() throws Exception {
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setMode("local_fs");
        AttachmentService service = new AttachmentService(tempDir, properties);
        Path avatars = tempDir.resolve("avatars");
        Files.createDirectories(avatars);
        Files.write(avatars.resolve("77.png"), new byte[] {9});

        AttachmentService.StoredAvatar stored = service.storeClientAvatar(
            77L, false, ".jpg", "image/jpeg", new ByteArrayInputStream(new byte[] {1, 2, 3}));

        assertThat(stored.storedName()).isEqualTo("77.jpg");
        assertThat(Files.readAllBytes(avatars.resolve("77.jpg"))).containsExactly((byte) 1, (byte) 2, (byte) 3);
        assertThat(Files.exists(avatars.resolve("77.png"))).isFalse();
    }
}
