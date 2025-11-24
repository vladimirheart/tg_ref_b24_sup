package com.example.supportbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "vk-bot")
public class VkBotProperties {

    /** Enable VK long-poll runner. */
    private boolean enabled = false;

    /** Community token from VK. */
    private String token = "";

    /** Group id for long poll. */
    private Integer groupId = 0;

    /** Optional operator chat/peer id to forward summaries. */
    private Long channelId = 0L;

    /** Delay between long poll retries, seconds. */
    private int retryDelaySeconds = 5;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Integer getGroupId() {
        return groupId;
    }

    public void setGroupId(Integer groupId) {
        this.groupId = groupId;
    }

    public Long getChannelId() {
        return channelId;
    }

    public void setChannelId(Long channelId) {
        this.channelId = channelId;
    }

    public int getRetryDelaySeconds() {
        return retryDelaySeconds;
    }

    public void setRetryDelaySeconds(int retryDelaySeconds) {
        this.retryDelaySeconds = retryDelaySeconds;
    }
}
