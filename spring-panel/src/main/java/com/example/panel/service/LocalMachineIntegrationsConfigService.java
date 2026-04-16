package com.example.panel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class LocalMachineIntegrationsConfigService {

    private static final Logger log = LoggerFactory.getLogger(LocalMachineIntegrationsConfigService.class);
    private static final String DEFAULT_RELATIVE_PATH = "ai-context/parameters/local-machine/integrations.local.json";

    private final ObjectMapper objectMapper;
    private final Path configPath;

    public LocalMachineIntegrationsConfigService(ObjectMapper objectMapper,
                                                 @Value("${APP_LOCAL_INTEGRATIONS_CONFIG:}") String configuredPath) {
        this.objectMapper = objectMapper;
        this.configPath = resolveConfigPath(configuredPath);
    }

    public IntegrationsLocalConfig loadConfig() {
        if (!Files.isRegularFile(configPath)) {
            return IntegrationsLocalConfig.empty(configPath);
        }
        try {
            JsonNode root = objectMapper.readTree(configPath.toFile());
            return new IntegrationsLocalConfig(
                parseBitrixConfig(root.path("bitrix24")),
                parseIikoConfig(root.path("iiko")),
                configPath
            );
        } catch (IOException ex) {
            log.warn("Failed to read local integrations config {}: {}", configPath, ex.getMessage());
            return IntegrationsLocalConfig.empty(configPath);
        }
    }

    public Map<String, Object> loadStatus() {
        IntegrationsLocalConfig config = loadConfig();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("path", config.path().toString());
        status.put("exists", Files.isRegularFile(config.path()));
        status.put("bitrix_configured", StringUtils.hasText(config.bitrix24().webhookUrl()));
        status.put("iiko_discovery_configured",
            StringUtils.hasText(config.iiko().categoriesUrl()) || StringUtils.hasText(config.iiko().walletsUrl()));
        status.put("iiko_mutation_configured",
            StringUtils.hasText(config.iiko().customerLookupUrl()) && StringUtils.hasText(config.iiko().customerUpdateUrl()));
        status.put("bitrix_portal", config.bitrix24().portalUrl());
        status.put("iiko_group_name", config.iiko().groupName());
        return status;
    }

    private Bitrix24Config parseBitrixConfig(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Bitrix24Config.empty();
        }
        return new Bitrix24Config(
            text(node, "portal_url", "portalUrl"),
            text(node, "webhook_url", "webhookUrl")
        );
    }

    private IikoLocalConfig parseIikoConfig(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return IikoLocalConfig.empty();
        }
        return new IikoLocalConfig(
            text(node, "group_name", "groupName"),
            text(node, "base_url", "baseUrl"),
            text(node, "login"),
            text(node, "password"),
            text(node, "organization_id", "organizationId"),
            text(node, "token"),
            text(node, "categories_url", "categoriesUrl"),
            text(node, "wallets_url", "walletsUrl"),
            text(node, "customer_lookup_url", "customerLookupUrl"),
            text(node, "customer_update_url", "customerUpdateUrl")
        );
    }

    private String text(JsonNode node, String... fields) {
        if (node == null || fields == null) {
            return "";
        }
        for (String field : fields) {
            if (node.has(field) && !node.path(field).isNull()) {
                String value = node.path(field).asText("");
                if (StringUtils.hasText(value)) {
                    return value.trim();
                }
            }
        }
        return "";
    }

    private Path resolveConfigPath(String configuredPath) {
        if (StringUtils.hasText(configuredPath)) {
            Path path = Paths.get(configuredPath.trim());
            if (!path.isAbsolute()) {
                return Paths.get("").toAbsolutePath().normalize().resolve(path).normalize();
            }
            return path.normalize();
        }

        Path current = Paths.get("").toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve(DEFAULT_RELATIVE_PATH).normalize();
            if (Files.exists(candidate.getParent())) {
                return candidate;
            }
            current = current.getParent();
        }
        return Paths.get(DEFAULT_RELATIVE_PATH).toAbsolutePath().normalize();
    }

    public record IntegrationsLocalConfig(Bitrix24Config bitrix24,
                                          IikoLocalConfig iiko,
                                          Path path) {

        public static IntegrationsLocalConfig empty(Path path) {
            return new IntegrationsLocalConfig(Bitrix24Config.empty(), IikoLocalConfig.empty(), path);
        }
    }

    public record Bitrix24Config(String portalUrl, String webhookUrl) {

        public static Bitrix24Config empty() {
            return new Bitrix24Config("", "");
        }
    }

    public record IikoLocalConfig(String groupName,
                                  String baseUrl,
                                  String login,
                                  String password,
                                  String organizationId,
                                  String token,
                                  String categoriesUrl,
                                  String walletsUrl,
                                  String customerLookupUrl,
                                  String customerUpdateUrl) {

        public static IikoLocalConfig empty() {
            return new IikoLocalConfig("", "", "", "", "", "", "", "", "", "");
        }
    }
}
