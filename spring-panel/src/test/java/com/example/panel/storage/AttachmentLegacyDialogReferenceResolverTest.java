package com.example.panel.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.util.UriUtils;

class AttachmentLegacyDialogReferenceResolverTest {
    @ParameterizedTest
    @ValueSource(strings = {"report+%2F#.pdf", "T/2026/report+%2F#.pdf", "attachments/T/report.pdf",
            "C:\\legacy\\attachments\\T\\report.pdf", "/opt/attachments/T/report.pdf"})
    void literalReferencesKeepPercentAndPlusWithoutUrlDecoding(String raw) {
        var reference = AttachmentStorageKeyResolver.parseLegacyDialogReference(raw);

        assertThat(reference).isNotNull();
        assertThat(reference.reference()).isEqualTo(raw);
        assertThat(reference.ticketId()).isNull();
        assertThat(reference.kind()).isEqualTo(raw.contains("attachments")
                ? AttachmentStorageKeyResolver.LegacyDialogReferenceKind.STORED_PATH
                : raw.contains("/") ? AttachmentStorageKeyResolver.LegacyDialogReferenceKind.STORAGE_KEY
                : AttachmentStorageKeyResolver.LegacyDialogReferenceKind.FILENAME);
    }

    @ParameterizedTest
    @ValueSource(strings = {"T", " T ", " T/100?&%#+ Ю ", "T%2F+", " null "})
    void directApiReferenceDecodesOpaqueTicketIdExactlyOnceWithoutTrimming(String owner) {
        String filename = "photo+%2F name.pdf";
        String raw = "/api/attachments/tickets/" + UriUtils.encodePathSegment(owner, StandardCharsets.UTF_8)
                + "/" + UriUtils.encodePathSegment(filename, StandardCharsets.UTF_8);

        var reference = AttachmentStorageKeyResolver.parseLegacyDialogReference(raw);

        assertThat(reference).isNotNull();
        assertThat(reference.ticketId()).isEqualTo(owner);
        assertThat(reference.reference()).isEqualTo(filename);
        assertThat(reference.kind()).isEqualTo(AttachmentStorageKeyResolver.LegacyDialogReferenceKind.FILENAME);
    }

    @ParameterizedTest
    @ValueSource(strings = {"key", "path"})
    void queryEnvelopeUsesServletPlusAndSinglePercentDecode(String kind) {
        String raw = "/api/attachments/tickets/" + ("key".equals(kind) ? "by-storage-key?key=" : "by-path?path=")
                + "T%2Fphoto%2B%252F+name%23.pdf";

        var reference = AttachmentStorageKeyResolver.parseLegacyDialogReference(raw);

        assertThat(reference).isNotNull();
        assertThat(reference.reference()).isEqualTo("T/photo+%2F name#.pdf");
        assertThat(reference.ticketId()).isNull();
        assertThat(reference.kind()).isEqualTo("key".equals(kind)
                ? AttachmentStorageKeyResolver.LegacyDialogReferenceKind.STORAGE_KEY
                : AttachmentStorageKeyResolver.LegacyDialogReferenceKind.STORED_PATH);
    }

    @Test
    void relativeApiReferenceUsesTheSameClassifier() {
        var reference = AttachmentStorageKeyResolver.parseLegacyDialogReference(
                "api/attachments/tickets/by-storage-key?key=T%2Fphoto.pdf");

        assertThat(reference.kind()).isEqualTo(AttachmentStorageKeyResolver.LegacyDialogReferenceKind.STORAGE_KEY);
        assertThat(reference.reference()).isEqualTo("T/photo.pdf");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "/api/avatars/file", "/api/attachments/knowledge-base/file",
            "attachments-api/file", "//example.test/file", "\\\\example.test\\file",
            "https://example.test/file", "/api/attachments/tickets/by-storage-key",
            "/api/attachments/tickets/by-storage-key?path=T/file",
            "/api/attachments/tickets/by-storage-key?key=",
            "/api/attachments/tickets/by-storage-key?key=T/file&key=U/file",
            "/api/attachments/tickets/by-path?path=T/file&unexpected=1",
            "/api/attachments/tickets/by-path?path=%ZZ",
            "/api/attachments/tickets/by-path?path=T/file#fragment",
            "/api/attachments/tickets/T/file?key=U/file",
            "/api/attachments/tickets/T/folder/file",
            "/api/attachments/tickets//file", "/api/attachments/tickets/T/",
            "/api/attachments/tickets/%ZZ/file", "/api/attachments/tickets/T/%ZZ",
            "/api/attachments/tickets/T/fi\nle"})
    void malformedOrUnsupportedApiDoesNotFallBackToFilenameOrStorageKey(String raw) {
        assertThat(AttachmentStorageKeyResolver.parseLegacyDialogReference(raw)).isNull();
    }

    @Test
    void excessiveEnvelopeAndEmbeddedControlsAreRejected() {
        assertThat(AttachmentStorageKeyResolver.parseLegacyDialogReference("T/" + "x".repeat(4096))).isNull();
        assertThat(AttachmentStorageKeyResolver.parseLegacyDialogReference("T/fi\nle")).isNull();
    }
}
