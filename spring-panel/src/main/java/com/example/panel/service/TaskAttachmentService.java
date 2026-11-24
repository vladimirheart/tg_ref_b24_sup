package com.example.panel.service;

import com.example.panel.storage.AttachmentObjectStorageService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriUtils;

/** Binary payloads use S3; their durable references live in the task/comment HTML in PostgreSQL. */
@Service
public class TaskAttachmentService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TaskAttachmentService.class);
    public static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private final AttachmentObjectStorageService storage;

    public TaskAttachmentService(AttachmentObjectStorageService storage) { this.storage = storage; }

    public String appendFiles(long taskId, String html, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return html;
        if (files.size() > 10 || files.stream().mapToLong(MultipartFile::getSize).sum() > 40L * 1024 * 1024)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "До 10 файлов, суммарно до 40 МБ за одно сохранение");
        for (var file : files) {
            if (file.isEmpty() || file.getSize() > MAX_FILE_SIZE || !safeName(file.getOriginalFilename()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Выберите непустой файл до 20 МБ с корректным именем");
        }
        var storedKeys = new ArrayList<String>();
        var result = new StringBuilder(html == null ? "" : html);
        try {
            for (var file : files) {
                String name = file.getOriginalFilename();
                String storedName = UUID.randomUUID() + "_" + storageName(name);
                String key = taskId + "/" + storedName;
                storedKeys.add(key);
                String mime;
                try (var input = file.getInputStream()) {
                    byte[] prefix = input.readNBytes(16);
                    mime = imageType(prefix);
                    try (var binary = storage.storeTaskAttachment(key, mime,
                            new SequenceInputStream(new ByteArrayInputStream(prefix), input))) { /* Storage owns the payload. */ }
                }
                String url = "/api/tasks/" + taskId + "/attachments/" + UriUtils.encodePathSegment(storedName, StandardCharsets.UTF_8);
                String label = HtmlUtils.htmlEscape(name);
                String attributes = " data-task-attachment=\"true\" data-attachment-name=\"" + label
                    + "\" data-attachment-size=\"" + file.getSize() + "\" data-attachment-type=\"" + mime + "\"";
                if (mime.startsWith("image/")) {
                    result.append("<figure").append(attributes).append("><a href=\"").append(url)
                        .append("\" download=\"").append(label).append("\"><img src=\"").append(url)
                        .append("?preview=true\" alt=\"").append(label).append("\"></a><figcaption>")
                        .append(label).append("</figcaption></figure>");
                } else if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    String tag = mime.startsWith("video/") ? "video" : "audio";
                    result.append("<figure").append(attributes).append("><").append(tag).append(" src=\"").append(url)
                        .append("?preview=true\" controls preload=\"metadata\"></").append(tag).append("><figcaption><a href=\"")
                        .append(url).append("\" download=\"").append(label).append("\">").append(label).append("</a></figcaption></figure>");
                } else {
                    result.append("<p><a").append(attributes).append(" href=\"").append(url).append("\" download=\"")
                        .append(label).append("\">").append(label).append("</a></p>");
                }
            }
        } catch (IOException | RuntimeException failure) {
            storedKeys.forEach(this::cleanup);
            if (failure instanceof ResponseStatusException status) throw status;
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Не удалось сохранить вложения. Повторите отправку", failure);
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) storedKeys.forEach(TaskAttachmentService.this::cleanup);
                }
            });
        }
        return result.toString();
    }

    public AttachmentObjectStorageService.StoredBinary open(long taskId, String storedName) throws IOException {
        if (storedName == null || storedName.length() < 38 || storedName.charAt(36) != '_' || !safeName(storedName.substring(37)))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        try { if (!UUID.fromString(storedName.substring(0, 36)).toString().equals(storedName.substring(0, 36))) throw new IllegalArgumentException(); }
        catch (IllegalArgumentException invalid) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
        AttachmentObjectStorageService.StoredBinary binary;
        try { binary = storage.openTaskAttachment(taskId + "/" + storedName); }
        catch (IllegalArgumentException | java.nio.file.NoSuchFileException missing) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
        catch (software.amazon.awssdk.services.s3.model.S3Exception failure) {
            if (failure.statusCode() == 404) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            throw failure;
        }
        try {
            byte[] prefix = binary.inputStream().readNBytes(16);
            return new AttachmentObjectStorageService.StoredBinary(binary.logicalKey(), imageType(prefix), binary.size(),
                new SequenceInputStream(new ByteArrayInputStream(prefix), binary.inputStream()));
        } catch (IOException | RuntimeException failure) {
            binary.close(); throw failure;
        }
    }

    private void cleanup(String key) {
        try { storage.deleteTaskAttachment(key); }
        catch (IOException | RuntimeException ignored) { /* Keep the original failure; storage availability is monitored separately. */ }
    }

    public void deleteAfterCommit(long taskId, String reference) {
        String prefix = "/api/tasks/" + taskId + "/attachments/";
        String url = TaskAttachmentRemovalService.canonical(reference);
        // Legacy external/data images belong to their original storage owner.
        if (!url.startsWith(prefix)) return;
        String name = UriUtils.decode(url.substring(prefix.length()), StandardCharsets.UTF_8);
        if (name.length() < 38 || name.charAt(36) != '_' || !safeName(name.substring(37))) return;
        try { UUID.fromString(name.substring(0, 36)); } catch (IllegalArgumentException invalid) { return; }
        Runnable deletion = () -> {
            try { storage.deleteTaskAttachment(taskId + "/" + name); }
            catch (IOException | RuntimeException failure) { log.warn("Task attachment storage cleanup failed for task {}", taskId, failure); }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { deletion.run(); }
            });
        } else deletion.run();
    }

    private static boolean safeName(String name) {
        return name != null && !name.isBlank() && name.length() <= 180 && !name.contains("..")
            && name.chars().noneMatch(c -> c < 32 || c == 127 || c == '/' || c == '\\');
    }

    private static String storageName(String original) {
        String safe = original.replaceAll("[<>:\"|?*]", "_");
        int dot = safe.lastIndexOf('.');
        String extension = dot > 0 && safe.substring(dot + 1).matches("[a-zA-Z0-9]{1,12}") ? safe.substring(dot) : "";
        String stem = extension.isEmpty() ? safe : safe.substring(0, dot);
        var result = new StringBuilder();
        int length = extension.getBytes(StandardCharsets.UTF_8).length;
        for (int point : stem.codePoints().toArray()) {
            String character = new String(Character.toChars(point));
            int bytes = character.getBytes(StandardCharsets.UTF_8).length;
            if (length + bytes > 170) break;
            result.append(character); length += bytes;
        }
        return result + extension;
    }

    static String imageType(byte[] bytes) {
        if (bytes.length >= 8 && bytes[0] == (byte)137 && bytes[1] == 80 && bytes[2] == 78 && bytes[3] == 71
                && bytes[4] == 13 && bytes[5] == 10 && bytes[6] == 26 && bytes[7] == 10) return "image/png";
        if (bytes.length >= 3 && bytes[0] == (byte)255 && bytes[1] == (byte)216 && bytes[2] == (byte)255) return "image/jpeg";
        if (bytes.length >= 6 && (new String(bytes, 0, 6, StandardCharsets.US_ASCII).equals("GIF87a")
                || new String(bytes, 0, 6, StandardCharsets.US_ASCII).equals("GIF89a"))) return "image/gif";
        if (bytes.length >= 12 && new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) return "image/webp";
        if (bytes.length >= 12 && new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WAVE")) return "audio/wav";
        if (bytes.length >= 8 && new String(bytes, 4, 4, StandardCharsets.US_ASCII).equals("ftyp")) return "video/mp4";
        if (bytes.length >= 4 && new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("OggS")) return "audio/ogg";
        if (bytes.length >= 3 && new String(bytes, 0, 3, StandardCharsets.US_ASCII).equals("ID3")) return "audio/mpeg";
        if (bytes.length >= 2 && bytes[0] == (byte)255 && (bytes[1] & 0xE0) == 0xE0) return "audio/mpeg";
        if (bytes.length >= 4 && bytes[0] == 0x1A && bytes[1] == 0x45 && bytes[2] == (byte)0xDF && bytes[3] == (byte)0xA3) return "video/webm";
        return "application/octet-stream";
    }
}
