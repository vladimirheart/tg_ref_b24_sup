package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.model.publicform.PublicFormConfig;
import com.example.panel.model.publicform.PublicFormSessionDto;
import com.example.panel.repository.ChannelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class PublicFormChannelService {
    private final ChannelRepository channelRepository;
    private final PublicFormDefinitionService publicFormDefinitionService;
    private final PublicFormSessionService publicFormSessionService;

    public PublicFormChannelService(ChannelRepository channelRepository,
                                    PublicFormDefinitionService publicFormDefinitionService,
                                    PublicFormSessionService publicFormSessionService) {
        this.channelRepository = channelRepository;
        this.publicFormDefinitionService = publicFormDefinitionService;
        this.publicFormSessionService = publicFormSessionService;
    }

    public Optional<PublicFormConfig> loadConfig(String channelRef) {
        return loadConfigRaw(channelRef).filter(PublicFormConfig::enabled);
    }

    public Optional<PublicFormConfig> loadConfigRaw(String channelRef) {
        if (channelRef != null && channelRef.trim().equalsIgnoreCase("demo")) {
            return Optional.of(publicFormDefinitionService.buildDemoConfig());
        }
        return resolveChannel(channelRef).map(publicFormDefinitionService::buildConfig);
    }

    public Optional<PublicFormSessionDto> findSession(String channelRef, String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        return resolveChannel(channelRef)
                .flatMap(channel -> publicFormSessionService.findSession(channel, token));
    }

    public Optional<Long> resolveChannelId(String channelRef) {
        return resolveChannel(channelRef).map(Channel::getId);
    }

    public Map<String, Object> buildContinuationOptions(String channelRef, String sessionToken) {
        return resolveChannel(channelRef)
                .map(channel -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    String platform = Optional.ofNullable(channel.getPlatform())
                            .filter(StringUtils::hasText)
                            .orElse("telegram")
                            .trim()
                            .toLowerCase(Locale.ROOT);
                    String command = StringUtils.hasText(sessionToken) ? "/continue " + sessionToken.trim() : "/continue <token>";
                    payload.put("enabled", true);
                    payload.put("platform", platform);
                    payload.put("platformLabel", switch (platform) {
                        case "vk" -> "VK";
                        case "max" -> "MAX";
                        default -> "Telegram";
                    });
                    payload.put("channelName", channel.getChannelName());
                    payload.put("botName", Optional.ofNullable(channel.getBotName()).orElse(""));
                    payload.put("botUsername", Optional.ofNullable(channel.getBotUsername()).orElse(""));
                    payload.put("command", command);
                    payload.put("token", Optional.ofNullable(sessionToken).orElse(""));
                    payload.put("openUrl", buildContinuationOpenUrl(channel, platform, sessionToken));
                    payload.put("hint", switch (platform) {
                        case "vk", "max" -> "Откройте бота и отправьте команду продолжения, чтобы привязать внешний диалог.";
                        default -> "Откройте бота по ссылке или отправьте команду продолжения.";
                    });
                    return payload;
                })
                .orElseGet(() -> Map.of(
                        "enabled", false,
                        "platform", "telegram",
                        "platformLabel", "Telegram",
                        "command", "/continue <token>",
                        "token", "",
                        "openUrl", "",
                        "hint", ""
                ));
    }

    public Optional<Channel> resolveChannel(String channelRef) {
        if (!StringUtils.hasText(channelRef)) {
            return Optional.empty();
        }
        String trimmed = channelRef.trim();
        Optional<Channel> direct = channelRepository.findByPublicIdIgnoreCase(trimmed);
        if (direct.isPresent()) {
            return direct;
        }
        try {
            long id = Long.parseLong(trimmed);
            return channelRepository.findById(id);
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private String buildContinuationOpenUrl(Channel channel, String platform, String sessionToken) {
        if (!StringUtils.hasText(sessionToken)) {
            return "";
        }
        if ("telegram".equals(platform)) {
            String botUsername = Optional.ofNullable(channel.getBotUsername()).map(String::trim).orElse("");
            if (StringUtils.hasText(botUsername)) {
                String normalized = botUsername.startsWith("@") ? botUsername.substring(1) : botUsername;
                return "https://t.me/" + normalized + "?start=web_" + urlEncode(sessionToken.trim());
            }
        }
        return "";
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(Optional.ofNullable(value).orElse(""), StandardCharsets.UTF_8);
    }
}
