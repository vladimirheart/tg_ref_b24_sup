package com.example.supportbot.settings.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RatingSystemDto {

    @JsonProperty("prompt_text")
    private String promptText;

    @JsonProperty("scale_size")
    private int scaleSize;

    @JsonProperty("responses")
    private List<RatingResponseDto> responses;

    public RatingSystemDto() {
    }

    public RatingSystemDto(String promptText, int scaleSize, List<RatingResponseDto> responses) {
        this.promptText = promptText;
        this.scaleSize = scaleSize;
        this.responses = responses;
    }

    public String getPromptText() {
        return promptText;
    }

    public void setPromptText(String promptText) {
        this.promptText = promptText;
    }

    public int getScaleSize() {
        return scaleSize;
    }

    public void setScaleSize(int scaleSize) {
        this.scaleSize = scaleSize;
    }

    public List<RatingResponseDto> getResponses() {
        return responses;
    }

    public void setResponses(List<RatingResponseDto> responses) {
        this.responses = responses;
    }
}
