package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.model.dialog.DialogMyDialogs;
import com.example.panel.model.dialog.DialogSummary;
import com.example.panel.repository.PanelUserRepository;
import com.example.panel.service.DialogLookupReadService;
import com.example.panel.service.DialogListReadService;
import com.example.panel.service.NavigationService;
import com.example.panel.service.PanelUserPhotoService;
import com.example.panel.service.PermissionService;
import com.example.panel.service.SharedConfigService;
import com.example.panel.service.UnblockRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(DialogsController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(NavigationService.class)
class DialogsControllerWebMvcTest {

    @Test
    void initialHtmlContainsOnlyABoundedPageAndIndependentTotals() throws Exception {
        stubNavigationDefaults();
        var dialogs = java.util.stream.IntStream.rangeClosed(1, 20).mapToObj(n -> new DialogListItem(
                "T-" + String.format("%05d", n), "20261026-" + String.format("%03d", n), 1000L + n,
                "client", "Клиент " + n, "Поддержка", 1L, "Telegram", "Город", "Ресторан",
                "Проблема " + n, "2026-10-26T09:00:00Z", "open", false, null, null, null,
                "2026-10-26", "09:00:00", "VIP", "user", "2026-10-26T09:00:00Z", 1, null, "Техническая поддержка", null, null)).toList();
        stubDialogsPageData(new DialogSummary(10000, 2000, 8000, List.of()), dialogs,
                new DialogMyDialogs(dialogs, List.of(), List.of()));
        var result = mockMvc.perform(get("/dialogs").with(user("operator")))
                .andExpect(status().isOk()).andReturn();
        String html = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        long rowCount = java.util.regex.Pattern.compile("<tr\\b[^>]*\\bdata-ticket-id=", java.util.regex.Pattern.DOTALL)
                .matcher(html).results().count();
        org.assertj.core.api.Assertions.assertThat(rowCount).isEqualTo(20);
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("<option value=\"all\">Все</option>");
        org.assertj.core.api.Assertions.assertThat(html).contains("DIALOG_LIST_PAGINATION", "10000");
        org.mockito.Mockito.verifyNoInteractions(dialogLookupReadService);
        String capture = System.getProperty("iguana.dialogs.capture");
        if (capture != null) {
            var file = java.nio.file.Path.of(capture);
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.writeString(file, html, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DialogLookupReadService dialogLookupReadService;

    @MockBean
    private DialogListReadService dialogListReadService;

    @MockBean
    private SharedConfigService sharedConfigService;

    @MockBean
    private PermissionService permissionService;

    @MockBean
    private PanelUserRepository panelUserRepository;

    @MockBean
    private UnblockRequestService unblockRequestService;

    @MockBean
    private PanelUserPhotoService panelUserPhotoService;

    @MockBean
    private MultibusinessFeatureFlags multibusinessFlags;

    @Test
    void dialogsTicketRouteProvidesInitialDialogTicketId() throws Exception {
        stubNavigationDefaults();
        stubDialogsPageData(new DialogSummary(0, 0, 0, Collections.emptyList()),
                Collections.emptyList(),
                DialogMyDialogs.empty());

        mockMvc.perform(get("/dialogs/T-123").with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(view().name("dialogs/index"))
                .andExpect(model().attribute("initialDialogTicketId", "T-123"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-page-key=\"dialogs\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("sidebar-user-name")));
    }

    @Test
    void businessOverviewLinkAppearsOnlyWhenBothFlagsAreEnabled() throws Exception {
        stubNavigationDefaults();
        stubDialogsPageData(new DialogSummary(0, 0, 0, Collections.emptyList()),
                Collections.emptyList(), DialogMyDialogs.empty());
        when(multibusinessFlags.isFoundationEnabled()).thenReturn(true);
        when(multibusinessFlags.isUiSelector()).thenReturn(true);

        mockMvc.perform(get("/dialogs").with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/business-dialogs\"")));
        when(multibusinessFlags.isUiSelector()).thenReturn(false);
        mockMvc.perform(get("/dialogs").with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("href=\"/business-dialogs\""))));
    }

    @Test
    void dialogsQueryRoutePreservesExactOpaqueTicketId() throws Exception {
        stubNavigationDefaults();
        stubDialogsPageData(new DialogSummary(0, 0, 0, Collections.emptyList()),
                Collections.emptyList(), DialogMyDialogs.empty());

        String ticketId = " T/100?&%# Ю ";
        mockMvc.perform(get("/dialogs").param("ticketId", ticketId).with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(view().name("dialogs/index"))
                .andExpect(model().attribute("initialDialogTicketId", ticketId))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "data-initial-dialog-ticket-id=\" T/100?&amp;%# Ю \"")));
    }

    @Test
    void dialogsListRendersOpenActionsAsRealDialogLinks() throws Exception {
        stubNavigationDefaults();
        List<DialogListItem> dialogs = Collections.singletonList(
                new DialogListItem(
                        " T/555?&%# Ю ",
                        1001L,
                        2002L,
                        "client",
                        "РљР»РёРµРЅС‚",
                        "Billing",
                        7L,
                        "РџРѕРґРґРµСЂР¶РєР°",
                        "РњРѕСЃРєРІР°",
                        "РћС„РёСЃ",
                        "РџСЂРѕР±Р»РµРјР°",
                        "2026-03-31T09:00:00Z",
                        "open",
                        null,
                        null,
                        "",
                        "31.03.2026",
                        "12:00:00",
                        null,
                        null,
                        null,
                        0,
                        null,
                        null
                )
        );
        stubDialogsPageData(new DialogSummary(1, 1, 0, Collections.emptyList()),
                dialogs,
                new DialogMyDialogs(List.of(), dialogs, List.of()));

        mockMvc.perform(get("/dialogs").with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(view().name("dialogs/index"))
                .andExpect(model().attributeExists("myNewDialogs"))
                .andExpect(model().attributeExists("myUnansweredDialogs"))
                .andExpect(model().attributeExists("myInWorkDialogs"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"dialogsTable\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(" T/555?&amp;%# Ю ")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "href=\"/dialogs?ticketId=%20T/555?%26%25%23%20%D0%AE%20&amp;channelId=7\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-page-key=\"ai-ops\"")));
    }

    @Test
    void dialogsPageIncludesEarlyUiBootstrapScriptsFromSharedHeadFragment() throws Exception {
        stubNavigationDefaults();
        stubDialogsPageData(new DialogSummary(0, 0, 0, Collections.emptyList()),
                Collections.emptyList(),
                DialogMyDialogs.empty());

        mockMvc.perform(get("/dialogs").with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-preferences.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/theme.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-config.js")));
    }

    @Test
    void aiOpsPageIncludesUiHeadBootstrapAndExplicitPagePreset() throws Exception {
        stubNavigationDefaults();

        mockMvc.perform(get("/ai-ops")
                        .with(user("operator").authorities(() -> "PAGE_DIALOGS"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("dialogs/ai-ops"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-preferences.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/theme.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-config.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-ui-page=\"ai-ops\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-page-key=\"dialogs\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-page-key=\"ai-ops\"")));
    }

    @Test
    void dialogsPageRendersChannelsSidebarLinkFromNavigationPermissions() throws Exception {
        stubNavigationDefaults();
        when(permissionService.hasAuthority(any(), anyString())).thenAnswer(invocation -> {
            String authority = invocation.getArgument(1, String.class);
            return "PAGE_DIALOGS".equals(authority) || "PAGE_CHANNELS".equals(authority);
        });
        stubDialogsPageData(new DialogSummary(0, 0, 0, Collections.emptyList()),
                Collections.emptyList(),
                DialogMyDialogs.empty());

        mockMvc.perform(get("/dialogs")
                        .with(user("operator").authorities(() -> "PAGE_DIALOGS", () -> "PAGE_CHANNELS")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-page-key=\"channels\"")));
    }

    @Test
    void dialogsPagePrefersSettingsSidebarLinkOverChannelsWhenSettingsAreAvailable() throws Exception {
        stubNavigationDefaults();
        when(permissionService.hasAuthority(any(), anyString())).thenAnswer(invocation -> {
            String authority = invocation.getArgument(1, String.class);
            return "PAGE_DIALOGS".equals(authority)
                    || "PAGE_CHANNELS".equals(authority)
                    || "PAGE_SETTINGS".equals(authority);
        });
        stubDialogsPageData(new DialogSummary(0, 0, 0, Collections.emptyList()),
                Collections.emptyList(),
                DialogMyDialogs.empty());

        mockMvc.perform(get("/dialogs")
                        .with(user("operator").authorities(
                                () -> "PAGE_DIALOGS",
                                () -> "PAGE_CHANNELS",
                                () -> "PAGE_SETTINGS")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-page-key=\"settings\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("data-page-key=\"channels\""))));
    }

    private void stubDialogsPageData(DialogSummary summary,
                                     List<DialogListItem> dialogs,
                                     DialogMyDialogs myDialogs) {
        when(dialogListReadService.loadListPayload(anyString())).thenReturn(Map.of(
                "summary", summary, "dialogs", dialogs,
                "pagination", Map.of("page", 1, "page_size", 20, "total_items", summary.totalTickets()),
                "my_dialogs", Map.of("new", myDialogs.newUnassigned(), "unanswered", myDialogs.unanswered(),
                        "in_work", myDialogs.inWork(), "counts", Map.of())));
        when(sharedConfigService.loadSettings()).thenReturn(Map.of());
    }

    private void stubNavigationDefaults() {
        when(permissionService.hasAuthority(any(), anyString())).thenReturn(false);
        when(permissionService.isSuperUser(any())).thenReturn(false);
        when(panelUserRepository.findByUsernameIgnoreCase("operator")).thenReturn(Optional.empty());
        when(unblockRequestService.countPendingRequests()).thenReturn(0L);
        when(panelUserPhotoService.resolveUrl(any(), anyString())).thenReturn("/avatar_default.svg");
    }
}
