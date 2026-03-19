package com.example.supportbot.service;

import com.example.supportbot.entity.Channel;
import com.example.supportbot.repository.ChannelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;

@Service
public class ChannelService {

    private static final Logger log = LoggerFactory.getLogger(ChannelService.class);
    private static final SecureRandom random = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private final ChannelRepository channelRepository;

    public ChannelService(ChannelRepository channelRepository) {
        this.channelRepository = channelRepository;
    }

    @Transactional
    public Channel ensurePublicIdForToken(String token) {
        return ensurePublicIdForToken(token, "Telegram", "telegram");
    }

    @Transactional
    public Channel ensurePublicIdForToken(String token, String channelName, String platform) {
        String lookupToken = (token == null || token.isBlank()) ? "__default__" : token;
        return channelRepository.findByToken(lookupToken)
                .map(channel -> ensurePersistedPublicId(channel, channelName, platform))
                .orElseGet(() -> createChannel(lookupToken, channelName, platform));
    }

    @Transactional
    public Channel updateSupportChatId(Channel channel, String supportChatId) {
        if (channel == null) {
            throw new IllegalArgumentException("Channel is required to update support chat id");
        }
        if (supportChatId == null || supportChatId.isBlank()) {
            throw new IllegalArgumentException("Support chat id must be a non-empty string");
        }
        String current = channel.getSupportChatId();
        if (supportChatId.equals(current)) {
            return channel;
        }
        channel.setSupportChatId(supportChatId);
        Channel saved = channelRepository.save(channel);
        log.info("Updated support chat id for channel {} to {}", saved.getId(), supportChatId);
        return saved;
    }

    private Channel ensurePersistedPublicId(Channel channel, String channelName, String platform) {
        if (channel.getPublicId() != null && !channel.getPublicId().isBlank()) {
            return channel;
        }

        if (channel.getChannelName() == null || channel.getChannelName().isBlank()) {
            channel.setChannelName(channelName);
        }
        if (channel.getPlatform() == null || channel.getPlatform().isBlank()) {
            channel.setPlatform(platform);
        }
        if (channel.getQuestionsCfg() == null || channel.getQuestionsCfg().isBlank()) {
            channel.setQuestionsCfg("{}");
        }

        String publicId = generatePublicId();
        channel.setPublicId(publicId);
        Channel saved = channelRepository.save(channel);
        log.info("Assigned public id {} to channel {}", publicId, saved.getId());
        return saved;
    }

    private Channel createChannel(String token, String channelName, String platform) {
        Channel created = new Channel();
        created.setToken(token);
        created.setChannelName(channelName);
        created.setPlatform(platform);
        created.setQuestionsCfg("{}");
        created.setPublicId(generatePublicId());
        Channel saved = channelRepository.save(created);
        log.info("Created channel {} for platform {} with public id {}", saved.getId(), platform, saved.getPublicId());
        return saved;
    }

    private String generatePublicId() {
        String publicId;
        do {
            byte[] data = new byte[16];
            random.nextBytes(data);
            publicId = HEX.formatHex(data).toLowerCase();
        } while (channelRepository.findByPublicId(publicId).isPresent());
        return publicId;
    }
}
