package com.example.panel.controller;

import com.example.panel.repository.PanelUserRepository;
import com.example.panel.service.PanelUserPhotoService;
import com.example.panel.service.SharedSystemAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Read-only work identity and contacts for current panel accounts; credentials and administrative grants are excluded. */
@RestController
public class EmployeeProfileController {
    private final PanelUserRepository users;
    private final PanelUserPhotoService photos;
    private final ObjectMapper objectMapper;

    public EmployeeProfileController(PanelUserRepository users, PanelUserPhotoService photos, ObjectMapper objectMapper) {
        this.users = users;
        this.photos = photos;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/api/panel-users/profile")
    @PreAuthorize(SharedSystemAccessService.REQUIRE)
    public ResponseEntity<Profile> profile(@RequestParam(required = false) String username,
                                           @RequestParam(required = false) String userId) {
        if ((username == null) == (userId == null)
                || (username != null && (username.isBlank() || username.length() > 200))
                || (userId != null && !userId.matches("[1-9][0-9]{0,18}"))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        Long accountId = null;
        if (userId != null) {
            try { accountId = Long.parseLong(userId); }
            catch (NumberFormatException ignored) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST); }
        }
        var person = (accountId != null ? users.findEmployeeProfile(accountId.longValue()) : users.findEmployeeProfile(username))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Сотрудник не найден"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Profile(
            person.id().toString(), person.username(), person.fullName(), person.department(),
            photos.resolveUserAvatarUrl(person.id(), person.photo(), null), person.email(), contactPhones(person.phones()),
            person.lastPortalActivityAt() == null ? null : person.lastPortalActivityAt().toInstant().toString(),
            person.blocked(), Instant.now().toString()));
    }

    private List<ContactPhone> contactPhones(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            Object parsed = objectMapper.readValue(json, Object.class);
            if (!(parsed instanceof List<?> entries)) return List.of();
            List<ContactPhone> result = new ArrayList<>();
            for (Object entry : entries) {
                if (!(entry instanceof Map<?, ?> fields)) continue;
                Object rawNumber = fields.containsKey("value") ? fields.get("value") : fields.get("number");
                String number = contactText(rawNumber, 100);
                if (number.isEmpty()) continue;
                Object rawLabel = fields.containsKey("type") ? fields.get("type") : fields.get("label");
                result.add(new ContactPhone(contactText(rawLabel, 80), number));
                if (result.size() == 20) break;
            }
            return result;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
            return List.of();
        }
    }

    private String contactText(Object value, int limit) {
        if (!(value instanceof String text)) return "";
        String clean = text.replaceAll("\\p{Cntrl}", "").trim();
        return clean.substring(0, Math.min(clean.length(), limit));
    }

    public record ContactPhone(String label, String value) {}
    public record Profile(String userId, String username, String fullName, String department, String photo,
                          String email, List<ContactPhone> phones, String lastPortalActivityAt,
                          boolean blocked, String serverTime) {}
}
