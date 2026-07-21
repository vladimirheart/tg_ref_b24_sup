package com.example.supportbot.settings.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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

    private List<QuestionFlowItemDto> legacyQuestionFlow;

    private RatingSystemDto legacyRatingSystem;

    public BotSettingsDto() {
    }

    public BotSettingsDto(
            List<QuestionTemplateDto> questionTemplates,
            String activeTemplateId,
            List<QuestionFlowItemDto> questionFlow,
            List<RatingTemplateDto> ratingTemplates,
            String activeRatingTemplateId,
            RatingSystemDto ratingSystem,
            Integer unblockRequestCooldownMinutes,
            Map<String, List<String>> businessAliases) {
        this.questionTemplates = questionTemplates;
        this.activeTemplateId = activeTemplateId;
        this.ratingTemplates = ratingTemplates;
        this.activeRatingTemplateId = activeRatingTemplateId;
        this.unblockRequestCooldownMinutes = unblockRequestCooldownMinutes;
        this.businessAliases = businessAliases;
        this.legacyQuestionFlow = questionFlow;
        this.legacyRatingSystem = ratingSystem;
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

    @JsonProperty("question_flow")
    public List<QuestionFlowItemDto> getQuestionFlow() {
        QuestionTemplateDto template = resolveActiveQuestionTemplate();
        if (template != null && template.getQuestionFlow() != null && !template.getQuestionFlow().isEmpty()) {
            return template.getQuestionFlow();
        }
        return legacyQuestionFlow;
    }

    @JsonProperty("question_flow")
    public void setQuestionFlow(List<QuestionFlowItemDto> questionFlow) {
        this.legacyQuestionFlow = questionFlow;
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

    @JsonProperty("rating_system")
    public RatingSystemDto getRatingSystem() {
        RatingTemplateDto template = resolveActiveRatingTemplate();
        if (template != null) {
            return new RatingSystemDto(
                    template.getPromptText(),
                    template.getScaleSize(),
                    template.getResponses()
            );
        }
        return legacyRatingSystem;
    }

    @JsonProperty("rating_system")
    public void setRatingSystem(RatingSystemDto ratingSystem) {
        this.legacyRatingSystem = ratingSystem;
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
