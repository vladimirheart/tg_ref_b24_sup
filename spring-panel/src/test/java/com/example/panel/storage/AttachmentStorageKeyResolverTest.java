package com.example.panel.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AttachmentStorageKeyResolverTest {

    @Test
    void normalizesLegacyAbsoluteAttachmentPathIntoStorageKey() {
        String storageKey = AttachmentStorageKeyResolver.normalizeStorageKey(
                "ignored-ticket",
                "C:\\legacy\\java-bot\\attachments\\channel-public-id\\2026\\07\\31\\video.mp4"
        );

        assertEquals("channel-public-id/2026/07/31/video.mp4", storageKey);
    }

    @Test
    void normalizesFilenameOnlyAttachmentIntoTicketScopedStorageKey() {
        String storageKey = AttachmentStorageKeyResolver.normalizeStorageKey(
                "ticket-123",
                "4467342b-4316-40d9-b99d-fc1ae347b87c_clip.mp4"
        );

        assertEquals("ticket-123/4467342b-4316-40d9-b99d-fc1ae347b87c_clip.mp4", storageKey);
    }

    @Test
    void stripsUuidPrefixWhenResolvingOriginalName() {
        String originalName = AttachmentStorageKeyResolver.resolveOriginalName(
                null,
                null,
                "ticket-123/4467342b-4316-40d9-b99d-fc1ae347b87c_clip.mp4"
        );

        assertEquals("clip.mp4", originalName);
    }

    @Test
    void ignoresApiUrlsWhenBuildingStorageKey() {
        assertNull(AttachmentStorageKeyResolver.normalizeStorageKey(
                "ticket-123",
                "/api/attachments/tickets/by-path?path=legacy-file"
        ));
    }
}
