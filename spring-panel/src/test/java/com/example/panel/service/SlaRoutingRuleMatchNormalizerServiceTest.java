package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaRoutingRuleMatchNormalizerServiceTest {

    private final SlaRoutingRuleMatchNormalizerService service =
            new SlaRoutingRuleMatchNormalizerService(new SlaRoutingRuleScalarParserService());

    @Test
    void parsesCandidateCategoriesAndAssigneePoolWithNormalization() {
        assertEquals(Set.of("billing", "vip"), service.parseCandidateCategories("Billing, VIP"));
        assertEquals(List.of("duty_a", "duty_b"), service.parseAssigneePool(List.of("duty_a", "duty_b", "duty_a")));
    }

    @Test
    void normalizesRuleLayerAndSlaStates() {
        assertEquals("domain", service.normalizeRuleLayer("team"));
        assertEquals(Set.of("breached", "at_risk"), service.parseRuleSlaStates("expired", List.of("warning", "broken")));
        assertTrue(service.parseRuleRequestPrefixes("INC", "SRV, BUG").contains("inc"));
    }
}
