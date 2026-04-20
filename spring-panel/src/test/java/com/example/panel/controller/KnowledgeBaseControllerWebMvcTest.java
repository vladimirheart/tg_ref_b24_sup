package com.example.panel.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.panel.model.knowledge.KnowledgeBaseNotionConfigForm;
import com.example.panel.service.KnowledgeBaseNotionService;
import com.example.panel.service.KnowledgeBaseService;
import com.example.panel.service.NavigationService;
import com.example.panel.service.NotificationService;
import com.example.panel.service.PermissionService;
import com.example.panel.storage.AttachmentService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(KnowledgeBaseController.class)
@AutoConfigureMockMvc
class KnowledgeBaseControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PermissionService permissionService;

    @MockBean
    private KnowledgeBaseService knowledgeBaseService;

    @MockBean
    private KnowledgeBaseNotionService knowledgeBaseNotionService;

    @MockBean
    private AttachmentService attachmentService;

    @MockBean
    private NavigationService navigationService;

    @MockBean
    private NotificationService notificationService;

    @Test
    void knowledgeListIncludesUiHeadBootstrapAndExplicitPagePreset() throws Exception {
        doNothing().when(navigationService).enrich(any(), any());
        when(knowledgeBaseService.listArticles()).thenReturn(List.of());
        when(permissionService.hasAuthority(any(), org.mockito.ArgumentMatchers.eq("PAGE_KNOWLEDGE_BASE"))).thenReturn(true);
        when(knowledgeBaseNotionService.buildForm()).thenReturn(new KnowledgeBaseNotionConfigForm());
        when(knowledgeBaseNotionService.hasSavedToken()).thenReturn(false);

        mockMvc.perform(get("/knowledge-base").with(user("operator").authorities(() -> "PAGE_KNOWLEDGE_BASE")))
            .andExpect(status().isOk())
            .andExpect(view().name("knowledge/list"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-preferences.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/theme.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-config.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-ui-page=\"knowledge\"")));
    }
}
