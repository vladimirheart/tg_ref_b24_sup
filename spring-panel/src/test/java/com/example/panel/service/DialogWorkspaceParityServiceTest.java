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
                new ChatMessageDto("client", "hello", null, "2026-04-20T10:00:00Z", "text", null, 101L, null, null, null, null, null)
        );

        Map<String, Object> composer = service.buildComposerMeta(summary, history, Map.of("can_reply", true));

        assertThat(composer.get("reply_supported")).isEqualTo(true);
        assertThat(composer.get("media_supported")).isEqualTo(true);
        assertThat(composer.get("reply_target_supported")).isEqualTo(true);
        assertThat(composer.get("channel_id")).isEqualTo(44L);
    }

    @Test
    void buildParityMetaReturnsOkWhenCoreWorkspaceCapabilitiesAreReady() {
        Map<String, Object> composer = service.buildComposerMeta(sampleDialog(), List.of(
                new ChatMessageDto("operator", "ok", null, "2026-04-20T10:01:00Z", "text", null, 200L, null, null, null, null, null)
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
                Map.of("reply_supported", true, "media_supported", true, "reply_target_supported", false),
                "unknown",
                sampleDialog(),
                Map.of("mode", "legacy_primary")
        );

        assertThat(parity.get("status")).isEqualTo("blocked");
        assertThat(parity.get("missing_capabilities").toString()).contains("operator_actions");
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
}
