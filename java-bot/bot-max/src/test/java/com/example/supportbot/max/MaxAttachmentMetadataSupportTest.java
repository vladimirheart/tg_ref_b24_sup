package com.example.supportbot.max;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MaxAttachmentMetadataSupportTest {

    @Test
    void firstNonBlankTrimsAndSkipsMissingValues() {
        assertThat(MaxAttachmentMetadataSupport.firstNonBlank((String[]) null)).isNull();
        assertThat(MaxAttachmentMetadataSupport.firstNonBlank()).isNull();
        assertThat(MaxAttachmentMetadataSupport.firstNonBlank(null, "   ", "  report.pdf  ", "later"))
                .isEqualTo("report.pdf");
        assertThat(MaxAttachmentMetadataSupport.firstNonBlank(" ", "\t"))
                .isNull();
    }

    @Test
    void filenameExtensionWinsAndPreservesLegacySanitizing() {
        assertThat(MaxAttachmentMetadataSupport.resolveAttachmentExtension(" photo.JPEG ", "image/png", "photo"))
                .isEqualTo("jpeg");
        assertThat(MaxAttachmentMetadataSupport.resolveAttachmentExtension("report.PD-F", "image/png", "photo"))
                .isEqualTo("pdf");
        assertThat(MaxAttachmentMetadataSupport.resolveAttachmentExtension("archive.verylongextension", "image/png", "photo"))
                .isEqualTo("png");
    }

    @Test
    void contentTypeFallbackPreservesLegacyMappings() {
        assertThat(MaxAttachmentMetadataSupport.resolveAttachmentExtension(null, "image/jpeg", null)).isEqualTo("jpg");
        assertThat(MaxAttachmentMetadataSupport.resolveAttachmentExtension(null, "IMAGE/PNG", null)).isEqualTo("png");
        assertThat(MaxAttachmentMetadataSupport.resolveAttachmentExtension(null, "audio/mpeg", null)).isEqualTo("mp3");
        assertThat(MaxAttachmentMetadataSupport.resolveAttachmentExtension(null, "application/pdf", null)).isEqualTo("pdf");
    }

    @Test
    void attachmentTypeFallbackAndBinaryDefaultArePreserved() {
        assertThat(MaxAttachmentMetadataSupport.resolveAttachmentExtension(null, null, "photo")).isEqualTo("jpg");
        assertThat(MaxAttachmentMetadataSupport.resolveAttachmentExtension(null, null, "video")).isEqualTo("mp4");
        assertThat(MaxAttachmentMetadataSupport.resolveAttachmentExtension(null, null, "audio")).isEqualTo("ogg");
        assertThat(MaxAttachmentMetadataSupport.resolveAttachmentExtension(null, null, "file")).isEqualTo("bin");
    }
}
