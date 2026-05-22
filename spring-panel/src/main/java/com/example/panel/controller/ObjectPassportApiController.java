package com.example.panel.controller;

import com.example.panel.service.ObjectPassportService;
import com.example.panel.service.NotificationRoutingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/object_passports")
public class ObjectPassportApiController {

    private final ObjectPassportService objectPassportService;
    private final NotificationRoutingService notificationRoutingService;
    private final ObjectMapper objectMapper;

    public ObjectPassportApiController(ObjectPassportService objectPassportService,
                                       NotificationRoutingService notificationRoutingService,
                                       ObjectMapper objectMapper) {
        this.objectPassportService = objectPassportService;
        this.notificationRoutingService = notificationRoutingService;
        this.objectMapper = objectMapper;
    }

    @PostMapping({"", "/"})
    @PreAuthorize("hasAuthority('PAGE_OBJECT_PASSPORTS')")
    public Map<String, Object> createPassport(HttpServletRequest request,
                                              Authentication authentication) throws IOException {
        Map<String, Object> payload = RequestPayloadUtils.readPayload(request, objectMapper);
        Map<String, Object> response = objectPassportService.createPassport(payload);
        notifyPassportSaved(response, true, authentication);
        return response;
    }

    @PutMapping("/{passportId}")
    @PreAuthorize("hasAuthority('PAGE_OBJECT_PASSPORTS')")
    public Map<String, Object> updatePassport(@PathVariable long passportId,
                                              @RequestBody Map<String, Object> payload,
                                              Authentication authentication) {
        Map<String, Object> response = objectPassportService.updatePassport(passportId, payload);
        notifyPassportSaved(response, false, authentication);
        return response;
    }

    @GetMapping("/{passportId}")
    @PreAuthorize("hasAuthority('PAGE_OBJECT_PASSPORTS')")
    public Map<String, Object> getPassport(@PathVariable long passportId) {
        return objectPassportService.getPassport(passportId);
    }

    @GetMapping("/{passportId}/cases")
    @PreAuthorize("hasAuthority('PAGE_OBJECT_PASSPORTS')")
    public Map<String, Object> getPassportCases(@PathVariable long passportId) {
        return objectPassportService.getEmptyCasesPayload(passportId);
    }

    @GetMapping("/{passportId}/tasks")
    @PreAuthorize("hasAuthority('PAGE_OBJECT_PASSPORTS')")
    public Map<String, Object> getPassportTasks(@PathVariable long passportId) {
        return objectPassportService.getEmptyTasksPayload(passportId);
    }

    @PostMapping(value = "/{passportId}/photos", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('PAGE_OBJECT_PASSPORTS')")
    public Map<String, Object> uploadPassportPhoto(@PathVariable long passportId,
                                                   @RequestParam("file") MultipartFile file,
                                                   @RequestParam(value = "category", required = false) String category,
                                                   @RequestParam(value = "caption", required = false) String caption) throws IOException {
        return objectPassportService.uploadPhoto(passportId, file, category, caption);
    }

    @PatchMapping("/photos/{photoId}")
    @PreAuthorize("hasAuthority('PAGE_OBJECT_PASSPORTS')")
    public Map<String, Object> updatePassportPhoto(@PathVariable String photoId,
                                                   @RequestBody Map<String, Object> payload) {
        return objectPassportService.updatePhoto(photoId, payload);
    }

    @DeleteMapping("/photos/{photoId}")
    @PreAuthorize("hasAuthority('PAGE_OBJECT_PASSPORTS')")
    public Map<String, Object> deletePassportPhoto(@PathVariable String photoId) {
        return objectPassportService.deletePhoto(photoId);
    }

    @GetMapping("/photos/file/{storedName:.+}")
    @PreAuthorize("hasAuthority('PAGE_OBJECT_PASSPORTS')")
    public ResponseEntity<Resource> downloadPassportPhoto(@PathVariable String storedName) throws IOException {
        return objectPassportService.downloadPhoto(storedName);
    }

    private void notifyPassportSaved(Map<String, Object> response,
                                     boolean created,
                                     Authentication authentication) {
        if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
            return;
        }
        Long passportId = parseLong(response.get("id"));
        Map<String, Object> passport = response.get("passport") instanceof Map<?, ?> raw
                ? new LinkedHashMap<>((Map<String, Object>) raw)
                : Map.of();
        String department = stringValue(passport.get("department"));
        String city = stringValue(passport.get("city"));
        String label = department;
        if (!department.isBlank() && !city.isBlank()) {
            label = city + " / " + department;
        } else if (label.isBlank()) {
            label = passportId != null ? "паспорт #" + passportId : "паспорт объекта";
        }
        String text = created
                ? "Создан паспорт объекта: " + label
                : "Обновлён паспорт объекта: " + label;
        String url = passportId != null ? "/object-passports/" + passportId : "/object-passports";
        String actor = authentication != null ? authentication.getName() : null;
        notificationRoutingService.notify("passports", "passport_saved", java.util.Set.of(), text, url, actor);
    }

    private Long parseLong(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        try {
            String value = stringValue(raw);
            return value.isBlank() ? null : Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String stringValue(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }
}
