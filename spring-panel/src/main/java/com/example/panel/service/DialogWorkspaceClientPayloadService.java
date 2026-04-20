package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DialogWorkspaceClientPayloadService {

    public Set<String> resolveHiddenClientAttributes(Map<String, Object> settings) {
        Set<String> hidden = new HashSet<>();
        if (settings == null || settings.isEmpty()) {
            return hidden;
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return hidden;
        }
        Object hiddenRaw = dialogConfig.get("workspace_client_hidden_attributes");
        if (!(hiddenRaw instanceof List<?> hiddenList)) {
            return hidden;
        }
        hiddenList.forEach(item -> {
            String normalized = normalizeMacroVariableKey(String.valueOf(item));
            if (StringUtils.hasText(normalized)) {
                hidden.add(normalized);
            }
        });
        return hidden;
    }

    public Map<String, Object> filterProfileEnrichment(Map<String, Object> profileEnrichment,
                                                       Set<String> hiddenAttributes) {
        Map<String, Object> filtered = new LinkedHashMap<>();
        if (profileEnrichment == null || profileEnrichment.isEmpty()) {
            return filtered;
        }
        profileEnrichment.forEach((keyRaw, valueRaw) -> {
            String normalizedKey = normalizeMacroVariableKey(String.valueOf(keyRaw));
            if (StringUtils.hasText(normalizedKey) && hiddenAttributes != null && hiddenAttributes.contains(normalizedKey)) {
                return;
            }
            filtered.put(String.valueOf(keyRaw), valueRaw);
        });
        return filtered;
    }

    public Map<String, Object> resolveExternalProfileLinks(Map<String, Object> settings,
                                                           DialogListItem summary,
                                                           String ticketId,
                                                           Map<String, Object> profileEnrichment) {
        if (settings == null || summary == null) {
            return Map.of();
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return Map.of();
        }
        Map<String, Object> links = new LinkedHashMap<>();
        Map<String, String> placeholders = buildExternalLinkPlaceholders(summary, ticketId, profileEnrichment);
        appendWorkspaceExternalProfileLink(links, "crm", dialogConfig, "workspace_client_crm_profile_url_template",
                "workspace_client_crm_profile_label", "CRM профиль", placeholders);
        appendWorkspaceExternalProfileLink(links, "contract", dialogConfig, "workspace_client_contract_profile_url_template",
                "workspace_client_contract_profile_label", "Договор/контракт", placeholders);
        appendConfiguredWorkspaceExternalProfileLinks(links, dialogConfig, placeholders);
        return links;
    }

    public Map<String, String> resolveClientAttributeLabels(Map<String, Object> settings) {
        Map<String, String> labels = new LinkedHashMap<>();
        if (settings == null || settings.isEmpty()) {
            return labels;
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return labels;
        }
        Object labelsRaw = dialogConfig.get("workspace_client_attribute_labels");
        if (!(labelsRaw instanceof Map<?, ?> labelMap)) {
            return labels;
        }
        labelMap.forEach((keyRaw, valueRaw) -> {
            String key = normalizeMacroVariableKey(String.valueOf(keyRaw));
            String value = trimToNull(String.valueOf(valueRaw));
            if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
                labels.put(key, value);
            }
        });
        return labels;
    }

    public List<String> resolveClientAttributeOrder(Map<String, Object> settings) {
        if (settings == null || settings.isEmpty()) {
            return List.of();
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return List.of();
        }
        Object orderRaw = dialogConfig.get("workspace_client_attribute_order");
        if (!(orderRaw instanceof List<?> orderList)) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        orderList.forEach(item -> {
            String key = normalizeMacroVariableKey(String.valueOf(item));
            if (StringUtils.hasText(key) && !normalized.contains(key)) {
                normalized.add(key);
            }
        });
        return normalized;
    }

    private void appendConfiguredWorkspaceExternalProfileLinks(Map<String, Object> links,
                                                               Map<?, ?> dialogConfig,
                                                               Map<String, String> placeholders) {
        Object configuredRaw = dialogConfig.get("workspace_client_external_links");
        if (!(configuredRaw instanceof List<?> configuredLinks)) {
            return;
        }
        for (Object candidate : configuredLinks) {
            if (!(candidate instanceof Map<?, ?> linkConfig)) {
                continue;
            }
            boolean enabled = !linkConfig.containsKey("enabled") || asBoolean(linkConfig.get("enabled"));
            if (!enabled) {
                continue;
            }
            String key = normalizeMacroVariableKey(String.valueOf(linkConfig.get("key")));
            if (!StringUtils.hasText(key) || links.containsKey(key)) {
                continue;
            }
            String template = trimToNull(String.valueOf(linkConfig.get("url_template")));
            if (!StringUtils.hasText(template)) {
                continue;
            }
            String fallbackLabel = StringUtils.hasText(trimToNull(String.valueOf(linkConfig.get("label"))))
                    ? trimToNull(String.valueOf(linkConfig.get("label")))
                    : "Внешний профиль";
            appendWorkspaceExternalProfileLink(links, key, linkConfig, "url_template", "label", fallbackLabel, placeholders);
        }
    }

    private void appendWorkspaceExternalProfileLink(Map<String, Object> links,
                                                    String key,
                                                    Map<?, ?> dialogConfig,
                                                    String templateKey,
                                                    String labelKey,
                                                    String fallbackLabel,
                                                    Map<String, String> placeholders) {
        String template = trimToNull(String.valueOf(dialogConfig.get(templateKey)));
        if (!StringUtils.hasText(template)) {
            return;
        }
        String resolved = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolved = resolved.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        resolved = trimToNull(resolved);
        if (!StringUtils.hasText(resolved) || !(resolved.startsWith("https://") || resolved.startsWith("http://"))) {
            return;
        }
        String label = trimToNull(String.valueOf(dialogConfig.get(labelKey)));
        links.put(key, Map.of(
                "label", StringUtils.hasText(label) ? label : fallbackLabel,
                "url", resolved
        ));
    }

    public Map<String, String> buildExternalLinkPlaceholders(DialogListItem summary,
                                                             String ticketId,
                                                             Map<String, Object> profileEnrichment) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("ticket_id", StringUtils.hasText(ticketId) ? ticketId : "");
        placeholders.put("user_id", summary.userId() != null ? String.valueOf(summary.userId()) : "");
        placeholders.put("username", StringUtils.hasText(summary.username()) ? summary.username() : "");
        placeholders.put("channel", StringUtils.hasText(summary.channelLabel()) ? summary.channelLabel() : "");
        placeholders.put("business", StringUtils.hasText(summary.businessLabel()) ? summary.businessLabel() : "");
        placeholders.put("location", StringUtils.hasText(summary.location()) ? summary.location() : "");
        placeholders.put("responsible", StringUtils.hasText(summary.responsible()) ? summary.responsible() : "");
        if (profileEnrichment != null && !profileEnrichment.isEmpty()) {
            profileEnrichment.forEach((keyRaw, valueRaw) -> {
                String key = normalizeMacroVariableKey(String.valueOf(keyRaw));
                String value = stringifyValue(valueRaw);
                if (StringUtils.hasText(key) && value != null && !placeholders.containsKey(key)) {
                    placeholders.put(key, value);
                }
            });
        }
        return placeholders;
    }

    private String stringifyValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof CharSequence sequence) {
            String text = sequence.toString().trim();
            return StringUtils.hasText(text) ? text : null;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(this::stringifyValue)
                    .filter(StringUtils::hasText)
                    .reduce((left, right) -> left + ", " + right)
                    .orElse(null);
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .map(entry -> {
                        String mapKey = entry.getKey() != null ? String.valueOf(entry.getKey()).trim() : "";
                        String mapValue = stringifyValue(entry.getValue());
                        if (!StringUtils.hasText(mapKey) || !StringUtils.hasText(mapValue)) {
                            return null;
                        }
                        return mapKey + ": " + mapValue;
                    })
                    .filter(StringUtils::hasText)
                    .reduce((left, right) -> left + "; " + right)
                    .orElse(null);
        }
        String fallback = String.valueOf(value).trim();
        return StringUtils.hasText(fallback) ? fallback : null;
    }

    private boolean asBoolean(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw instanceof Number n) {
            return n.intValue() != 0;
        }
        if (raw == null) {
            return false;
        }
        String normalized = String.valueOf(raw).trim();
        return "true".equalsIgnoreCase(normalized) || "1".equals(normalized);
    }

    private String normalizeMacroVariableKey(String rawValue) {
        String value = trimToNull(rawValue);
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase()
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        return normalized.isEmpty() ? null : normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
