package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class QuestionnaireScopeTest {

    @Test
    void selectedBusinessKeepsTheStableOwnerAndBothFingerprintsAcrossJson() throws Exception {
        var selected = new PanelRuntimeConfigClient.SelectedQuestionnaire(1, 8L, "BUSINESS", "USER_SELECTION",
            7L, "q-a", "a".repeat(64), "c".repeat(64), Map.of(), Map.of(), Map.of());
        var scope = QuestionnaireScope.fromSelection(selected);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        assertThat(mapper.readValue(mapper.writeValueAsString(scope), QuestionnaireScope.class)).isEqualTo(scope);
        assertThat(scope.businessId()).isEqualTo(7L);
        assertThat(scope.catalogSha256()).isEqualTo("c".repeat(64));
        assertThat(mapper.readValue("{\"scopeKind\":\"GENERAL\",\"scopeSelectionSource\":\"GENERAL_CHANNEL\","
            + "\"questionTemplateId\":\"q-general\",\"templateSha256\":\"hash\"}", QuestionnaireScope.class))
            .isEqualTo(new QuestionnaireScope("GENERAL", "GENERAL_CHANNEL", "q-general", "hash"));
    }

    @Test
    void incompleteBusinessSessionCannotBeRestoredAsAnUnresolvedOrGeneralSession() {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String[] invalid : new String[][] {
            {"BUSINESS", "USER_SELECTION", "q-a", "a".repeat(64), "7", "null"},
            {"BUSINESS", "USER_SELECTION", "q-a", "a".repeat(64), "null", "\"" + "c".repeat(64) + "\""},
            {"GENERAL", "USER_SELECTION", "q-a", "a".repeat(64), "7", "\"" + "c".repeat(64) + "\""}
        }) {
            String json=String.format("{\"scopeKind\":\"%s\",\"scopeSelectionSource\":\"%s\","
                + "\"questionTemplateId\":\"%s\",\"templateSha256\":\"%s\",\"businessId\":%s,\"catalogSha256\":%s}",
                (Object[])invalid);
            assertThatThrownBy(() -> mapper.readValue(json, QuestionnaireScope.class))
                .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
        }
    }

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
