package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.BusinessAccessService;
import com.example.panel.service.BusinessScopedTicketReadService;
import com.example.panel.storage.AttachmentObjectStorageService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
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
    public ResponseEntity<List<BusinessScopedTicketReadService.BusinessOption>> businesses(Authentication authentication) {
        requireFoundation();
        return privateRead(readService.availableBusinesses(
                readService.requireAuthenticatedUserId(authentication)));
    }

    @GetMapping("/tickets/summary")
    public ResponseEntity<BusinessScopedTicketReadService.TicketSummary> ticketSummary(
            Authentication authentication, @RequestParam("business_id") String businessId) {
        requireFoundation();
        Long userId = readService.requireAuthenticatedUserId(authentication);
        return privateRead(readService.summarizeTickets(userId, requestedContext(businessId)));
    }

    @GetMapping("/tickets/categories")
    public ResponseEntity<List<BusinessScopedTicketReadService.CategoryCount>> categorySummary(
            Authentication authentication, @RequestParam("business_id") String businessId) {
        requireFoundation();
        Long userId = readService.requireAuthenticatedUserId(authentication);
        try {
            return privateRead(readService.summarizeCategories(userId, requestedContext(businessId)));
        } catch (BusinessScopedTicketReadService.CategorySummaryTooLargeException ex) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, ex.getMessage(), ex);
        }
    }

    @GetMapping("/tickets")
    public ResponseEntity<List<BusinessScopedTicketReadService.TicketRow>> tickets(
            Authentication authentication,
            @RequestParam("business_id") String businessId,
            @RequestParam(defaultValue = "50") String limit,
            @RequestParam(defaultValue = "0") String offset,
            @RequestParam(required = false) String status,
            @RequestParam(name = "ticket", required = false) String ticketId,
            @RequestParam(name = "q", required = false) String queryText,
            @RequestParam(required = false) String category) {
        requireFoundation();
        TicketQuery query = ticketQuery(limit, offset, status, ticketId, queryText);
        Long userId = readService.requireAuthenticatedUserId(authentication);
        try {
            BusinessScopedTicketReadService.validateCategory(category);
            return privateRead(readService.listTickets(userId, requestedContext(businessId),
                    query.limit(), query.offset(), query.status(), query.ticketId(), query.queryText(), category));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping("/tickets/page")
    public ResponseEntity<BusinessScopedTicketReadService.TicketPage> ticketPage(
            Authentication authentication,
            @RequestParam("business_id") String businessId,
            @RequestParam(defaultValue = "50") String limit,
            @RequestParam(defaultValue = "0") String offset,
            @RequestParam(required = false) String status,
            @RequestParam(name = "ticket", required = false) String ticketId,
            @RequestParam(name = "q", required = false) String queryText,
            @RequestParam(required = false) String category) {
        requireFoundation();
        TicketQuery query = ticketQuery(limit, offset, status, ticketId, queryText);
        Long userId = readService.requireAuthenticatedUserId(authentication);
        try {
            BusinessScopedTicketReadService.validateCategory(category);
            return privateRead(readService.pageTickets(userId, requestedContext(businessId),
                    query.limit(), query.offset(), query.status(), query.ticketId(), query.queryText(), category));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping(value = "/tickets/export", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<BusinessScopedTicketReadService.TicketRow>> exportTickets(
            Authentication authentication,
            @RequestParam("business_id") String businessId,
            @RequestParam(required = false) String status,
            @RequestParam(name = "ticket", required = false) String ticketId,
            @RequestParam(name = "q", required = false) String queryText,
            @RequestParam(required = false) String category) {
        requireFoundation();
        validateTicketFilters(status, ticketId, queryText);
        Long userId = readService.requireAuthenticatedUserId(authentication);
        try {
            BusinessScopedTicketReadService.validateCategory(category);
            List<BusinessScopedTicketReadService.TicketRow> rows = readService.exportTickets(
                    userId, requestedContext(businessId), status, ticketId, queryText, category);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename("business-tickets.json").build().toString())
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header("X-Content-Type-Options", "nosniff")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(rows);
        } catch (BusinessScopedTicketReadService.ExportTooLargeException ex) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    private TicketQuery ticketQuery(String limit, String offset, String status, String ticketId, String queryText) {
        if (!limit.matches("[1-9][0-9]*") || !offset.matches("0|[1-9][0-9]*")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page bounds");
        }
        int pageLimit;
        int pageOffset;
        try {
            pageLimit = Integer.parseInt(limit);
            pageOffset = Integer.parseInt(offset);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page bounds", ex);
        }
        if (pageLimit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page bounds");
        }
        validateTicketFilters(status, ticketId, queryText);
        return new TicketQuery(pageLimit, pageOffset, status, ticketId, queryText);
    }

    private void validateTicketFilters(String status, String ticketId, String queryText) {
        if ((status != null && !Set.of("pending", "open", "resolved", "closed").contains(status))
                || (ticketId != null && (ticketId.isBlank() || ticketId.length() > 255
                || ticketId.chars().anyMatch(Character::isISOControl)))
                || (queryText != null && (queryText.isBlank() || queryText.length() > 100
                || queryText.chars().anyMatch(Character::isISOControl)))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ticket filter");
        }
    }

    private record TicketQuery(int limit, int offset, String status, String ticketId, String queryText) {
    }

    @GetMapping("/tickets/{publicId}")
    public ResponseEntity<BusinessScopedTicketReadService.TicketRow> ticket(
            @PathVariable String publicId, Authentication authentication) {
        requireFoundation();
        readService.requireAuthenticatedUserId(authentication);
        return privateRead(readService.findByPublicId(authentication, publicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    @GetMapping("/tickets/{publicId}/history")
    public ResponseEntity<BusinessScopedTicketReadService.TicketHistory> history(
            @PathVariable String publicId, Authentication authentication,
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
        readService.requireAuthenticatedUserId(authentication);
        return privateRead(readService.findHistoryByPublicId(authentication, publicId, beforeId, pageLimit)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    @GetMapping("/tickets/{publicId}/answers")
    public ResponseEntity<BusinessScopedTicketReadService.TicketAnswers> answers(
            @PathVariable String publicId, Authentication authentication) {
        requireFoundation();
        readService.requireAuthenticatedUserId(authentication);
        try {
            var snapshot = readService.findAnswersByPublicId(authentication, publicId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(snapshot);
        } catch (BusinessScopedTicketReadService.AnswerLimitExceededException ex) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, ex.getMessage(), ex);
        }
    }

    @GetMapping("/tickets/{publicId}/workspace")
    public ResponseEntity<BusinessScopedTicketReadService.TicketWorkspace> workspace(
            @PathVariable String publicId, Authentication authentication) {
        requireFoundation();
        readService.requireAuthenticatedUserId(authentication);
        try {
            var snapshot = readService.findWorkspaceByPublicId(authentication, publicId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            return privateRead(snapshot);
        } catch (BusinessScopedTicketReadService.WorkspaceLimitExceededException ex) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, ex.getMessage(), ex);
        }
    }

    @GetMapping("/tickets/{publicId}/spans")
    public ResponseEntity<BusinessScopedTicketReadService.TicketSpans> spans(
            @PathVariable String publicId, Authentication authentication) {
        requireFoundation();
        readService.requireAuthenticatedUserId(authentication);
        try {
            var snapshot = readService.findSpansByPublicId(authentication, publicId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            return privateRead(snapshot);
        } catch (BusinessScopedTicketReadService.SpanLimitExceededException ex) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, ex.getMessage(), ex);
        }
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
        readService.requireAuthenticatedUserId(authentication);
        var reference = readService.findAttachmentByPublicId(authentication, publicId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        AttachmentObjectStorageService.StoredBinary binary;
        try {
            binary = objectStorageService.openDialogAttachmentByStorageKey(reference.storageKey());
        } catch (IllegalArgumentException | NoSuchKeyException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        String filename = safeDownloadName(reference.originalName());
        if(reference.sha256()!=null) {
            byte[] bytes;
            try(binary) {bytes=com.example.panel.storage.QuestionnaireMediaStorageProof.read(binary,reference.sha256(),reference.size());}
            catch(IOException ex) {throw new ResponseStatusException(HttpStatus.NOT_FOUND);}
            if(readService.findAttachmentByPublicId(authentication,publicId,id).filter(reference::equals).isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(filename,StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL,"no-store").header("X-Content-Type-Options","nosniff")
                .contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(bytes.length).body(new org.springframework.core.io.ByteArrayResource(bytes));
        }
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

    private <T> ResponseEntity<T> privateRead(T body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(body);
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
