package com.example.panel.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DialogReplyTargetMessageActionsUiSourceContractTest {

    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void replyTargetPulseAndExternalActionRailLiveInSourceFiles() throws IOException {
        String runtime = read("spring-panel/src/main/resources/static/js/dialogs-details-history-runtime.js");
        String dialogsScss = read("spring-panel/src/main/resources/scss/app/_dialogs.scss");
        String template = read("spring-panel/src/main/resources/templates/dialogs/index.html");

        assertThat(runtime)
            .contains("target.scrollIntoView({ behavior: 'smooth', block: 'center' });")
            .contains("target.classList.add('is-reply-target-highlight');")
            .contains("<div class=\"chat-message-bubble-line ${canReply ? 'has-actions' : ''}\">")
            .contains("${media}\n              </div>\n              ${actionButtons}\n            </div>")
            .doesNotContain("${media}\n              ${actionButtons}\n            </div>");

        assertThat(dialogsScss)
            .contains("/* 01-253: reply target pulse and external message action rail */")
            .contains("#dialogDetailsHistory .chat-message-bubble-line")
            .contains("#dialogDetailsHistory .chat-message-menu.is-portaled-open .chat-message-menu-list")
            .contains("#dialogHistoryActionMenuPortal.chat-message-menu-portal")
            .contains(".chat-message-row.is-reply-target-highlight .chat-message::after")
            .contains("@keyframes dialog-reply-target-ring")
            .contains("@media (prefers-reduced-motion: reduce)");

        assertThat(template)
            .contains("@{/css/app.css(v='20260904-1')}")
            .contains("dialogsAssetVersion='20260904-1'")
            .contains("id=\"dialogHistoryActionMenuPortal\"");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(REPO_ROOT.resolve(relativePath), StandardCharsets.UTF_8)
            .replace("\r\n", "\n");
    }
}
