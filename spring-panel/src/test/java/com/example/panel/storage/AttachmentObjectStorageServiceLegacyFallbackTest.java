package com.example.panel.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

class AttachmentObjectStorageServiceLegacyFallbackTest {

    @TempDir
    Path tempDir;

    private AttachmentObjectStorageService service;
    private S3Client s3Client;
    private Path attachmentsRoot;
    private Path avatarsRoot;

    @BeforeEach
    void setUp() throws Exception {
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setMode("s3");
        properties.setBucket("iguana");
        properties.setEndpoint("http://minio:9000");
        properties.setAccessKey("iguana-minio");
        properties.setSecretKey("iguana-minio-secret");
        properties.setKeyPrefix("iguana");

        attachmentsRoot = tempDir.resolve("attachments");
        Path knowledgeBaseRoot = tempDir.resolve("knowledge_base");
        Path passportPhotosRoot = tempDir.resolve("passport_photos");
        avatarsRoot = tempDir.resolve("avatars");

        service = new AttachmentObjectStorageService(
                properties,
                attachmentsRoot.toString(),
                knowledgeBaseRoot.toString(),
                passportPhotosRoot.toString(),
                avatarsRoot.toString()
        );

        s3Client = mock(S3Client.class);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().eTag("ok").build());
        injectS3Client(service, s3Client);
    }

    @Test
    void avatarExistsFallsBackToLegacyLocalFileWhenS3ObjectIsMissing() throws Exception {
        Path avatar = avatarsRoot.resolve("380742186.jpg");
        Files.writeString(avatar, "avatar-bytes", StandardCharsets.UTF_8);

        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("missing").build());

        assertThat(service.avatarExists("380742186.jpg")).isTrue();

        ArgumentCaptor<PutObjectRequest> putRequest = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(putRequest.capture(), any(RequestBody.class));
        assertThat(putRequest.getValue().bucket()).isEqualTo("iguana");
        assertThat(putRequest.getValue().key()).isEqualTo("iguana/avatars/380742186.jpg");
    }

    @Test
    void openDialogAttachmentByStorageKeyServesLegacyLocalFileWhenS3ObjectIsMissing() throws Exception {
        Path attachment = attachmentsRoot.resolve("ticket-1").resolve("file.txt");
        Files.createDirectories(attachment.getParent());
        Files.writeString(attachment, "legacy-payload", StandardCharsets.UTF_8);

        when(s3Client.getObject(any(software.amazon.awssdk.services.s3.model.GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("missing").build());
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("missing").build());

        try (AttachmentObjectStorageService.StoredBinary binary =
                     service.openDialogAttachmentByStorageKey("ticket-1/file.txt")) {
            assertThat(binary.logicalKey()).isEqualTo("ticket-1/file.txt");
            assertThat(binary.size()).isEqualTo("legacy-payload".getBytes(StandardCharsets.UTF_8).length);
            assertThat(new String(binary.inputStream().readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("legacy-payload");
        }

        ArgumentCaptor<PutObjectRequest> putRequest = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(putRequest.capture(), any(RequestBody.class));
        assertThat(putRequest.getValue().bucket()).isEqualTo("iguana");
        assertThat(putRequest.getValue().key()).isEqualTo("iguana/attachments/ticket-1/file.txt");
    }

    @Test
    void openDialogAttachmentByStorageKeyDoesNotUseLegacyLocalFileWhenFallbackIsDisabled() throws Exception {
        ObjectStorageProperties disabledProperties = new ObjectStorageProperties();
        disabledProperties.setMode("s3");
        disabledProperties.setBucket("iguana");
        disabledProperties.setEndpoint("http://minio:9000");
        disabledProperties.setAccessKey("iguana-minio");
        disabledProperties.setSecretKey("iguana-minio-secret");
        disabledProperties.setKeyPrefix("iguana");
        disabledProperties.setLegacyLocalFallbackEnabled(false);

        AttachmentObjectStorageService disabledService = new AttachmentObjectStorageService(
                disabledProperties,
                attachmentsRoot.toString(),
                tempDir.resolve("knowledge-disabled").toString(),
                tempDir.resolve("passport-disabled").toString(),
                avatarsRoot.toString()
        );
        S3Client disabledS3Client = mock(S3Client.class);
        when(disabledS3Client.getObject(any(software.amazon.awssdk.services.s3.model.GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("missing").build());
        injectS3Client(disabledService, disabledS3Client);

        Path attachment = attachmentsRoot.resolve("ticket-2").resolve("file.txt");
        Files.createDirectories(attachment.getParent());
        Files.writeString(attachment, "legacy-payload", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> disabledService.openDialogAttachmentByStorageKey("ticket-2/file.txt"))
                .isInstanceOf(NoSuchKeyException.class);
        verify(disabledS3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    private static void injectS3Client(AttachmentObjectStorageService service, S3Client s3Client) throws Exception {
        Field field = AttachmentObjectStorageService.class.getDeclaredField("s3Client");
        field.setAccessible(true);
        field.set(service, s3Client);
    }
}
