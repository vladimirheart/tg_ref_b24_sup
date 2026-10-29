package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.GeneralTicketAccessService;
import com.example.panel.service.GeneralTicketReadService;
import com.example.panel.service.GeneralTicketRealtimeService;
import com.example.panel.service.NavigationService;
import com.example.panel.storage.AttachmentObjectStorageService;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({GeneralTicketReadController.class, GeneralTicketAccessController.class, GeneralTicketPageController.class, GeneralTicketStreamController.class})
@Import(GeneralTicketControllersWebMvcTest.MethodSecurity.class)
class GeneralTicketControllersWebMvcTest {
    @TestConfiguration @EnableMethodSecurity static class MethodSecurity {}
    @Autowired MockMvc mvc;
    @MockBean GeneralTicketReadService service;
    @MockBean GeneralTicketRealtimeService realtime;
    @MockBean GeneralTicketAccessService access;
    @MockBean MultibusinessFeatureFlags flags;
    @MockBean NavigationService navigation;
    @MockBean AttachmentObjectStorageService storage;
    private static final String PUBLIC_ID = "0123456789abcdef0123456789abcdef";

    @BeforeEach
    void actor() {
        when(access.requireAuthenticatedUserId(org.mockito.ArgumentMatchers.argThat(actor -> actor != null && "reader".equals(actor.getName())))).thenReturn(1L);
    }

    @Test
    void generalStreamRequiresFlagsPageAuthorityAndExplicitRead() throws Exception {
        String path = "/api/general-tickets/events/stream";
        mvc.perform(get(path).with(user("reader").authorities(() -> "PAGE_DIALOGS"))).andExpect(status().isNotFound());
        enable();
        mvc.perform(get(path).with(user("admin").authorities(() -> "ROLE_ADMIN"))).andExpect(status().isForbidden());
        when(realtime.connect(org.mockito.ArgumentMatchers.argThat(a->a!=null&&a.getName().equals("reader")))).thenThrow(new AccessDeniedException("No GENERAL grant"));
        mvc.perform(get(path).with(user("reader").authorities(() -> "PAGE_DIALOGS"))).andExpect(status().isForbidden());
    }

    @Test
    void generalStreamHandshakeIsPrivateAndDoesNotTrustRequestedIdentity() throws Exception {
        enable();
        var emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(0L);
        emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("connected").data(java.util.Map.of("connected", true)));
        when(realtime.connect(org.mockito.ArgumentMatchers.argThat(a->a!=null&&a.getName().equals("reader")))).thenReturn(emitter);
        mvc.perform(get("/api/general-tickets/events/stream?user=other").with(user("reader").authorities(() -> "PAGE_DIALOGS")))
            .andExpect(status().isOk()).andExpect(request().asyncStarted())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("X-Accel-Buffering", "no"))
            .andExpect(content().contentTypeCompatibleWith("text/event-stream"));
        verify(realtime).connect(org.mockito.ArgumentMatchers.argThat(a->a!=null&&a.getName().equals("reader")));
        verify(realtime, never()).connect(org.mockito.ArgumentMatchers.argThat(a->a!=null&&a.getName().equals("other")));
        emitter.complete();
    }

    private void enable() {
        when(flags.isFoundationEnabled()).thenReturn(true);
        when(flags.isGeneralTicketQueue()).thenReturn(true);
    }

    @Test
    void featureAndPageAuthorityAreBothRequired() throws Exception {
        mvc.perform(get("/api/general-tickets/page").with(user("reader").authorities(() -> "PAGE_DIALOGS")))
            .andExpect(status().isNotFound());
        when(flags.isFoundationEnabled()).thenReturn(true);
        mvc.perform(get("/api/general-tickets/page").with(user("reader").authorities(() -> "PAGE_DIALOGS")))
            .andExpect(status().isNotFound());
        enable();
        mvc.perform(get("/api/general-tickets/page").with(user("admin").authorities(() -> "ROLE_ADMIN")))
            .andExpect(status().isForbidden());
        verify(service, never()).page(1L, 50, 0, null, null);
    }

    @Test
    void readGrantDenialAndEntityDenialDoNotRevealTicketData() throws Exception {
        enable();
        when(service.page(1L, 50, 0, null, null)).thenThrow(new AccessDeniedException("no grant"));
        mvc.perform(get("/api/general-tickets/page").with(user("reader").authorities(() -> "PAGE_DIALOGS")))
            .andExpect(status().isForbidden());
        for (String suffix : List.of("", "/history", "/answers", "/history/7/attachment")) {
            mvc.perform(get("/api/general-tickets/" + PUBLIC_ID + suffix)
                    .with(user("reader").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isNotFound());
        }
        verify(storage, never()).openDialogAttachmentByStorageKey(any());
    }

    @Test
    void pagingAndHistoryValidateNumbersAndReturnPrivateResponses() throws Exception {
        enable();
        when(service.page(1L, 25, 50, "open", "query"))
            .thenReturn(new GeneralTicketReadService.TicketPage(List.of(), false));
        mvc.perform(get("/api/general-tickets/page?limit=25&offset=50&status=open&q=query")
                .with(user("reader").authorities(() -> "PAGE_DIALOGS")))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.hasMore").value(false));
        for (String query : List.of("limit=0", "limit=101", "limit=99999999999999999", "offset=-1", "limit=01")) {
            mvc.perform(get("/api/general-tickets/page?" + query)
                    .with(user("reader").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isBadRequest());
        }
        for (String query : List.of("before=0", "before=-1", "before=999999999999999999999999", "limit=101")) {
            mvc.perform(get("/api/general-tickets/" + PUBLIC_ID + "/history?" + query)
                    .with(user("reader").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isBadRequest());
        }
        when(service.history(1L, PUBLIC_ID, 50L, 10))
            .thenReturn(Optional.of(new GeneralTicketReadService.TicketHistory(null, List.of(), false, null)));
        mvc.perform(get("/api/general-tickets/" + PUBLIC_ID + "/history?before=50&limit=10")
                .with(user("reader").authorities(() -> "PAGE_DIALOGS")))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void answersHavePrivateHeadersAndRefuseSilentTruncation() throws Exception {
        enable();
        when(service.answers(1L, PUBLIC_ID)).thenReturn(Optional.of(
            new GeneralTicketReadService.TicketAnswers(null, true, List.of())));
        mvc.perform(get("/api/general-tickets/" + PUBLIC_ID + "/answers")
                .with(user("reader").authorities(() -> "PAGE_DIALOGS")))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        when(service.answers(1L, PUBLIC_ID)).thenThrow(new GeneralTicketReadService.AnswerLimitExceededException());
        mvc.perform(get("/api/general-tickets/" + PUBLIC_ID + "/answers")
                .with(user("reader").authorities(() -> "PAGE_DIALOGS")))
            .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void attachmentDownloadsOnlyTheAuthorizedStorageReference() throws Exception {
        enable();
        when(service.attachment(1L, PUBLIC_ID, 7L))
            .thenReturn(Optional.of(new GeneralTicketReadService.AttachmentReference("T/file.bin", "../file.bin")));
        when(storage.openDialogAttachmentByStorageKey("T/file.bin")).thenReturn(
            new AttachmentObjectStorageService.StoredBinary("T/file.bin", "text/plain", 3, new ByteArrayInputStream(new byte[] {1, 2, 3})));
        mvc.perform(get("/api/general-tickets/" + PUBLIC_ID + "/history/7/attachment")
                .with(user("reader").authorities(() -> "PAGE_DIALOGS")))
            .andExpect(status().isOk()).andExpect(content().bytes(new byte[] {1, 2, 3}))
            .andExpect(content().contentTypeCompatibleWith("application/octet-stream"))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));
        mvc.perform(get("/api/general-tickets/" + PUBLIC_ID + "/history/0/attachment")
                .with(user("reader").authorities(() -> "PAGE_DIALOGS")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void grantsRequireSettingsSystemAdminFeatureAndCsrf() throws Exception {
        String path = "/api/general-ticket-admin/access/5";
        mvc.perform(put(path).with(user("admin").authorities(() -> "PAGE_SETTINGS", () -> "ROLE_ADMIN")).with(csrf()))
            .andExpect(status().isNotFound());
        enable();
        for (String authority : List.of("PAGE_SETTINGS", "ROLE_ADMIN", "PAGE_DIALOGS")) {
            mvc.perform(put(path).with(user("operator").authorities(() -> authority)).with(csrf()))
                .andExpect(status().isForbidden());
        }
        mvc.perform(put(path).with(user("admin").authorities(() -> "PAGE_SETTINGS", () -> "ROLE_ADMIN")))
            .andExpect(status().isForbidden());
        verify(access, never()).grantRead(5L);
        when(access.grantRead(5L)).thenReturn(true);
        when(access.revokeRead(5L)).thenReturn(true);
        mvc.perform(put(path).with(user("admin").authorities(() -> "PAGE_SETTINGS", () -> "ROLE_ADMIN")).with(csrf()))
            .andExpect(status().isNoContent());
        mvc.perform(delete(path).with(user("admin").authorities(() -> "PAGE_SETTINGS", () -> "ROLE_ADMIN")).with(csrf()))
            .andExpect(status().isNoContent());
        mvc.perform(put("/api/general-ticket-admin/access/0")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS", () -> "ROLE_ADMIN")).with(csrf()))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/api/general-ticket-admin/access/99")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS", () -> "ROLE_ADMIN")).with(csrf()))
            .andExpect(status().isNotFound());
    }

    @Test
    void adminAccountListingIsPrivateAndDoesNotRequireAReadGrant() throws Exception {
        enable();
        when(access.accounts()).thenReturn(List.of(new GeneralTicketAccessService.AccountAccess(5, "operator", false)));
        mvc.perform(get("/api/general-ticket-admin/access")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS", () -> "ROLE_ADMIN")))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$[0].canRead").value(false));
        verify(access, never()).requireRead(any());
    }

    @Test
    void pagesEnforceTheirOwnAuthoritiesAndQueueGrant() throws Exception {
        mvc.perform(get("/general-dialogs").with(user("reader").authorities(() -> "PAGE_DIALOGS")))
            .andExpect(status().isNotFound());
        enable();
        doThrow(new AccessDeniedException("no read grant")).when(access).requireRead(1L);
        mvc.perform(get("/general-dialogs").with(user("reader").authorities(() -> "PAGE_DIALOGS")))
            .andExpect(status().isForbidden());
        doNothing().when(access).requireRead(1L);
        mvc.perform(get("/general-dialogs").with(user("reader").authorities(() -> "PAGE_DIALOGS")))
            .andExpect(status().isOk()).andExpect(view().name("general/dialogs"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-general-dialogs")));
        mvc.perform(get("/general-dialogs/access").with(user("reader").authorities(() -> "PAGE_SETTINGS")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/general-dialogs/access")
                .with(user("admin").authorities(() -> "PAGE_SETTINGS", () -> "ROLE_ADMIN")))
            .andExpect(status().isOk()).andExpect(view().name("general/access"));
    }
}
