package com.example.supportbot.service;

/** Scope selected before the first question; kept with the questionnaire session. */
public record QuestionnaireScope(String scopeKind, String scopeSelectionSource,
                                 String questionTemplateId, String templateSha256) {

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
