package com.example.supportbot.telegram;

import java.io.IOException;

final class TelegramStartupFailureSupport {

    private TelegramStartupFailureSupport() {
    }

    static String describe(String fallbackMessage, Throwable exception, String apiRootUrl) {
        Throwable rootCause = rootCauseOf(exception);
        if (isProxyTunnelFailure(rootCause)) {
            return "Telegram runtime could not establish an HTTPS tunnel through the configured proxy to "
                    + apiRootUrl
                    + ". The endpoint behaves like a Bot API mirror/reverse proxy instead of a forward proxy. "
                    + "If direct requests like " + apiRootUrl + "/bot<TOKEN>/getMe work, configure this endpoint as Telegram Bot API base URL instead of proxy.";
        }
        if (isConnectivityFailure(rootCause)) {
            return "Telegram runtime could not reach Telegram Bot API at "
                    + apiRootUrl
                    + ". Verify outbound network access, firewall/proxy rules, TLS interception, and antivirus filtering.";
        }
        String rootMessage = rootCause != null && rootCause.getMessage() != null && !rootCause.getMessage().isBlank()
                ? rootCause.getMessage().trim()
                : rootCause != null ? rootCause.getClass().getSimpleName() : "unknown";
        return fallbackMessage + " Root cause: " + rootMessage;
    }

    private static Throwable rootCauseOf(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static boolean isConnectivityFailure(Throwable throwable) {
        return throwable instanceof IOException
                || throwable instanceof java.net.SocketException
                || throwable instanceof java.net.SocketTimeoutException;
    }

    private static boolean isProxyTunnelFailure(Throwable throwable) {
        return throwable instanceof IOException
                && throwable.getMessage() != null
                && throwable.getMessage().contains("Unable to tunnel through proxy");
    }
}
