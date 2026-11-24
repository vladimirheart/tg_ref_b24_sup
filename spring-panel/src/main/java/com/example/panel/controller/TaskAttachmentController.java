package com.example.panel.controller;

import com.example.panel.service.SharedWorkAccessService;
import com.example.panel.service.TaskAttachmentService;
import com.example.panel.service.TaskAttachmentRemovalService;
import com.example.panel.service.TaskCommentEditService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAnyAuthority('PAGE_TASKS','ROLE_ADMIN')")
public class TaskAttachmentController {
    private final SharedWorkAccessService access;
    private final TaskAttachmentService attachments;
    private final TaskAttachmentRemovalService removal;
    private final TaskCommentEditService commentEdits;
    public TaskAttachmentController(SharedWorkAccessService access, TaskAttachmentService attachments, TaskAttachmentRemovalService removal, TaskCommentEditService commentEdits) {
        this.access = access; this.attachments = attachments; this.removal = removal; this.commentEdits = commentEdits;
    }

    public record CommentEditRequest(String html, String expectedHtml) {}
    @org.springframework.web.bind.annotation.PatchMapping("/api/tasks/{taskId}/comments/{commentId}")
    public java.util.Map<String, Object> editComment(@PathVariable long taskId, @PathVariable long commentId,
            @org.springframework.web.bind.annotation.RequestBody CommentEditRequest request, Authentication authentication) {
        access.require(SharedWorkAccessService.Kind.TASK, taskId, SharedWorkAccessService.Access.WRITE, authentication);
        var comment = commentEdits.edit(taskId, commentId, authentication.getName(), request.html(), request.expectedHtml());
        return java.util.Map.of("ok", true, "item", java.util.Map.of("id", comment.getId(), "author", comment.getAuthor(),
            "html", comment.getHtml(), "created_at", comment.getCreatedAt() == null ? "" : comment.getCreatedAt().toString(), "can_edit", true));
    }

    public record RemovalRequest(String scope, Long commentId, String reference) {}
    @org.springframework.web.bind.annotation.DeleteMapping("/api/tasks/{taskId}/attachments")
    public java.util.Map<String, Object> remove(@PathVariable long taskId,
            @org.springframework.web.bind.annotation.RequestBody RemovalRequest request, Authentication authentication) {
        access.require(SharedWorkAccessService.Kind.TASK, taskId, SharedWorkAccessService.Access.WRITE, authentication);
        String html = removal.remove(taskId, request.scope(), request.commentId(), request.reference());
        return java.util.Map.of("ok", true, "html", html);
    }

    @GetMapping("/api/tasks/{taskId}/attachments/{storedName:.+}")
    public ResponseEntity<InputStreamResource> download(@PathVariable long taskId, @PathVariable String storedName,
            @RequestParam(defaultValue = "false") boolean preview,
            @org.springframework.web.bind.annotation.RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
            Authentication authentication) throws IOException {
        access.require(SharedWorkAccessService.Kind.TASK, taskId, SharedWorkAccessService.Access.READ, authentication);
        var binary = attachments.open(taskId, storedName);
        boolean image = preview && binary.contentType() != null && java.util.Set.of("image/png", "image/jpeg", "image/gif", "image/webp", "video/mp4", "video/webm", "audio/wav", "audio/mpeg", "audio/ogg").contains(binary.contentType());
        var disposition = (image ? ContentDisposition.inline() : ContentDisposition.attachment())
            .filename(storedName.substring(37), StandardCharsets.UTF_8).build();
        long start = 0, end = binary.size() - 1;
        boolean partial = image && range != null;
        if (partial) {
            try {
                var ranges = org.springframework.http.HttpRange.parseRanges(range);
                if (ranges.size() != 1 || binary.size() == 0) throw new IllegalArgumentException();
                start = ranges.get(0).getRangeStart(binary.size()); end = ranges.get(0).getRangeEnd(binary.size());
                if (start > end || start >= binary.size()) throw new IllegalArgumentException();
                binary.inputStream().skipNBytes(start);
            } catch (IllegalArgumentException invalid) {
                binary.close();
                return ResponseEntity.status(416).header(HttpHeaders.CONTENT_RANGE, "bytes */" + binary.size()).build();
            } catch (IOException failure) { binary.close(); throw failure; }
        }
        long length = end - start + 1;
        var response = ResponseEntity.status(partial ? 206 : 200).cacheControl(CacheControl.noStore());
        if (image) response.header(HttpHeaders.ACCEPT_RANGES, "bytes");
        if (partial) response.header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + binary.size());
        var stream = new java.io.FilterInputStream(binary.inputStream()) {
            long remaining = length;
            @Override public int read() throws IOException { if (remaining == 0) return -1; int value = super.read(); if (value >= 0) remaining--; return value; }
            @Override public int read(byte[] bytes, int offset, int count) throws IOException {
                if (count == 0) return 0;
                if (remaining == 0) return -1;
                int read = in.read(bytes, offset, (int)Math.min(count, remaining)); if (read > 0) remaining -= read; return read;
            }
        };
        return response
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .header("X-Content-Type-Options", "nosniff").header("Content-Security-Policy", "sandbox; default-src 'none'")
            .contentType(image ? MediaType.parseMediaType(binary.contentType()) : MediaType.APPLICATION_OCTET_STREAM)
            .contentLength(length).body(new InputStreamResource(stream));
    }
}
