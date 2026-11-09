package com.example.panel.controller;

import com.example.panel.service.SharedWorkAccessService;
import com.example.panel.service.SharedWorkAccessService.*;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Administration grants no entity READ. Membership management requires the entity's current owner. */
@RestController
public class SharedWorkAccessController {
    private final SharedWorkAccessService access;
    public SharedWorkAccessController(SharedWorkAccessService access){this.access=access;}
    @GetMapping("/api/shared-work-admin/access")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') and hasAuthority('PAGE_SETTINGS')")
    public ResponseEntity<List<AccountGrant>> accounts(Authentication actor){
        return ResponseEntity.ok().header("Cache-Control","no-store").body(access.accounts(actor));
    }
    @PutMapping("/api/shared-work-admin/access/{userId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') and hasAuthority('PAGE_SETTINGS')")
    public ResponseEntity<Void> grant(@PathVariable long userId,@RequestBody Grant grant,Authentication actor){
        access.setGrant(actor,userId,grant);return ResponseEntity.noContent().build();
    }
    @GetMapping("/api/shared-work/{kind}/{id}/members")
    @PreAuthorize("hasAuthority('PAGE_TASKS')")
    public ResponseEntity<List<Member>> members(@PathVariable Kind kind,@PathVariable long id,Authentication actor){
        return ResponseEntity.ok().header("Cache-Control","no-store").body(access.members(kind,id,actor));
    }
    public record MemberRole(Role role) {}
    @PutMapping("/api/shared-work/{kind}/{id}/members/{userId}")
    @PreAuthorize("hasAuthority('PAGE_TASKS')")
    public ResponseEntity<Void> member(@PathVariable Kind kind,@PathVariable long id,@PathVariable long userId,
                                       @RequestBody MemberRole member,Authentication actor){
        access.setMember(kind,id,userId,member.role(),actor);return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/api/shared-work/{kind}/{id}/members/{userId}")
    @PreAuthorize("hasAuthority('PAGE_TASKS')")
    public ResponseEntity<Void> remove(@PathVariable Kind kind,@PathVariable long id,@PathVariable long userId,Authentication actor){
        access.removeMember(kind,id,userId,actor);return ResponseEntity.noContent().build();
    }
}
