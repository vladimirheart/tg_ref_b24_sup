package com.example.panel.controller;

import com.example.panel.service.SharedWorkAccessService;
import com.example.panel.service.TaskAttachmentService;
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
    public TaskAttachmentController(SharedWorkAccessService access, TaskAttachmentService attachments) {
        this.access = access; this.attachments = attachments;
    }

    @GetMapping("/api/tasks/{taskId}/attachments/{storedName:.+}")
    public ResponseEntity<InputStreamResource> download(@PathVariable long taskId, @PathVariable String storedName,
            @RequestParam(defaultValue = "false") boolean preview, Authentication authentication) throws IOException {
        access.require(SharedWorkAccessService.Kind.TASK, taskId, SharedWorkAccessService.Access.READ, authentication);
        var binary = attachments.open(taskId, storedName);
        boolean image = preview && binary.contentType() != null && java.util.Set.of("image/png", "image/jpeg", "image/gif", "image/webp").contains(binary.contentType());
        var disposition = (image ? ContentDisposition.inline() : ContentDisposition.attachment())
            .filename(storedName.substring(37), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .header("X-Content-Type-Options", "nosniff").header("Content-Security-Policy", "sandbox; default-src 'none'")
            .contentType(image ? MediaType.parseMediaType(binary.contentType()) : MediaType.APPLICATION_OCTET_STREAM)
            .contentLength(binary.size()).body(new InputStreamResource(binary.inputStream()));
    }
}
