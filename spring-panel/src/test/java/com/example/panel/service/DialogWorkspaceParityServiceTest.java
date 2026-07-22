package com.example.panel.service;

import com.example.panel.model.dialog.ChatMessageDto;
import com.example.panel.model.dialog.DialogListItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DialogWorkspaceParityServiceTest {

    private final DialogWorkspaceParityService service = new DialogWorkspaceParityService();

    @Test
    void buildComposerMetaMarksReplyTargetSupportWhenReplyPermissionAndThreadTargetsExist() {
        DialogListItem summary = sampleDialog();
        List<ChatMessageDto> history = List.of(
                chatMessage("client", "hello", 101L)
        );

        Map<String, Object> composer = service.buildComposerMeta(summary, history, Map.of("can_reply", true));

        assertThat(composer.get("reply_supported")).isEqualTo(true);
        assertThat(composer.get("media_supported")).isEqualTo(true);
        assertThat(composer.get("reply_target_supported")).isEqualTo(true);
        assertThat(composer.get("channel_id")).isEqualTo(44L);
    }

    @Test
    void buildComposerMetaDisablesReplyCapabilitiesWithoutReplyPermission() {
        DialogListItem summary = sampleDialog();
        List<ChatMessageDto> history = List.of(
                chatMessage("client", "hello", 101L)
        );

        Map<String, Object> composer = service.buildComposerMeta(summary, history, Map.of("can_reply", false));

        assertThat(composer.get("reply_supported")).isEqualTo(false);
        assertThat(composer.get("media_supported")).isEqualTo(false);
        assertThat(composer.get("reply_target_supported")).isEqualTo(false);
    }

    @Test
    void buildParityMetaReturnsOkWhenCoreWorkspaceCapabilitiesAreReady() {
        Map<String, Object> composer = service.buildComposerMeta(sampleDialog(), List.of(
                chatMessage("operator", "ok", 200L)
        ), Map.of(
                "can_reply", true,
                "can_assign", true,
                "can_close", true,
                "can_snooze", true
        ));

        Map<String, Object> parity = service.buildParityMeta(
                Set.of("messages", "context", "sla"),
                Map.of("id", "client-1"),
                List.of(),
                List.of(),
                Map.of("enabled", true, "ready", true),
                Map.of("enabled", true, "ready", true),
                Map.of(
                        "can_reply", true,
                        "can_assign", true,
                        "can_close", true,
                        "can_snooze", true
                ),
                Map.of(
                        "responsible", Map.of("assigned", true),
                        "participants", List.of(),
                        "reassign_candidates", List.of(),
                        "participant_candidates", List.of(),
                        "triage_preferences", Map.of(),
                        "collaboration", Map.of(),
                        "actions", Map.ofEntries(
                                Map.entry("reply", Map.of("enabled", true)),
                                Map.entry("reply_media", Map.of("enabled", true)),
                                Map.entry("take", Map.of("enabled", false)),
                                Map.entry("resolve", Map.of("enabled", true)),
                                Map.entry("reopen", Map.of("enabled", false)),
                                Map.entry("categories", Map.of("enabled", true)),
                                Map.entry("spam", Map.of("enabled", true)),
                                Map.entry("snooze", Map.of("enabled", true)),
                                Map.entry("reassign", Map.of("enabled", true)),
                                Map.entry("participants_add", Map.of("enabled", true)),
                                Map.entry("participants_remove", Map.of("enabled", false))
                        )
                ),
                composer,
                "healthy",
                sampleDialog(),
                Map.of("mode", "workspace_primary")
        );

        assertThat(parity.get("status")).isEqualTo("ok");
        assertThat(parity.get("score_pct")).isEqualTo(100);
        assertThat(parity.get("missing_capabilities")).asList().isEmpty();
    }

    @Test
    void buildParityMetaReturnsBlockedWhenOperatorActionContractIsIncomplete() {
        Map<String, Object> parity = service.buildParityMeta(
                Set.of("messages"),
                Map.of("id", "client-1"),
                List.of(),
                List.of(),
                Map.of("enabled", true, "ready", true),
                Map.of("enabled", true, "ready", true),
                Map.of("can_reply", true),
                Map.of(
                        "actions", Map.of()
                ),
                Map.of("reply_supported", true, "media_supported", true, "reply_target_supported", false),
                "unknown",
                sampleDialog(),
                Map.of("mode", "legacy_primary")
        );

        assertThat(parity.get("status")).isEqualTo("blocked");
        assertThat(parity.get("missing_capabilities").toString()).contains("operator_actions");
    }

    @Test
    void buildParityMetaReturnsAttentionWhenPermissionsExplicitlyDenyReplyButContractIsPresent() {
        Map<String, Object> parity = service.buildParityMeta(
                Set.of("messages", "context", "sla"),
                Map.of("id", "client-1"),
                List.of(),
                List.of(),
                Map.of("enabled", true, "ready", true),
                Map.of("enabled", true, "ready", true),
                Map.of(
                        "can_reply", false,
                        "can_assign", false,
                        "can_close", false,
                        "can_snooze", false
                ),
                Map.of(
                        "responsible", Map.of("assigned", true),
                        "participants", List.of(),
                        "reassign_candidates", List.of(),
                        "participant_candidates", List.of(),
                        "triage_preferences", Map.of(),
                        "collaboration", Map.of(),
                        "actions", Map.ofEntries(
                                Map.entry("reply", Map.of("enabled", false)),
                                Map.entry("reply_media", Map.of("enabled", false)),
                                Map.entry("take", Map.of("enabled", true)),
                                Map.entry("resolve", Map.of("enabled", true)),
                                Map.entry("reopen", Map.of("enabled", false)),
                                Map.entry("categories", Map.of("enabled", false)),
                                Map.entry("spam", Map.of("enabled", false)),
                                Map.entry("snooze", Map.of("enabled", false)),
                                Map.entry("reassign", Map.of("enabled", false)),
                                Map.entry("participants_add", Map.of("enabled", false)),
                                Map.entry("participants_remove", Map.of("enabled", false))
                        )
                ),
                Map.of(
                        "reply_supported", false,
                        "media_supported", false,
                        "reply_target_supported", false
                ),
                "healthy",
                sampleDialog(),
                Map.of("mode", "workspace_primary")
        );

        assertThat(parity.get("status")).isEqualTo("attention");
        assertThat(parity.get("missing_capabilities").toString()).contains("reply_threading");
        assertThat(parity.get("missing_capabilities").toString()).contains("media_reply");
        assertThat(parity.get("missing_capabilities").toString()).doesNotContain("operator_actions");
    }

    private DialogListItem sampleDialog() {
        return new DialogListItem(
                "T-401",
                401L,
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

    private ChatMessageDto chatMessage(String sender, String message, long telegramMessageId) {
        return new ChatMessageDto(
                sender,
                message,
                null,
                "2026-04-20T10:00:00Z",
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
