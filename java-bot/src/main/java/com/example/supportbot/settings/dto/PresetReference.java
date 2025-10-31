package com.example.supportbot.settings.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PresetReference(
        @JsonProperty("group") String group,
        @JsonProperty("field") String field
) {
}
