package com.example.panel.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.service.KnowledgeNoteLocationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DialogKnowledgeNotesApiController.class)
@Import(DialogKnowledgeNotesApiControllerWebMvcTest.MethodSecurityConfiguration.class)
class DialogKnowledgeNotesApiControllerWebMvcTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfiguration {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KnowledgeNoteLocationService knowledgeNoteLocationService;

    @Test
    void queryNotesPreservesExactTicketIdAndOperator() throws Exception {
        String ticketId = " T/100?&%# Ю ";
        when(knowledgeNoteLocationService.listDialogNotes(ticketId, "operator")).thenReturn(List.of());

        mockMvc.perform(get("/api/dialog-ticket/notes")
                        .param("ticketId", ticketId)
                        .with(user("operator").authorities(
                                new SimpleGrantedAuthority("PAGE_DIALOGS"),
                                new SimpleGrantedAuthority("PAGE_KNOWLEDGE_BASE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.notes").isEmpty());
        verify(knowledgeNoteLocationService).listDialogNotes(ticketId, "operator");
    }

    @Test
    void legacyNotesRouteRemainsAvailableWithBothAuthorities() throws Exception {
        when(knowledgeNoteLocationService.listDialogNotes("T-100", "operator")).thenReturn(List.of());

        mockMvc.perform(get("/api/dialogs/T-100/notes")
                        .with(user("operator").authorities(
                                new SimpleGrantedAuthority("PAGE_DIALOGS"),
                                new SimpleGrantedAuthority("PAGE_KNOWLEDGE_BASE"))))
                .andExpect(status().isOk());
        verify(knowledgeNoteLocationService).listDialogNotes("T-100", "operator");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PAGE_DIALOGS", "PAGE_KNOWLEDGE_BASE"})
    void queryNotesRequiresBothAuthorities(String singleAuthority) throws Exception {
        mockMvc.perform(get("/api/dialog-ticket/notes")
                        .param("ticketId", "T-100")
                        .with(user("operator").authorities(new SimpleGrantedAuthority(singleAuthority))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(knowledgeNoteLocationService);
    }

    @Test
    void legacyNotesRouteStillRequiresKnowledgeAuthority() throws Exception {
        mockMvc.perform(get("/api/dialogs/T-100/notes")
                        .with(user("operator").authorities(new SimpleGrantedAuthority("PAGE_DIALOGS"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(knowledgeNoteLocationService);
    }

    @Test
    void queryNotesRequiresTicketId() throws Exception {
        mockMvc.perform(get("/api/dialog-ticket/notes")
                        .with(user("operator").authorities(
                                new SimpleGrantedAuthority("PAGE_DIALOGS"),
                                new SimpleGrantedAuthority("PAGE_KNOWLEDGE_BASE"))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(knowledgeNoteLocationService);
    }
}
