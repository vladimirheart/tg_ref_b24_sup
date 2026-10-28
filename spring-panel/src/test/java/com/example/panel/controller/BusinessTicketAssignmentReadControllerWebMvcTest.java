package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.BusinessTicketAssignmentReadService;
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

@WebMvcTest(BusinessTicketAssignmentReadController.class)
@Import(BusinessTicketAssignmentReadControllerWebMvcTest.MethodSecurity.class)
class BusinessTicketAssignmentReadControllerWebMvcTest {
    private static final String PUBLIC="a".repeat(32),PATH="/api/business-scope/tickets/"+PUBLIC+"/assignments/history";
    @TestConfiguration @EnableMethodSecurity static class MethodSecurity { }
    @Autowired MockMvc mvc;
    @MockBean BusinessTicketAssignmentReadService service;
    @MockBean MultibusinessFeatureFlags flags;
    @Test void privateHistoryKeepsBigintIdsRevisionsAndCursorExact() throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);
        var owner=new BusinessTicketAssignmentReadService.Owner("9007199254740993"," raw ",PUBLIC,"11");
        when(service.history(eq(PUBLIC),any(Authentication.class),eq(Long.MAX_VALUE),eq(1))).thenReturn(Optional.of(new BusinessTicketAssignmentReadService.History(owner,Long.MAX_VALUE,Long.MAX_VALUE,
            List.of(new BusinessTicketAssignmentReadService.Event(9,"MANUAL","assign",Long.MAX_VALUE,"target",1L,"actor",null)),true,9L)));
        mvc.perform(get(PATH).param("before",Long.toString(Long.MAX_VALUE)).param("limit","1").with(user("viewer").authorities(()->"PAGE_DIALOGS")))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(header().string("X-Content-Type-Options","nosniff"))
            .andExpect(jsonPath("$.owner.userId").value("9007199254740993"))
            .andExpect(jsonPath("$.revision").value(Long.toString(Long.MAX_VALUE)))
            .andExpect(jsonPath("$.latestEventRevision").value(Long.toString(Long.MAX_VALUE)))
            .andExpect(jsonPath("$.events[0].assigneeId").value(Long.toString(Long.MAX_VALUE)))
            .andExpect(jsonPath("$.events[0].revision").value("9")).andExpect(jsonPath("$.nextBeforeRevision").value("9"));
    }
    @Test void requiresPageFoundationAndCurrentScopeAndDoesNotHideSchemaErrors() throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);
        when(service.history(eq(PUBLIC),any(Authentication.class),isNull(),eq(50))).thenReturn(Optional.empty())
            .thenThrow(new BusinessTicketAssignmentReadService.ReadUnavailableException(new IllegalStateException()));
        mvc.perform(get(PATH).with(user("viewer").authorities(()->"PAGE_DIALOGS"))).andExpect(status().isNotFound());
        mvc.perform(get(PATH).with(user("viewer").authorities(()->"PAGE_DIALOGS"))).andExpect(status().isServiceUnavailable());
        mvc.perform(get(PATH).with(user("viewer").authorities(()->"PAGE_SETTINGS"))).andExpect(status().isForbidden());
        when(flags.isFoundationEnabled()).thenReturn(false);
        mvc.perform(get(PATH).with(user("viewer").authorities(()->"PAGE_DIALOGS"))).andExpect(status().isNotFound());
        verify(service,times(2)).history(eq(PUBLIC),argThat(auth->auth!=null && "viewer".equals(auth.getName())),isNull(),eq(50));
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","01"," ","bad","9223372036854775808"})
    void invalidCursorIsRejectedBeforeRead(String value) throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);
        mvc.perform(get(PATH).param("before",value).with(user("viewer").authorities(()->"PAGE_DIALOGS"))).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","01","101","bad","2147483648"})
    void invalidPageSizeIsRejectedBeforeRead(String value) throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);
        mvc.perform(get(PATH).param("limit",value).with(user("viewer").authorities(()->"PAGE_DIALOGS"))).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
