package com.example.supportbot.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class TelegramStartupFailureSupportTest {

    private static final String API_ROOT = "https://telegram.ftl-dev.ru";

    @Test
    void proxyTunnelFailureExplainsMirrorVersusForwardProxy() {
        Throwable failure = new RuntimeException(
                new IOException("Unable to tunnel through proxy. Proxy returns HTTP/1.1 400 Bad Request")
        );

        assertThat(TelegramStartupFailureSupport.describe("fallback", failure, API_ROOT))
                .contains("could not establish an HTTPS tunnel through the configured proxy")
                .contains("Bot API mirror/reverse proxy instead of a forward proxy")
                .contains(API_ROOT + "/bot<TOKEN>/getMe");
    }

    @Test
    void connectivityFailurePointsToNetworkChecks() {
        Throwable failure = new RuntimeException(new IOException("Connection reset"));

        assertThat(TelegramStartupFailureSupport.describe("fallback", failure, API_ROOT))
                .isEqualTo("Telegram runtime could not reach Telegram Bot API at "
                        + API_ROOT
                        + ". Verify outbound network access, firewall/proxy rules, TLS interception, and antivirus filtering.");
    }

    @Test
    void genericFailureUsesDeepestRootCauseMessage() {
        Throwable failure = new RuntimeException(new IllegalStateException("  invalid token response  "));

        assertThat(TelegramStartupFailureSupport.describe("fallback", failure, API_ROOT))
                .isEqualTo("fallback Root cause: invalid token response");
    }

    @Test
    void blankRootCauseMessageFallsBackToClassName() {
        Throwable failure = new RuntimeException(new IllegalArgumentException("   "));

        assertThat(TelegramStartupFailureSupport.describe("fallback", failure, API_ROOT))
                .isEqualTo("fallback Root cause: IllegalArgumentException");
    }
}
