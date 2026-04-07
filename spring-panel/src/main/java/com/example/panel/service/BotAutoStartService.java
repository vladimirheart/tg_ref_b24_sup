package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BotAutoStartService {

    private static final Logger log = LoggerFactory.getLogger(BotAutoStartService.class);

    private final ChannelRepository channelRepository;
    private final BotProcessService botProcessService;

    public BotAutoStartService(ChannelRepository channelRepository,
                               BotProcessService botProcessService) {
        this.channelRepository = channelRepository;
        this.botProcessService = botProcessService;
    }

    @PostConstruct
    public void autoStartActiveBots() {
        try {
            List<Channel> channels = channelRepository.findAll();
            int started = 0;
            for (Channel channel : channels) {
                if (channel == null || channel.getId() == null) {
                    continue;
                }
                if (!Boolean.TRUE.equals(channel.getActive())) {
                    continue;
                }
                String token = channel.getToken();
                if (token == null || token.isBlank()) {
                    continue;
                }
                BotProcessService.BotProcessStatus status = botProcessService.start(channel);
                if (status.running()) {
                    started++;
                    log.info("Auto-started bot for channel {} ({})", channel.getId(), channel.getChannelName());
                } else {
                    log.warn("Failed to auto-start bot for channel {}: {}", channel.getId(), status.message());
                }
            }
            log.info("Bot auto-start completed. Started {} active bot(s).", started);
        } catch (Exception ex) {
            log.warn("Bot auto-start failed", ex);
        }
    }
}
