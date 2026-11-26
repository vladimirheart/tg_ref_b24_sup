package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.BusinessTicketAssignmentService;
import com.example.panel.service.BusinessTicketParticipantService;
import com.example.panel.service.UiEventTicketScope;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

@WebMvcTest(BusinessTicketParticipantManagementController.class)
@Import(BusinessTicketParticipantManagementControllerWebMvcTest.MethodSecurity.class)
class BusinessTicketParticipantManagementControllerWebMvcTest {
    private static final String PUBLIC = "a".repeat(32), PATH = "/api/business-scope/tickets/" + PUBLIC + "/participants";
    private final UiEventTicketScope scope = new UiEventTicketScope(9007199254740993L, " raw ", PUBLIC, "BUSINESS", 11L);
    @TestConfiguration @EnableMethodSecurity static class MethodSecurity { }
    @Autowired MockMvc mvc;
    @MockBean BusinessTicketAssignmentService assignments;
    @MockBean BusinessTicketParticipantService participants;
    @MockBean MultibusinessFeatureFlags flags;

    @BeforeEach void setup() {
        when(flags.isFoundationEnabled()).thenReturn(true);
        state(true);
        when(participants.revision(eq(scope), any(Authentication.class))).thenReturn(Optional.of(7L));
    }
    private void state(boolean canManage) {
        when(assignments.find(eq(PUBLIC), any(Authentication.class)))
            .thenReturn(Optional.of(new BusinessTicketAssignmentService.State(scope, 3L, "owner", canManage)));
    }
    private String body(String revision, String member) {
        return "{\"userId\":\"9007199254740993\",\"ticketId\":\" raw \",\"ticketPublicId\":\"" + PUBLIC
            + "\",\"businessId\":\"11\",\"revision\":\"" + revision + "\",\"memberId\":\"" + member + "\"}";
    }
    @Test void managementUsesScopedCandidatesAndStringIds() throws Exception {
        when(participants.candidates(eq(scope), any(Authentication.class)))
            .thenReturn(List.of(new BusinessTicketParticipantService.Candidate(9007199254740995L, "member")));
        mvc.perform(get(PATH + "/management").with(user("operator").authorities(() -> "PAGE_DIALOGS")))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.owner.userId").value("9007199254740993"))
            .andExpect(jsonPath("$.owner.ticketId").value(" raw "))
            .andExpect(jsonPath("$.revision").value("7"))
            .andExpect(jsonPath("$.canManage").value(true))
            .andExpect(jsonPath("$.candidates[0].userId").value("9007199254740995"));
    }
    @Test void readOnlyUserSeesRosterManagementWithoutCandidateEnumerationOrWrites() throws Exception {
        state(false);
        mvc.perform(get(PATH + "/management").with(user("viewer").authorities(() -> "PAGE_DIALOGS")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.canManage").value(false)).andExpect(jsonPath("$.candidates").isEmpty());
        mvc.perform(post(PATH).with(user("viewer").authorities(() -> "PAGE_DIALOGS")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body("7", "42"))).andExpect(status().isForbidden());
        verify(participants, never()).candidates(any(), any()); verify(participants, never()).change(any(), any(), anyLong(), anyLong(), anyBoolean());
    }
    @Test void addAndRemoveDelegateExactOwnerRevisionAndAuthentication() throws Exception {
        when(participants.change(eq(scope), any(Authentication.class), eq(7L), eq(9007199254740995L), anyBoolean())).thenReturn(true);
        mvc.perform(post(PATH).with(user("operator").authorities(() -> "PAGE_DIALOGS")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body("7", "9007199254740995")))
            .andExpect(status().isNoContent()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(delete(PATH).with(user("operator").authorities(() -> "PAGE_DIALOGS")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body("7", "9007199254740995"))).andExpect(status().isNoContent());
        for (boolean add : List.of(true, false)) verify(participants).change(eq(scope), argThat(auth -> "operator".equals(auth.getName())),
            eq(7L), eq(9007199254740995L), eq(add));
    }
    @Test void signedClientOwnerIdRetainsTheExistingFullOwnerContract() throws Exception {
        var signed = new UiEventTicketScope(-42L, " raw ", PUBLIC, "BUSINESS", 11L);
        when(assignments.find(eq(PUBLIC), any(Authentication.class)))
            .thenReturn(Optional.of(new BusinessTicketAssignmentService.State(signed, 3L, "owner", true)));
        when(participants.change(eq(signed), any(Authentication.class), eq(7L), eq(42L), eq(true))).thenReturn(true);
        mvc.perform(post(PATH).with(user("operator").authorities(() -> "PAGE_DIALOGS")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body("7", "42").replace("9007199254740993", "-42")))
            .andExpect(status().isNoContent());
    }
    @Test void staleOwnerAndRevisionCannotBecomeSuccessfulLegacyMutation() throws Exception {
        mvc.perform(post(PATH).with(user("operator").authorities(() -> "PAGE_DIALOGS")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body("7", "42").replace(" raw ", "raw"))).andExpect(status().isConflict());
        verify(participants, never()).change(any(), any(), anyLong(), anyLong(), anyBoolean());
        mvc.perform(post(PATH).with(user("operator").authorities(() -> "PAGE_DIALOGS")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body("6", "42"))).andExpect(status().isConflict());
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "01", "bad", "9223372036854775808"})
    void rejectsInvalidMemberIdsBeforeAnyWrite(String member) throws Exception {
        mvc.perform(post(PATH).with(user("operator").authorities(() -> "PAGE_DIALOGS")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body("7", member))).andExpect(status().isBadRequest());
        verify(participants, never()).change(any(), any(), anyLong(), anyLong(), anyBoolean());
    }
    @Test void preservesPageCsrfFoundationAndCurrentScopeGuards() throws Exception {
        mvc.perform(post(PATH).with(user("operator").authorities(() -> "PAGE_DIALOGS"))
            .contentType(MediaType.APPLICATION_JSON).content(body("7", "42"))).andExpect(status().isForbidden());
        mvc.perform(get(PATH + "/management").with(user("admin").authorities(() -> "PAGE_SETTINGS"))).andExpect(status().isForbidden());
        when(flags.isFoundationEnabled()).thenReturn(false);
        mvc.perform(get(PATH + "/management").with(user("operator").authorities(() -> "PAGE_DIALOGS"))).andExpect(status().isNotFound());
        when(flags.isFoundationEnabled()).thenReturn(true);
        when(assignments.find(eq(PUBLIC), any(Authentication.class))).thenReturn(Optional.empty());
        mvc.perform(get(PATH + "/management").with(user("operator").authorities(() -> "PAGE_DIALOGS"))).andExpect(status().isNotFound());
        verify(participants, never()).change(any(), any(), anyLong(), anyLong(), anyBoolean());
    }
    @Test void unavailableRevisionIsNotReportedAsAnEmptyWritableRoster() throws Exception {
        when(participants.revision(eq(scope), any(Authentication.class))).thenReturn(Optional.empty());
        mvc.perform(get(PATH + "/management").with(user("operator").authorities(() -> "PAGE_DIALOGS"))).andExpect(status().isServiceUnavailable());
    }
}
