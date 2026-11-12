package com.example.panel.passports;

import com.example.panel.storage.AttachmentObjectStorageService;
import com.example.panel.storage.ObjectStorageProperties;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ObjectPassportDocumentStorageTest {
    @TempDir Path temporary;

    ObjectPassportDocumentStorage storage() throws Exception {
        var objects = new AttachmentObjectStorageService(new ObjectStorageProperties(), temporary.resolve("files").toString(),
                temporary.resolve("knowledge").toString(), temporary.resolve("photos").toString(), temporary.resolve("avatars").toString());
        return new ObjectPassportDocumentStorage(objects);
    }

    static MockMultipartFile file(String name, byte[] bytes) { return new MockMultipartFile("file", name, "text/html", bytes); }

    @Test void acceptsOptionalFilesButRejectsEmptyAndOversizedUploadsBeforeReading() throws Exception {
        var storage = storage();
        assertThat(storage.prepare(null)).isNull();
        assertThatThrownBy(() -> storage.prepare(file("empty.pdf", new byte[0]))).isInstanceOf(ResponseStatusException.class);
        MultipartFile large = mock(MultipartFile.class);
        when(large.getSize()).thenReturn((long) ObjectPassportDocumentStorage.MAX_BYTES + 1);
        when(large.isEmpty()).thenReturn(false);
        assertThatThrownBy(() -> storage.prepare(large)).isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(413);
        verify(large, never()).getInputStream();
    }

    @Test void inlineTypeComesFromBytesAndUntrustedHtmlOrSvgIsOnlyDownloaded() throws Exception {
        var storage = storage();
        assertThat(storage.prepare(file("fake.jpg", "<svg onload='alert(1)'>".getBytes())).previewKind()).isEqualTo("none");
        assertThat(storage.prepare(file("file.bin", "%PDF-1.4\ncontent".getBytes())).previewKind()).isEqualTo("pdf");
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 13, 10, 26, 10};
        assertThat(storage.prepare(file("fake.html", png)).mediaType()).isEqualTo("image/png");
    }

    @Test void storesUniqueScopedBinariesAndDownloadsExactBytesWithSafeNames() throws Exception {
        var storage = storage();
        byte[] bytes = "данные акта".getBytes(StandardCharsets.UTF_8);
        var prepared = storage.prepare(file("..\\..\\акт\r\n.txt", bytes));
        assertThat(prepared.name()).isEqualTo("акт.txt");
        var first = storage.store(9L, UUID.randomUUID(), prepared);
        var second = storage.store(10L, UUID.randomUUID(), prepared);
        assertThat(first.key()).startsWith("9/").isNotEqualTo(second.key());
        var response = storage.download(first, false);
        assertThat(response.getHeaders().getFirst("Content-Disposition")).startsWith("attachment;").contains("UTF-8");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        try (var input = response.getBody().getInputStream()) { assertThat(input.readAllBytes()).isEqualTo(bytes); }
        storage.discard(first.key());
        var remaining = storage.download(second, false);
        try (var input = remaining.getBody().getInputStream()) { assertThat(input.readAllBytes()).isEqualTo(bytes); }
    }

    @Test void pdfPreviewUsesInlineSameOriginAndDownloadsStayAttachments() throws Exception {
        var storage = storage();
        var info = storage.store(9L, UUID.randomUUID(), storage.prepare(file("акт.pdf", "%PDF-1.4\ncontent".getBytes())));
        var response = storage.download(info, true);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/pdf");
        assertThat(response.getHeaders().getFirst("X-Frame-Options")).isEqualTo("SAMEORIGIN");
        assertThat(response.getHeaders().getFirst("Content-Disposition")).startsWith("inline;");
        response.getBody().getInputStream().close();
    }

    @Test void docxHasBoundedTextPreviewWithoutMarkupOrExternalEntities() throws Exception {
        var storage = storage();
        var valid = storage.prepare(file("акт.docx", docx("""
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>
            <w:p><w:r><w:t>Касса настроена</w:t></w:r></w:p><w:p><w:r><w:t>Принял инженер</w:t></w:r></w:p>
            </w:body></w:document>
            """)));
        assertThat(valid.previewKind()).isEqualTo("text");
        assertThat(valid.previewText()).isEqualTo("Касса настроена\nПринял инженер\n");
        var unsafe = storage.prepare(file("акт.docx", docx("""
            <!DOCTYPE document [<!ENTITY xxe SYSTEM "file:///never-read-passport-document-secret">]>
            <document><p>&xxe;</p></document>
            """)));
        assertThat(unsafe.previewKind()).isEqualTo("none");
        assertThat(unsafe.previewText()).isEmpty();
    }

    @Test void compressedHugeXmlAndBinaryDisguisedAsTextHaveNoPreview() throws Exception {
        var storage = storage();
        var bomb = storage.prepare(file("large.docx", docx("<document><p>" + "x".repeat(2 * 1024 * 1024) + "</p></document>")));
        assertThat(bomb.previewKind()).isEqualTo("none");
        assertThat(storage.prepare(file("binary.txt", new byte[]{0, 1, (byte) 0xff})).previewKind()).isEqualTo("none");
    }

    @Test void plainTextIsBoundedAndArbitraryFormatsCannotUseInlineEndpoint() throws Exception {
        var storage = storage();
        var text = storage.prepare(file("log.txt", "x".repeat(150_000).getBytes()));
        assertThat(text.previewText()).hasSize(100_002);
        var info = storage.store(9L, UUID.randomUUID(), storage.prepare(file("other.doc", new byte[]{1, 2, 3})));
        assertThatThrownBy(() -> storage.download(info, true)).isInstanceOf(ResponseStatusException.class);
    }

    static byte[] docx(String xml) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("word/document.xml")); zip.write(xml.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
