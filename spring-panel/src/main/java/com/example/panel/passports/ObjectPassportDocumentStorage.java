package com.example.panel.passports;

import com.example.panel.storage.AttachmentObjectStorageService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ObjectPassportDocumentStorage {
    public static final int MAX_BYTES = 50 * 1024 * 1024;
    private static final int MAX_PREVIEW = 100_000;
    private static final Logger log = LoggerFactory.getLogger(ObjectPassportDocumentStorage.class);
    private final AttachmentObjectStorageService objects;

    public ObjectPassportDocumentStorage(AttachmentObjectStorageService objects) { this.objects = objects; }

    public PreparedFile prepare(MultipartFile file) throws IOException {
        if (file == null) return null;
        if (file.isEmpty()) throw bad("Файл пуст");
        if (file.getSize() > MAX_BYTES) throw tooLarge();
        byte[] bytes;
        try (var input = file.getInputStream()) { bytes = input.readNBytes(MAX_BYTES + 1); }
        if (bytes.length > MAX_BYTES) throw tooLarge();
        if (bytes.length == 0) throw bad("Файл пуст");
        String name = String.valueOf(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename())
                .replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isBlank()) name = "file";
        if (name.length() > 240) name = name.substring(name.length() - 240);
        String lower = name.toLowerCase(Locale.ROOT);
        String type = "application/octet-stream", kind = "none", text = "";
        if (starts(bytes, 0x89, 0x50, 0x4e, 0x47, 13, 10, 26, 10)) type = "image/png";
        else if (starts(bytes, 0xff, 0xd8, 0xff)) type = "image/jpeg";
        else if (startsAscii(bytes, "GIF87a") || startsAscii(bytes, "GIF89a")) type = "image/gif";
        else if (startsAscii(bytes, "BM")) type = "image/bmp";
        else if (bytes.length >= 12 && startsAscii(bytes, "RIFF")
                && new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) type = "image/webp";
        else if (startsAscii(bytes, "%PDF-")) { type = "application/pdf"; kind = "pdf"; }
        if (type.startsWith("image/")) kind = "image";
        if (kind.equals("none") && lower.endsWith(".docx")) text = docxPreview(bytes);
        else if (kind.equals("none") && lower.matches(".*\\.(txt|md|csv|log|json)$")) text = textPreview(bytes);
        if (!text.isEmpty()) kind = "text";
        return new PreparedFile(name, type, kind, text, bytes);
    }

    public FileInfo store(long passportId, UUID documentId, PreparedFile prepared) throws IOException {
        String key = passportId + "/" + documentId + "/" + UUID.randomUUID();
        try (var stored = objects.storePassportDocument(key, prepared.mediaType(), new ByteArrayInputStream(prepared.bytes()))) {
            return new FileInfo(key, prepared.name(), prepared.mediaType(), stored.size(), prepared.previewKind(), prepared.previewText());
        }
    }

    public void discard(String key) {
        try { objects.deletePassportDocument(key); }
        catch (Exception ex) { log.warn("Could not remove an uncommitted passport document binary {}", key, ex); }
    }

    public ResponseEntity<Resource> download(FileInfo file, boolean preview) throws IOException {
        boolean inline = preview && (file.previewKind().equals("image") || file.previewKind().equals("pdf"));
        if (preview && !inline) throw bad("Этот формат доступен для скачивания");
        var binary = objects.openPassportDocument(file.key());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.builder(inline ? "inline" : "attachment")
                        .filename(file.name(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Frame-Options", inline ? "SAMEORIGIN" : "DENY")
                .header("Content-Security-Policy", inline ? "frame-ancestors 'self'" : "default-src 'none'")
                .contentType(MediaType.parseMediaType(inline ? file.mediaType() : "application/octet-stream"))
                .contentLength(binary.size()).body(new InputStreamResource(binary.inputStream()));
    }

    private String textPreview(byte[] bytes) {
        if (bytes.length > 2 * 1024 * 1024) return "";
        try {
            String value = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            if (value.indexOf('\0') >= 0) return "";
            return value.length() <= MAX_PREVIEW ? value : value.substring(0, MAX_PREVIEW) + "\n…";
        } catch (Exception ex) { return ""; }
    }

    private String docxPreview(byte[] bytes) {
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            int remaining = 2 * 1024 * 1024;
            for (int entries = 0; entries < 200; entries++) {
                var entry = zip.getNextEntry();
                if (entry == null) break;
                byte[] xml = zip.readNBytes(remaining + 1);
                if (xml.length > remaining) return "";
                remaining -= xml.length;
                if (!"word/document.xml".equals(entry.getName())) continue;
                var factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
                var parser = factory.newDocumentBuilder();
                parser.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                    @Override public void fatalError(org.xml.sax.SAXParseException ex) throws org.xml.sax.SAXException { throw ex; }
                });
                var document = parser.parse(new ByteArrayInputStream(xml));
                var paragraphs = document.getElementsByTagNameNS("*", "p");
                var text = new StringBuilder();
                for (int i = 0; i < paragraphs.getLength() && text.length() < MAX_PREVIEW; i++)
                    text.append(paragraphs.item(i).getTextContent()).append('\n');
                return text.substring(0, Math.min(text.length(), MAX_PREVIEW));
            }
        } catch (Exception ex) { /* A file without a safe text preview remains downloadable. */ }
        return "";
    }

    private boolean startsAscii(byte[] bytes, String prefix) { return starts(bytes, prefix.chars().toArray()); }
    private boolean starts(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if ((bytes[i] & 255) != prefix[i]) return false;
        return true;
    }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException tooLarge() { return new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Размер файла — не более 50 МБ"); }
    public record PreparedFile(String name, String mediaType, String previewKind, String previewText, byte[] bytes) {}
    public record FileInfo(String key, String name, String mediaType, long size, String previewKind, String previewText) {}
}
