package com.example.panel.controller;

import com.example.panel.service.DialogOperatorCollaborationService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DialogInternalUnreadController.class)
@Import(DialogInternalUnreadControllerWebMvcTest.Security.class)
class DialogInternalUnreadControllerWebMvcTest {
    @TestConfiguration @EnableMethodSecurity static class Security {}
    @Autowired MockMvc mvc;
    @MockBean DialogOperatorCollaborationService service;
    final String pub="a".repeat(32),path="/api/operator-dialog-unread";
    @Test void batchIsBoundedAndRequiresPageAndCsrf() throws Exception {
        String body="{\"ticketPublicIds\":[\""+pub+"\"]}";
        mvc.perform(post(path).with(user("viewer").authorities(()->"PAGE_DIALOGS")).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mvc.perform(post(path).with(user("viewer")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mvc.perform(post(path).with(user("viewer").authorities(()->"PAGE_DIALOGS")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"ticketPublicIds\":[\"bad\"]}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void authorizedReadReturnsOnlyScopedCountsAndStringIds() throws Exception {
        when(service.unread(eq(List.of(pub)),any())).thenReturn(List.of(new DialogOperatorCollaborationService.Unread(pub,"4","9007199254740999")));
        mvc.perform(post(path).with(user("viewer").authorities(()->"PAGE_DIALOGS")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"ticketPublicIds\":[\""+pub+"\"]}"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.items[0].unreadCount").value("4")).andExpect(jsonPath("$.items[0].latestId").value("9007199254740999"));
    }
}
