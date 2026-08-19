package com.example.panel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Makes ordinary location-catalog reads deterministic and non-blocking.
 *
 * <p>The settings page and its lazy page-data endpoints are read paths. They
 * must not perform synchronous iikoServer HTTP requests after obtaining JPA/
 * JDBC state, because a slow or unavailable iiko endpoint can keep web
 * requests alive long enough to exhaust the shared Hikari pool.</p>
 *
 * <p>Explicit/manual/scheduled location synchronization still calls
 * {@link #loadCatalog(boolean)} with {@code forceRefresh=true}; that method is
 * inherited unchanged and therefore continues to perform the live iikoServer
 * refresh. Successful synchronization persists the effective catalog into the
 * shared locations config, which is what normal reads consume here.</p>
 */
@Service
@Primary
public class NonBlockingIikoDepartmentLocationCatalogService
        extends IikoDepartmentLocationCatalogService {

    public NonBlockingIikoDepartmentLocationCatalogService(
            LocationsIikoServerSourceSettingsService locationsIikoServerSourceSettingsService,
            SharedConfigService sharedConfigService,
            ObjectMapper objectMapper) {
        super(locationsIikoServerSourceSettingsService, sharedConfigService, objectMapper);
    }

    @Override
    public LocationCatalogSnapshot loadCatalog() {
        Map<String, Object> payload = buildEffectiveLocationsPayload(null);
        return new LocationCatalogSnapshot(
                toStringObjectMap(payload.get("tree")),
                toStringObjectMap(payload.get("statuses")),
                "shared_config",
                true,
                List.of()
        );
    }

    private Map<String, Object> toStringObjectMap(Object rawValue) {
        if (!(rawValue instanceof Map<?, ?> map)) {
            return Map.of();
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> {
            if (key != null) {
                result.put(String.valueOf(key), value);
            }
        });
        return result;
    }
}
