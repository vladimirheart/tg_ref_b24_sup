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

    @JsonProperty("start_message")
    private String startMessage;

    @JsonProperty("question_flow")
    private List<QuestionFlowItemDto> questionFlow;

    @JsonProperty("first_response_timeout_minutes")
    private Integer firstResponseTimeoutMinutes;

    @JsonProperty("first_response_timeout_message")
    private String firstResponseTimeoutMessage;

    public QuestionTemplateDto() {
    }

    public QuestionTemplateDto(String id,
                               String name,
                               String description,
                               String startMessage,
                               List<QuestionFlowItemDto> questionFlow,
                               Integer firstResponseTimeoutMinutes,
                               String firstResponseTimeoutMessage) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.startMessage = startMessage;
        this.questionFlow = questionFlow;
        this.firstResponseTimeoutMinutes = firstResponseTimeoutMinutes;
        this.firstResponseTimeoutMessage = firstResponseTimeoutMessage;
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

    public String getStartMessage() {
        return startMessage;
    }

    public void setStartMessage(String startMessage) {
        this.startMessage = startMessage;
    }

    public List<QuestionFlowItemDto> getQuestionFlow() {
        return questionFlow;
    }

    public void setQuestionFlow(List<QuestionFlowItemDto> questionFlow) {
        this.questionFlow = questionFlow;
    }

    public Integer getFirstResponseTimeoutMinutes() {
        return firstResponseTimeoutMinutes;
    }

    public void setFirstResponseTimeoutMinutes(Integer firstResponseTimeoutMinutes) {
        this.firstResponseTimeoutMinutes = firstResponseTimeoutMinutes;
    }

    public String getFirstResponseTimeoutMessage() {
        return firstResponseTimeoutMessage;
    }

    public void setFirstResponseTimeoutMessage(String firstResponseTimeoutMessage) {
        this.firstResponseTimeoutMessage = firstResponseTimeoutMessage;
    }
}
