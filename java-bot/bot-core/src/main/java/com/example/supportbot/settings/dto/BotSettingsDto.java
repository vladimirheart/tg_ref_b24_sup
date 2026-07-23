package com.example.supportbot.settings.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class BotSettingsDto {

    @JsonProperty("question_templates")
    private List<QuestionTemplateDto> questionTemplates;

    @JsonProperty("active_template_id")
    private String activeTemplateId;

    @JsonProperty("rating_templates")
    private List<RatingTemplateDto> ratingTemplates;

    @JsonProperty("active_rating_template_id")
    private String activeRatingTemplateId;

    @JsonProperty("unblock_request_cooldown_minutes")
    private Integer unblockRequestCooldownMinutes;

    @JsonProperty("business_aliases")
    private Map<String, List<String>> businessAliases;

    public BotSettingsDto() {
    }

    public BotSettingsDto(
            List<QuestionTemplateDto> questionTemplates,
            String activeTemplateId,
            List<RatingTemplateDto> ratingTemplates,
            String activeRatingTemplateId,
            Integer unblockRequestCooldownMinutes,
            Map<String, List<String>> businessAliases) {
        this.questionTemplates = questionTemplates;
        this.activeTemplateId = activeTemplateId;
        this.ratingTemplates = ratingTemplates;
        this.activeRatingTemplateId = activeRatingTemplateId;
        this.unblockRequestCooldownMinutes = unblockRequestCooldownMinutes;
        this.businessAliases = businessAliases;
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

    @JsonIgnore
    public List<QuestionFlowItemDto> getQuestionFlow() {
        QuestionTemplateDto template = resolveActiveQuestionTemplate();
        if (template != null && template.getQuestionFlow() != null && !template.getQuestionFlow().isEmpty()) {
            return template.getQuestionFlow();
        }
        return List.of();
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

    @JsonIgnore
    public RatingSystemDto getRatingSystem() {
        RatingTemplateDto template = resolveActiveRatingTemplate();
        if (template != null) {
            return new RatingSystemDto(
                    template.getPromptText(),
                    template.getScaleSize(),
                    template.getResponses()
            );
        }
        return null;
    }

    public Integer getUnblockRequestCooldownMinutes() {
        return unblockRequestCooldownMinutes;
    }

    public void setUnblockRequestCooldownMinutes(Integer unblockRequestCooldownMinutes) {
        this.unblockRequestCooldownMinutes = unblockRequestCooldownMinutes;
    }

    public Map<String, List<String>> getBusinessAliases() {
        return businessAliases;
    }

    public void setBusinessAliases(Map<String, List<String>> businessAliases) {
        this.businessAliases = businessAliases;
    }

    private QuestionTemplateDto resolveActiveQuestionTemplate() {
        if (questionTemplates == null || questionTemplates.isEmpty()) {
            return null;
        }
        if (activeTemplateId != null && !activeTemplateId.isBlank()) {
            for (QuestionTemplateDto template : questionTemplates) {
                if (template != null && activeTemplateId.equals(template.getId())) {
                    return template;
                }
            }
        }
        return questionTemplates.get(0);
    }

    private RatingTemplateDto resolveActiveRatingTemplate() {
        if (ratingTemplates == null || ratingTemplates.isEmpty()) {
            return null;
        }
        if (activeRatingTemplateId != null && !activeRatingTemplateId.isBlank()) {
            for (RatingTemplateDto template : ratingTemplates) {
                if (template != null && activeRatingTemplateId.equals(template.getId())) {
                    return template;
                }
            }
        }
        return ratingTemplates.get(0);
    }
}
