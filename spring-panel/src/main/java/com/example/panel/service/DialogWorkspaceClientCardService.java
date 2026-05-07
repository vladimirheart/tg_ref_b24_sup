package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DialogWorkspaceClientCardService {

    private final DialogWorkspaceClientProfileService dialogWorkspaceClientProfileService;
    private final DialogWorkspaceClientPayloadService dialogWorkspaceClientPayloadService;

    public DialogWorkspaceClientCardService(DialogWorkspaceClientProfileService dialogWorkspaceClientProfileService,
                                            DialogWorkspaceClientPayloadService dialogWorkspaceClientPayloadService) {
        this.dialogWorkspaceClientProfileService = dialogWorkspaceClientProfileService;
        this.dialogWorkspaceClientPayloadService = dialogWorkspaceClientPayloadService;
    }

    public WorkspaceClientCard build(Map<String, Object> settings,
                                     DialogListItem summary,
                                     String ticketId,
                                     Map<String, Object> filteredProfileEnrichment) {
        Map<String, Object> workspaceClient = new LinkedHashMap<>();
        workspaceClient.put("id", summary.userId());
        workspaceClient.put("name", summary.displayClientName());
        workspaceClient.put("language", "ru");
        workspaceClient.put("username", summary.username());
        workspaceClient.put("status", summary.clientStatusLabel());
        workspaceClient.put("channel", summary.channelLabel());
        workspaceClient.put("business", summary.businessLabel());
        workspaceClient.put("location", summary.location());
        workspaceClient.put("responsible", summary.responsible());
        workspaceClient.put("unread_count", summary.unreadCount());
        workspaceClient.put("rating", summary.rating());
        workspaceClient.put("last_message_at", summary.lastMessageTimestamp());
        workspaceClient.put("segments", dialogWorkspaceClientProfileService.buildClientSegments(summary, filteredProfileEnrichment, settings));

        Map<String, Object> externalLinks = dialogWorkspaceClientPayloadService.resolveExternalProfileLinks(
                settings, summary, ticketId, filteredProfileEnrichment
        );
        if (!externalLinks.isEmpty()) {
            workspaceClient.put("external_links", externalLinks);
        }

        Map<String, String> attributeLabels = dialogWorkspaceClientPayloadService.resolveClientAttributeLabels(settings);
        List<String> attributeOrder = dialogWorkspaceClientPayloadService.resolveClientAttributeOrder(settings);
        if (!attributeLabels.isEmpty()) {
            workspaceClient.put("attribute_labels", attributeLabels);
        }
        if (!attributeOrder.isEmpty()) {
            workspaceClient.put("attribute_order", attributeOrder);
        }
        if (!filteredProfileEnrichment.isEmpty()) {
            workspaceClient.putAll(filteredProfileEnrichment);
        }
        return new WorkspaceClientCard(workspaceClient, externalLinks, attributeLabels);
    }

    public record WorkspaceClientCard(Map<String, Object> workspaceClient,
                                      Map<String, Object> externalLinks,
                                      Map<String, String> attributeLabels) {
    }
}
