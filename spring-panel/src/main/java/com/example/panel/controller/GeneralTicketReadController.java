package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.GeneralTicketAccessService;
import com.example.panel.service.GeneralTicketReadService;
import com.example.panel.storage.AttachmentObjectStorageService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

@RestController
@RequestMapping("/api/general-tickets")
@PreAuthorize("hasAuthority('PAGE_DIALOGS')")
public class GeneralTicketReadController {
    private final GeneralTicketReadService service;
    private final GeneralTicketAccessService access;
    private final MultibusinessFeatureFlags flags;
    private final AttachmentObjectStorageService storage;

    public GeneralTicketReadController(GeneralTicketReadService service, GeneralTicketAccessService access,
                                      MultibusinessFeatureFlags flags, AttachmentObjectStorageService storage) {
        this.service = service;
        this.access = access;
        this.flags = flags;
        this.storage = storage;
    }

    @GetMapping("/page")
    public ResponseEntity<GeneralTicketReadService.TicketPage> page(Authentication authentication,
            @RequestParam(defaultValue = "50") String limit, @RequestParam(defaultValue = "0") String offset,
            @RequestParam(required = false) String status, @RequestParam(name = "q", required = false) String query) {
        long userId = userId(authentication);
        try {
            return privateRead(service.page(userId, number(limit, false), number(offset, true), status, query));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ticket query", ex);
        }
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<GeneralTicketReadService.TicketRow> ticket(@PathVariable String publicId,
            Authentication authentication) {
        return privateRead(service.find(userId(authentication), publicId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    @GetMapping("/{publicId}/history")
    public ResponseEntity<GeneralTicketReadService.TicketHistory> history(@PathVariable String publicId,
            Authentication authentication, @RequestParam(defaultValue = "50") String limit,
            @RequestParam(required = false) String before) {
        long userId = userId(authentication);
        try {
            Long cursor = null;
            if (before != null) {
                if (!before.matches("[1-9][0-9]*")) throw new IllegalArgumentException();
                cursor = Long.parseLong(before);
            }
            return privateRead(service.history(userId, publicId, cursor, number(limit, false))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid history query", ex);
        }
    }

    @GetMapping("/{publicId}/answers")
    public ResponseEntity<GeneralTicketReadService.TicketAnswers> answers(@PathVariable String publicId,
            Authentication authentication) {
        long userId = userId(authentication);
        try {
            return privateRead(service.answers(userId, publicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
        } catch (GeneralTicketReadService.AnswerLimitExceededException ex) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
        }
    }

    @GetMapping("/{publicId}/history/{historyId}/attachment")
    public ResponseEntity<Resource> attachment(@PathVariable String publicId, @PathVariable String historyId,
            Authentication authentication) throws IOException {
        long userId = userId(authentication);
        long id;
        try {
            if (!historyId.matches("[1-9][0-9]*")) throw new IllegalArgumentException();
            id = Long.parseLong(historyId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        var reference = service.attachment(userId, publicId, id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        AttachmentObjectStorageService.StoredBinary binary;
        try {
            binary = storage.openDialogAttachmentByStorageKey(reference.storageKey());
        } catch (IllegalArgumentException | NoSuchKeyException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        String filename = reference.originalName() == null ? "file" : reference.originalName().replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1).trim();
        if (filename.isBlank() || filename.length() > 255 || filename.chars().anyMatch(Character::isISOControl)) {
            filename = "file";
        }
        return ResponseEntity.ok().header("Cache-Control", "no-store").header("X-Content-Type-Options", "nosniff")
            .header("Content-Disposition", ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8)
                .build().toString()).contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(binary.size())
            .body(new InputStreamResource(binary.inputStream()));
    }

    private int number(String value, boolean zeroAllowed) {
        if (!value.matches(zeroAllowed ? "0|[1-9][0-9]*" : "[1-9][0-9]*")) throw new IllegalArgumentException();
        int parsed = Integer.parseInt(value);
        if (!zeroAllowed && parsed > 100) throw new IllegalArgumentException();
        return parsed;
    }

    private long userId(Authentication authentication) {
        if (!flags.isFoundationEnabled() || !flags.isGeneralTicketQueue()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return access.requireAuthenticatedUserId(authentication != null ? authentication.getName() : null);
    }

    private <T> ResponseEntity<T> privateRead(T body) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
            .header("X-Content-Type-Options", "nosniff").body(body);
    }
}
