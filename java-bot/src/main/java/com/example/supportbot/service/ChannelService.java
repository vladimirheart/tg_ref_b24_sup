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
        String lookupToken = (token == null || token.isBlank()) ? "__default__" : token;
        Channel channel = channelRepository.findByToken(lookupToken)
                .orElseGet(() -> {
                    Channel created = new Channel();
                    created.setToken(lookupToken);
                    return channelRepository.save(created);
                });

        if (channel.getPublicId() != null && !channel.getPublicId().isBlank()) {
            return channel;
        }

        String publicId;
        do {
            byte[] data = new byte[16];
            random.nextBytes(data);
            publicId = HEX.formatHex(data).toLowerCase();
        } while (channelRepository.findByPublicId(publicId).isPresent());

        channel.setPublicId(publicId);
        Channel saved = channelRepository.save(channel);
        log.info("Assigned public id {} to channel {}", publicId, saved.getId());
        return saved;
    }
}
