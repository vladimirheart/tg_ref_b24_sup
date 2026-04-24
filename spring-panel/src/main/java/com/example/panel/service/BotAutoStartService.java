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
import java.util.Objects;

@Service
public class BotAutoStartService {

    private static final Logger log = LoggerFactory.getLogger(BotAutoStartService.class);

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
                    BotProcessService.BotProcessStatus status = botProcessService.start(channel);
                    if (status != null && status.running()) {
                        started++;
                        log.info("Auto-started bot for channel {} ({})", channel.getId(), channel.getChannelName());
                    } else {
                        String message = status != null ? status.message() : "runtime returned null status";
                        log.warn("Failed to auto-start bot for channel {}: {}", channel.getId(), message);
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
