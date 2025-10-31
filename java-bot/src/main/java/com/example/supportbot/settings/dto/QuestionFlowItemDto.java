package com.example.supportbot.settings.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuestionFlowItemDto {

    @JsonProperty("id")
    private String id;

    @JsonProperty("type")
    private String type;

    @JsonProperty("text")
    private String text;

    @JsonProperty("order")
    private int order;

    @JsonProperty("preset")
    private PresetReference preset;

    @JsonProperty("excluded_options")
    private List<String> excludedOptions;

    public QuestionFlowItemDto() {
    }

    public QuestionFlowItemDto(String id, String type, String text, int order, PresetReference preset, List<String> excludedOptions) {
        this.id = id;
        this.type = type;
        this.text = text;
        this.order = order;
        this.preset = preset;
        this.excludedOptions = excludedOptions;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public PresetReference getPreset() {
        return preset;
    }

    public void setPreset(PresetReference preset) {
        this.preset = preset;
    }

    public List<String> getExcludedOptions() {
        return excludedOptions;
    }

    public void setExcludedOptions(List<String> excludedOptions) {
        this.excludedOptions = excludedOptions;
    }
}
