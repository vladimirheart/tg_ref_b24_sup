package com.example.supportbot.settings.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuestionRouteDto {

    @JsonProperty("value_id")
    private String valueId;

    @JsonProperty("next_question_id")
    private String nextQuestionId;

    public QuestionRouteDto() {
    }

    public QuestionRouteDto(String valueId, String nextQuestionId) {
        this.valueId = valueId;
        this.nextQuestionId = nextQuestionId;
    }

    public String getValueId() {
        return valueId;
    }

    public void setValueId(String valueId) {
        this.valueId = valueId;
    }

    public String getNextQuestionId() {
        return nextQuestionId;
    }

    public void setNextQuestionId(String nextQuestionId) {
        this.nextQuestionId = nextQuestionId;
    }
}
