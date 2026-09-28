package com.example.panel.controller;

import com.example.panel.model.notification.NotificationDto;
import com.example.panel.model.notification.NotificationPage;
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
    void listReturnsCursorPageForUserDetailsPrincipal() throws Exception {
        when(notificationService.findPageForUser("operator", null, 20)).thenReturn(new NotificationPage(
                List.of(
                        new NotificationDto(
                                102L,
                                "Обращение №20260928-2 автоматически закрыто",
                                "/dialogs/T-102",
                                true,
                                OffsetDateTime.parse("2026-05-20T10:16:30+03:00")
                        ),
                        new NotificationDto(
                                101L,
                                "Новое сообщение в обращении №20260928-1",
                                "/dialogs/T-101",
                                false,
                                OffsetDateTime.parse("2026-05-20T10:15:30+03:00")
                        )
                ),
                "next-token",
                true
        ));

        mockMvc.perform(get("/api/notifications")
                        .param("limit", "20")
                        .principal(userDetailsAuthentication("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(102))
                .andExpect(jsonPath("$.items[0].read").value(true))
                .andExpect(jsonPath("$.items[1].id").value(101))
                .andExpect(jsonPath("$.items[1].read").value(false))
                .andExpect(jsonPath("$.nextCursor").value("next-token"))
                .andExpect(jsonPath("$.hasMore").value(true));

        verify(notificationService).findPageForUser("operator", null, 20);
    }

    @Test
    void listPassesCursorAndFallsBackToAllIdentityWhenAuthenticationIsMissing() throws Exception {
        when(notificationService.findPageForUser("all", "cursor-2", 20))
                .thenReturn(new NotificationPage(List.of(), null, false));

        mockMvc.perform(get("/api/notifications")
                        .param("cursor", "cursor-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.hasMore").value(false));

        verify(notificationService).findPageForUser("all", "cursor-2", 20);
    }

    @Test
    void invalidCursorReturnsBadRequest() throws Exception {
        when(notificationService.findPageForUser("operator", "bad", 20))
                .thenThrow(new IllegalArgumentException("bad cursor"));

        mockMvc.perform(get("/api/notifications")
                        .param("cursor", "bad")
                        .principal(userDetailsAuthentication("operator")))
                .andExpect(status().isBadRequest());
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

    @Test
    void markAllAsReadUsesAuthenticatedUsername() throws Exception {
        when(notificationService.markAllAsRead("watcher_peer")).thenReturn(3L);

        mockMvc.perform(post("/api/notifications/read-all").principal(userDetailsAuthentication("watcher_peer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.updated").value(3));

        verify(notificationService).markAllAsRead("watcher_peer");
    }

    @Test
    void markAllAsReadFallsBackToAllIdentityWhenAuthenticationIsMissing() throws Exception {
        when(notificationService.markAllAsRead("all")).thenReturn(2L);

        mockMvc.perform(post("/api/notifications/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.updated").value(2));

        verify(notificationService).markAllAsRead("all");
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
