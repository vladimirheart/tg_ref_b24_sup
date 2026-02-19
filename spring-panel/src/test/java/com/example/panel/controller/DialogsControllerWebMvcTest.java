package com.example.panel.controller;

import com.example.panel.model.dialog.DialogSummary;
import com.example.panel.service.DialogService;
import com.example.panel.service.NavigationService;
import com.example.panel.service.SharedConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(DialogsController.class)
@AutoConfigureMockMvc(addFilters = false)
class DialogsControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NavigationService navigationService;

    @MockBean
    private DialogService dialogService;

    @MockBean
    private SharedConfigService sharedConfigService;

    @Test
    void dialogsTicketRouteProvidesInitialDialogTicketId() throws Exception {
        doNothing().when(navigationService).enrich(any(), any());
        when(dialogService.loadSummary()).thenReturn(new DialogSummary(0, 0, 0, Collections.emptyList()));
        when(dialogService.loadDialogs(anyString())).thenReturn(Collections.emptyList());
        when(sharedConfigService.loadSettings()).thenReturn(Map.of());

        mockMvc.perform(get("/dialogs/T-123").with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(view().name("dialogs/index"))
                .andExpect(model().attribute("initialDialogTicketId", "T-123"));
    }
}
