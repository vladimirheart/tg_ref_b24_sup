package com.example.panel.storage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ObjectPassportPhotoStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void deleteQuietlyIgnoresAlreadyMissingFile() throws Exception {
        AttachmentObjectStorageService attachmentObjectStorageService = mock(AttachmentObjectStorageService.class);
        ObjectPassportPhotoStorageService service =
                new ObjectPassportPhotoStorageService(attachmentObjectStorageService, tempDir.toString());

        assertDoesNotThrow(() -> service.deleteQuietly("missing-photo.jpg"));
    }
}
