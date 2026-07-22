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
                List.of(chatMessage("client", "hello", "2026-04-30T08:00:00Z", 1L)),
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
                Map.of("participants", List.of(), "triage_preferences", Map.of()),
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

    @Test
    void buildWorkspacePayloadBuildsRichIncludedSectionsAndEscalationState() {
        ChatMessageDto message = new ChatMessageDto(
                "operator",
                "",
                "Изначальный ответ",
                "2026-05-26T10:02:00Z",
                "image",
                "/api/attachments/tickets/T-WS-RICH/reply.png",
                "reply.png",
                962L,
                961L,
                null,
                "Изображение",
                "2026-05-26T10:02:30Z",
                "2026-05-26T10:03:30Z",
                "lead"
        );

        Map<String, Object> payload = service.buildWorkspacePayload(
                Set.of("messages", "context", "sla"),
                50,
                2,
                List.of(message),
                null,
                false,
                Map.of("id", 910096L, "name", "Клиент Rich"),
                List.of(Map.of("ticket_id", "T-WS-PREV")),
                Map.of("matches", List.of("crm")),
                List.of(Map.of("type", "audit")),
                Map.of("enabled", true, "ready", true),
                List.of(Map.of("key", "crm", "status", "ok")),
                List.of(Map.of("key", "phone", "ready", true)),
                List.of(Map.of("key", "customer_profile", "ready", true)),
                Map.of("enabled", true, "ready", true),
                Map.of("enabled", true, "ready", true),
                Map.of("can_reply", true, "can_assign", true, "can_close", true, "can_snooze", true),
                Map.of(
                        "responsible", Map.of("username", "watcher_owner", "assigned", true),
                        "participants", List.of(Map.of("username", "watcher_peer")),
                        "reassign_candidates", List.of(Map.of("username", "watcher_new")),
                        "participant_candidates", List.of(Map.of("username", "watcher_new")),
                        "triage_preferences", Map.of("view", "sla_critical"),
                        "collaboration", Map.of("participant_count", 1),
                        "actions", Map.of("reassign", Map.of("enabled", true))
                ),
                Map.of("reply_supported", true, "media_supported", true, "reply_target_supported", true),
                1440,
                240,
                30,
                "2026-05-27T08:00:00Z",
                "critical",
                15L,
                Map.of("enabled", true, "owner", "sla-core"),
                Map.of("mode", "workspace_primary"),
                Map.of("current_ticket_id", "T-WS-RICH", "enabled", true),
                Map.of("status", "attention", "missing_capabilities", List.of("media_reply"))
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> messages = (Map<String, Object>) payload.get("messages");
        @SuppressWarnings("unchecked")
        List<ChatMessageDto> items = (List<ChatMessageDto>) messages.get("items");
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) payload.get("context");
        @SuppressWarnings("unchecked")
        Map<String, Object> workflow = (Map<String, Object>) payload.get("workflow");
        @SuppressWarnings("unchecked")
        Map<String, Object> sla = (Map<String, Object>) payload.get("sla");
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) payload.get("meta");

        assertThat(payload).containsEntry("contract_version", "workspace.v1");
        assertThat(payload).containsEntry("success", true);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).replyPreview()).isEqualTo("Изображение");
        assertThat(items.get(0).originalMessage()).isEqualTo("Изначальный ответ");
        assertThat(items.get(0).deletedAt()).isEqualTo("2026-05-26T10:03:30Z");
        assertThat(context).containsEntry("client", Map.of("id", 910096L, "name", "Клиент Rich"));
        assertThat(context).containsEntry("profile_match_candidates", Map.of("matches", List.of("crm")));
        assertThat(workflow).containsKey("responsible");
        assertThat(workflow).containsEntry("triage_preferences", Map.of("view", "sla_critical"));
        assertThat(workflow).containsKey("actions");
        assertThat(sla).containsEntry("state", "critical");
        assertThat(sla).containsEntry("escalation_required", true);
        assertThat(meta).containsEntry("cursor", 2);
        assertThat(meta).containsEntry("parity", Map.of("status", "attention", "missing_capabilities", List.of("media_reply")));
    }

    private ChatMessageDto chatMessage(String sender, String message, String timestamp, Long telegramMessageId) {
        return new ChatMessageDto(
                sender,
                message,
                null,
                timestamp,
                "text",
                null,
                null,
                null,
                telegramMessageId,
                null,
                null,
                null,
                null,
                null
        );
    }
}
