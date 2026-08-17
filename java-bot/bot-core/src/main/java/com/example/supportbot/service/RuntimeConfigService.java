package com.example.supportbot.service;

import com.example.supportbot.config.BotIntegrationTransportMode;
import com.example.supportbot.entity.Channel;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class RuntimeConfigService {

    private final SharedConfigService sharedConfigService;
    private final BotIntegrationTransportMode integrationTransportMode;
    private final PanelRuntimeConfigClient panelRuntimeConfigClient;
    private final ObjectMapper objectMapper;

    public RuntimeConfigService(SharedConfigService sharedConfigService,
                                BotIntegrationTransportMode integrationTransportMode,
                                PanelRuntimeConfigClient panelRuntimeConfigClient,
                                ObjectMapper objectMapper) {
        this.sharedConfigService = sharedConfigService;
        this.integrationTransportMode = integrationTransportMode;
        this.panelRuntimeConfigClient = panelRuntimeConfigClient;
        this.objectMapper = objectMapper;
    }

    public Optional<PanelRuntimeConfigClient.RuntimeConfigSnapshot> findChannelConfig(Channel channel) {
        if (!integrationTransportMode.isRabbitMqMode() || !panelRuntimeConfigClient.isEnabled() || channel == null) {
            return Optional.empty();
        }
        return panelRuntimeConfigClient.findByChannelId(channel.getId());
    }

    public Map<String, Object> locationTree(Channel channel) {
        Optional<PanelRuntimeConfigClient.RuntimeConfigSnapshot> remoteConfig = findChannelConfig(channel);
        if (remoteConfig.isPresent()) {
            return new LinkedHashMap<>(remoteConfig.get().locationTree());
        }
        return loadLocalLocationTree();
    }

    public Map<String, Object> basePresetDefinitions(Channel channel) {
        Optional<PanelRuntimeConfigClient.RuntimeConfigSnapshot> remoteConfig = findChannelConfig(channel);
        if (remoteConfig.isPresent()) {
            return new LinkedHashMap<>(remoteConfig.get().presetDefinitions());
        }
        return new LinkedHashMap<>(sharedConfigService.presetDefinitions());
    }

    private Map<String, Object> loadLocalLocationTree() {
        JsonNode locations = sharedConfigService.loadLocations();
        if (locations == null || locations.isNull()) {
            return new LinkedHashMap<>();
        }
        JsonNode treeNode = locations.get("tree");
        Map<String, Object> resolved = objectMapper.convertValue(
            treeNode != null && !treeNode.isNull() ? treeNode : locations,
            new TypeReference<>() {}
        );
        return resolved != null ? resolved : new LinkedHashMap<>();
    }
}
