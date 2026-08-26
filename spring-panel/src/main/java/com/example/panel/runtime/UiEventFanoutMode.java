package com.example.panel.runtime;

import java.util.Locale;
import org.springframework.util.StringUtils;

public enum UiEventFanoutMode {
    AUTO,
    LOCAL,
    REDIS;

    public static UiEventFanoutMode from(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return AUTO;
        }
        return switch (rawValue.trim().toLowerCase(Locale.ROOT)) {
            case "auto" -> AUTO;
            case "local" -> LOCAL;
            case "redis" -> REDIS;
            default -> throw new IllegalArgumentException(
                "Unsupported UI event fanout mode '" + rawValue + "'. Expected auto, local or redis."
            );
        };
    }
}
