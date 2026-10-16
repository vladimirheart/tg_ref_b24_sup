package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.config.RuntimeCoordinationProperties;
import com.example.panel.security.InternalBotApiProperties;
import com.example.panel.security.InternalBotApiRequestGuardService;
import com.example.panel.service.BusinessQuestionTemplateRegistryService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BusinessQuestionTemplateRuntimeController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({InternalBotApiRequestGuardService.class, InternalBotApiProperties.class, RuntimeCoordinationProperties.class})
@TestPropertySource(properties = "app.bots.internal-api.token=test-internal-token")
class BusinessQuestionTemplateRuntimeControllerWebMvcTest {

    @Autowired MockMvc mvc;
    @MockBean BusinessQuestionTemplateRegistryService registry;
    @MockBean MultibusinessFeatureFlags flags;

    @Test
    void scopedRuntimeRequiresTokenBothFlagsAndVerifiedSelection() throws Exception {
        String path = "/internal/api/bot/channels/10/businesses/7/runtime-config";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("X-Iguana-Bot-Api-Token", "test-internal-token"))
                .andExpect(status().isNotFound());
        verify(registry, never()).resolveExplicitChannel(10L, 7L);
        when(flags.isFoundationEnabled()).thenReturn(true);
        when(flags.isSharedChannelResolution()).thenReturn(true);
        mvc.perform(get(path).header("X-Iguana-Bot-Api-Token", "test-internal-token"))
                .andExpect(status().isNotFound());
        when(registry.resolveExplicitChannel(10L, 7L)).thenReturn(Optional.of(
                new BusinessQuestionTemplateRegistryService.ScopedRuntimeConfig(10L, 7L, "q-a",
                        "a".repeat(64), Map.of("active_template_id", "q-a",
                                "question_templates", List.of(Map.of("id", "q-a"))), Map.of(), Map.of())));
        mvc.perform(get(path).header("X-Iguana-Bot-Api-Token", "test-internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(7))
                .andExpect(jsonPath("$.questionTemplateId").value("q-a"))
                .andExpect(jsonPath("$.botSettings.question_templates.length()").value(1));
        verify(registry, times(2)).resolveExplicitChannel(10L, 7L);
    }
}
