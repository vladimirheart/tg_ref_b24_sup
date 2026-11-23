package com.example.panel.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.model.clients.*;
import com.example.panel.repository.PanelUserRepository;
import com.example.panel.service.ClientsService;
import com.example.panel.service.NavigationService;
import com.example.panel.service.PanelUserPhotoService;
import com.example.panel.service.PermissionService;
import com.example.panel.service.SharedConfigService;
import com.example.panel.service.UnblockRequestService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ClientsController.class)
@AutoConfigureMockMvc
@Import(NavigationService.class)
class ClientsControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClientsService clientsService;

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
    void clientsPageIncludesUiHeadBootstrapAndExplicitPagePreset() throws Exception {
        when(clientsService.loadClients(null, null)).thenReturn(List.of());
        when(sharedConfigService.loadSettings()).thenReturn(Map.of());
        when(permissionService.hasAuthority(any(), any())).thenReturn(false);
        when(panelUserRepository.findByUsernameIgnoreCase("operator")).thenReturn(Optional.empty());
        when(panelUserPhotoService.resolveUrl(any(), any())).thenReturn("/avatar_default.svg");

        mockMvc.perform(get("/clients").with(user("operator").authorities(() -> "PAGE_CLIENTS")))
            .andExpect(status().isOk())
            .andExpect(view().name("clients/index"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-preferences.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/theme.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-config.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-ui-page=\"clients\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/unblock-requests")));
    }

    @Test
    void profileRendersDirectNormalAndGeneralLinksWithoutInventingOrphanLinks() throws Exception {
        when(sharedConfigService.loadSettings()).thenReturn(Map.of());
        when(permissionService.hasAuthority(any(), any())).thenReturn(false);
        when(panelUserRepository.findByUsernameIgnoreCase("operator")).thenReturn(Optional.empty());
        when(panelUserPhotoService.resolveUrl(any(), any())).thenReturn("/avatar_default.svg");
        when(clientsService.loadKnownClientStatuses()).thenReturn(List.of());
        String normal = "/dialogs?ticketId=normal%26id&channelId=7";
        String general = "/general-dialog-work/" + "a".repeat(32);
        var profile = new ClientProfile(
            new ClientProfileHeader(55L,"employee","Сотрудник",7L,"Telegram","telegram","telegram","Telegram","ID","ID"),
            new ClientProfileStats(3,0,3,0,"0 мин"),
            List.of(profileTicket("normal&id","42",normal),profileTicket("general","43",general),profileTicket("orphan","44",null)),
            new ClientBlacklistInfo(false,false,null,null,null,null),null,List.of(),0,"",List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
        when(clientsService.loadClientProfile(55)).thenReturn(Optional.of(profile));
        String html = mockMvc.perform(get("/client/55").with(user("operator").authorities(() -> "PAGE_CLIENTS")))
            .andExpect(status().isOk()).andExpect(view().name("clients/profile"))
            .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(html).contains("href=\"/dialogs?ticketId=normal%26id&amp;channelId=7\"", "href=\""+general+"\"");
        assertThat(html.split("data-client-ticket-open",-1)).hasSize(3);
        assertThat(html).contains("data-dialog-url=\"/dialogs?ticketId=normal%26id&amp;channelId=7\"")
            .doesNotContain("data-ticket-id=\"general\"", "data-ticket-id=\"orphan\"", "ticketId=orphan");
        Path capture = Path.of("target/298-rendered-profile.html");
        Files.createDirectories(capture.getParent());
        Files.writeString(capture,html);
    }

    private ClientProfileTicket profileTicket(String id,String number,String url) {
        return new ClientProfileTicket(id,number,"Бизнес","Москва","Магазин","Локация","Проблема",null,"pending",null,"Тема",null,"Telegram",7L,url);
    }
}
