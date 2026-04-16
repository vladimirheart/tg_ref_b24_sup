package com.example.panel.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SettingsDialogConfigRoutingServiceTest {

    private final SettingsDialogConfigRoutingService routingService = new SettingsDialogConfigRoutingService();

    @Test
    void detectsKnownDialogConfigPrefixes() {
        assertTrue(routingService.hasDialogConfigUpdates(Map.of("dialog_workspace_v1", true)));
        assertTrue(routingService.hasDialogConfigUpdates(Map.of("dialog_sla_target_minutes", 15)));
        assertTrue(routingService.hasDialogConfigUpdates(Map.of("dialog_public_form_message_max_length", 2000)));
        assertTrue(routingService.hasDialogConfigUpdates(Map.of("dialog_macro_templates", "[]")));
    }

    @Test
    void detectsDirectDialogConfigKeysWithoutPrefixGroups() {
        assertTrue(routingService.hasDialogConfigUpdates(Map.of("dialog_time_metrics", Map.of())));
        assertTrue(routingService.hasDialogConfigUpdates(Map.of("dialog_summary_badges", Map.of("vip", true))));
        assertTrue(routingService.hasDialogConfigUpdates(Map.of("dialog_default_view", "workspace")));
    }

    @Test
    void ignoresNonDialogConfigPayload() {
        assertFalse(routingService.hasDialogConfigUpdates(null));
        assertFalse(routingService.hasDialogConfigUpdates(Map.of()));
        assertFalse(routingService.hasDialogConfigUpdates(Map.of("locations", Map.of())));
        assertFalse(routingService.hasDialogConfigUpdates(Map.of("integration", Map.of("mode", "proxy"))));
    }
}
