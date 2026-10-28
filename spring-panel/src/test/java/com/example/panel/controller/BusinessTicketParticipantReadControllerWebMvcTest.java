package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.BusinessTicketParticipantReadService;
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
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import org.springframework.security.core.Authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BusinessTicketParticipantReadController.class)
@Import(BusinessTicketParticipantReadControllerWebMvcTest.MethodSecurity.class)
class BusinessTicketParticipantReadControllerWebMvcTest {
    private static final String PUBLIC="a".repeat(32),PATH="/api/business-scope/tickets/"+PUBLIC+"/participants";
    @TestConfiguration @EnableMethodSecurity static class MethodSecurity { }
    @Autowired MockMvc mvc;
    @MockBean BusinessTicketParticipantReadService service;
    @MockBean MultibusinessFeatureFlags flags;
    @Test void rosterAndHistoryArePrivateAndPreserveExactStringIdsAndRevisions() throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);
        var owner=new BusinessTicketParticipantReadService.Owner("9007199254740993"," raw ",PUBLIC,"11");
        when(service.find(eq(PUBLIC),any(Authentication.class))).thenReturn(Optional.of(new BusinessTicketParticipantReadService.Roster(owner,Long.MAX_VALUE,List.of(
            new BusinessTicketParticipantReadService.Member(Long.MAX_VALUE,"snapshot","current",false,null)))));
        mvc.perform(get(PATH).with(user("viewer").authorities(()->"PAGE_DIALOGS")))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(header().string("X-Content-Type-Options","nosniff"))
            .andExpect(jsonPath("$.owner.userId").value("9007199254740993"))
            .andExpect(jsonPath("$.revision").value(Long.toString(Long.MAX_VALUE)))
            .andExpect(jsonPath("$.members[0].id").value(Long.toString(Long.MAX_VALUE)))
            .andExpect(jsonPath("$.members[0].eligible").value(false));
        when(service.history(eq(PUBLIC),any(Authentication.class),eq(9L),eq(1))).thenReturn(Optional.of(new BusinessTicketParticipantReadService.History(owner,10,
            List.of(new BusinessTicketParticipantReadService.Event(8,"add",2,"member",1,"actor",null)),true,8L)));
        mvc.perform(get(PATH+"/history").param("before","9").param("limit","1").with(user("viewer").authorities(()->"PAGE_DIALOGS")))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.events[0].revision").value("8"))
            .andExpect(jsonPath("$.nextBeforeRevision").value("8"));
    }
    @Test void requiresPageFoundationAndCurrentScopeAndDoesNotMaskSchemaFailureAsEmpty() throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);
        when(service.find(eq(PUBLIC),any(Authentication.class))).thenReturn(Optional.empty())
            .thenThrow(new BusinessTicketParticipantReadService.RosterTooLargeException())
            .thenThrow(new BusinessTicketParticipantReadService.ReadUnavailableException(new IllegalStateException()));
        mvc.perform(get(PATH).with(user("viewer").authorities(()->"PAGE_DIALOGS"))).andExpect(status().isNotFound());
        mvc.perform(get(PATH).with(user("viewer").authorities(()->"PAGE_DIALOGS"))).andExpect(status().isPayloadTooLarge());
        mvc.perform(get(PATH).with(user("viewer").authorities(()->"PAGE_DIALOGS"))).andExpect(status().isServiceUnavailable());
        mvc.perform(get(PATH).with(user("viewer").authorities(()->"PAGE_SETTINGS"))).andExpect(status().isForbidden());
        when(flags.isFoundationEnabled()).thenReturn(false);
        mvc.perform(get(PATH).with(user("viewer").authorities(()->"PAGE_DIALOGS"))).andExpect(status().isNotFound());
        verify(service,times(3)).find(eq(PUBLIC),argThat(auth->auth!=null && "viewer".equals(auth.getName())));
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","01"," ","bad","9223372036854775808"})
    void invalidCursorIsRejectedBeforeDatabaseRead(String value) throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);
        mvc.perform(get(PATH+"/history").param("before",value).with(user("viewer").authorities(()->"PAGE_DIALOGS")))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","01","101","bad","2147483648"})
    void invalidPageSizeIsRejectedBeforeDatabaseRead(String value) throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);
        mvc.perform(get(PATH+"/history").param("limit",value).with(user("viewer").authorities(()->"PAGE_DIALOGS")))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
