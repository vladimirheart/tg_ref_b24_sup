package com.example.supportbot.max;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class MaxFeedbackInputSupportTest {

    @Test
    void numericCandidatePreservesTrimAndDigitsGate() {
        assertThat(MaxFeedbackInputSupport.normalizeNumericRating(null)).isNull();
        assertThat(MaxFeedbackInputSupport.normalizeNumericRating("   ")).isNull();
        assertThat(MaxFeedbackInputSupport.normalizeNumericRating("  5  ")).isEqualTo("5");
        assertThat(MaxFeedbackInputSupport.normalizeNumericRating("5a")).isNull();
    }

    @Test
    void allowedRatingUsesExactConfiguredStringMembership() {
        Set<String> allowed = Set.of("1", "5");
        assertThat(MaxFeedbackInputSupport.isAllowedRating("5", allowed)).isTrue();
        assertThat(MaxFeedbackInputSupport.isAllowedRating("05", allowed)).isFalse();
    }

    @Test
    void parseRatingPreservesIntegerParsing() {
        assertThat(MaxFeedbackInputSupport.parseRating("05")).isEqualTo(5);
        assertThat(MaxFeedbackInputSupport.parseRating("10")).isEqualTo(10);
    }

    @Test
    void invalidPromptPreservesLegacyWording() {
        assertThat(MaxFeedbackInputSupport.buildInvalidRatingPrompt(5))
                .isEqualTo("Отправьте число от 1 до 5");
    }
}
