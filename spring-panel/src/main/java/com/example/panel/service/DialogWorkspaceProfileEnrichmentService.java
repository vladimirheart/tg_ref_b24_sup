package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class DialogWorkspaceProfileEnrichmentService {

    private final DialogWorkspaceExternalProfileService dialogWorkspaceExternalProfileService;
    private final DialogWorkspaceClientPayloadService dialogWorkspaceClientPayloadService;

    public DialogWorkspaceProfileEnrichmentService(DialogWorkspaceExternalProfileService dialogWorkspaceExternalProfileService,
                                                   DialogWorkspaceClientPayloadService dialogWorkspaceClientPayloadService) {
        this.dialogWorkspaceExternalProfileService = dialogWorkspaceExternalProfileService;
        this.dialogWorkspaceClientPayloadService = dialogWorkspaceClientPayloadService;
    }

    public ProfileEnrichmentBundle resolve(Map<String, Object> settings,
                                           DialogListItem summary,
                                           String ticketId,
                                           Map<String, Object> profileEnrichment) {
        Map<String, Object> effectiveProfileEnrichment = mergeExternalProfileEnrichment(settings, summary, ticketId, profileEnrichment);
        Set<String> hiddenProfileAttributes = dialogWorkspaceClientPayloadService.resolveHiddenClientAttributes(settings);
        Map<String, Object> filteredProfileEnrichment = dialogWorkspaceClientPayloadService.filterProfileEnrichment(
                effectiveProfileEnrichment,
                hiddenProfileAttributes
        );
        return new ProfileEnrichmentBundle(effectiveProfileEnrichment, filteredProfileEnrichment);
    }

    private Map<String, Object> mergeExternalProfileEnrichment(Map<String, Object> settings,
                                                               DialogListItem summary,
                                                               String ticketId,
                                                               Map<String, Object> profileEnrichment) {
        Object rawDialogConfig = settings != null ? settings.get("dialog_config") : null;
        if (!(rawDialogConfig instanceof Map<?, ?> dialogConfig) || summary == null) {
            return profileEnrichment == null ? Map.of() : profileEnrichment;
        }
        String externalUrlTemplate = trimToNull(String.valueOf(dialogConfig.get("workspace_client_external_profile_url")));
        if (!StringUtils.hasText(externalUrlTemplate)) {
            return profileEnrichment == null ? Map.of() : profileEnrichment;
        }
        Map<String, String> placeholders = dialogWorkspaceClientPayloadService.buildExternalLinkPlaceholders(summary, ticketId, profileEnrichment);
        String resolvedUrl = externalUrlTemplate;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolvedUrl = resolvedUrl.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        resolvedUrl = trimToNull(resolvedUrl);
        if (!StringUtils.hasText(resolvedUrl)) {
            return profileEnrichment == null ? Map.of() : profileEnrichment;
        }
        Map<String, Object> externalProfileEnrichment = dialogWorkspaceExternalProfileService.resolveProfile(dialogConfig, resolvedUrl);
        if (externalProfileEnrichment.isEmpty()) {
            return profileEnrichment == null ? Map.of() : profileEnrichment;
        }
        Map<String, Object> mergedEnrichment = new LinkedHashMap<>();
        if (profileEnrichment != null) {
            mergedEnrichment.putAll(profileEnrichment);
        }
        externalProfileEnrichment.forEach(mergedEnrichment::putIfAbsent);
        return mergedEnrichment;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public record ProfileEnrichmentBundle(Map<String, Object> effectiveProfileEnrichment,
                                          Map<String, Object> filteredProfileEnrichment) {
    }
}
