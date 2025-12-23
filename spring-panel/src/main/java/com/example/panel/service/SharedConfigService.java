package com.example.panel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SharedConfigService {

    private static final Logger log = LoggerFactory.getLogger(SharedConfigService.class);

    private final ObjectMapper objectMapper;
    private final Path sharedConfigDir;

    public SharedConfigService(ObjectMapper objectMapper,
                               @Value("${shared-config.dir:../config/shared}") String sharedDir) {
        this.objectMapper = objectMapper;
        this.sharedConfigDir = resolveSharedDir(sharedDir);
    }

    public Map<String, Object> loadSettings() {
        return readAsMap("settings.json");
    }

    public JsonNode loadOrgStructure() {
        return readAsTree("org_structure.json");
    }

    public JsonNode loadLocations() {
        return readAsTree("locations.json");
    }

    private Path resolveSharedDir(String configuredPath) {
        Path configured = Paths.get(configuredPath);
        Path absolute = configured.toAbsolutePath().normalize();

        if (Files.isDirectory(absolute)) {
            log.info("Using shared config directory at {}", absolute);
            return absolute;
        }

        if (!configured.isAbsolute()) {
            Path current = Paths.get("").toAbsolutePath().normalize();
            for (int depth = 0; depth < 4 && current != null; depth++) {
                Path candidate = current.resolve(configured).normalize();
                if (Files.isDirectory(candidate)) {
                    log.info("Using shared config directory at {}", candidate);
                    return candidate;
                }
                current = current.getParent();
            }
        }

        throw new IllegalStateException(
            "Shared config directory not found at " + absolute
                + ". Set SHARED_CONFIG_DIR or shared-config.dir to point to the Python panel's config/shared." 
        );
    }

    private Map<String, Object> readAsMap(String fileName) {
        Path file = sharedConfigDir.resolve(fileName);
        if (!Files.isRegularFile(file)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(file.toFile(), LinkedHashMap.class);
        } catch (IOException ex) {
            log.warn("Failed to read shared config {}: {}", file, ex.getMessage());
            return Collections.emptyMap();
        }
    }

    private JsonNode readAsTree(String fileName) {
        Path file = sharedConfigDir.resolve(fileName);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return objectMapper.readTree(file.toFile());
        } catch (IOException ex) {
            log.warn("Failed to read shared config {}: {}", file, ex.getMessage());
            return null;
        }
    }
}
