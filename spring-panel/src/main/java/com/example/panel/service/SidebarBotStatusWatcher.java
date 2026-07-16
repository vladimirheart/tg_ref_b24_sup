package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SidebarBotStatusWatcher {

    private final ChannelRepository channelRepository;
    private final BotProcessService botProcessService;
    private final UiEventStreamService uiEventStreamService;
    private final ConcurrentHashMap<Long, String> lastStatusByChannel = new ConcurrentHashMap<>();

    public SidebarBotStatusWatcher(ChannelRepository channelRepository,
                                   BotProcessService botProcessService,
                                   UiEventStreamService uiEventStreamService) {
        this.channelRepository = channelRepository;
        this.botProcessService = botProcessService;
        this.uiEventStreamService = uiEventStreamService;
    }

    @Scheduled(fixedDelayString = "${panel.sidebar.bots-watch-interval-ms:10000}")
    void watch() {
        Map<Long, String> currentStatuses = new ConcurrentHashMap<>();
        for (Channel channel : channelRepository.findAll()) {
            if (channel == null || channel.getId() == null) {
                continue;
            }
            String status = botProcessService.status(channel.getId()).message();
            currentStatuses.put(channel.getId(), status);
            String previous = lastStatusByChannel.put(channel.getId(), status);
            if (!Objects.equals(previous, status)) {
                uiEventStreamService.publishSidebarBotsChanged("bot_status_changed", channel.getId());
            }
        }
        lastStatusByChannel.keySet().removeIf(channelId -> !currentStatuses.containsKey(channelId));
    }
}
