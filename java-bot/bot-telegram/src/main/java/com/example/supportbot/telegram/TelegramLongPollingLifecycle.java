package com.example.supportbot.telegram;

import com.example.supportbot.config.BotProperties;
import com.example.supportbot.service.BotIngressCoordinationService;
import java.time.Duration;
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
    private final BotProperties properties;
    private final BotIngressCoordinationService ingressCoordinationService;

    private volatile boolean running = false;
    private TelegramBotsApi botsApi;
    private BotSession botSession;
    private volatile Thread coordinatorThread;

    public TelegramLongPollingLifecycle(SupportBot supportBot,
                                        BotProperties properties,
                                        BotIngressCoordinationService ingressCoordinationService) {
        this.supportBot = supportBot;
        this.properties = properties;
        this.ingressCoordinationService = ingressCoordinationService;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        Thread thread = new Thread(this::coordinationLoop, "telegram-ingress-coordinator");
        thread.setDaemon(true);
        coordinatorThread = thread;
        thread.start();
    }

    private void coordinationLoop() {
        while (running) {
            try {
                if (ingressCoordinationService.tryAcquireOrRenew("telegram", properties.ingressLeaseIdentity())) {
                    ensureSessionStarted();
                    sleepSilently(ingressCoordinationService.renewInterval());
                } else {
                    ensureSessionStopped(false);
                    sleepSilently(ingressCoordinationService.followerBackoff());
                }
            } catch (RuntimeException ex) {
                ensureSessionStopped(false);
                log.warn("Telegram ingress coordination iteration failed: {}", ex.getMessage());
                sleepSilently(ingressCoordinationService.followerBackoff());
            }
        }
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
    public void stop() {
        running = false;
        Thread thread = coordinatorThread;
        coordinatorThread = null;
        if (thread != null) {
            thread.interrupt();
        }
        ensureSessionStopped(true);
        ingressCoordinationService.release("telegram", properties.ingressLeaseIdentity());
        log.info("Telegram long polling stopped. username={}", supportBot.getBotUsername());
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    private void ensureSessionStarted() {
        if (botSession != null) {
            return;
        }
        try {
            botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botSession = botsApi.registerBot(supportBot);
            log.info("Telegram long polling became active ingress owner. username={}", supportBot.getBotUsername());
        } catch (TelegramApiException e) {
            throw new IllegalStateException(supportBot.describeStartupFailure(
                "Failed to start Telegram long polling. Bot will NOT receive updates.",
                e
            ), e);
        }
    }

    private void ensureSessionStopped(boolean logStop) {
        BotSession currentSession = botSession;
        botSession = null;
        if (currentSession != null) {
            currentSession.stop();
            if (logStop) {
                log.info("Telegram long polling session closed. username={}", supportBot.getBotUsername());
            } else {
                log.info("Telegram long polling released active ingress ownership. username={}", supportBot.getBotUsername());
            }
        }
        botsApi = null;
    }

    private void sleepSilently(Duration duration) {
        long millis = duration == null ? 1000L : Math.max(250L, duration.toMillis());
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
