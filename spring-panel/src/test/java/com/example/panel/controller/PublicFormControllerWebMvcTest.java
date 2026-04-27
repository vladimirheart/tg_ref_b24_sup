package com.example.panel.controller;

import com.example.panel.model.publicform.PublicFormConfig;
import com.example.panel.service.PublicFormService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(PublicFormController.class)
@AutoConfigureMockMvc(addFilters = false)
class PublicFormControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PublicFormService publicFormService;

    @Test
    void publicFormPageIncludesUiHeadBootstrapAndExplicitPagePreset() throws Exception {
        when(publicFormService.loadConfigRaw("support-main")).thenReturn(Optional.of(
                new PublicFormConfig(
                        12L,
                        "support-main",
                        "Support Main",
                        1,
                        true,
                        false,
                        404,
                        "Спасибо, мы свяжемся с вами.",
                        15,
                        List.of()
                )));

        mockMvc.perform(get("/public/forms/support-main"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/form"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-preferences.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/theme.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-config.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-ui-page=\"public\"")));
    }

    @Test
    void publicFormPageUsesTokenFallbackFromDialogParam() throws Exception {
        when(publicFormService.loadConfigRaw("support-dialog")).thenReturn(Optional.of(
                new PublicFormConfig(
                        14L,
                        "support-dialog",
                        "Support Dialog",
                        1,
                        true,
                        false,
                        404,
                        null,
                        null,
                        List.of()
                )));

        mockMvc.perform(get("/public/forms/support-dialog").param("dialog", "dlg-token-1"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/form"))
                .andExpect(model().attribute("initialToken", "dlg-token-1"))
                .andExpect(model().attribute("channelRef", "support-dialog"));
    }

    @Test
    void publicFormPagePrefersExplicitTokenOverDialogFallback() throws Exception {
        when(publicFormService.loadConfigRaw("support-explicit")).thenReturn(Optional.of(
                new PublicFormConfig(
                        16L,
                        "support-explicit",
                        "Support Explicit",
                        1,
                        true,
                        false,
                        404,
                        null,
                        null,
                        List.of()
                )));

        mockMvc.perform(get("/public/forms/support-explicit")
                        .param("token", "token-123")
                        .param("dialog", "dialog-456"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/form"))
                .andExpect(model().attribute("initialToken", "token-123"));
    }

    @Test
    void publicFormPageReturnsNotFoundForUnknownChannel() throws Exception {
        when(publicFormService.loadConfigRaw("missing-form")).thenReturn(Optional.empty());

        mockMvc.perform(get("/public/forms/missing-form"))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicFormPageUsesConfiguredDisabledStatus() throws Exception {
        when(publicFormService.loadConfigRaw("disabled-form")).thenReturn(Optional.of(
                new PublicFormConfig(
                        15L,
                        "disabled-form",
                        "Disabled Form",
                        1,
                        false,
                        false,
                        410,
                        null,
                        null,
                        List.of()
                )));

        mockMvc.perform(get("/public/forms/disabled-form"))
                .andExpect(status().isGone());
    }

    @Test
    void publicFormPageFallsBackToNotFoundWhenDisabledStatusIsInvalid() throws Exception {
        when(publicFormService.loadConfigRaw("invalid-disabled")).thenReturn(Optional.of(
                new PublicFormConfig(
                        17L,
                        "invalid-disabled",
                        "Invalid Disabled",
                        1,
                        false,
                        false,
                        999,
                        null,
                        null,
                        List.of()
                )));

        mockMvc.perform(get("/public/forms/invalid-disabled"))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicFormPagePopulatesChannelMetadataInModel() throws Exception {
        when(publicFormService.loadConfigRaw("meta-form")).thenReturn(Optional.of(
                new PublicFormConfig(
                        18L,
                        "meta-form",
                        "Meta Form",
                        1,
                        true,
                        false,
                        404,
                        null,
                        null,
                        List.of()
                )));

        mockMvc.perform(get("/public/forms/meta-form"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("channelId", 18L))
                .andExpect(model().attribute("channelRef", "meta-form"))
                .andExpect(model().attribute("channelName", "Meta Form"));
    }
}
