package com.example.supportbot.settings.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class BotSettingsDto {

    @JsonProperty("question_templates")
    private List<QuestionTemplateDto> questionTemplates;

    @JsonProperty("active_template_id")
    private String activeTemplateId;

    @JsonProperty("question_flow")
    private List<QuestionFlowItemDto> questionFlow;

    @JsonProperty("rating_templates")
    private List<RatingTemplateDto> ratingTemplates;

    @JsonProperty("active_rating_template_id")
    private String activeRatingTemplateId;

    @JsonProperty("rating_system")
    private RatingSystemDto ratingSystem;

    @JsonProperty("unblock_request_cooldown_minutes")
    private Integer unblockRequestCooldownMinutes;

    public BotSettingsDto() {
    }

    public BotSettingsDto(
            List<QuestionTemplateDto> questionTemplates,
            String activeTemplateId,
            List<QuestionFlowItemDto> questionFlow,
            List<RatingTemplateDto> ratingTemplates,
            String activeRatingTemplateId,
            RatingSystemDto ratingSystem,
            Integer unblockRequestCooldownMinutes) {
        this.questionTemplates = questionTemplates;
        this.activeTemplateId = activeTemplateId;
        this.questionFlow = questionFlow;
        this.ratingTemplates = ratingTemplates;
        this.activeRatingTemplateId = activeRatingTemplateId;
        this.ratingSystem = ratingSystem;
        this.unblockRequestCooldownMinutes = unblockRequestCooldownMinutes;
    }

    public List<QuestionTemplateDto> getQuestionTemplates() {
        return questionTemplates;
    }

    public void setQuestionTemplates(List<QuestionTemplateDto> questionTemplates) {
        this.questionTemplates = questionTemplates;
    }

    public String getActiveTemplateId() {
        return activeTemplateId;
    }

    public void setActiveTemplateId(String activeTemplateId) {
        this.activeTemplateId = activeTemplateId;
    }

    public List<QuestionFlowItemDto> getQuestionFlow() {
        return questionFlow;
    }

    public void setQuestionFlow(List<QuestionFlowItemDto> questionFlow) {
        this.questionFlow = questionFlow;
    }

    public List<RatingTemplateDto> getRatingTemplates() {
        return ratingTemplates;
    }

    public void setRatingTemplates(List<RatingTemplateDto> ratingTemplates) {
        this.ratingTemplates = ratingTemplates;
    }

    public String getActiveRatingTemplateId() {
        return activeRatingTemplateId;
    }

    public void setActiveRatingTemplateId(String activeRatingTemplateId) {
        this.activeRatingTemplateId = activeRatingTemplateId;
    }

    public RatingSystemDto getRatingSystem() {
        return ratingSystem;
    }

    public void setRatingSystem(RatingSystemDto ratingSystem) {
        this.ratingSystem = ratingSystem;
    }

    public Integer getUnblockRequestCooldownMinutes() {
        return unblockRequestCooldownMinutes;
    }

    public void setUnblockRequestCooldownMinutes(Integer unblockRequestCooldownMinutes) {
        this.unblockRequestCooldownMinutes = unblockRequestCooldownMinutes;
    }
}
