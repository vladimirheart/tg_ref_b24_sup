package com.example.panel.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AttachmentStorageKeyResolverTest {

    @ParameterizedTest
    @ValueSource(strings = {"attachments/channel/attachments/file.pdf",
            "/attachments/channel/attachments/file.pdf", "Attachments\\channel\\attachments\\file.pdf"})
    void leadingRootMarkerTakesPrecedenceOverLiteralNestedAttachmentsDirectory(String path) {
        assertEquals("channel/attachments/file.pdf", AttachmentStorageKeyResolver.extractAttachmentsSuffix(path));
        assertEquals("channel/attachments/file.pdf", AttachmentStorageKeyResolver.normalizeStorageKey("ignored-owner", path));
    }

    @Test
    void extractedStorageReferenceDoesNotDecodePercentLookingSegmentsOrPlus() {
        assertEquals("channel/T%2F+/file.pdf",
                AttachmentStorageKeyResolver.extractAttachmentsSuffix("attachments/channel/T%2F+/file.pdf"));
    }

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

    @Test
    void extractsTicketIdFromNestedStorageKeyAndLegacyPath() {
        assertEquals("ticket-123", AttachmentStorageKeyResolver.extractDialogTicketId(
                "ticket-123/2026/07/31/video.mp4"
        ));
        assertEquals("channel-public-id", AttachmentStorageKeyResolver.extractDialogTicketId(
                "C:\\legacy\\attachments\\channel-public-id\\2026\\07\\31\\video.mp4"
        ));
        assertNull(AttachmentStorageKeyResolver.extractDialogTicketId("file.mp4"));
    }
}
