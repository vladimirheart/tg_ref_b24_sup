package com.example.panel.controller;

import com.example.panel.storage.AttachmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @GetMapping("/tickets/{ticketId}/{filename:.+}")
    public ResponseEntity<?> downloadTicket(Authentication authentication,
                                            @PathVariable String ticketId,
                                            @PathVariable String filename) throws IOException {
        return attachmentService.downloadTicketAttachment(authentication, ticketId, filename);
    }

    @GetMapping("/tickets/by-path")
    public ResponseEntity<?> downloadTicketByPath(Authentication authentication,
                                                  @RequestParam("path") String path) throws IOException {
        return attachmentService.downloadTicketAttachmentByPath(authentication, path);
    }

    @GetMapping("/knowledge-base/{fileId:.+}")
    public ResponseEntity<?> downloadKnowledge(Authentication authentication, @PathVariable String fileId) throws IOException {
        return attachmentService.downloadKnowledgeBaseFile(authentication, fileId);
    }

    @GetMapping("/avatars/{avatarId:.+}")
    public ResponseEntity<?> downloadAvatar(Authentication authentication, @PathVariable String avatarId) throws IOException {
        return attachmentService.downloadAvatar(authentication, avatarId);
    }

    @PostMapping(value = "/knowledge-base", consumes = "multipart/form-data")
    public Map<String, Object> upload(Authentication authentication, @RequestParam("file") MultipartFile file) throws IOException {
        var metadata = attachmentService.storeKnowledgeBaseFile(authentication, file);
        return Map.of(
                "originalName", metadata.originalName(),
                "storedName", metadata.storedName(),
                "size", metadata.size(),
                "uploadedAt", metadata.uploadedAt().toString()
        );
    }

    @DeleteMapping("/knowledge-base/{storedName:.+}")
    public void delete(Authentication authentication, @PathVariable String storedName) throws IOException {
        attachmentService.deleteKnowledgeBaseFile(authentication, storedName);
    }

}
