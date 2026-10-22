package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.BusinessTicketParticipantReadService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/business-scope/tickets/{publicId}/participants")
@PreAuthorize("hasAuthority('PAGE_DIALOGS')")
public class BusinessTicketParticipantReadController {
    private final BusinessTicketParticipantReadService service;
    private final MultibusinessFeatureFlags flags;
    public BusinessTicketParticipantReadController(BusinessTicketParticipantReadService service,MultibusinessFeatureFlags flags) {
        this.service=service;this.flags=flags;
    }
    @GetMapping
    public ResponseEntity<BusinessTicketParticipantReadService.Roster> roster(@PathVariable String publicId,Authentication authentication) {
        requireFoundation();
        try {
            return privateRead(service.find(publicId,identity(authentication)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND)));
        } catch (BusinessTicketParticipantReadService.RosterTooLargeException ex) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Participant roster exceeds 200 members",ex);
        } catch (BusinessTicketParticipantReadService.ReadUnavailableException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Participant data is unavailable",ex);
        }
    }
    @GetMapping("/history")
    public ResponseEntity<BusinessTicketParticipantReadService.History> history(@PathVariable String publicId,Authentication authentication,
            @RequestParam(required=false) String before,@RequestParam(defaultValue="50") String limit) {
        requireFoundation();
        Long cursor;int pageSize;
        try {
            if (!limit.matches("[1-9][0-9]*") || before!=null && !before.matches("[1-9][0-9]*")) throw new IllegalArgumentException();
            pageSize=Integer.parseInt(limit);cursor=before==null?null:Long.parseLong(before);
            if (pageSize>100) throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid participant history page",ex);
        }
        try {
            return privateRead(service.history(publicId,identity(authentication),cursor,pageSize)
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND)));
        } catch (BusinessTicketParticipantReadService.ReadUnavailableException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Participant data is unavailable",ex);
        }
    }
    private void requireFoundation() { if(!flags.isFoundationEnabled())throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
    private String identity(Authentication authentication) { return authentication==null?null:authentication.getName(); }
    private <T> ResponseEntity<T> privateRead(T body) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store").header("X-Content-Type-Options","nosniff").body(body);
    }
}
