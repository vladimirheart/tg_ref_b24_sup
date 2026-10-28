package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.service.BusinessTicketCurrentAssignmentReadService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BusinessTicketCurrentAssignmentReadController.class)
@Import(BusinessTicketCurrentAssignmentReadControllerWebMvcTest.MethodSecurity.class)
class BusinessTicketCurrentAssignmentReadControllerWebMvcTest {
    private static final String PUBLIC="a".repeat(32),PATH="/api/business-scope/tickets/"+PUBLIC+"/assignments/current";
    @TestConfiguration @EnableMethodSecurity static class MethodSecurity { }
    @Autowired MockMvc mvc;
    @MockBean BusinessTicketCurrentAssignmentReadService service;
    @MockBean MultibusinessFeatureFlags flags;
    private PanelUserPrincipal principal(String... authorities) {
        return new PanelUserPrincipal(9007199254740993L," viewer ","",true,
                java.util.Arrays.stream(authorities).<org.springframework.security.core.GrantedAuthority>map(value->()->value).toList());
    }
    @Test void currentDtoIsPrivateAndBigintPrecisionIsPreserved() throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);
        var state=new BusinessTicketCurrentAssignmentReadService.State(
                new BusinessTicketCurrentAssignmentReadService.Owner("9007199254740993"," raw ",PUBLIC,"9223372036854775807"),
                Long.MAX_VALUE,"ASSIGNED",new BusinessTicketCurrentAssignmentReadService.Assignee(Long.MAX_VALUE," target ","MANUAL",OffsetDateTime.parse("2026-10-27T10:00:00Z")));
        when(service.current(eq(PUBLIC),any())).thenReturn(Optional.of(state));
        mvc.perform(get(PATH).param("readerId","99").param("username","other").with(user(principal("PAGE_DIALOGS"))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(header().string("X-Content-Type-Options","nosniff"))
                .andExpect(jsonPath("$.owner.userId").value("9007199254740993"))
                .andExpect(jsonPath("$.revision").value(Long.toString(Long.MAX_VALUE)))
                .andExpect(jsonPath("$.assignee.id").value(Long.toString(Long.MAX_VALUE)))
                .andExpect(jsonPath("$.assignee.username").value(" target "))
                .andExpect(jsonPath("$.status").value("ASSIGNED"));
        verify(service).current(eq(PUBLIC),argThat(auth->auth.getPrincipal() instanceof PanelUserPrincipal actor
                && actor.getUserId()==9007199254740993L && actor.getUsername().equals(" viewer ")));
    }
    @ParameterizedTest @ValueSource(strings={"UNASSIGNED","UNVERIFIED"})
    void explicitNonAssignedStatesDoNotInventAnAssignee(String status) throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);
        when(service.current(eq(PUBLIC),any())).thenReturn(Optional.of(new BusinessTicketCurrentAssignmentReadService.State(
                new BusinessTicketCurrentAssignmentReadService.Owner("42","raw",PUBLIC,"11"),0,status,null)));
        mvc.perform(get(PATH).with(user(principal("PAGE_DIALOGS")))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(status)).andExpect(jsonPath("$.assignee").isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"PAGE_SETTINGS","ROLE_ADMIN","ALL_BUSINESSES","PAGE_USERS"})
    void anotherPageOrGlobalRoleDoesNotReplaceDialogsPage(String authority) throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);
        mvc.perform(get(PATH).with(user(principal(authority)))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void currentScopeDenialIsNotFoundAndStorageFailureIs503() throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);
        when(service.current(eq(PUBLIC),any())).thenReturn(Optional.empty()).thenThrow(
                new BusinessTicketCurrentAssignmentReadService.ReadUnavailableException(new IllegalStateException("private SQL")));
        mvc.perform(get(PATH).with(user(principal("PAGE_DIALOGS")))).andExpect(status().isNotFound());
        mvc.perform(get(PATH).with(user(principal("PAGE_DIALOGS")))).andExpect(status().isServiceUnavailable());
    }
    @Test void featureOffDoesNotReadStorage() throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(false);
        mvc.perform(get(PATH).with(user(principal("PAGE_DIALOGS")))).andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }
}
