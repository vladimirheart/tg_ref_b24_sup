package com.example.panel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CredentialRotationExternalMetadataImportService {

    private static final Logger log = LoggerFactory.getLogger(CredentialRotationExternalMetadataImportService.class);
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(5);

    private final ObjectMapper objectMapper;
    private final MetadataEndpointClient endpointClient;

    @Autowired
    public CredentialRotationExternalMetadataImportService(ObjectMapper objectMapper) {
        this(objectMapper, new JdkMetadataEndpointClient());
    }

    CredentialRotationExternalMetadataImportService(ObjectMapper objectMapper,
                                                    MetadataEndpointClient endpointClient) {
        this.objectMapper = objectMapper;
        this.endpointClient = endpointClient;
    }

    public Map<String, ImportedMetadata> loadImportedMetadata(Map<String, Object> settings) {
        List<BackendDefinition> backends = parseBackends(settings);
        List<LinkDefinition> links = parseLinks(settings);
        if (backends.isEmpty() || links.isEmpty()) {
            return Map.of();
        }

        Map<String, BackendDefinition> backendsById = new LinkedHashMap<>();
        for (BackendDefinition backend : backends) {
            if (backend != null && backend.enabled() && StringUtils.hasText(backend.id())) {
                backendsById.putIfAbsent(backend.id(), backend);
            }
        }
        if (backendsById.isEmpty()) {
            return Map.of();
        }

        Map<String, Map<String, MetadataRecord>> recordsByBackendId = new LinkedHashMap<>();
        Map<String, ImportedMetadata> importedByEntryKey = new LinkedHashMap<>();
        for (LinkDefinition link : links) {
            if (link == null || !StringUtils.hasText(link.entryKey()) || !StringUtils.hasText(link.backendId())
                || !StringUtils.hasText(link.secretRef())) {
                continue;
            }
            BackendDefinition backend = backendsById.get(link.backendId());
            if (backend == null) {
                continue;
            }
            Map<String, MetadataRecord> records = recordsByBackendId.computeIfAbsent(
                backend.id(),
                ignored -> loadBackendRecords(backend)
            );
            MetadataRecord record = records.get(normalizeRef(link.secretRef()));
            if (record == null) {
                continue;
            }
            importedByEntryKey.put(link.entryKey(), new ImportedMetadata(
                link.entryKey(),
                backend.id(),
                record.secretRef(),
                record.expiresAt(),
                record.rotatedAt(),
                record.rotationIntervalDays(),
                record.ownerName(),
                record.note(),
                link.overrideManualMetadata()
            ));
        }
        return importedByEntryKey;
    }

    private Map<String, MetadataRecord> loadBackendRecords(BackendDefinition backend) {
        if (backend == null || !"http_json".equals(backend.type()) || !StringUtils.hasText(backend.metadataUrl())) {
            return Map.of();
        }
        try {
            String body = endpointClient.fetch(backend);
            if (!StringUtils.hasText(body)) {
                return Map.of();
            }
            JsonNode root = objectMapper.readTree(body);
            return parseBackendResponse(root);
        } catch (Exception ex) {
            log.warn("Credential rotation metadata import failed for backend {}: {}", backend.id(), ex.getMessage());
            return Map.of();
        }
    }

    private Map<String, MetadataRecord> parseBackendResponse(JsonNode root) {
        Map<String, MetadataRecord> records = new LinkedHashMap<>();
        if (root == null || root.isNull() || root.isMissingNode()) {
            return records;
        }
        if (root.isArray()) {
            for (JsonNode item : root) {
                appendRecord(records, null, item);
            }
            return records;
        }

        if (root.isObject()) {
            JsonNode items = firstChild(root, "items", "entries", "data", "records");
            if (items != null && items.isArray()) {
                for (JsonNode item : items) {
                    appendRecord(records, null, item);
                }
                return records;
            }
            root.fields().forEachRemaining(entry -> appendRecord(records, entry.getKey(), entry.getValue()));
        }
        return records;
    }

    private void appendRecord(Map<String, MetadataRecord> records, String defaultRef, JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode() || !node.isObject()) {
            return;
        }
        String secretRef = firstText(node, "secret_ref", "secretRef", "ref", "path", "key", "name");
        if (!StringUtils.hasText(secretRef)) {
            secretRef = normalizeRef(defaultRef);
        } else {
            secretRef = normalizeRef(secretRef);
        }
        if (!StringUtils.hasText(secretRef)) {
            return;
        }
        MetadataRecord record = new MetadataRecord(
            secretRef,
            parseTimestamp(firstText(node, "expires_at", "expiresAt")),
            parseTimestamp(firstText(node, "rotated_at", "rotatedAt")),
            parseInteger(firstText(node, "rotation_interval_days", "rotationIntervalDays")),
            normalizeNullable(firstText(node, "owner_name", "ownerName", "owner")),
            normalizeNullable(firstText(node, "note", "description"))
        );
        records.put(secretRef, record);
    }

    private List<BackendDefinition> parseBackends(Map<String, Object> settings) {
        List<BackendDefinition> backends = new ArrayList<>();
        for (Object item : asList(firstValue(settings, "credential_rotation_external_backends", "credentialRotationExternalBackends"))) {
            Map<String, Object> raw = asMap(item);
            String id = readString(raw, "id");
            String type = normalizeType(readString(raw, "type"));
            String metadataUrl = readString(raw, "metadata_url", "metadataUrl", "url");
            String authType = normalizeAuthType(readString(raw, "auth_type", "authType"));
            String authToken = readString(raw, "auth_token", "authToken", "token");
            String headerName = readString(raw, "header_name", "headerName");
            boolean enabled = !raw.containsKey("enabled") || parseBoolean(raw.get("enabled"));
            Duration connectTimeout = durationMillis(raw.get("connect_timeout_ms"), DEFAULT_CONNECT_TIMEOUT);
            Duration readTimeout = durationMillis(raw.get("read_timeout_ms"), DEFAULT_READ_TIMEOUT);
            if (!StringUtils.hasText(id) || !StringUtils.hasText(type) || !StringUtils.hasText(metadataUrl)) {
                continue;
            }
            backends.add(new BackendDefinition(
                id,
                type,
                enabled,
                metadataUrl,
                authType,
                authToken,
                headerName,
                connectTimeout,
                readTimeout
            ));
        }
        return backends;
    }

    private List<LinkDefinition> parseLinks(Map<String, Object> settings) {
        List<LinkDefinition> links = new ArrayList<>();
        for (Object item : asList(firstValue(settings, "credential_rotation_external_links", "credentialRotationExternalLinks"))) {
            Map<String, Object> raw = asMap(item);
            String entryKey = readString(raw, "entry_key", "entryKey");
            String backendId = readString(raw, "backend_id", "backendId");
            String secretRef = readString(raw, "secret_ref", "secretRef", "ref", "path");
            boolean overrideManualMetadata = parseBoolean(firstValue(raw, "override_manual_metadata", "overrideManualMetadata"));
            if (!StringUtils.hasText(entryKey) || !StringUtils.hasText(backendId) || !StringUtils.hasText(secretRef)) {
                continue;
            }
            links.add(new LinkDefinition(entryKey, backendId, normalizeRef(secretRef), overrideManualMetadata));
        }
        return links;
    }

    private Object firstValue(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            if (source.containsKey(key)) {
                return source.get(key);
            }
        }
        return null;
    }

    private JsonNode firstChild(JsonNode source, String... keys) {
        for (String key : keys) {
            JsonNode child = source.path(key);
            if (!child.isMissingNode() && !child.isNull()) {
                return child;
            }
        }
        return null;
    }

    private String firstText(JsonNode source, String... keys) {
        for (String key : keys) {
            JsonNode child = source.path(key);
            if (!child.isMissingNode() && !child.isNull()) {
                String value = child.asText("").trim();
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
        }
        return "";
    }

    private Duration durationMillis(Object raw, Duration fallback) {
        Integer millis = parseInteger(raw == null ? "" : String.valueOf(raw));
        if (millis == null || millis <= 0) {
            return fallback;
        }
        return Duration.ofMillis(millis.longValue());
    }

    private Integer parseInteger(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private OffsetDateTime parseTimestamp(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception ignored) {
            try {
                return LocalDate.parse(value).atStartOfDay().atOffset(ZoneOffset.UTC);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private Map<String, Object> asMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> values = new LinkedHashMap<>();
            map.forEach((key, value) -> values.put(String.valueOf(key), value));
            return values;
        }
        return Map.of();
    }

    private List<?> asList(Object raw) {
        return raw instanceof List<?> list ? list : List.of();
    }

    private boolean parseBoolean(Object raw) {
        if (raw instanceof Boolean value) {
            return value;
        }
        if (raw instanceof Number number) {
            return number.intValue() != 0;
        }
        String value = normalizeNullable(raw);
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "on".equalsIgnoreCase(value);
    }

    private String readString(Map<String, Object> raw, String... keys) {
        for (String key : keys) {
            if (raw.containsKey(key)) {
                String value = normalizeNullable(raw.get(key));
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
        }
        return "";
    }

    private String normalizeRef(String value) {
        return normalizeNullable(value);
    }

    private String normalizeType(String value) {
        String normalized = normalizeNullable(value).toLowerCase(Locale.ROOT);
        return "http_json".equals(normalized) ? normalized : "";
    }

    private String normalizeAuthType(String value) {
        String normalized = normalizeNullable(value).toLowerCase(Locale.ROOT);
        if ("bearer".equals(normalized) || "header".equals(normalized)) {
            return normalized;
        }
        return "none";
    }

    private String normalizeNullable(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    interface MetadataEndpointClient {
        String fetch(BackendDefinition backend) throws IOException, InterruptedException;
    }

    static final class JdkMetadataEndpointClient implements MetadataEndpointClient {

        @Override
        public String fetch(BackendDefinition backend) throws IOException, InterruptedException {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(backend.connectTimeout())
                .build();
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(backend.metadataUrl()))
                .timeout(backend.readTimeout())
                .header("Accept", "application/json")
                .GET();
            if ("bearer".equals(backend.authType()) && StringUtils.hasText(backend.authToken())) {
                requestBuilder.header("Authorization", "Bearer " + backend.authToken());
            } else if ("header".equals(backend.authType())
                && StringUtils.hasText(backend.headerName())
                && StringUtils.hasText(backend.authToken())) {
                requestBuilder.header(backend.headerName(), backend.authToken());
            }
            HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("HTTP " + response.statusCode());
            }
            return Objects.toString(response.body(), "");
        }
    }

    record BackendDefinition(String id,
                             String type,
                             boolean enabled,
                             String metadataUrl,
                             String authType,
                             String authToken,
                             String headerName,
                             Duration connectTimeout,
                             Duration readTimeout) {
    }

    private record LinkDefinition(String entryKey,
                                  String backendId,
                                  String secretRef,
                                  boolean overrideManualMetadata) {
    }

    private record MetadataRecord(String secretRef,
                                  OffsetDateTime expiresAt,
                                  OffsetDateTime rotatedAt,
                                  Integer rotationIntervalDays,
                                  String ownerName,
                                  String note) {
    }

    public record ImportedMetadata(String entryKey,
                                   String backendId,
                                   String secretRef,
                                   OffsetDateTime expiresAt,
                                   OffsetDateTime rotatedAt,
                                   Integer rotationIntervalDays,
                                   String ownerName,
                                   String note,
                                   boolean overrideManualMetadata) {
    }
}
