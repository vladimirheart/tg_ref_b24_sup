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
    void downloadBuilderKeepsConventionalPathAndEncodesFilenameExactlyOnce() {
        String url = AttachmentStorageKeyResolver.buildTicketAttachmentDownloadUrl("T-PLAIN", "photo+ Ю?#.png");
        assertThat(url).isEqualTo("/api/attachments/tickets/T-PLAIN/photo+%20%D0%AE%3F%23.png");
        var reference = AttachmentStorageKeyResolver.parseLegacyDialogReference(url);
        assertThat(reference.ticketId()).isEqualTo("T-PLAIN");
        assertThat(reference.reference()).isEqualTo("photo+ Ю?#.png");
    }

    @Test
    void conventionalIdWithPercentLookingFilenameUsesQueryWithoutDoubleDecoding() {
        String url = AttachmentStorageKeyResolver.buildTicketAttachmentDownloadUrl("T-PLAIN", "photo+%2F Ю.png");
        assertThat(url).startsWith("/api/attachments/tickets/by-ticket?ticketId=T-PLAIN&filename=");
        var reference = AttachmentStorageKeyResolver.parseLegacyDialogReference(url);
        assertThat(reference.ticketId()).isEqualTo("T-PLAIN");
        assertThat(reference.reference()).isEqualTo("photo+%2F Ю.png");
    }

    @ParameterizedTest
    @ValueSource(strings = {" T ", "T/100", " T/100?&%#+ Ю ", "T%2F+", " null ", "T\\opaque"})
    void downloadBuilderDoesNotNormalizeOpaqueIdentity(String owner) {
        String filename = "folder/photo+%2F Ю.png";
        String url = AttachmentStorageKeyResolver.buildTicketAttachmentDownloadUrl(owner, filename);
        assertThat(url).startsWith("/api/attachments/tickets/by-ticket?ticketId=");
        var reference = AttachmentStorageKeyResolver.parseLegacyDialogReference(url);
        assertThat(reference.ticketId()).isEqualTo(owner);
        assertThat(reference.reference()).isEqualTo(filename);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t"})
    void downloadBuilderDoesNotCreateLocatorForMissingContext(String missing) {
        assertThat(AttachmentStorageKeyResolver.buildTicketAttachmentDownloadUrl(missing, "file.pdf")).isNull();
        assertThat(AttachmentStorageKeyResolver.buildTicketAttachmentDownloadUrl("T", missing)).isNull();
    }

    @Test
    void ticketQueryAcceptsParameterOrderAndServletPlusDecoding() {
        var reference = AttachmentStorageKeyResolver.parseLegacyDialogReference(
                "api/attachments/tickets/by-ticket?filename=photo%2B%252F+name.pdf&ticketId=+T%2FX%2B+");

        assertThat(reference.ticketId()).isEqualTo(" T/X+ ");
        assertThat(reference.reference()).isEqualTo("photo+%2F name.pdf");
        assertThat(reference.kind()).isEqualTo(AttachmentStorageKeyResolver.LegacyDialogReferenceKind.FILENAME);
    }

    @ParameterizedTest
    @ValueSource(strings = {"T", " T ", " T/100?&%#+ Ю ", "T%2F+", " null "})
    void ticketQueryKeepsOpaqueIdentityAndNestedFilename(String owner) {
        String filename = "folder/photo+%2F Ю.bin";
        var reference = AttachmentStorageKeyResolver.parseLegacyDialogReference(
                "/api/attachments/tickets/by-ticket?ticketId=" + UriUtils.encode(owner, StandardCharsets.UTF_8)
                        + "&filename=" + UriUtils.encode(filename, StandardCharsets.UTF_8));

        assertThat(reference.ticketId()).isEqualTo(owner);
        assertThat(reference.reference()).isEqualTo(filename);
        assertThat(reference.kind()).isEqualTo(AttachmentStorageKeyResolver.LegacyDialogReferenceKind.FILENAME);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "?ticketId=T", "?filename=file.pdf", "?ticketId=&filename=file.pdf",
            "?ticketId=%20&filename=file.pdf", "?ticketId=T&filename=",
            "?ticketId=T&filename=file.pdf&filename=other.pdf", "?ticketId=T&ticketId=U",
            "?ticketId=T&filename=file.pdf&unexpected=1", "?ticketId=T&other=file.pdf",
            "?ticketId=T&filename=%ZZ", "?ticketId=%ZZ&filename=file.pdf",
            "?filename=file.pdf&filename=other.pdf", "?ticketId=T&filename=file.pdf#fragment"})
    void invalidTicketQueryDoesNotBecomeALocalFilename(String suffix) {
        assertThat(AttachmentStorageKeyResolver.parseLegacyDialogReference(
                "/api/attachments/tickets/by-ticket" + suffix)).isNull();
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
