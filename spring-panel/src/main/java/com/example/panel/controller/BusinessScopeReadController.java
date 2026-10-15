package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.BusinessAccessService;
import com.example.panel.service.BusinessScopedTicketReadService;
import com.example.panel.storage.AttachmentObjectStorageService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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
@RequestMapping("/api/business-scope")
@PreAuthorize("hasAuthority('PAGE_DIALOGS')")
public class BusinessScopeReadController {

    private final BusinessScopedTicketReadService readService;
    private final AttachmentObjectStorageService objectStorageService;
    private final MultibusinessFeatureFlags flags;

    public BusinessScopeReadController(BusinessScopedTicketReadService readService,
                                       AttachmentObjectStorageService objectStorageService,
                                       MultibusinessFeatureFlags flags) {
        this.readService = readService;
        this.objectStorageService = objectStorageService;
        this.flags = flags;
    }

    @GetMapping("/businesses")
    public List<BusinessScopedTicketReadService.BusinessOption> businesses(Authentication authentication) {
        requireFoundation();
        return readService.availableBusinesses(readService.requireAuthenticatedUserId(username(authentication)));
    }

    @GetMapping("/tickets")
    public List<BusinessScopedTicketReadService.TicketRow> tickets(
            Authentication authentication,
            @RequestParam("business_id") String businessId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        requireFoundation();
        if (limit < 1 || limit > 100 || offset < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page bounds");
        }
        Long userId = readService.requireAuthenticatedUserId(username(authentication));
        return readService.listTickets(userId, requestedContext(businessId), limit, offset);
    }

    @GetMapping("/tickets/{publicId}")
    public BusinessScopedTicketReadService.TicketRow ticket(@PathVariable String publicId,
                                                            Authentication authentication) {
        requireFoundation();
        Long userId = readService.requireAuthenticatedUserId(username(authentication));
        return readService.findByPublicId(userId, publicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/tickets/{publicId}/history")
    public BusinessScopedTicketReadService.TicketHistory history(@PathVariable String publicId,
                                                                 Authentication authentication,
                                                                 @RequestParam(required = false) String before,
                                                                 @RequestParam(defaultValue = "50") String limit) {
        requireFoundation();
        if (!limit.matches("[1-9][0-9]*") ||
                (before != null && !before.matches("[1-9][0-9]*"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid history page bounds");
        }
        int pageLimit;
        Long beforeId;
        try {
            pageLimit = Integer.parseInt(limit);
            beforeId = before == null ? null : Long.parseLong(before);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid history page bounds", ex);
        }
        if (pageLimit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid history page bounds");
        }
        Long userId = readService.requireAuthenticatedUserId(username(authentication));
        return readService.findHistoryByPublicId(userId, publicId, beforeId, pageLimit)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/tickets/{publicId}/history/{historyId}/attachment")
    public ResponseEntity<Resource> attachment(@PathVariable String publicId,
                                               @PathVariable String historyId,
                                               Authentication authentication) throws IOException {
        requireFoundation();
        if (!historyId.matches("[1-9][0-9]*")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid history ID");
        }
        long id;
        try {
            id = Long.parseLong(historyId);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid history ID", ex);
        }
        Long userId = readService.requireAuthenticatedUserId(username(authentication));
        var reference = readService.findAttachmentByPublicId(userId, publicId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        AttachmentObjectStorageService.StoredBinary binary;
        try {
            binary = objectStorageService.openDialogAttachmentByStorageKey(reference.storageKey());
        } catch (IllegalArgumentException | NoSuchKeyException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        String filename = safeDownloadName(reference.originalName());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(binary.size())
                .body(new InputStreamResource(binary.inputStream()));
    }

    private String safeDownloadName(String rawName) {
        if (rawName == null) {
            return "file";
        }
        String normalized = rawName.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        return name.isBlank() || name.length() > 255 || name.chars().anyMatch(Character::isISOControl)
                ? "file" : name;
    }

    private void requireFoundation() {
        if (!flags.isFoundationEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private String username(Authentication authentication) {
        return authentication != null ? authentication.getName() : null;
    }

    private BusinessAccessService.RequestedBusinessContext requestedContext(String businessId) {
        if ("all".equals(businessId)) {
            return BusinessAccessService.AllAccessibleBusinesses.INSTANCE;
        }
        if (businessId == null || !businessId.matches("[1-9][0-9]*")) {
            throw new AccessDeniedException("Business context is not accessible");
        }
        try {
            return new BusinessAccessService.OneBusiness(Long.parseLong(businessId));
        } catch (NumberFormatException ex) {
            throw new AccessDeniedException("Business context is not accessible", ex);
        }
    }
}
