package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UnifiedChannelQuestionnaireServiceFingerprintTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void fingerprintDoesNotDependOnMapIterationOrder() {
        Map<String, Object> idThenLabel = new LinkedHashMap<>();
        idThenLabel.put("id", "2");
        idThenLabel.put("label", "Блинбери");
        Map<String, Object> labelThenId = new LinkedHashMap<>();
        labelThenId.put("label", "Блинбери");
        labelThenId.put("id", "2");

        Map<String, Object> first = Map.of("question_flow", List.of(Map.of("options", List.of(idThenLabel))));
        Map<String, Object> second = Map.of("question_flow", List.of(Map.of("options", List.of(labelThenId))));

        assertThat(UnifiedChannelQuestionnaireService.fingerprint(mapper, first))
            .isEqualTo(UnifiedChannelQuestionnaireService.fingerprint(mapper, second));
    }
}
