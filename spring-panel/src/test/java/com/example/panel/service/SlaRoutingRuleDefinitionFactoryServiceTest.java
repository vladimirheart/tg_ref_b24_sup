package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaRoutingRuleDefinitionFactoryServiceTest {

    private final SlaRoutingRuleDefinitionFactoryService service = new SlaRoutingRuleDefinitionFactoryService();

    @Test
    void buildDefinitionNormalizesMetadataAndMatchers() {
        SlaRoutingRuleTypes.AutoAssignRuleDefinition definition = service.buildDefinition(Map.of(
                "name", "telegram_hot",
                "match_channel", "Telegram",
                "match_categories", List.of("Billing", "VIP"),
                "assign_to_pool", List.of("duty_a", "duty_b", "duty_a"),
                "layer", "team",
                "owner", "ops.lead",
                "reviewed_at", "2026-05-01T10:00:00Z"
        ), 1);

        assertNotNull(definition);
        assertEquals("telegram_hot", definition.ruleId());
        assertEquals("domain", definition.layer());
        assertEquals("ops.lead", definition.owner());
        assertEquals(List.of("duty_a", "duty_b"), definition.rule().assigneePool());
        assertTrue(definition.rule().categories().contains("billing"));
        assertTrue(definition.rule().categories().contains("vip"));
    }

    @Test
    void buildDefinitionSkipsRuleWithoutRoutingTarget() {
        assertNull(service.buildDefinition(Map.of("match_channel", "telegram"), 2));
    }
}
