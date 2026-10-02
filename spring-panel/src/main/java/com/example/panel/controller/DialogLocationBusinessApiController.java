package com.example.panel.controller;

import com.example.panel.service.DialogAuthorizationService;
import com.example.panel.service.IikoDepartmentLocationCatalogService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dialogs")
@PreAuthorize("hasAuthority('PAGE_DIALOGS')")
public class DialogLocationBusinessApiController {

    private final JdbcTemplate jdbcTemplate;
    private final DialogAuthorizationService dialogAuthorizationService;
    private final IikoDepartmentLocationCatalogService locationCatalogService;

    public DialogLocationBusinessApiController(JdbcTemplate jdbcTemplate,
                                               DialogAuthorizationService dialogAuthorizationService,
                                               IikoDepartmentLocationCatalogService locationCatalogService) {
        this.jdbcTemplate = jdbcTemplate;
        this.dialogAuthorizationService = dialogAuthorizationService;
        this.locationCatalogService = locationCatalogService;
    }

    @GetMapping("/location-business/catalog")
    public Map<String, Object> catalog() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", flattenLocationCatalog());
        return response;
    }

    @PatchMapping("/{ticketId}/location-business")
    public ResponseEntity<Map<String, Object>> update(@PathVariable String ticketId,
                                                       @RequestBody(required = false) DialogLocationBusinessRequest request,
                                                       Authentication authentication) {
        ResponseEntity<Map<String, Object>> denied = dialogAuthorizationService.requirePermission(
                authentication,
                "can_assign",
                "location_business",
                ticketId
        );
        if (denied != null) {
            return denied;
        }
        if (request == null) {
            return ResponseEntity.badRequest().body(error("Параметры бизнеса и локации не переданы"));
        }

        String business = clean(request.business());
        String locationType = clean(request.locationType());
        String city = clean(request.city());
        String locationName = clean(request.locationName());
        String hierarchyError = validateHierarchy(business, locationType, city, locationName);
        if (hierarchyError != null) {
            return ResponseEntity.badRequest().body(error(hierarchyError));
        }

        Integer ticketCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tickets WHERE ticket_id = ?",
                Integer.class,
                ticketId
        );
        if (ticketCount == null || ticketCount < 1) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("Диалог не найден"));
        }

        String operator = authentication != null ? clean(authentication.getName()) : null;
        int updated = jdbcTemplate.update(
                """
                UPDATE messages
                   SET business = ?,
                       location_type = ?,
                       city = ?,
                       location_name = ?,
                       updated_at = CURRENT_TIMESTAMP,
                       updated_by = ?
                 WHERE ticket_id = ?
                """,
                business,
                locationType,
                city,
                locationName,
                operator,
                ticketId
        );
        if (updated < 1) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(error("Для диалога отсутствует карточка сообщения с реквизитами локации"));
        }

        dialogAuthorizationService.logDialogAction(
                operator,
                ticketId,
                "location_business",
                "success",
                "dialog_metadata_updated"
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("updated", updated);
        response.put("business", business);
        response.put("locationType", locationType);
        response.put("city", city);
        response.put("locationName", locationName);
        return ResponseEntity.ok(response);
    }

    private List<LocationOption> flattenLocationCatalog() {
        Map<String, Object> payload = locationCatalogService.buildEffectiveLocationsPayload(locationCatalogService.loadCatalog());
        Object rawTree = payload.get("tree");
        if (!(rawTree instanceof Map<?, ?> businesses)) {
            return List.of();
        }
        List<LocationOption> result = new ArrayList<>();
        for (Map.Entry<?, ?> businessEntry : businesses.entrySet()) {
            String business = clean(businessEntry.getKey());
            if (business == null || !(businessEntry.getValue() instanceof Map<?, ?> types)) {
                continue;
            }
            for (Map.Entry<?, ?> typeEntry : types.entrySet()) {
                String locationType = clean(typeEntry.getKey());
                if (locationType == null || !(typeEntry.getValue() instanceof Map<?, ?> cities)) {
                    continue;
                }
                for (Map.Entry<?, ?> cityEntry : cities.entrySet()) {
                    String city = clean(cityEntry.getKey());
                    if (city == null) {
                        continue;
                    }
                    appendLocations(result, business, locationType, city, cityEntry.getValue());
                }
            }
        }
        return result;
    }

    private void appendLocations(List<LocationOption> result,
                                 String business,
                                 String locationType,
                                 String city,
                                 Object rawLocations) {
        if (rawLocations instanceof Collection<?> locations) {
            for (Object rawLocation : locations) {
                String locationName = clean(rawLocation);
                if (locationName != null) {
                    result.add(new LocationOption(business, locationType, city, locationName));
                }
            }
            return;
        }
        if (rawLocations instanceof Map<?, ?> locations) {
            for (Object rawLocation : locations.keySet()) {
                String locationName = clean(rawLocation);
                if (locationName != null) {
                    result.add(new LocationOption(business, locationType, city, locationName));
                }
            }
        }
    }

    private String validateHierarchy(String business,
                                     String locationType,
                                     String city,
                                     String locationName) {
        if (business == null && (locationType != null || city != null || locationName != null)) {
            return "Сначала выберите бизнес";
        }
        if (locationType == null && (city != null || locationName != null)) {
            return "Сначала выберите тип локации";
        }
        if (city == null && locationName != null) {
            return "Сначала выберите город";
        }
        return null;
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", false);
        payload.put("error", message);
        return payload;
    }

    private String clean(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() || "—".equals(text) ? null : text;
    }

    public record DialogLocationBusinessRequest(String business,
                                                String locationType,
                                                String city,
                                                String locationName) {
    }

    public record LocationOption(String business,
                                 String locationType,
                                 String city,
                                 String locationName) {
    }
}
