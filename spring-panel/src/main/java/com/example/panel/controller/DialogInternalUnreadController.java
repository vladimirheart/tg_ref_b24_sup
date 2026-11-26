package com.example.panel.controller;

import com.example.panel.service.DialogOperatorCollaborationService;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@PreAuthorize("hasAuthority('PAGE_DIALOGS')")
public class DialogInternalUnreadController {
    private final DialogOperatorCollaborationService service;
    public DialogInternalUnreadController(DialogOperatorCollaborationService service){this.service=service;}
    @PostMapping("/api/operator-dialog-unread")
    public ResponseEntity<?> unread(@RequestBody Request request,Authentication auth) {
        if(request==null||request.ticketPublicIds()==null||request.ticketPublicIds().size()>100
            ||request.ticketPublicIds().stream().anyMatch(id->id==null||!id.matches("[a-f0-9]{32}")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
            Map.of("items",service.unread(request.ticketPublicIds(),auth)));
    }
    public record Request(List<String> ticketPublicIds){}
}
