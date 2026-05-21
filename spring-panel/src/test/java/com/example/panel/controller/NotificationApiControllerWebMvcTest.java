package com.example.panel.controller;

import com.example.panel.model.notification.NotificationDto;
import com.example.panel.model.notification.NotificationSummary;
import com.example.panel.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationApiController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationApiControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @Test
    void listReturnsNotificationsForUserDetailsPrincipal() throws Exception {
        when(notificationService.findForUser("operator")).thenReturn(List.of(
                new NotificationDto(
                        101L,
                        "Новое сообщение в обращении T-101",
                        "/dialogs/T-101",
                        false,
                        OffsetDateTime.parse("2026-05-20T10:15:30+03:00")
                ),
                new NotificationDto(
                        102L,
                        "Обращение T-102 закрыто",
                        "/dialogs/T-102",
                        true,
                        OffsetDateTime.parse("2026-05-20T10:16:30+03:00")
                )
        ));

        mockMvc.perform(get("/api/notifications").principal(userDetailsAuthentication("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(101))
                .andExpect(jsonPath("$[0].text").value("Новое сообщение в обращении T-101"))
                .andExpect(jsonPath("$[0].url").value("/dialogs/T-101"))
                .andExpect(jsonPath("$[0].read").value(false))
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$[1].id").value(102))
                .andExpect(jsonPath("$[1].read").value(true));

        verify(notificationService).findForUser("operator");
    }

    @Test
    void listFallsBackToAllIdentityWhenAuthenticationIsMissing() throws Exception {
        when(notificationService.findForUser("all")).thenReturn(List.of());

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        verify(notificationService).findForUser("all");
    }

    @Test
    void unreadCountUsesAuthenticationNameWhenPrincipalIsNotUserDetails() throws Exception {
        when(notificationService.summary("team.lead")).thenReturn(new NotificationSummary(3));

        mockMvc.perform(get("/api/notifications/unread_count")
                        .principal(namedAuthentication("team.lead")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.unread").value(3));

        verify(notificationService).summary("team.lead");
    }

    @Test
    void markAsReadUsesAuthenticatedUsername() throws Exception {
        mockMvc.perform(post("/api/notifications/42/read").principal(userDetailsAuthentication("watcher_peer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(notificationService).markAsRead("watcher_peer", 42L);
    }

    @Test
    void markAsReadFallsBackToAllIdentityWhenAuthenticationIsMissing() throws Exception {
        mockMvc.perform(post("/api/notifications/77/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(notificationService).markAsRead("all", 77L);
    }

    private Authentication userDetailsAuthentication(String username) {
        return new TestingAuthenticationToken(
                new User(username, "n/a", AuthorityUtils.NO_AUTHORITIES),
                "n/a",
                AuthorityUtils.NO_AUTHORITIES
        );
    }

    private Authentication namedAuthentication(String name) {
        return new TestingAuthenticationToken(name, "n/a", AuthorityUtils.NO_AUTHORITIES);
    }
}
