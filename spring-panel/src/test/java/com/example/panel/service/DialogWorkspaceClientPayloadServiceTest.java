package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DialogWorkspaceClientPayloadServiceTest {

    private final DialogWorkspaceClientPayloadService service = new DialogWorkspaceClientPayloadService();

    @Test
    void filterProfileEnrichmentRemovesConfiguredHiddenAttributes() {
        Set<String> hidden = service.resolveHiddenClientAttributes(Map.of(
                "dialog_config", Map.of(
                        "workspace_client_hidden_attributes", List.of("phone", "crm_id")
                )
        ));

        Map<String, Object> filtered = service.filterProfileEnrichment(
                Map.of(
                        "name", "Клиент",
                        "phone", "+79990000000",
                        "crm_id", "123",
                        "city", "Moscow"
                ),
                hidden
        );

        assertThat(hidden).containsExactlyInAnyOrder("phone", "crm_id");
        assertThat(filtered).containsEntry("name", "Клиент").containsEntry("city", "Moscow");
        assertThat(filtered).doesNotContainKeys("phone", "crm_id");
    }

    @Test
    void resolveExternalProfileLinksBuildsBuiltinAndConfiguredLinks() {
        Map<String, Object> links = service.resolveExternalProfileLinks(
                Map.of(
                        "dialog_config", Map.of(
                                "workspace_client_crm_profile_url_template", "https://crm.test/client/{user_id}",
                                "workspace_client_crm_profile_label", "CRM карточка",
                                "workspace_client_external_links", List.of(
                                        Map.of(
                                                "key", "support",
                                                "label", "Support Portal",
                                                "url_template", "https://support.test/ticket/{ticket_id}",
                                                "enabled", true
                                        )
                                )
                        )
                ),
                sampleDialog(),
                "T-901",
                Map.of("crm_id", "C-1")
        );

        assertThat(links).containsKeys("crm", "support");
        assertThat(((Map<?, ?>) links.get("crm")).get("label")).isEqualTo("CRM карточка");
        assertThat(((Map<?, ?>) links.get("crm")).get("url")).isEqualTo("https://crm.test/client/1001");
        assertThat(((Map<?, ?>) links.get("support")).get("url")).isEqualTo("https://support.test/ticket/T-901");
    }

    private DialogListItem sampleDialog() {
        return new DialogListItem(
                "T-901",
                901L,
                1001L,
                "client_username",
                "Клиент",
                "sales",
                44L,
                "Telegram",
                "Moscow",
                "Moscow",
                "message preview",
                "2026-04-20T10:00:00Z",
                "open",
                false,
                null,
                "operator",
                "20.04.2026",
                "10:00:00",
                "vip",
                "client",
                "2026-04-20T10:01:00Z",
                1,
                5,
                "billing"
        );
    }
}
