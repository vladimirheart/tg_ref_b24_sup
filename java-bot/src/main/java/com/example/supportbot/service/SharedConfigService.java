package com.example.supportbot.service;

import com.example.supportbot.settings.BotSettingsDefaults;
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
                               @Value("${shared-config.dir:config/shared}") String sharedDir) {
        this.objectMapper = objectMapper;
        this.sharedConfigDir = Paths.get(sharedDir).toAbsolutePath().normalize();
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

    public Map<String, Object> presetDefinitions() {
        Map<String, Object> presets = loadSettings();
        if (!presets.isEmpty()) {
            Object dialogConfig = presets.get("dialog_config");
            if (dialogConfig instanceof Map<?, ?> dialogMap && dialogMap.get("preset_definitions") instanceof Map<?, ?> definitions) {
                Map<String, Object> copy = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : definitions.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() instanceof Map<?, ?> value) {
                        copy.put(entry.getKey().toString(), new LinkedHashMap<>((Map<?, ?>) value));
                    }
                }
                if (!copy.isEmpty()) {
                    return copy;
                }
            }
        }
        return BotSettingsDefaults.defaultPresetDefinitions();
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
