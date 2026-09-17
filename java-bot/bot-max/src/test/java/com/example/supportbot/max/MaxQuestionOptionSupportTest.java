package com.example.supportbot.max;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.supportbot.settings.dto.QuestionFlowItemDto;
import com.example.supportbot.settings.dto.QuestionOptionDto;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MaxQuestionOptionSupportTest {

    @Test
    void selectOptionsTrimLabelsAndDropBlankValues() {
        QuestionFlowItemDto select = new QuestionFlowItemDto();
        select.setOptions(List.of(
                new QuestionOptionDto("a", "  Alpha  "),
                new QuestionOptionDto("b", "   "),
                new QuestionOptionDto("c", "Beta")
        ));

        assertThat(MaxQuestionOptionSupport.resolveSelectOptions(select))
                .containsExactly("Alpha", "Beta");
    }

    @Test
    void locationOptionsFollowBusinessTypeCityHierarchy() {
        Map<String, Object> cityNode = new LinkedHashMap<>();
        cityNode.put("Moscow", List.of("Location B", "Location A"));
        Map<String, Object> typeNode = new LinkedHashMap<>();
        typeNode.put("Restaurant", cityNode);
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("Beta", Map.of());
        tree.put("Alpha", typeNode);

        assertThat(MaxQuestionOptionSupport.resolveLocationOptions("business", Map.of(), tree))
                .containsExactly("Alpha", "Beta");
        assertThat(MaxQuestionOptionSupport.resolveLocationOptions(
                "location_type", Map.of("business", "Alpha"), tree))
                .containsExactly("Restaurant");
        assertThat(MaxQuestionOptionSupport.resolveLocationOptions(
                "city", Map.of("business", "Alpha", "location_type", "Restaurant"), tree))
                .containsExactly("Moscow");
        assertThat(MaxQuestionOptionSupport.resolveLocationOptions(
                "location_name",
                Map.of("business", "Alpha", "location_type", "Restaurant", "city", "Moscow"),
                tree))
                .containsExactly("Location B", "Location A");
    }

    @Test
    void presetDefinitionOptionsReadNestedFieldOptions() {
        Map<String, Object> definitions = Map.of(
                "custom", Map.of(
                        "fields", Map.of(
                                "priority", Map.of("options", List.of("Low", "High"))
                        )
                )
        );

        assertThat(MaxQuestionOptionSupport.resolvePresetDefinitionOptions(
                "custom", "priority", definitions))
                .containsExactly("Low", "High");
    }

    @Test
    void excludedOptionsAreRemovedWithoutReorderingRemainingValues() {
        assertThat(MaxQuestionOptionSupport.applyExcludedOptions(
                List.of("A", "B", "C", "D"),
                List.of("B", "D")))
                .containsExactly("A", "C");

        assertThat(MaxQuestionOptionSupport.applyExcludedOptions(List.of("A", "B"), null))
                .containsExactly("A", "B");
    }
}
