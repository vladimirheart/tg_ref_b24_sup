package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.GeneralTicketAccessService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/general-ticket-admin/access")
@PreAuthorize("hasAuthority('PAGE_SETTINGS') and hasAuthority('ROLE_ADMIN')")
public class GeneralTicketAccessController {
    private static final Logger log = LoggerFactory.getLogger(GeneralTicketAccessController.class);
    private final GeneralTicketAccessService access;
    private final MultibusinessFeatureFlags flags;

    public GeneralTicketAccessController(GeneralTicketAccessService access, MultibusinessFeatureFlags flags) {
        this.access = access;
        this.flags = flags;
    }

    @GetMapping
    public ResponseEntity<List<GeneralTicketAccessService.AccountAccess>> accounts(Authentication actor) {
        requireEnabled();
        access.requireAuthenticatedUserId(actor);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(access.accounts(actor));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<Void> grant(@PathVariable long userId, Authentication actor) {
        requireEnabled();
        access.requireAuthenticatedUserId(actor);
        if (userId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        if (!access.grantRead(actor,userId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        log.info("GENERAL ticket READ granted: actor={}, userId={}", actor.getName(), userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> revoke(@PathVariable long userId, Authentication actor) {
        requireEnabled();
        access.requireAuthenticatedUserId(actor);
        if (userId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        if (!access.revokeRead(actor,userId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        log.info("GENERAL ticket READ revoked: actor={}, userId={}", actor.getName(), userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{userId}/operate")
    public ResponseEntity<Void> grantOperate(@PathVariable long userId, Authentication actor) {
        requireEnabled();
        access.requireAuthenticatedUserId(actor);
        if (userId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        if (!access.grantOperate(actor,userId)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Требуется действующий GENERAL READ");
        log.info("GENERAL ticket OPERATE granted: actor={}, userId={}",actor.getName(),userId);
        return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/{userId}/operate")
    public ResponseEntity<Void> revokeOperate(@PathVariable long userId, Authentication actor) {
        requireEnabled();
        access.requireAuthenticatedUserId(actor);
        if (userId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        if (!access.revokeOperate(actor,userId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        log.info("GENERAL ticket OPERATE revoked: actor={}, userId={}",actor.getName(),userId);
        return ResponseEntity.noContent().build();
    }

    private void requireEnabled() {
        if (!flags.isFoundationEnabled() || !flags.isGeneralTicketQueue()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
