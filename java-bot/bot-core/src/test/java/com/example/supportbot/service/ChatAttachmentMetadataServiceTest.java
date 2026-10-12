package com.example.supportbot.service;

import com.example.supportbot.config.ObjectStorageProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatAttachmentMetadataServiceTest {

    @Test
    void marksNormalizedS3ObjectAsAvailableWithoutLocalFile() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ObjectStorageProperties objectStorageProperties = mock(ObjectStorageProperties.class);
        when(objectStorageProperties.isS3Mode()).thenReturn(true);
        ChatAttachmentMetadataService service = new ChatAttachmentMetadataService(
                jdbcTemplate,
                objectStorageProperties
        );

        service.upsertForChatHistory(17L, "INC-17", 2L, "INC-17/photo.jpg", "photo.jpg", "photo");

        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, times(2)).update(anyString(), arguments.capture());
        Object[] insertArguments = arguments.getAllValues().stream()
                .filter(values -> values.length > 1)
                .findFirst()
                .orElseThrow();
        assertThat(insertArguments[3]).isEqualTo("INC-17/photo.jpg");
        assertThat(insertArguments[4]).isEqualTo("s3");
        assertThat(insertArguments[9]).isEqualTo("normalized");
        assertThat(insertArguments[10]).isEqualTo("available");
    }
    @Test
    void retainsPaddedTicketIdentityForChannelOwnedAttachmentPath() {
        assertThat(insertIdentityRow(" T-EXACT ")[1]).isEqualTo(" T-EXACT ");
        assertThat(insertIdentityRow(" T-EXACT ")[3]).isEqualTo("channel-public-id/2026/10/12/photo.jpg");
    }

    @Test
    void retainsLiteralNullIdentityWithoutCollapsingPlainSibling() {
        assertThat(insertIdentityRow(" null ")[1]).isEqualTo(" null ");
        assertThat(insertIdentityRow("null")[1]).isEqualTo("null");
    }

    @Test
    void nullOrBlankTicketIdentityRemainsUnassigned() {
        assertThat(insertIdentityRow(null)[1]).isNull();
        assertThat(insertIdentityRow("   ")[1]).isNull();
    }

    private Object[] insertIdentityRow(String ticketId) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectStorageProperties properties = mock(ObjectStorageProperties.class);
        when(properties.isS3Mode()).thenReturn(true);
        ChatAttachmentMetadataService service = new ChatAttachmentMetadataService(jdbc, properties);
        service.upsertForChatHistory(41L, ticketId, 7L,
                "channel-public-id/2026/10/12/photo.jpg", "photo.jpg", "photo");
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, times(2)).update(anyString(), arguments.capture());
        return arguments.getAllValues().stream().filter(values -> values.length > 1).findFirst().orElseThrow();
    }

}
