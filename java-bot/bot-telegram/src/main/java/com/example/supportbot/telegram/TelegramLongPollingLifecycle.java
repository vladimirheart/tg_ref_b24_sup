package com.example.supportbot.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.BotSession;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Component
public class TelegramLongPollingLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TelegramLongPollingLifecycle.class);

    private final SupportBot supportBot;

    private volatile boolean running = false;
    private TelegramBotsApi botsApi;
    private BotSession botSession;

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
            botSession = botsApi.registerBot(supportBot);

            running = true;
            log.info("Telegram long polling started. username={}", supportBot.getBotUsername());
        } catch (TelegramApiException e) {
            throw new IllegalStateException(supportBot.describeStartupFailure(
                    "Failed to start Telegram long polling. Bot will NOT receive updates.",
                    e
            ), e);
        }
    }

    @Override
    public void stop() {
        BotSession currentSession = botSession;
        botSession = null;
        if (currentSession != null) {
            currentSession.stop();
        }
        botsApi = null;
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
