package com.example.panel.controller;

import com.example.panel.service.SharedWorkAccessService;
import com.example.panel.service.SharedWorkAccessService.*;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Current system administrators have full shared-work access; owners manage their own objects. */
@RestController
public class SharedWorkAccessController {
    private final SharedWorkAccessService access;
    public SharedWorkAccessController(SharedWorkAccessService access){this.access=access;}
    @GetMapping("/api/shared-work-admin/history")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<AuditEvent>> audit(@RequestParam(defaultValue="9223372036854775807") long before,Authentication actor) {
        return ResponseEntity.ok().header("Cache-Control","no-store").body(access.history(null,null,before,actor));
    }
    @GetMapping("/api/shared-work/{kind}/{id}/history")
    @PreAuthorize("hasAnyAuthority('PAGE_TASKS','ROLE_ADMIN')")
    public ResponseEntity<List<AuditEvent>> history(@PathVariable Kind kind,@PathVariable long id,
        @RequestParam(defaultValue="9223372036854775807") long before,Authentication actor) {
        return ResponseEntity.ok().header("Cache-Control","no-store").body(access.history(kind,id,before,actor));
    }
    @GetMapping("/api/shared-work/context")
    @PreAuthorize("hasAnyAuthority('PAGE_TASKS','ROLE_ADMIN')")
    public ResponseEntity<Capabilities> context(Authentication actor) {
        return ResponseEntity.ok().header("Cache-Control","no-store").body(access.capabilities(actor));
    }
    @GetMapping("/api/shared-work/{kind}/{id}/access")
    @PreAuthorize("hasAnyAuthority('PAGE_TASKS','ROLE_ADMIN')")
    public ResponseEntity<ObjectAccess> access(@PathVariable Kind kind,@PathVariable long id,Authentication actor) {
        return ResponseEntity.ok().header("Cache-Control","no-store").body(access.objectAccess(kind,id,actor));
    }
    @GetMapping("/api/shared-work/{kind}/{id}/people")
    @PreAuthorize("hasAnyAuthority('PAGE_TASKS','ROLE_ADMIN')")
    public ResponseEntity<List<Person>> people(@PathVariable Kind kind,@PathVariable long id,
                                               @RequestParam(defaultValue="") String q,Authentication actor) {
        return ResponseEntity.ok().header("Cache-Control","no-store").body(access.people(kind,id,q,actor));
    }
    @GetMapping("/api/shared-work-admin/access")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<AccountGrant>> accounts(Authentication actor){
        return ResponseEntity.ok().header("Cache-Control","no-store").body(access.accounts(actor));
    }
    @PutMapping("/api/shared-work-admin/access/{userId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> grant(@PathVariable long userId,@RequestBody Grant grant,Authentication actor){
        access.setGrant(actor,userId,grant);return ResponseEntity.noContent().build();
    }
    @GetMapping("/api/shared-work/{kind}/{id}/members")
    @PreAuthorize("hasAnyAuthority('PAGE_TASKS','ROLE_ADMIN')")
    public ResponseEntity<List<Member>> members(@PathVariable Kind kind,@PathVariable long id,Authentication actor){
        return ResponseEntity.ok().header("Cache-Control","no-store").body(access.members(kind,id,actor));
    }
    @PutMapping("/api/shared-work/{kind}/{id}/owner/{userId}")
    @PreAuthorize("hasAnyAuthority('PAGE_TASKS','ROLE_ADMIN')")
    public ResponseEntity<Void> owner(@PathVariable Kind kind,@PathVariable long id,@PathVariable long userId,Authentication actor) {
        access.transferOwner(kind,id,userId,actor);return ResponseEntity.noContent().build();
    }
    public record MemberRole(Role role) {}
    @PutMapping("/api/shared-work/{kind}/{id}/members/{userId}")
    @PreAuthorize("hasAnyAuthority('PAGE_TASKS','ROLE_ADMIN')")
    public ResponseEntity<Void> member(@PathVariable Kind kind,@PathVariable long id,@PathVariable long userId,
                                       @RequestBody MemberRole member,Authentication actor){
        access.setMember(kind,id,userId,member.role(),actor);return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/api/shared-work/{kind}/{id}/members/{userId}")
    @PreAuthorize("hasAnyAuthority('PAGE_TASKS','ROLE_ADMIN')")
    public ResponseEntity<Void> remove(@PathVariable Kind kind,@PathVariable long id,@PathVariable long userId,Authentication actor){
        access.removeMember(kind,id,userId,actor);return ResponseEntity.noContent().build();
    }
}
