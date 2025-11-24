package com.example.supportbot.settings.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuestionTemplateDto {

    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("question_flow")
    private List<QuestionFlowItemDto> questionFlow;

    public QuestionTemplateDto() {
    }

    public QuestionTemplateDto(String id, String name, String description, List<QuestionFlowItemDto> questionFlow) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.questionFlow = questionFlow;
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

    public List<QuestionFlowItemDto> getQuestionFlow() {
        return questionFlow;
    }

    public void setQuestionFlow(List<QuestionFlowItemDto> questionFlow) {
        this.questionFlow = questionFlow;
    }
}