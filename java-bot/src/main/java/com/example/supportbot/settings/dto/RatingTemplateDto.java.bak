package com.example.supportbot.settings.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RatingTemplateDto {

    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("prompt_text")
    private String promptText;

    @JsonProperty("scale_size")
    private int scaleSize;

    @JsonProperty("responses")
    private List<RatingResponseDto> responses;

    public RatingTemplateDto() {
    }

    public RatingTemplateDto(String id, String name, String description, String promptText, int scaleSize, List<RatingResponseDto> responses) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.promptText = promptText;
        this.scaleSize = scaleSize;
        this.responses = responses;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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