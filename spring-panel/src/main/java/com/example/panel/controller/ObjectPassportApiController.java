package com.example.panel.controller;

import com.example.panel.service.ObjectPassportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/object_passports")
public class ObjectPassportApiController {

    private final ObjectPassportService objectPassportService;
    private final ObjectMapper objectMapper;

    public ObjectPassportApiController(ObjectPassportService objectPassportService,
                                       ObjectMapper objectMapper) {
        this.objectPassportService = objectPassportService;
        this.objectMapper = objectMapper;
    }

    @PostMapping({"", "/"})
    @PreAuthorize("hasAuthority('PAGE_OBJECT_PASSPORTS')")
    public Map<String, Object> createPassport(HttpServletRequest request) throws IOException {
        Map<String, Object> payload = RequestPayloadUtils.readPayload(request, objectMapper);
        return objectPassportService.createPassport(payload);
    }

    @PutMapping("/{passportId}")
    @PreAuthorize("hasAuthority('PAGE_OBJECT_PASSPORTS')")
    public Map<String, Object> updatePassport(@PathVariable long passportId,
                                              @RequestBody Map<String, Object> payload) {
        return objectPassportService.updatePassport(passportId, payload);
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
}
