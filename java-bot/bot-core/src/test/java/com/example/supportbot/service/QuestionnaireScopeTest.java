package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class QuestionnaireScopeTest {

    @Test
    void capturesExplicitGeneralChannelScopeAndTemplateIdentity() {
        var runtime = new PanelRuntimeConfigClient.RuntimeConfigSnapshot(8L, Map.of(), Map.of(), Map.of(),
            "GENERAL", "GENERAL_CHANNEL", "q-general", "hash");

        assertThat(QuestionnaireScope.from(runtime)).isEqualTo(
            new QuestionnaireScope("GENERAL", "GENERAL_CHANNEL", "q-general", "hash"));
    }

    @Test
    void legacyRuntimeStaysUnresolvedAndIncompleteScopeFailsClosed() {
        assertThat(QuestionnaireScope.from(new PanelRuntimeConfigClient.RuntimeConfigSnapshot(
            8L, Map.of(), Map.of(), Map.of(), null, null, null, null))).isNull();
        assertThatThrownBy(() -> QuestionnaireScope.from(new PanelRuntimeConfigClient.RuntimeConfigSnapshot(
            8L, Map.of(), Map.of(), Map.of(), null, "GENERAL_CHANNEL", "q-general", "hash")))
            .isInstanceOf(IllegalStateException.class);
    }
}
