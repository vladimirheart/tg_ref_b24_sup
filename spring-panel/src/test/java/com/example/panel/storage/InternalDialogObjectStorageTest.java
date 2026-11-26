package com.example.panel.storage;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;

class InternalDialogObjectStorageTest {
    @TempDir Path temp;
    AttachmentObjectStorageService create(ObjectStorageProperties properties) throws Exception {
        return new AttachmentObjectStorageService(properties,temp.resolve("files").toString(),temp.resolve("knowledge").toString(),
            temp.resolve("photos").toString(),temp.resolve("avatars").toString());
    }
    @Test void localInternalDomainIsSeparateFromCustomerTicketAndOtherFiles() throws Exception {
        var service=create(new ObjectStorageProperties());String key="a".repeat(32)+"/00000000-0000-0000-0000-000000000001";
        try(var stored=service.storeInternalDialogAttachment(key,"application/octet-stream",new ByteArrayInputStream(new byte[]{1,2,3}))){assertThat(stored.size()).isEqualTo(3);}
        assertThat(Files.exists(temp.resolve("files/internal_dialogs/"+key))).isTrue();
        assertThat(Files.exists(temp.resolve("files/"+key))).isFalse();
        try(var stored=service.openInternalDialogAttachment(key)){assertThat(stored.inputStream().readAllBytes()).containsExactly(1,2,3);}
        service.deleteInternalDialogAttachment(key);
        assertThat(Files.exists(temp.resolve("files/internal_dialogs/"+key))).isFalse();
    }
    @Test void s3UsesAnIndependentDomainRatherThanPublicTicketAttachments() throws Exception {
        var properties=new ObjectStorageProperties();properties.setMode("s3");properties.setBucket("fixture");properties.setKeyPrefix("fixture");
        var service=create(properties);var s3=mock(S3Client.class);ReflectionTestUtils.setField(service,"s3Client",s3);
        String key="b".repeat(32)+"/00000000-0000-0000-0000-000000000001";
        try(var stored=service.storeInternalDialogAttachment(key,"application/octet-stream",new ByteArrayInputStream(new byte[]{1}))){}
        verify(s3).putObject(argThat((PutObjectRequest request)->request.key().equals("fixture/internal_dialogs/"+key)),any(RequestBody.class));
    }
}
