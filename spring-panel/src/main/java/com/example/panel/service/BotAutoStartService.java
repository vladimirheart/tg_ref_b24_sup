package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.model.channel.BotCredential;
import com.example.panel.repository.ChannelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.time.Duration;
import java.util.Objects;

@Service
public class BotAutoStartService {

    private static final Logger log = LoggerFactory.getLogger(BotAutoStartService.class);
    private static final int AUTO_START_MAX_ATTEMPTS = 2;
    private static final Duration AUTO_START_RETRY_DELAY = Duration.ofSeconds(3);

    private final ChannelRepository channelRepository;
    private final BotProcessService botProcessService;
    private final SharedConfigService sharedConfigService;

    public BotAutoStartService(ChannelRepository channelRepository,
                               BotProcessService botProcessService,
                               SharedConfigService sharedConfigService) {
        this.channelRepository = channelRepository;
        this.botProcessService = botProcessService;
        this.sharedConfigService = sharedConfigService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void autoStartActiveBots() {
        try {
            botProcessService.stopAllForStartup();
            List<Channel> channels = channelRepository.findAll();
            int started = 0;
            for (Channel channel : channels) {
                try {
                    if (channel == null || channel.getId() == null) {
                        continue;
                    }
                    if (!Boolean.TRUE.equals(channel.getActive())) {
                        continue;
                    }
                    if (!isCredentialActive(channel)) {
                        continue;
                    }
                    BotProcessService.BotProcessStatus currentStatus = botProcessService.status(channel.getId());
                    if (currentStatus != null && currentStatus.running()) {
                        continue;
                    }
                    BotProcessService.BotProcessStatus status = startWithRetry(channel);
                    if (status != null && status.running()) {
                        started++;
                        log.info("Auto-started bot for channel {} ({})", channel.getId(), channel.getChannelName());
                    } else {
                        String message = status != null ? status.message() : "runtime returned null status";
                        log.warn("Failed to auto-start bot for channel {} after {} attempt(s): {}",
                                channel.getId(), AUTO_START_MAX_ATTEMPTS, message);
                    }
                } catch (Exception channelEx) {
                    log.warn("Auto-start failed for channel {} ({})", channel.getId(), channel.getChannelName(), channelEx);
                }
            }
            log.info("Bot auto-start completed. Started {} active bot(s).", started);
        } catch (Exception ex) {
            log.warn("Bot auto-start failed", ex);
        }
    }

    private BotProcessService.BotProcessStatus startWithRetry(Channel channel) {
        BotProcessService.BotProcessStatus lastStatus = null;
        for (int attempt = 1; attempt <= AUTO_START_MAX_ATTEMPTS; attempt++) {
            lastStatus = botProcessService.start(channel);
            if (lastStatus != null && lastStatus.running()) {
                if (attempt > 1) {
                    log.info("Bot channel {} ({}) became ready on auto-start attempt {}/{}",
                            channel.getId(), channel.getChannelName(), attempt, AUTO_START_MAX_ATTEMPTS);
                }
                return lastStatus;
            }

            String message = lastStatus != null ? lastStatus.message() : "runtime returned null status";
            if (attempt >= AUTO_START_MAX_ATTEMPTS) {
                break;
            }

            log.warn("Auto-start attempt {}/{} failed for channel {} ({}): {}. Retrying in {} ms.",
                    attempt,
                    AUTO_START_MAX_ATTEMPTS,
                    channel.getId(),
                    channel.getChannelName(),
                    message,
                    AUTO_START_RETRY_DELAY.toMillis());

            try {
                Thread.sleep(AUTO_START_RETRY_DELAY.toMillis());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                log.warn("Bot auto-start retry interrupted for channel {} ({})",
                        channel.getId(), channel.getChannelName());
                break;
            }
        }
        return lastStatus;
    }
    private boolean isCredentialActive(Channel channel) {
        Long credentialId = channel.getCredentialId();
        if (credentialId == null) {
            return true;
        }
        List<BotCredential> credentials = sharedConfigService.loadBotCredentials();
        return credentials.stream()
                .filter(item -> Objects.equals(item.id(), credentialId))
                .findFirst()
                .map(item -> Boolean.TRUE.equals(item.active()))
                .orElse(true);
    }
}
