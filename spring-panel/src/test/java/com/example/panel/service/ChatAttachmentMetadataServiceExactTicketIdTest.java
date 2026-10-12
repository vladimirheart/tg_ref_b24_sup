package com.example.panel.service;

import com.example.panel.storage.AttachmentObjectStorageService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatAttachmentMetadataServiceExactTicketIdTest {

    private Object[] insertRow(String ticketId) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AttachmentObjectStorageService storage = mock(AttachmentObjectStorageService.class);
        when(storage.providerLabel()).thenReturn("s3");
        ChatAttachmentMetadataService service = new ChatAttachmentMetadataService(jdbc, storage);
        service.upsertForChatHistory(41L, ticketId, 7L,
                "channel-public-id/2026/10/12/photo.jpg", "photo.jpg", "image/jpeg", 128L, "photo");
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, times(2)).update(anyString(), arguments.capture());
        return arguments.getAllValues().stream().filter(values -> values.length > 1).findFirst().orElseThrow();
    }

    @Test
    void preservesExactPaddedTicketIdentityWithoutRewritingChannelScopedStorageKey() {
        Object[] row = insertRow(" T-EXACT ");
        assertThat(row[1]).isEqualTo(" T-EXACT ");
        assertThat(row[3]).isEqualTo("channel-public-id/2026/10/12/photo.jpg");
        assertThat(row[8]).isEqualTo("channel-public-id/2026/10/12/photo.jpg");
        assertThat(row[9]).isEqualTo("normalized");
    }

    @Test
    void distinguishesLiteralNullTextAndTrimmedSibling() {
        assertThat(insertRow(" null ")[1]).isEqualTo(" null ");
        assertThat(insertRow("null")[1]).isEqualTo("null");
    }

    @Test
    void mapsAbsentOrBlankIdentityToNullWithoutTrimmingNonblankOne() {
        assertThat(insertRow(null)[1]).isNull();
        assertThat(insertRow("   ")[1]).isNull();
        assertThat(insertRow("T-EXACT")[1]).isEqualTo("T-EXACT");
    }
}
