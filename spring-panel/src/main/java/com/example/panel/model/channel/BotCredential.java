package com.example.panel.model.channel;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BotCredential(
    @JsonProperty("id") Long id,
    @JsonProperty("name") String name,
    @JsonProperty("platform") String platform,
    @JsonProperty("token") String token,
    @JsonProperty("is_active") Boolean active
) {
}
