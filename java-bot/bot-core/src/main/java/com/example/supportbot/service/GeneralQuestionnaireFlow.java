package com.example.supportbot.service;

import com.example.supportbot.settings.dto.QuestionFlowItemDto;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Keeps the administrator's general-only question set and order intact. */
public final class GeneralQuestionnaireFlow {

    private GeneralQuestionnaireFlow() {
    }

    public static List<QuestionFlowItemDto> from(List<QuestionFlowItemDto> configured) {
        if (configured == null || configured.isEmpty() || configured.stream().anyMatch(item -> item == null)) {
            throw new IllegalStateException("General questionnaire must contain valid questions");
        }
        List<QuestionFlowItemDto> flow = new ArrayList<>(configured);
        flow.sort(Comparator.comparingInt(QuestionFlowItemDto::getOrder));
        return flow;
    }
}
