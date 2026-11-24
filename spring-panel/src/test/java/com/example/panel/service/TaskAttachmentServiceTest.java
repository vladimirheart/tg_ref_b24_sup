package com.example.panel.service;

import com.example.panel.storage.AttachmentObjectStorageService;
import com.example.panel.storage.ObjectStorageProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TaskAttachmentServiceTest {
    @TempDir Path directory;
    private AttachmentObjectStorageService localStorage() throws IOException {
        var properties = new ObjectStorageProperties(); properties.setMode("local_fs");
        return new AttachmentObjectStorageService(properties, directory.resolve("attachments").toString(),
            directory.resolve("knowledge").toString(), directory.resolve("photos").toString(), directory.resolve("avatars").toString());
    }
    @Test void realBinaryRoundTripIsTaskBoundAndEscapesFileNames() throws Exception {
        var service = new TaskAttachmentService(localStorage());
        String name = "План <новый> & \"финал\".txt";
        String html = service.appendFiles(41, "<p>Описание</p>", List.of(new MockMultipartFile("files", name, "text/html", "payload".getBytes(StandardCharsets.UTF_8))));
        assertThat(html).contains("Описание", "data-task-attachment", "&lt;новый&gt;", "&quot;финал&quot;")
            .doesNotContain("<новый>");
        String stored;
        try (var files = Files.list(directory.resolve("attachments/task_attachments/41"))) { stored = files.findFirst().orElseThrow().getFileName().toString(); }
        try (var binary = service.open(41, stored)) { assertThat(binary.contentType()).isEqualTo("application/octet-stream"); assertThat(binary.inputStream().readAllBytes()).isEqualTo("payload".getBytes(StandardCharsets.UTF_8)); }
        assertThatThrownBy(() -> service.open(42, stored)).isInstanceOfSatisfying(ResponseStatusException.class,
            error -> assertThat(error.getStatusCode().value()).isEqualTo(404));
    }
    @Test void detectedImageTypeDoesNotTrustMultipartMimeOrExtension() throws Exception {
        var service = new TaskAttachmentService(localStorage());
        byte[] png = {(byte)137,80,78,71,13,10,26,10,0,0,0,0};
        String image = service.appendFiles(41, "", List.of(new MockMultipartFile("files", "screen.png", "text/html", png)));
        assertThat(image).contains("<figure", "?preview=true", "<img");
        String fake = service.appendFiles(41, "", List.of(new MockMultipartFile("files", "fake.png", "image/png", "<script>alert(1)</script>".getBytes())));
        assertThat(fake).doesNotContain("<img", "preview=true").contains("download=");
    }
    @Test void rolledBackTransactionRemovesEveryUploadedBinary() throws Exception {
        var storage = localStorage(); var service = new TaskAttachmentService(storage);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.appendFiles(41, "", List.of(new MockMultipartFile("files", "a.txt", "text/plain", new byte[]{1}), new MockMultipartFile("files", "b.txt", "text/plain", new byte[]{2})));
            for (var synchronization : TransactionSynchronizationManager.getSynchronizations()) synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            try (var files = Files.list(directory.resolve("attachments/task_attachments/41"))) { assertThat(files).isEmpty(); }
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
    @Test void invalidNamesAndLimitsNeverReachBinaryStorage() {
        var storage = mock(AttachmentObjectStorageService.class); var service = new TaskAttachmentService(storage);
        for (String name : List.of("../secret.txt", "nested/a.txt", "nested\\a.txt", "bad\nname.txt")) {
            assertThatThrownBy(() -> service.appendFiles(41, "", List.of(new MockMultipartFile("files", name, "text/plain", new byte[]{1})))).isInstanceOf(ResponseStatusException.class);
        }
        assertThatThrownBy(() -> service.appendFiles(41, "", List.of(new MockMultipartFile("files", "empty.txt", "text/plain", new byte[0])))).isInstanceOf(ResponseStatusException.class);
        var large = mock(org.springframework.web.multipart.MultipartFile.class); when(large.getSize()).thenReturn(TaskAttachmentService.MAX_FILE_SIZE + 1); when(large.getOriginalFilename()).thenReturn("large.txt");
        assertThatThrownBy(() -> service.appendFiles(41, "", List.of(large))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.open(41, "../../secret")).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(storage);
    }
    @Test void partialUploadFailureAttemptsCleanupWithoutReturningBrokenLinks() throws Exception {
        var storage = mock(AttachmentObjectStorageService.class); var service = new TaskAttachmentService(storage);
        when(storage.storeTaskAttachment(anyString(), anyString(), any())).thenReturn(new AttachmentObjectStorageService.StoredBinary("unused", "application/octet-stream", 1, null)).thenThrow(new IOException("unavailable"));
        assertThatThrownBy(() -> service.appendFiles(41, "", List.of(new MockMultipartFile("files", "a.txt", "", new byte[]{1}), new MockMultipartFile("files", "b.txt", "", new byte[]{2})))).isInstanceOf(ResponseStatusException.class);
        verify(storage, times(2)).deleteTaskAttachment(startsWith("41/"));
    }
}
