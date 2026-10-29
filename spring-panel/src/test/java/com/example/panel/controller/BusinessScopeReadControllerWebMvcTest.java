package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.BusinessAccessService;
import com.example.panel.service.BusinessScopedTicketReadService;
import com.example.panel.storage.AttachmentObjectStorageService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BusinessScopeReadController.class)
@Import(BusinessScopeReadControllerWebMvcTest.MethodSecurity.class)
class BusinessScopeReadControllerWebMvcTest {

    @Test void categorySummaryIsPrivateBoundedAndRequiresPageFoundationAndBusinessScope() throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);
        when(service.requireAuthenticatedUserId(org.mockito.ArgumentMatchers.argThat((org.springframework.security.core.Authentication actor) -> actor != null && "operator".equals(actor.getName())))).thenReturn(1L);
        var context=new BusinessAccessService.OneBusiness(2L);
        when(service.summarizeCategories(1L,context)).thenReturn(List.of(
            new BusinessScopedTicketReadService.CategoryCount(2L,"sushi","Суши","Delivery",3)));
        String path="/api/business-scope/tickets/categories";
        mvc.perform(get(path).param("business_id","2").with(user("operator").authorities(()->"PAGE_DIALOGS")))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(header().string("X-Content-Type-Options","nosniff"))
            .andExpect(jsonPath("$[0].category").value("Delivery")).andExpect(jsonPath("$[0].total").value(3));
        when(service.summarizeCategories(1L,context)).thenThrow(new BusinessScopedTicketReadService.CategorySummaryTooLargeException());
        mvc.perform(get(path).param("business_id","2").with(user("operator").authorities(()->"PAGE_DIALOGS"))).andExpect(status().isPayloadTooLarge());
        mvc.perform(get(path).param("business_id","bad").with(user("operator").authorities(()->"PAGE_DIALOGS"))).andExpect(status().isForbidden());
        mvc.perform(get(path).param("business_id","2").with(user("operator").authorities(()->"PAGE_SETTINGS"))).andExpect(status().isForbidden());
        when(flags.isFoundationEnabled()).thenReturn(false);
        mvc.perform(get(path).param("business_id","2").with(user("operator").authorities(()->"PAGE_DIALOGS"))).andExpect(status().isNotFound());
    }

    @Test void categoriesFilterIsForwardedLiterallyAndInvalidValuesAreRejectedBeforeMockedReads() throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);
        when(service.requireAuthenticatedUserId(org.mockito.ArgumentMatchers.argThat((org.springframework.security.core.Authentication actor) -> actor != null && "operator".equals(actor.getName())))).thenReturn(1L);
        String category="A%_'😀";
        var context=new BusinessAccessService.OneBusiness(2L);
        when(service.listTickets(1L,context,50,0,null,null,null,category)).thenReturn(List.of());
        when(service.pageTickets(1L,context,50,0,null,null,null,category))
            .thenReturn(new BusinessScopedTicketReadService.TicketPage(List.of(),false));
        when(service.exportTickets(1L,context,null,null,null,category)).thenReturn(List.of());
        for(String suffix:List.of("","/page","/export")) {
            String path="/api/business-scope/tickets"+suffix;
            mvc.perform(get(path).param("business_id","2").param("category",category)
                .with(user("operator").authorities(()->"PAGE_DIALOGS"))).andExpect(status().isOk());
            for(String invalid:List.of("","  ","bad\n","x".repeat(161),"😀".repeat(161)))
                mvc.perform(get(path).param("business_id","2").param("category",invalid)
                    .with(user("operator").authorities(()->"PAGE_DIALOGS"))).andExpect(status().isBadRequest());
        }
        org.mockito.Mockito.verify(service).listTickets(1L,context,50,0,null,null,null,category);
        org.mockito.Mockito.verify(service).pageTickets(1L,context,50,0,null,null,null,category);
        org.mockito.Mockito.verify(service).exportTickets(1L,context,null,null,null,category);
    }

    private static final String PUBLIC_ID = "0123456789abcdef0123456789abcdef";
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-10-15T10:15:30Z");

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurity {
    }

    @Autowired
    private MockMvc mvc;

    @MockBean
    private BusinessScopedTicketReadService service;

    @MockBean
    private MultibusinessFeatureFlags flags;

    @MockBean
    private AttachmentObjectStorageService objectStorageService;

    @Test
    void availableBusinessesArePrivateAndStillRequireCurrentPageAndFoundation() throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);
        when(service.requireAuthenticatedUserId(org.mockito.ArgumentMatchers.argThat((org.springframework.security.core.Authentication actor) -> actor != null && "operator".equals(actor.getName())))).thenReturn(1L);
        when(service.availableBusinesses(1L)).thenReturn(List.of(
                new BusinessScopedTicketReadService.BusinessOption(2L, "sushi", "Суши")));
        String path = "/api/business-scope/businesses";
        mvc.perform(get(path).with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(jsonPath("$[0].code").value("sushi"));
        mvc.perform(get(path).with(user("operator").authorities(() -> "PAGE_SETTINGS")))
                .andExpect(status().isForbidden());
        when(flags.isFoundationEnabled()).thenReturn(false);
        mvc.perform(get(path).with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isNotFound());
    }

    @Test
    void requiresDialogsPageAndEnabledFoundation() throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);
        mvc.perform(get("/api/business-scope/tickets/" + PUBLIC_ID)
                .with(user("operator").authorities(() -> "PAGE_SETTINGS")))
                .andExpect(status().isForbidden());
        when(flags.isFoundationEnabled()).thenReturn(false);
        mvc.perform(get("/api/business-scope/tickets/" + PUBLIC_ID)
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isNotFound());
    }

    @Test
    void directLookupReturnsNonDisclosingNotFoundOrAuthorizedTicket() throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);
        when(service.requireAuthenticatedUserId(org.mockito.ArgumentMatchers.argThat((org.springframework.security.core.Authentication actor) -> actor != null && "operator".equals(actor.getName())))).thenReturn(1L);
        when(service.findByPublicId(1L, PUBLIC_ID)).thenReturn(Optional.empty());
        mvc.perform(get("/api/business-scope/tickets/" + PUBLIC_ID)
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isNotFound());

        when(service.findByPublicId(1L, PUBLIC_ID)).thenReturn(Optional.of(
                new BusinessScopedTicketReadService.TicketRow(
                        8L, "raw-ticket", PUBLIC_ID, 2L, "sushi", "Суши", "resolved",
                        CREATED_AT, OffsetDateTime.parse("2026-10-15T12:00:00Z"),
                        "Оператор", "Иван", "Нет света")));
        mvc.perform(get("/api/business-scope/tickets/" + PUBLIC_ID)
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(jsonPath("$.businessCode").value("sushi"))
                .andExpect(jsonPath("$.ticketId").value("raw-ticket"))
                .andExpect(jsonPath("$.clientName").value("Иван"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-15T10:15:30Z"))
                .andExpect(jsonPath("$.resolvedAt").value("2026-10-15T12:00:00Z"))
                .andExpect(jsonPath("$.resolvedBy").value("Оператор"))
                .andExpect(jsonPath("$.problemPreview").value("Нет света"));
    }

    @Test
    void historyRequiresPageAndReturnsOnlyScopedSnapshot() throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);
        when(service.requireAuthenticatedUserId(org.mockito.ArgumentMatchers.argThat((org.springframework.security.core.Authentication actor) -> actor != null && "operator".equals(actor.getName())))).thenReturn(1L);
        String path = "/api/business-scope/tickets/" + PUBLIC_ID + "/history";
        mvc.perform(get(path).with(user("operator").authorities(() -> "PAGE_SETTINGS")))
                .andExpect(status().isForbidden());
        mvc.perform(get(path).with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isNotFound());
        var ticket = new BusinessScopedTicketReadService.TicketRow(
                8L, "raw-ticket", PUBLIC_ID, 2L, "sushi", "Суши", "open",
                CREATED_AT, "Иван", "Нет света");
        when(service.findHistoryByPublicId(1L, PUBLIC_ID, null, 50)).thenReturn(Optional.of(
                new BusinessScopedTicketReadService.TicketHistory(ticket, List.of(
                        new BusinessScopedTicketReadService.HistoryEntry(
                                3L, "client", "Здравствуйте", null, "text", false)), true, 3L)));
        mvc.perform(get(path).with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(jsonPath("$.ticket.businessCode").value("sushi"))
                .andExpect(jsonPath("$.ticket.clientName").value("Иван"))
                .andExpect(jsonPath("$.ticket.createdAt").value("2026-10-15T10:15:30Z"))
                .andExpect(jsonPath("$.entries[0].message").value("Здравствуйте"))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.nextBeforeId").value(3));
        when(service.findHistoryByPublicId(1L, PUBLIC_ID, 3L, 2)).thenReturn(Optional.of(
                new BusinessScopedTicketReadService.TicketHistory(ticket, List.of(), false, null)));
        mvc.perform(get(path).param("before", "3").param("limit", "2")
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries").isEmpty())
                .andExpect(jsonPath("$.hasMore").value(false));
        mvc.perform(get(path).param("limit", "0")
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isBadRequest());
        mvc.perform(get(path).param("before", "-1")
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isBadRequest());
        mvc.perform(get(path).param("limit", "101")
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isBadRequest());
        mvc.perform(get(path).param("before", "abc")
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isBadRequest());
        when(flags.isFoundationEnabled()).thenReturn(false);
        mvc.perform(get(path).with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isNotFound());
    }

    @Test
    void answersReturnOnlyVerifiedRelationWithinCurrentPageAndFoundation() throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);
        when(service.requireAuthenticatedUserId(org.mockito.ArgumentMatchers.argThat((org.springframework.security.core.Authentication actor) -> actor != null && "operator".equals(actor.getName())))).thenReturn(1L);
        String path = "/api/business-scope/tickets/" + PUBLIC_ID + "/answers";
        var ticket = new BusinessScopedTicketReadService.TicketRow(
                8L, "raw-ticket", PUBLIC_ID, 2L, "sushi", "Суши", "open",
                CREATED_AT, "Иван", "Нет света");
        when(service.findAnswersByPublicId(1L, PUBLIC_ID)).thenReturn(Optional.empty());
        mvc.perform(get(path).with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isNotFound());
        when(service.findAnswersByPublicId(1L, PUBLIC_ID)).thenReturn(Optional.of(
                new BusinessScopedTicketReadService.TicketAnswers(ticket, true, List.of(
                        new BusinessScopedTicketReadService.AnswerEntry(
                                "q1", "Причина", "text", null, "<script>безопасный текст</script>")))));
        mvc.perform(get(path).with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(jsonPath("$.ticket.businessCode").value("sushi"))
                .andExpect(jsonPath("$.ticket.problemPreview").value("Нет света"))
                .andExpect(jsonPath("$.relationVerified").value(true))
                .andExpect(jsonPath("$.answers[0].valueText").value("<script>безопасный текст</script>"));
        when(service.findAnswersByPublicId(1L, PUBLIC_ID)).thenReturn(Optional.of(
                new BusinessScopedTicketReadService.TicketAnswers(ticket, false, List.of())));
        mvc.perform(get(path).with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relationVerified").value(false))
                .andExpect(jsonPath("$.answers").isEmpty());
        when(service.findAnswersByPublicId(1L, PUBLIC_ID))
                .thenThrow(new BusinessScopedTicketReadService.AnswerLimitExceededException());
        mvc.perform(get(path).with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isPayloadTooLarge());
        mvc.perform(get(path).with(user("operator").authorities(() -> "PAGE_SETTINGS")))
                .andExpect(status().isForbidden());
        when(flags.isFoundationEnabled()).thenReturn(false);
        mvc.perform(get(path).with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isNotFound());
    }

    @Test
    void workspaceReturnsOnlyUnambiguousScopedData() throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);
        when(service.requireAuthenticatedUserId(org.mockito.ArgumentMatchers.argThat((org.springframework.security.core.Authentication actor) -> actor != null && "operator".equals(actor.getName())))).thenReturn(1L);
        String path = "/api/business-scope/tickets/" + PUBLIC_ID + "/workspace";
        var ticket = new BusinessScopedTicketReadService.TicketRow(
                8L, "raw-ticket", PUBLIC_ID, 2L, "sushi", "Суши", "open");
        when(service.findWorkspaceByPublicId(1L, PUBLIC_ID)).thenReturn(Optional.empty());
        mvc.perform(get(path).with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isNotFound());
        when(service.findWorkspaceByPublicId(1L, PUBLIC_ID)).thenReturn(Optional.of(
                new BusinessScopedTicketReadService.TicketWorkspace(ticket, true, "operator-a",
                        List.of("Доставка"), List.of("alice"))));
        mvc.perform(get(path).with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(jsonPath("$.ticket.businessCode").value("sushi"))
                .andExpect(jsonPath("$.relationVerified").value(true))
                .andExpect(jsonPath("$.responsible").value("operator-a"))
                .andExpect(jsonPath("$.categories[0]").value("Доставка"))
                .andExpect(jsonPath("$.participants[0]").value("alice"));
        when(service.findWorkspaceByPublicId(1L, PUBLIC_ID)).thenReturn(Optional.of(
                new BusinessScopedTicketReadService.TicketWorkspace(ticket, false, null,
                        List.of(), List.of())));
        mvc.perform(get(path).with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relationVerified").value(false))
                .andExpect(jsonPath("$.categories").isEmpty())
                .andExpect(jsonPath("$.participants").isEmpty());
        when(service.findWorkspaceByPublicId(1L, PUBLIC_ID))
                .thenThrow(new BusinessScopedTicketReadService.WorkspaceLimitExceededException());
        mvc.perform(get(path).with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isPayloadTooLarge());
        mvc.perform(get(path).with(user("operator").authorities(() -> "PAGE_SETTINGS")))
                .andExpect(status().isForbidden());
        when(flags.isFoundationEnabled()).thenReturn(false);
        mvc.perform(get(path).with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isNotFound());
    }

    @Test
    void spansReturnOnlyVerifiedIntervalsUnderDialogsPermission() throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);
        when(service.requireAuthenticatedUserId(org.mockito.ArgumentMatchers.argThat((org.springframework.security.core.Authentication actor) -> actor != null && "operator".equals(actor.getName())))).thenReturn(1L);
        String path = "/api/business-scope/tickets/" + PUBLIC_ID + "/spans";
        var ticket = new BusinessScopedTicketReadService.TicketRow(
                8L, "raw-ticket", PUBLIC_ID, 2L, "sushi", "Суши", "open");
        mvc.perform(get(path).with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isNotFound());
        when(service.findSpansByPublicId(1L, PUBLIC_ID)).thenReturn(Optional.of(
                new BusinessScopedTicketReadService.TicketSpans(ticket, true, List.of(
                        new BusinessScopedTicketReadService.SpanEntry(1, CREATED_AT,
                                OffsetDateTime.parse("2026-10-15T10:16:30Z"), 60)))));
        mvc.perform(get(path).with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(jsonPath("$.ticket.businessCode").value("sushi"))
                .andExpect(jsonPath("$.relationVerified").value(true))
                .andExpect(jsonPath("$.spans[0].spanNo").value(1))
                .andExpect(jsonPath("$.spans[0].durationSeconds").value(60));
        when(service.findSpansByPublicId(1L, PUBLIC_ID)).thenReturn(Optional.of(
                new BusinessScopedTicketReadService.TicketSpans(ticket, false, List.of())));
        mvc.perform(get(path).with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relationVerified").value(false))
                .andExpect(jsonPath("$.spans").isEmpty());
        when(service.findSpansByPublicId(1L, PUBLIC_ID))
                .thenThrow(new BusinessScopedTicketReadService.SpanLimitExceededException());
        mvc.perform(get(path).with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isPayloadTooLarge());
        mvc.perform(get(path).with(user("operator").authorities(() -> "PAGE_SETTINGS")))
                .andExpect(status().isForbidden());
        when(flags.isFoundationEnabled()).thenReturn(false);
        mvc.perform(get(path).with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isNotFound());
    }

    @Test
    void attachmentRequiresCurrentScopeAndReturnsOnlyVerifiedBinary() throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);
        when(service.requireAuthenticatedUserId(org.mockito.ArgumentMatchers.argThat((org.springframework.security.core.Authentication actor) -> actor != null && "operator".equals(actor.getName())))).thenReturn(1L);
        String path = "/api/business-scope/tickets/" + PUBLIC_ID + "/history/7/attachment";
        mvc.perform(get(path).with(user("operator").authorities(() -> "PAGE_SETTINGS")))
                .andExpect(status().isForbidden());
        mvc.perform(get(path).with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/business-scope/tickets/" + PUBLIC_ID + "/history/nope/attachment")
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isBadRequest());
        var reference = new BusinessScopedTicketReadService.AttachmentReference(
                "ticket/file.bin", "../document.txt");
        when(service.findAttachmentByPublicId(1L, PUBLIC_ID, 7L)).thenReturn(Optional.of(reference));
        byte[] contents = "verified".getBytes(StandardCharsets.UTF_8);
        when(objectStorageService.openDialogAttachmentByStorageKey("ticket/file.bin"))
                .thenReturn(new AttachmentObjectStorageService.StoredBinary(
                        "ticket/file.bin", "text/plain", contents.length, new ByteArrayInputStream(contents)));
        mvc.perform(get(path).with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("document.txt")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("../"))))
                .andExpect(content().bytes(contents));
        when(flags.isFoundationEnabled()).thenReturn(false);
        mvc.perform(get(path).with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isNotFound());
    }

    @Test
    void listFiltersAreValidatedAndReturnedWithinSelectedBusiness() throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);
        when(service.requireAuthenticatedUserId(org.mockito.ArgumentMatchers.argThat((org.springframework.security.core.Authentication actor) -> actor != null && "operator".equals(actor.getName())))).thenReturn(1L);
        var ticket = new BusinessScopedTicketReadService.TicketRow(
                8L, "raw-ticket", PUBLIC_ID, 2L, "sushi", "Суши", "open",
                CREATED_AT, "Иван", "Нет света");
        when(service.listTickets(1L, new BusinessAccessService.OneBusiness(2L),
                50, 0, "open", PUBLIC_ID, null, null)).thenReturn(List.of(ticket));
        String path = "/api/business-scope/tickets";
        mvc.perform(get(path).param("business_id", "2").param("status", "open")
                .param("ticket", PUBLIC_ID).with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(jsonPath("$[0].ticketId").value("raw-ticket"))
                .andExpect(jsonPath("$[0].clientName").value("Иван"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-10-15T10:15:30Z"))
                .andExpect(jsonPath("$[0].problemPreview").value("Нет света"))
                .andExpect(jsonPath("$[0].status").value("open"));
        mvc.perform(get(path).param("business_id", "2").param("status", "invalid")
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isBadRequest());
        mvc.perform(get(path).param("business_id", "2").param("ticket", "bad\n")
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isBadRequest());
        mvc.perform(get(path).param("business_id", "2").param("q", "  ")
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isBadRequest());
        mvc.perform(get(path).param("business_id", "2").param("limit", "abc")
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isBadRequest());
        mvc.perform(get(path).param("business_id", "2").param("offset", "-1")
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isBadRequest());

        String pagePath = path + "/page";
        when(service.pageTickets(1L, new BusinessAccessService.OneBusiness(2L),
                50, 0, "open", PUBLIC_ID, null, null)).thenReturn(
                new BusinessScopedTicketReadService.TicketPage(List.of(ticket), false));
        mvc.perform(get(pagePath).param("business_id", "2").param("status", "open")
                .param("ticket", PUBLIC_ID).with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(jsonPath("$.tickets[0].ticketId").value("raw-ticket"))
                .andExpect(jsonPath("$.tickets[0].clientName").value("Иван"))
                .andExpect(jsonPath("$.hasMore").value(false));
        when(service.pageTickets(1L, new BusinessAccessService.OneBusiness(2L),
                50, 0, "open", null, "проблема", null)).thenReturn(
                new BusinessScopedTicketReadService.TicketPage(List.of(ticket), false));
        mvc.perform(get(pagePath).param("business_id", "2").param("status", "open")
                .param("q", "проблема").with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickets[0].ticketId").value("raw-ticket"));
        mvc.perform(get(pagePath).param("business_id", "2").param("q", "bad\n")
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isBadRequest());
        mvc.perform(get(pagePath).param("business_id", "2").param("q", "x".repeat(101))
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isBadRequest());
        mvc.perform(get(pagePath).param("business_id", "2").param("limit", "101")
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isBadRequest());
        mvc.perform(get(pagePath).param("business_id", "2").param("status", "invalid")
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isBadRequest());
        mvc.perform(get(pagePath).param("business_id", "2")
                .with(user("operator").authorities(() -> "PAGE_SETTINGS")))
                .andExpect(status().isForbidden());
        when(flags.isFoundationEnabled()).thenReturn(false);
        mvc.perform(get(pagePath).param("business_id", "2")
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isNotFound());
    }

    @Test
    void exportDownloadsCompleteFilteredJsonAndRejectsOversizedOrUnauthorizedRequests() throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);
        when(service.requireAuthenticatedUserId(org.mockito.ArgumentMatchers.argThat((org.springframework.security.core.Authentication actor) -> actor != null && "operator".equals(actor.getName())))).thenReturn(1L);
        String path = "/api/business-scope/tickets/export";
        var context = new BusinessAccessService.OneBusiness(2L);
        var ticket = new BusinessScopedTicketReadService.TicketRow(
                8L, "raw-ticket", PUBLIC_ID, 2L, "sushi", "Суши", "open",
                CREATED_AT, "Иван", "Нет света");
        when(service.exportTickets(1L, context, "open", null, "Иван", null))
                .thenReturn(List.of(ticket));
        mvc.perform(get(path).param("business_id", "2").param("status", "open")
                .param("q", "Иван").with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"business-tickets.json\""))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(jsonPath("$[0].ticketId").value("raw-ticket"))
                .andExpect(jsonPath("$[0].clientName").value("Иван"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-10-15T10:15:30Z"))
                .andExpect(jsonPath("$[0].problemPreview").value("Нет света"))
                .andExpect(jsonPath("$[0].businessCode").value("sushi"));
        mvc.perform(get(path).param("business_id", "2").param("q", "  ")
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isBadRequest());
        mvc.perform(get(path).param("business_id", "2").param("status", "bad")
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isBadRequest());
        when(service.exportTickets(1L, context, null, null, null, null))
                .thenThrow(new BusinessScopedTicketReadService.ExportTooLargeException());
        mvc.perform(get(path).param("business_id", "2")
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isPayloadTooLarge());
        mvc.perform(get(path).param("business_id", "bad")
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isForbidden());
        mvc.perform(get(path).param("business_id", "2")
                .with(user("operator").authorities(() -> "PAGE_SETTINGS")))
                .andExpect(status().isForbidden());
        when(flags.isFoundationEnabled()).thenReturn(false);
        mvc.perform(get(path).param("business_id", "2")
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isNotFound());
    }

    @Test
    void summaryRequiresCurrentBusinessScopeAndFoundation() throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);
        when(service.requireAuthenticatedUserId(org.mockito.ArgumentMatchers.argThat((org.springframework.security.core.Authentication actor) -> actor != null && "operator".equals(actor.getName())))).thenReturn(1L);
        String path = "/api/business-scope/tickets/summary";
        var counts = new BusinessScopedTicketReadService.BusinessTicketCounts(
                2L, "sushi", "Суши", 2, 0, 1, 1, 0, 0);
        when(service.summarizeTickets(1L, new BusinessAccessService.OneBusiness(2L)))
                .thenReturn(new BusinessScopedTicketReadService.TicketSummary(
                        2, 0, 1, 1, 0, 0, List.of(counts)));
        mvc.perform(get(path).param("business_id", "2")
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.businesses[0].businessCode").value("sushi"))
                .andExpect(jsonPath("$.businesses[0].open").value(1));
        mvc.perform(get(path).param("business_id", "invalid")
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isForbidden());
        when(service.summarizeTickets(1L, new BusinessAccessService.OneBusiness(9L)))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("Forbidden"));
        mvc.perform(get(path).param("business_id", "9")
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isForbidden());
        mvc.perform(get(path).param("business_id", "2")
                .with(user("operator").authorities(() -> "PAGE_SETTINGS")))
                .andExpect(status().isForbidden());
        when(flags.isFoundationEnabled()).thenReturn(false);
        mvc.perform(get(path).param("business_id", "2")
                .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isNotFound());
    }
}
