package com.example.panel.controller;

import com.example.panel.service.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DialogOperatorCollaborationController.class)
@Import(DialogOperatorCollaborationControllerWebMvcTest.Security.class)
class DialogOperatorCollaborationControllerWebMvcTest {
    @TestConfiguration @EnableMethodSecurity static class Security {}
    @Autowired MockMvc mvc;
    @MockBean DialogOperatorCollaborationService collaboration;
    @MockBean BusinessTicketAssignmentService assignment;
    @MockBean UnresolvedTicketBusinessSelectionService selection;
    final String pub="a".repeat(32),base="/api/operator-dialogs/"+pub;
    final UiEventTicketScope scope=new UiEventTicketScope(-9007199254740993L," raw ",pub,"BUSINESS",11L);
    DialogOperatorCollaborationService.Context state;
    String body(String extra) {
        return "{\"userId\":\"-9007199254740993\",\"ticketId\":\" raw \",\"ticketPublicId\":\""+pub
            +"\",\"scopeKind\":\"BUSINESS\",\"businessId\":\"11\",\"revision\":\"7\""+extra+"}";
    }
    @BeforeEach void setup() {
        state=new DialogOperatorCollaborationService.Context(scope,7,"open",true);
        when(collaboration.find(eq(pub),any())).thenReturn(Optional.of(state));
        when(collaboration.messages(eq(pub),any(),eq(0L))).thenReturn(List.of());
        when(assignment.find(eq(pub),any())).thenReturn(Optional.of(new BusinessTicketAssignmentService.State(scope,7,"other",true)));
        when(assignment.change(eq(scope),any(),eq(7L),isNull(),eq(true))).thenReturn(true);
        when(collaboration.transfer(eq(state),eq(22L),any())).thenReturn(true);
        when(collaboration.post(eq(state),any(),any(),eq("Internal"))).thenReturn(true);
        when(selection.availableBusinesses(any())).thenReturn(List.of(new UnresolvedTicketBusinessSelectionService.BusinessOption(9007199254740995L,"Target")));
    }
    @Test void readsReturnExactStringOwnerAndBoundedOperatorMessages() throws Exception {
        mvc.perform(get(base+"/internal-messages").with(user("viewer").authorities(()->"PAGE_DIALOGS")))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.owner.userId").value("-9007199254740993"))
            .andExpect(jsonPath("$.owner.ticketId").value(" raw ")).andExpect(jsonPath("$.messages").isArray());
        mvc.perform(get(base+"/business").with(user("Operator").authorities(()->"PAGE_DIALOGS")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.businesses[0].id").value("9007199254740995"));
    }
    @ParameterizedTest @ValueSource(strings={"take","business","internal-messages"})
    void mutationsRequireCsrfAndDialogs(String action) throws Exception {
        mvc.perform(post(base+"/"+action).with(user("Operator").authorities(()->"PAGE_DIALOGS"))
            .contentType(MediaType.APPLICATION_JSON).content(body(""))).andExpect(status().isForbidden());
        mvc.perform(post(base+"/"+action).with(user("Operator").roles("USER")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body(""))).andExpect(status().isForbidden());
        verifyNoInteractions(collaboration,assignment,selection);
    }
    @Test void takeUsesBoundAuthenticationAndCompleteOwnerRevision() throws Exception {
        mvc.perform(post(base+"/take").with(user("Operator").authorities(()->"PAGE_DIALOGS")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body("")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.responsible").value("Operator"));
        verify(assignment).change(eq(scope),any(Authentication.class),eq(7L),isNull(),eq(true));
        mvc.perform(post(base+"/take").with(user("Operator").authorities(()->"PAGE_DIALOGS")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body("").replace("\"7\"","\"6\""))).andExpect(status().isConflict());
    }
    @Test void transferAndInternalPostUseDifferentCommands() throws Exception {
        mvc.perform(post(base+"/business").with(user("Operator").authorities(()->"PAGE_DIALOGS")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body(",\"businessTargetId\":\"22\""))).andExpect(status().isOk());
        mvc.perform(post(base+"/internal-messages").with(user("Operator").authorities(()->"PAGE_DIALOGS")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body(",\"requestId\":\""+UUID.randomUUID()+"\",\"body\":\"Internal\"")))
            .andExpect(status().isOk());
        verify(collaboration).transfer(eq(state),eq(22L),any());
        verify(collaboration).post(eq(state),any(),any(UUID.class),eq("Internal"));
    }
    @Test void forgedOwnerDoesNotReachEitherMutation() throws Exception {
        mvc.perform(post(base+"/business").with(user("Operator").authorities(()->"PAGE_DIALOGS")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body(",\"businessTargetId\":\"22\"").replace(" raw ","raw")))
            .andExpect(status().isConflict());
        verify(collaboration,never()).transfer(any(),anyLong(),any());
    }
    @Test void invalidRequestAndCursorAreRejected() throws Exception {
        mvc.perform(get(base+"/internal-messages").param("before","-1").with(user("Operator").authorities(()->"PAGE_DIALOGS")))
            .andExpect(status().isBadRequest());
        mvc.perform(post(base+"/internal-messages").with(user("Operator").authorities(()->"PAGE_DIALOGS")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body(",\"requestId\":\"bad\",\"body\":\"Internal\"")))
            .andExpect(status().isBadRequest());
    }
    @Test void revokedReadDoesNotExposeMessagesOrMutation() throws Exception {
        when(collaboration.find(eq(pub),any())).thenReturn(Optional.empty());
        mvc.perform(get(base+"/internal-messages").with(user("Operator").authorities(()->"PAGE_DIALOGS")))
            .andExpect(status().isNotFound());
        verify(collaboration,never()).messages(any(),any(),anyLong());
    }
    @Test void readAcknowledgementUsesExactOwnerAndReadPermissionRatherThanOperate() throws Exception {
        state=new DialogOperatorCollaborationService.Context(scope,7,"open",false);
        when(collaboration.find(eq(pub),any())).thenReturn(Optional.of(state));
        when(collaboration.markRead(eq(state),any(),eq(99L))).thenReturn(true);
        mvc.perform(post(base+"/internal-messages/read").with(user("viewer").authorities(()->"PAGE_DIALOGS")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body(",\"lastReadId\":\"99\"")))
            .andExpect(status().isOk());
        mvc.perform(post(base+"/internal-messages/read").with(user("viewer").authorities(()->"PAGE_DIALOGS"))
            .contentType(MediaType.APPLICATION_JSON).content(body(",\"lastReadId\":\"99\""))).andExpect(status().isForbidden());
    }
}

