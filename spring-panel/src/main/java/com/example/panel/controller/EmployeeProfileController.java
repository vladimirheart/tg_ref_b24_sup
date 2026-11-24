package com.example.panel.controller;

import com.example.panel.repository.PanelUserRepository;
import com.example.panel.service.PanelUserPhotoService;
import com.example.panel.service.SharedSystemAccessService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Read-only work identity for current panel accounts; administrative and private fields are excluded. */
@RestController
public class EmployeeProfileController {
    private final PanelUserRepository users;
    private final PanelUserPhotoService photos;

    public EmployeeProfileController(PanelUserRepository users, PanelUserPhotoService photos) {
        this.users = users;
        this.photos = photos;
    }

    @GetMapping("/api/panel-users/profile")
    @PreAuthorize(SharedSystemAccessService.REQUIRE)
    public ResponseEntity<Profile> profile(@RequestParam String username) {
        if (username.isBlank() || username.length() > 200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        var person = users.findEmployeeProfile(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Сотрудник не найден"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Profile(
            person.username(), person.fullName(), person.department(),
            photos.resolveUserAvatarUrl(person.id(), person.photo(), null)));
    }

    public record Profile(String username, String fullName, String department, String photo) {}
}
