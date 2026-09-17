package com.example.supportbot.max;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.supportbot.settings.dto.PresetReference;
import com.example.supportbot.settings.dto.QuestionFlowItemDto;
import com.example.supportbot.settings.dto.QuestionRouteDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class MaxIncidentFlowSupportTest {

    @Test
    void normalizesCoreLocationQuestionsInFixedOrderAndPreservesMetadata() {
        QuestionRouteDto cityRoute = new QuestionRouteDto("city-1", "problem");

        QuestionFlowItemDto city = new QuestionFlowItemDto(
                "legacy-city", "preset", "Выберите город", 40,
                new PresetReference("locations", "city"), List.of("City X")
        );
        city.setBindingKey("city_binding");
        city.setIncludeInDashboard(false);
        city.setRoutes(List.of(cityRoute));

        QuestionFlowItemDto businessLater = new QuestionFlowItemDto(
                "business-later", "preset", "Поздний бизнес", 30,
                new PresetReference("locations", "business"), List.of()
        );
        QuestionFlowItemDto businessEarlier = new QuestionFlowItemDto(
                "business-earlier", "preset", "Ранний бизнес", 10,
                new PresetReference("locations", "business"), List.of("Business X")
        );
        businessEarlier.setBindingKey("business_binding");

        QuestionFlowItemDto ignored = new QuestionFlowItemDto(
                "custom", "preset", "Не location", 5,
                new PresetReference("custom", "business"), List.of()
        );

        List<QuestionFlowItemDto> normalized = MaxIncidentFlowSupport.normalize(
                List.of(city, businessLater, ignored, businessEarlier)
        );

        assertThat(normalized).hasSize(5);
        assertThat(normalized).extracting(QuestionFlowItemDto::getId)
                .containsExactly("business", "location_type", "city", "location_name", "problem");
        assertThat(normalized).extracting(QuestionFlowItemDto::getOrder)
                .containsExactly(1, 2, 3, 4, 5);

        QuestionFlowItemDto business = normalized.get(0);
        assertThat(business.getText()).isEqualTo("Ранний бизнес");
        assertThat(business.getExcludedOptions()).containsExactly("Business X");
        assertThat(business.getBindingKey()).isEqualTo("business_binding");
        assertThat(business.getPreset().group()).isEqualTo("locations");
        assertThat(business.getPreset().field()).isEqualTo("business");

        assertThat(normalized.get(1).getText()).isEqualTo("Тип бизнеса");

        QuestionFlowItemDto normalizedCity = normalized.get(2);
        assertThat(normalizedCity.getText()).isEqualTo("Выберите город");
        assertThat(normalizedCity.getExcludedOptions()).containsExactly("City X");
        assertThat(normalizedCity.getBindingKey()).isEqualTo("city_binding");
        assertThat(normalizedCity.getIncludeInDashboard()).isFalse();
        assertThat(normalizedCity.getRoutes()).containsExactly(cityRoute);

        assertThat(normalized.get(3).getText()).isEqualTo("Локация");
        assertThat(normalized.get(4).getType()).isEqualTo("text");
        assertThat(normalized.get(4).getText()).isEqualTo("Опишите проблему");
        assertThat(normalized.get(4).getPreset()).isNull();
    }

    @Test
    void blankConfiguredPromptFallsBackToDefaultAndNullExclusionsBecomeEmpty() {
        QuestionFlowItemDto locationType = new QuestionFlowItemDto(
                "legacy-type", "preset", "   ", 1,
                new PresetReference("locations", "location_type"), null
        );

        List<QuestionFlowItemDto> normalized = MaxIncidentFlowSupport.normalize(List.of(locationType));

        assertThat(normalized.get(1).getText()).isEqualTo("Тип бизнеса");
        assertThat(normalized.get(1).getExcludedOptions()).isEmpty();
        assertThat(normalized.get(0).getText()).isEqualTo("Бизнес");
        assertThat(normalized.get(2).getText()).isEqualTo("Город");
        assertThat(normalized.get(3).getText()).isEqualTo("Локация");
    }
}
