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
}
