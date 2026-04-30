package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DialogWorkspaceRequestContractServiceTest {

    private final DialogWorkspaceRequestContractService service = new DialogWorkspaceRequestContractService();

    @Test
    void resolveWorkspaceIncludeFallsBackToDefaultWhenInputIsBlankOrInvalid() {
        assertThat(service.resolveWorkspaceInclude(null))
                .containsExactlyInAnyOrder("messages", "context", "sla", "permissions");
        assertThat(service.resolveWorkspaceInclude("foo,bar"))
                .containsExactlyInAnyOrder("messages", "context", "sla", "permissions");
    }

    @Test
    void resolveWorkspaceIncludeFiltersAndNormalizesValues() {
        Set<String> include = service.resolveWorkspaceInclude("messages, SLA ,context,unknown");

        assertThat(include).containsExactlyInAnyOrder("messages", "sla", "context");
    }

    @Test
    void resolveWorkspaceLimitAndCursorClampValues() {
        assertThat(service.resolveWorkspaceLimit(null)).isEqualTo(50);
        assertThat(service.resolveWorkspaceLimit(500)).isEqualTo(200);
        assertThat(service.resolveWorkspaceCursor(null)).isZero();
        assertThat(service.resolveWorkspaceCursor("-10")).isZero();
        assertThat(service.resolveWorkspaceCursor("abc")).isZero();
        assertThat(service.resolveWorkspaceCursor("15")).isEqualTo(15);
    }

    @Test
    void resolveDialogConfigRangeMinutesUsesFallbackOutsideBounds() {
        Map<String, Object> settings = Map.of(
                "dialog_config", Map.of(
                        "workspace_context_history_limit", "8",
                        "workspace_context_related_events_limit", "99"
                )
        );

        assertThat(service.resolveDialogConfigRangeMinutes(settings, "workspace_context_history_limit", 5, 1, 20))
                .isEqualTo(8);
        assertThat(service.resolveDialogConfigRangeMinutes(settings, "workspace_context_related_events_limit", 5, 1, 20))
                .isEqualTo(5);
    }

    @Test
    void putProfileMatchFieldSkipsNoiseAndKeepsMeaningfulValues() {
        Map<String, String> target = new LinkedHashMap<>();

        service.putProfileMatchField(target, "business", " Sales ");
        service.putProfileMatchField(target, "empty", " ");
        service.putProfileMatchField(target, "dash", "—");
        service.putProfileMatchField(target, "country", "RU");

        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("business", "Sales");
        expected.put("country", "RU");
        assertThat(target).containsExactlyEntriesOf(expected);
    }
}
