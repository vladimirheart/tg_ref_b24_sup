package com.example.panel.controller;

import com.example.panel.service.AdminStorageInventoryService;
import com.example.panel.service.PermissionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/storage-inventory")
public class AdminStorageInventoryApiController {

    private final AdminStorageInventoryService storageInventoryService;
    private final PermissionService permissionService;

    public AdminStorageInventoryApiController(AdminStorageInventoryService storageInventoryService,
                                              PermissionService permissionService) {
        this.storageInventoryService = storageInventoryService;
        this.permissionService = permissionService;
    }

    @PostMapping(value = "/run", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public ResponseEntity<Map<String, Object>> runInventory(Authentication authentication,
                                                            @RequestBody(required = false) Map<String, Object> payload) {
        if (!permissionService.isSuperUser(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "error", "Storage inventory доступен только admin/portal admin."
            ));
        }

        Integer top = readInteger(payload != null ? payload.get("top") : null);
        try {
            AdminStorageInventoryService.StorageInventoryRunResult result = storageInventoryService.runInventory(top);
            return ResponseEntity.ok(result.toResponsePayload());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", false);
            response.put("error", ex.getMessage() != null ? ex.getMessage() : "Не удалось запустить storage inventory.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    private Integer readInteger(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
