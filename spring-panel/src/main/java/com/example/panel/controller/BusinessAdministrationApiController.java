package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.BusinessAdministrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/business-admin")
@PreAuthorize("hasAuthority('PAGE_SETTINGS') and hasAuthority('ROLE_ADMIN')")
public class BusinessAdministrationApiController {

    private static final Logger log = LoggerFactory.getLogger(BusinessAdministrationApiController.class);
    private final BusinessAdministrationService service;
    private final MultibusinessFeatureFlags flags;

    public BusinessAdministrationApiController(BusinessAdministrationService service,
                                               MultibusinessFeatureFlags flags) {
        this.service = service;
        this.flags = flags;
    }

    @GetMapping("/overview")
    public BusinessAdministrationService.Overview overview() {
        requireFoundation();
        return service.overview();
    }

    @GetMapping("/channels/{channelId}/binding-preview")
    public BusinessAdministrationService.ChannelBindingPreview previewChannelBinding(@PathVariable long channelId) {
        requireFoundation();
        try {
            return service.previewChannelBinding(channelId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Channel not found"));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PostMapping("/businesses")
    public ResponseEntity<BusinessAdministrationService.Business> createBusiness(
            @RequestBody(required = false) CreateBusinessRequest request, Authentication authentication) {
        requireFoundation();
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Business details are required");
        }
        try {
            BusinessAdministrationService.Business created = service.createBusiness(
                    request.code(), request.displayName());
            log.info("Business created: actor={}, businessId={}", actor(authentication), created.id());
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Business code already exists", ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PutMapping("/businesses/{businessId}")
    public BusinessAdministrationService.Business renameBusiness(
            @PathVariable long businessId, @RequestBody(required = false) RenameBusinessRequest request,
            Authentication authentication) {
        requireFoundation();
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Business name is required");
        }
        try {
            BusinessAdministrationService.Business updated = service.renameBusiness(
                    businessId, request.displayName())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business not found"));
            log.info("Business renamed: actor={}, businessId={}", actor(authentication), businessId);
            return updated;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PutMapping("/businesses/{businessId}/status")
    public ResponseEntity<Void> setBusinessStatus(
            @PathVariable long businessId, @RequestBody(required = false) BusinessStatusRequest request,
            Authentication authentication) {
        requireFoundation();
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Business status is required");
        }
        try {
            if (!service.setBusinessStatus(businessId, request.status())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Business not found");
            }
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
        log.info("Business status updated: actor={}, businessId={}, status={}",
                actor(authentication), businessId, request.status());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/businesses/{businessId}/memberships/{userId}")
    public ResponseEntity<Void> grantMembership(@PathVariable long businessId, @PathVariable long userId,
                                                @RequestBody(required = false) RoleRequest request,
                                                Authentication authentication) {
        requireFoundation();
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Business role is required");
        }
        try {
            if (!service.grantMembership(businessId, userId, request.roleCode())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Business or user not found");
            }
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
        log.info("Business membership saved: actor={}, businessId={}, userId={}",
                actor(authentication), businessId, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/businesses/{businessId}/memberships/{userId}")
    public ResponseEntity<Void> revokeMembership(@PathVariable long businessId, @PathVariable long userId,
                                                 Authentication authentication) {
        requireFoundation();
        try {
            service.revokeMembership(businessId, userId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
        log.info("Business membership revoked: actor={}, businessId={}, userId={}",
                actor(authentication), businessId, userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/all-grants/{userId}")
    public ResponseEntity<Void> grantAll(@PathVariable long userId,
                                         @RequestBody(required = false) RoleRequest request,
                                         Authentication authentication) {
        requireFoundation();
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Business role is required");
        }
        try {
            if (!service.grantAllBusinesses(userId, request.roleCode())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
            }
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
        log.info("All-business grant saved: actor={}, userId={}", actor(authentication), userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/all-grants/{userId}")
    public ResponseEntity<Void> revokeAll(@PathVariable long userId, Authentication authentication) {
        requireFoundation();
        try {
            service.revokeAllBusinesses(userId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
        log.info("All-business grant revoked: actor={}, userId={}", actor(authentication), userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/businesses/{businessId}/locations")
    public ResponseEntity<BusinessAdministrationService.Location> createLocation(
            @PathVariable long businessId, @RequestBody(required = false) CreateLocationRequest request,
            Authentication authentication) {
        requireFoundation();
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Location details are required");
        }
        try {
            BusinessAdministrationService.Location created = service.createLocation(
                    businessId, request.locationName(), request.locationType(), request.city())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business not found"));
            log.info("Business location created: actor={}, businessId={}, locationId={}",
                    actor(authentication), businessId, created.id());
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Location already exists", ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PutMapping("/businesses/{businessId}/locations/{locationId}/status")
    public ResponseEntity<Void> setLocationStatus(
            @PathVariable long businessId, @PathVariable long locationId,
            @RequestBody(required = false) LocationStatusRequest request,
            Authentication authentication) {
        requireFoundation();
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Location status is required");
        }
        try {
            if (!service.setLocationStatus(businessId, locationId, request.status())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Location not found");
            }
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
        log.info("Business location status updated: actor={}, businessId={}, locationId={}, status={}",
                actor(authentication), businessId, locationId, request.status());
        return ResponseEntity.noContent().build();
    }

    private void requireFoundation() {
        if (!flags.isFoundationEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private String actor(Authentication authentication) {
        return authentication == null ? "unknown" : authentication.getName();
    }

    public record CreateBusinessRequest(String code, String displayName) {
    }

    public record RenameBusinessRequest(String displayName) {
    }

    public record BusinessStatusRequest(String status) {
    }

    public record RoleRequest(String roleCode) {
    }

    public record CreateLocationRequest(String locationName, String locationType, String city) {
    }

    public record LocationStatusRequest(String status) {
    }
}
