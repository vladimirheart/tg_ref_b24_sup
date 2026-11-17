package com.example.supportbot.service;

import java.util.List;

/** Scope selected before the first question; kept with the questionnaire session. */
public record QuestionnaireScope(String scopeKind, String scopeSelectionSource,
                                 String questionTemplateId, String templateSha256,
                                 Long businessId, String catalogSha256) {

    public QuestionnaireScope(String scopeKind, String scopeSelectionSource,
            String questionTemplateId, String templateSha256) {
        this(scopeKind, scopeSelectionSource, questionTemplateId, templateSha256, null, null);
    }

    public QuestionnaireScope {
        if ("QUESTIONNAIRE_FIELD".equals(scopeSelectionSource)) {
            if (!("BUSINESS".equals(scopeKind) || "UNRESOLVED".equals(scopeKind))
                    || ("BUSINESS".equals(scopeKind) && (businessId == null || businessId <= 0))
                    || ("UNRESOLVED".equals(scopeKind) && businessId != null)
                    || questionTemplateId == null || questionTemplateId.isBlank()
                    || !questionTemplateId.equals(questionTemplateId.strip())
                    || !hash(templateSha256) || catalogSha256 != null) {
                throw new IllegalArgumentException("Incomplete field-bound questionnaire scope");
            }
        } else if ("BUSINESS".equals(scopeKind) || ("GENERAL".equals(scopeKind) && "USER_SELECTION".equals(scopeSelectionSource))) {
            if (!"USER_SELECTION".equals(scopeSelectionSource)
                    || ("BUSINESS".equals(scopeKind) && (businessId == null || businessId <= 0))
                    || ("GENERAL".equals(scopeKind) && businessId != null)
                    || questionTemplateId == null || questionTemplateId.isBlank()
                    || !questionTemplateId.equals(questionTemplateId.strip())
                    || !hash(templateSha256) || !hash(catalogSha256)) {
                throw new IllegalArgumentException("Incomplete selected business questionnaire scope");
            }
        } else if (businessId != null || catalogSha256 != null) {
            throw new IllegalArgumentException("Unexpected business questionnaire identity");
        }
    }

    public static QuestionnaireScope fromSelection(PanelRuntimeConfigClient.SelectedQuestionnaire selected) {
        if (selected == null || (selected.version() != 1 && selected.version() != 2 && selected.version() != 3 && selected.version() != 4) || selected.channelId() <= 0
                || !("BUSINESS".equals(selected.scopeKind()) || (selected.version()>=2 && "GENERAL".equals(selected.scopeKind())))) {
            throw new IllegalArgumentException("Unsupported questionnaire selection");
        }
        return new QuestionnaireScope(selected.scopeKind(), selected.scopeSelectionSource(),
            selected.questionTemplateId(), selected.templateSha256(), selected.businessId(), selected.catalogSha256());
    }

    private static boolean hash(String value) {
        return value != null && value.matches("[a-f0-9]{64}");
    }

    public static QuestionnaireScope from(PanelRuntimeConfigClient.RuntimeConfigSnapshot runtime) {
        if (runtime == null) {
            return null;
        }
        if (runtime.scopeKind() == null && runtime.scopeSelectionSource() == null
                && runtime.questionTemplateId() == null && runtime.templateSha256() == null) {
            return null;
        }
        if (!runtime.isGeneralChannel()) {
            if (runtime.isUnifiedFieldQuestionnaire()) {
                return new QuestionnaireScope("UNRESOLVED", "QUESTIONNAIRE_FIELD",
                    runtime.questionTemplateId(), runtime.templateSha256(), null, null);
            }
            throw new IllegalStateException("Unsupported or incomplete scoped questionnaire runtime config");
        }
        return new QuestionnaireScope(runtime.scopeKind(), runtime.scopeSelectionSource(),
            runtime.questionTemplateId(), runtime.templateSha256());
    }

    /** Resolve the canonical owner after the configured business field has been answered. */
    public QuestionnaireScope resolveQuestionnaireBusiness(List<TicketService.TicketAttributeInput> attributes) {
        if (!"QUESTIONNAIRE_FIELD".equals(scopeSelectionSource)) {
            return this;
        }
        TicketService.TicketAttributeInput business = null;
        if (attributes != null) {
            for (TicketService.TicketAttributeInput attribute : attributes) {
                if (attribute == null || !"business".equals(attribute.attributeKey())) {
                    continue;
                }
                if (business != null) {
                    throw new IllegalStateException("Questionnaire has more than one business answer");
                }
                business = attribute;
            }
        }
        if (business == null) {
            return new QuestionnaireScope("UNRESOLVED", scopeSelectionSource, questionTemplateId, templateSha256, null, null);
        }
        try {
            long businessId = Long.parseLong(business.valueId());
            if (businessId <= 0) {
                throw new NumberFormatException("non-positive");
            }
            return new QuestionnaireScope("BUSINESS", scopeSelectionSource, questionTemplateId, templateSha256, businessId, null);
        } catch (NumberFormatException invalid) {
            throw new IllegalStateException("Questionnaire business answer does not contain a canonical identifier", invalid);
        }
    }
}
