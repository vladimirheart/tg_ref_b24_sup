package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.config.RuntimeCoordinationProperties;
import com.example.panel.security.InternalBotApiProperties;
import com.example.panel.security.InternalBotApiRequestGuardService;
import com.example.panel.service.GeneralChannelQuestionTemplateService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GeneralChannelQuestionTemplateRuntimeController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({InternalBotApiRequestGuardService.class, InternalBotApiProperties.class, RuntimeCoordinationProperties.class})
@TestPropertySource(properties = "app.bots.internal-api.token=test-internal-token")
class GeneralChannelQuestionTemplateRuntimeControllerWebMvcTest {

    @Autowired MockMvc mvc;
    @MockBean GeneralChannelQuestionTemplateService service;
    @MockBean MultibusinessFeatureFlags flags;

    @Test
    void runtimeRequiresInternalTokenFlagAndVerifiedChannelTemplate() throws Exception {
        String path = "/internal/api/bot/channels/10/general/runtime-config";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("X-Iguana-Bot-Api-Token", "test-internal-token"))
                .andExpect(status().isNotFound());
        verify(service, never()).resolve(10L);
        when(flags.isFoundationEnabled()).thenReturn(true);
        when(flags.isGeneralChannelResolution()).thenReturn(true);
        mvc.perform(get(path).header("X-Iguana-Bot-Api-Token", "test-internal-token"))
                .andExpect(status().isNotFound());
        when(service.resolve(10L)).thenReturn(Optional.of(
                new GeneralChannelQuestionTemplateService.RuntimeConfig(10L, "GENERAL", "GENERAL_CHANNEL",
                    "q-general", "a".repeat(64),
                    Map.of("active_template_id", "q-general", "question_templates",
                        List.of(Map.of("id", "q-general"))), Map.of(), Map.of())));
        mvc.perform(get(path).header("X-Iguana-Bot-Api-Token", "test-internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopeKind").value("GENERAL"))
                .andExpect(jsonPath("$.scopeSelectionSource").value("GENERAL_CHANNEL"))
                .andExpect(jsonPath("$.botSettings.question_templates.length()").value(1));
        verify(service, org.mockito.Mockito.times(2)).resolve(10L);
    }
}
