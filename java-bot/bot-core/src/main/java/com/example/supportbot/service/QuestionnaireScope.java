package com.example.supportbot.service;

/** Scope selected before the first question; kept with the questionnaire session. */
public record QuestionnaireScope(String scopeKind, String scopeSelectionSource,
                                 String questionTemplateId, String templateSha256,
                                 Long businessId, String catalogSha256) {

    public QuestionnaireScope(String scopeKind, String scopeSelectionSource,
            String questionTemplateId, String templateSha256) {
        this(scopeKind, scopeSelectionSource, questionTemplateId, templateSha256, null, null);
    }

    public QuestionnaireScope {
        if ("BUSINESS".equals(scopeKind)) {
            if (!"USER_SELECTION".equals(scopeSelectionSource) || businessId == null || businessId <= 0
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
        if (selected == null || selected.version() != 1 || selected.channelId() <= 0
                || !"BUSINESS".equals(selected.scopeKind())) {
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
            throw new IllegalStateException("Unsupported or incomplete scoped questionnaire runtime config");
        }
        return new QuestionnaireScope(runtime.scopeKind(), runtime.scopeSelectionSource(),
            runtime.questionTemplateId(), runtime.templateSha256());
    }
}
