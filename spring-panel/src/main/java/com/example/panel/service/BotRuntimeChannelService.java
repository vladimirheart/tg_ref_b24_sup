package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class BotRuntimeChannelService {

    private static final Logger log = LoggerFactory.getLogger(BotRuntimeChannelService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private final ChannelRepository channelRepository;

    public BotRuntimeChannelService(ChannelRepository channelRepository) {
        this.channelRepository = channelRepository;
    }

    @Transactional
    public Channel resolveConfiguredChannel(Long channelId, String token, String channelName, String platform) {
        if (channelId != null && channelId > 0) {
            Optional<Channel> configured = channelRepository.findById(channelId);
            if (configured.isPresent()) {
                return ensurePersistedPublicId(configured.get(), channelName, platform);
            }
            log.warn("Configured channel {} was not found, falling back to token lookup", channelId);
        }
        return ensurePublicIdForToken(token, channelName, platform);
    }

    @Transactional
    public Channel updateSupportChatId(Long channelId, String supportChatId) {
        if (channelId == null || channelId <= 0) {
            throw new IllegalArgumentException("Channel id is required to update support chat id");
        }
        if (!StringUtils.hasText(supportChatId)) {
            throw new IllegalArgumentException("Support chat id must be a non-empty string");
        }
        Channel channel = channelRepository.findById(channelId)
            .orElseThrow(() -> new IllegalArgumentException("Channel " + channelId + " was not found"));
        String normalized = supportChatId.trim();
        if (normalized.equals(channel.getSupportChatId())) {
            return channel;
        }
        channel.setSupportChatId(normalized);
        Channel saved = channelRepository.save(channel);
        log.info("Updated support chat id for channel {} to {}", saved.getId(), normalized);
        return saved;
    }

    private Channel ensurePublicIdForToken(String token, String channelName, String platform) {
        String lookupToken = StringUtils.hasText(token) ? token.trim() : "__default__";
        return channelRepository.findByToken(lookupToken)
            .map(channel -> ensurePersistedPublicId(channel, channelName, platform))
            .orElseGet(() -> createChannel(lookupToken, channelName, platform));
    }

    private Channel ensurePersistedPublicId(Channel channel, String channelName, String platform) {
        if (StringUtils.hasText(channel.getPublicId())) {
            return channel;
        }
        if (!StringUtils.hasText(channel.getChannelName())) {
            channel.setChannelName(StringUtils.hasText(channelName) ? channelName.trim() : "Telegram");
        }
        if (!StringUtils.hasText(channel.getPlatform())) {
            channel.setPlatform(StringUtils.hasText(platform) ? platform.trim() : "telegram");
        }
        if (!StringUtils.hasText(channel.getQuestionsCfg())) {
            channel.setQuestionsCfg("{}");
        }
        channel.setPublicId(generatePublicId());
        Channel saved = channelRepository.save(channel);
        log.info("Assigned public id {} to channel {}", saved.getPublicId(), saved.getId());
        return saved;
    }

    private Channel createChannel(String token, String channelName, String platform) {
        Channel created = new Channel();
        created.setToken(token);
        created.setChannelName(StringUtils.hasText(channelName) ? channelName.trim() : "Telegram");
        created.setPlatform(StringUtils.hasText(platform) ? platform.trim() : "telegram");
        created.setQuestionsCfg("{}");
        created.setPublicId(generatePublicId());
        Channel saved = channelRepository.save(created);
        log.info("Created channel {} for platform {} with public id {}", saved.getId(), saved.getPlatform(), saved.getPublicId());
        return saved;
    }

    private String generatePublicId() {
        String publicId;
        do {
            byte[] data = new byte[16];
            RANDOM.nextBytes(data);
            publicId = HEX.formatHex(data).toLowerCase();
        } while (channelRepository.findByPublicId(publicId).isPresent());
        return publicId;
    }
}
