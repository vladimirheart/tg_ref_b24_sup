package com.example.panel.controller;

import com.example.panel.service.AvatarService;
import java.io.IOException;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AvatarController {

    private final AvatarService avatarService;

    public AvatarController(AvatarService avatarService) {
        this.avatarService = avatarService;
    }

    @GetMapping("/avatar/{userId}")
    public ResponseEntity<Resource> avatar(Authentication authentication,
                                           @PathVariable long userId,
                                           @RequestParam(name = "full", required = false) String full) throws IOException {
        return avatarService.loadAvatar(authentication, userId, isTruthy(full));
    }

    private boolean isTruthy(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.equals("1") || normalized.equals("true") || normalized.equals("yes");
    }
}
