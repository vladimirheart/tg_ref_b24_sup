package com.example.supportbot.max;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.supportbot.entity.ClientAvatarHistory;
import com.example.supportbot.repository.ClientAvatarHistoryRepository;
import com.example.supportbot.service.AttachmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MaxClientAvatarSyncServiceTest {

    @Test
    void storesThumbAndFullAvatarAndWritesProviderEvidence() throws Exception {
        MaxApiClient api = mock(MaxApiClient.class);
        AttachmentService attachments = mock(AttachmentService.class);
        ClientAvatarHistoryRepository historyRepository = mock(ClientAvatarHistoryRepository.class);
        MaxClientAvatarSyncService service = new MaxClientAvatarSyncService(api, attachments, historyRepository, new ObjectMapper());
        try {
            when(api.fetchDialogUser(2002L)).thenReturn(Optional.of(new MaxApiClient.MaxDialogUser(
                1001L, "https://cdn.max.ru/thumb.jpg", "https://cdn.max.ru/full.jpg")));
            when(api.downloadAvatar("https://cdn.max.ru/thumb.jpg"))
                .thenReturn(new MaxApiClient.DownloadedAvatar(new byte[] {1, 2}, "image/jpeg", "thumb.jpg"));
            when(api.downloadAvatar("https://cdn.max.ru/full.jpg"))
                .thenReturn(new MaxApiClient.DownloadedAvatar(new byte[] {3, 4, 5}, "image/jpeg", "full.jpg"));
            when(historyRepository.findByUserIdAndFingerprint(eq(1001L), anyString())).thenReturn(Optional.empty());
            when(attachments.storeClientAvatar(eq(1001L), eq(false), eq(".jpg"), eq("image/jpeg"), any(InputStream.class)))
                .thenReturn(new AttachmentService.StoredAvatar("1001.jpg", "local_fs", null));
            when(attachments.storeClientAvatar(eq(1001L), eq(true), eq(".jpg"), eq("image/jpeg"), any(InputStream.class)))
                .thenReturn(new AttachmentService.StoredAvatar("1001_full.jpg", "local_fs", null));

            assertThat(service.syncNow(1001L, 2002L)).isTrue();

            ArgumentCaptor<ClientAvatarHistory> captor = ArgumentCaptor.forClass(ClientAvatarHistory.class);
            verify(historyRepository).save(captor.capture());
            ClientAvatarHistory history = captor.getValue();
            assertThat(history.getUserId()).isEqualTo(1001L);
            assertThat(history.getSource()).isEqualTo("max");
            assertThat(history.getThumbPath()).isEqualTo("1001.jpg");
            assertThat(history.getFullPath()).isEqualTo("1001_full.jpg");
            assertThat(history.getFileSize()).isEqualTo(5);
            assertThat(history.getMetadata()).contains("\"chat_id\":2002");
        } finally {
            service.shutdown();
        }
    }

    @Test
    void acceptsProfileWithoutAvatarWithoutCreatingHistoryAuditRow() {
        MaxApiClient api = mock(MaxApiClient.class);
        AttachmentService attachments = mock(AttachmentService.class);
        ClientAvatarHistoryRepository historyRepository = mock(ClientAvatarHistoryRepository.class);
        MaxClientAvatarSyncService service = new MaxClientAvatarSyncService(api, attachments, historyRepository, new ObjectMapper());
        try {
            when(api.fetchDialogUser(2002L)).thenReturn(Optional.of(new MaxApiClient.MaxDialogUser(1001L, null, null)));

            assertThat(service.syncNow(1001L, 2002L)).isTrue();

            verifyNoInteractions(attachments, historyRepository);
        } finally {
            service.shutdown();
        }
    }

    @Test
    void refusesProfileThatDoesNotMatchMessageUser() {
        MaxApiClient api = mock(MaxApiClient.class);
        AttachmentService attachments = mock(AttachmentService.class);
        ClientAvatarHistoryRepository historyRepository = mock(ClientAvatarHistoryRepository.class);
        MaxClientAvatarSyncService service = new MaxClientAvatarSyncService(api, attachments, historyRepository, new ObjectMapper());
        try {
            when(api.fetchDialogUser(2002L)).thenReturn(Optional.of(new MaxApiClient.MaxDialogUser(9999L, null, null)));

            assertThat(service.syncNow(1001L, 2002L)).isFalse();

            verifyNoInteractions(attachments, historyRepository);
        } finally {
            service.shutdown();
        }
    }
}
