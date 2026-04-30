package com.example.panel.service;

import com.example.panel.model.dialog.ChatMessageDto;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DialogWorkspacePayloadAssemblerServiceTest {

    private final DialogWorkspacePayloadAssemblerService service = new DialogWorkspacePayloadAssemblerService();

    @Test
    void buildWorkspacePayloadBuildsUnavailableSectionsAndSuccessEnvelope() {
        Map<String, Object> payload = service.buildWorkspacePayload(
                Set.of("messages"),
                25,
                0,
                List.of(new ChatMessageDto("client", "hello", null, "2026-04-30T08:00:00Z", "text", null, 1L, null, null, null, null, null)),
                1,
                true,
                Map.of("id", 1001L),
                List.of(),
                Map.of(),
                List.of(),
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Map.of("enabled", true),
                Map.of("can_reply", true),
                Map.of("reply_supported", true),
                1440,
                240,
                30,
                "2026-05-01T08:00:00Z",
                "healthy",
                120L,
                Map.of("enabled", true),
                Map.of("mode", "workspace_primary"),
                Map.of("current_ticket_id", "T-1"),
                Map.of("status", "ok")
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> messages = (Map<String, Object>) payload.get("messages");
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) payload.get("context");
        @SuppressWarnings("unchecked")
        Map<String, Object> sla = (Map<String, Object>) payload.get("sla");
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) payload.get("meta");

        assertThat(payload).containsEntry("contract_version", "workspace.v1");
        assertThat(payload).containsEntry("success", true);
        assertThat(messages).containsEntry("has_more", true);
        assertThat(context).containsEntry("unavailable", true);
        assertThat(sla).containsEntry("unavailable", true);
        assertThat(meta).containsEntry("limit", 25);
    }

    @Test
    void mapWithNullableValuesPreservesNullsAndOrder() {
        Map<String, Object> payload = service.mapWithNullableValues("a", 1, "b", null, "c", "x");
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("a", 1);
        expected.put("b", null);
        expected.put("c", "x");

        assertThat(payload).containsExactlyEntriesOf(expected);
    }
}
