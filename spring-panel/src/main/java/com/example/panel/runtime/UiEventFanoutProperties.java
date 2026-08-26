package com.example.panel.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "app.ui-events.fanout")
public class UiEventFanoutProperties {

    private String mode = "auto";
    private String channel = "iguana:ui-events:v1";

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public UiEventFanoutMode resolvedMode() {
        return UiEventFanoutMode.from(mode);
    }

    public String resolvedChannel() {
        return StringUtils.hasText(channel) ? channel.trim() : "iguana:ui-events:v1";
    }
}
