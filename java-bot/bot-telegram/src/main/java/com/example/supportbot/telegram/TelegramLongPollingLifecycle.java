package com.example.supportbot.telegram;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Component
public class TelegramLongPollingLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TelegramLongPollingLifecycle.class);

    private final SupportBot supportBot;

    private volatile boolean running = false;
    private TelegramBotsApi botsApi;

    public TelegramLongPollingLifecycle(SupportBot supportBot) {
        this.supportBot = supportBot;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        try {
            botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(supportBot);

            running = true;
            log.info("Telegram long polling started. username={}", supportBot.getBotUsername());
        } catch (TelegramApiException e) {
            Throwable rootCause = rootCauseOf(e);
            if (isConnectivityFailure(rootCause)) {
                log.error("Failed to start Telegram long polling because the application could not reach api.telegram.org over HTTPS. " +
                        "The bot token may still be valid, but the bot will not receive updates until network connectivity is restored.", e);
                return;
            }
            log.error("Failed to start Telegram long polling. Bot will NOT receive updates.", e);
        }
    }

    private Throwable rootCauseOf(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private boolean isConnectivityFailure(Throwable throwable) {
        return throwable instanceof IOException
                || throwable instanceof java.net.SocketException
                || throwable instanceof java.net.SocketTimeoutException;
    }

    @Override
    public void stop() {
        running = false;
        log.info("Telegram long polling stopped. username={}", supportBot.getBotUsername());
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MIN_VALUE;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }
}
