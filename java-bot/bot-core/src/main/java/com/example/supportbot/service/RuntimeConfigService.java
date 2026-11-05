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

    public PanelRuntimeConfigClient.ChoiceCatalog questionnaireChoices(Channel channel) {
        requireRemoteQuestionnaire(channel);
        return "vk".equals(channel.getPlatform())?panelRuntimeConfigClient.choicesV4ForStart(channel.getId(),true):panelRuntimeConfigClient.choicesV4ForStart(channel.getId());
    }

    public PanelRuntimeConfigClient.SelectedQuestionnaire selectQuestionnaire(Channel channel,
            PanelRuntimeConfigClient.ChoiceCatalog catalog, long businessId) {
        requireRemoteQuestionnaire(channel);
        if (catalog == null || catalog.channelId() != channel.getId()) {
            throw new PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException("Questionnaire channel mismatch");
        }
        return "vk".equals(channel.getPlatform()) && catalog.version()==4?panelRuntimeConfigClient.selectForStart(catalog,businessId,true):panelRuntimeConfigClient.selectForStart(catalog, businessId);
    }

    public PanelRuntimeConfigClient.SelectedQuestionnaire selectGeneralQuestionnaire(Channel channel,
            PanelRuntimeConfigClient.ChoiceCatalog catalog) {
        requireRemoteQuestionnaire(channel);
        if(catalog==null || catalog.channelId()!=channel.getId())
            throw new PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException("Questionnaire channel mismatch");
        return "vk".equals(channel.getPlatform()) && catalog.version()==4?panelRuntimeConfigClient.selectGeneralForStart(catalog,true):panelRuntimeConfigClient.selectGeneralForStart(catalog);
    }

    public PanelRuntimeConfigClient.SelectedMediaAdmission selectedMediaAdmission(Channel channel,long userId,String ticketId,
            PanelRuntimeConfigClient.SelectedQuestionnaire selected) {
        requireRemoteQuestionnaire(channel);
        if(!"telegram".equals(channel.getPlatform())) throw new PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException("Unsupported selected media platform");
        return panelRuntimeConfigClient.selectedMediaAdmission(channel.getId(),userId,ticketId,selected);
    }
    public boolean confirmMaxQuestionnaireMedia(Channel channel,PanelRuntimeConfigClient.ChoiceCatalog catalog,
            PanelRuntimeConfigClient.SelectedQuestionnaire frozen) {
        requireRemoteQuestionnaire(channel);
        if(!"max".equals(channel.getPlatform()) || catalog==null || frozen==null || catalog.version()!=4 || frozen.version()!=4
                || catalog.channelId()!=channel.getId() || frozen.channelId()!=channel.getId())
            throw new PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException("Invalid MAX media selection");
        var current=panelRuntimeConfigClient.selectMaxMediaForStart(catalog,frozen.businessId());
        if(!QuestionnaireScope.fromSelection(current).equals(QuestionnaireScope.fromSelection(frozen)))
            throw new PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException("MAX media selection changed");
        return true;
    }

    public PanelRuntimeConfigClient.SelectedMediaAdmission selectedVkMediaAdmission(Channel channel,long userId,String ticketId,
            PanelRuntimeConfigClient.SelectedQuestionnaire selected) {
        requireRemoteQuestionnaire(channel);
        if(!"vk".equals(channel.getPlatform())) throw new PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException("Unsupported selected media platform");
        return panelRuntimeConfigClient.selectedVkMediaAdmission(channel.getId(),userId,ticketId,selected);
    }

    public PanelRuntimeConfigClient.SelectedMediaAdmission selectedMaxMediaAdmission(Channel channel,long userId,String ticketId) {
        requireRemoteQuestionnaire(channel);
        if(!"max".equals(channel.getPlatform()))throw new PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException("Unsupported selected media platform");
        return panelRuntimeConfigClient.selectedMaxMediaAdmission(channel.getId(),userId,ticketId);
    }

    private void requireRemoteQuestionnaire(Channel channel) {
        if (!integrationTransportMode.isRabbitMqMode() || !panelRuntimeConfigClient.isEnabled()
                || channel == null || channel.getId() == null || channel.getId() <= 0
                || !channel.isActive()) {
            throw new PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException("Verified questionnaire runtime is unavailable");
        }
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
