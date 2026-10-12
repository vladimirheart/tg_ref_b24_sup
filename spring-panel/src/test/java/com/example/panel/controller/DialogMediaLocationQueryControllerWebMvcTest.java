package com.example.panel.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.service.DialogAuthorizationService;
import com.example.panel.service.DialogQuickActionService;
import com.example.panel.service.IikoDepartmentLocationCatalogService;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.multipart.MultipartFile;

@WebMvcTest({DialogQuickActionsController.class, DialogLocationBusinessApiController.class})
@Import(DialogMediaLocationQueryControllerWebMvcTest.MethodSecurityConfiguration.class)
class DialogMediaLocationQueryControllerWebMvcTest {
    private static final String TICKET_ID = " T/100?&%#+ Ю ";
    private static final byte[] FILE_BYTES = {0, 1, (byte) 0xff, 13, 10};
    private static final MockMultipartFile FILE = new MockMultipartFile("file", "image+ Ю.png", "image/png", FILE_BYTES);
    private static final String BODY = """
            {"business":" Business ","locationType":" Office ","city":" City ","locationName":" Location "}
            """;

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfiguration { }

    @Autowired private MockMvc mockMvc;
    @MockBean private DialogQuickActionService quickActions;
    @MockBean private DialogAuthorizationService authorization;
    @MockBean private JdbcTemplate jdbc;
    @MockBean private IikoDepartmentLocationCatalogService catalog;

    static Stream<Arguments> deniedRoutes() {
        return Stream.of(403, 404).flatMap(code -> Stream.of("media", "location-business").map(route -> Arguments.of(route, code)));
    }

    private MockHttpServletRequestBuilder request(String route) {
        return ("media".equals(route) ? multipart("/api/dialog-ticket/media").file(FILE)
                : patch("/api/dialog-ticket/location-business").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .with(user("operator").authorities(new SimpleGrantedAuthority("PAGE_DIALOGS")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"media", "location-business"})
    void requiredIdIsCheckedBeforeServices(String route) throws Exception {
        mockMvc.perform(request(route).with(csrf())).andExpect(status().isBadRequest());
        verifyNoInteractions(quickActions, authorization, jdbc, catalog);
    }

    @ParameterizedTest
    @MethodSource("deniedRoutes")
    void permissionAndAmbiguityDenialsStopAllOperations(String route, int code) throws Exception {
        String permission = "media".equals(route) ? "can_reply" : "can_assign";
        String action = "media".equals(route) ? "reply_media" : "location_business";
        when(authorization.requirePermission(any(), eq(permission), eq(action), eq(TICKET_ID)))
                .thenReturn(ResponseEntity.status(code).body(Map.of("success", false, "error", "denied")));
        mockMvc.perform(request(route).param("ticketId", TICKET_ID).with(csrf()))
                .andExpect(status().is(code)).andExpect(jsonPath("$.error").value("denied"));
        verify(authorization).requirePermission(argThat(auth -> "operator".equals(auth.getName())),
                eq(permission), eq(action), eq(TICKET_ID));
        verifyNoInteractions(quickActions, jdbc, catalog);
    }

    @ParameterizedTest
    @ValueSource(strings = {"media", "location-business"})
    void bothMutationsStillRequireCsrf(String route) throws Exception {
        mockMvc.perform(request(route).param("ticketId", TICKET_ID)).andExpect(status().isForbidden());
        verifyNoInteractions(quickActions, authorization, jdbc, catalog);
    }

    @ParameterizedTest
    @ValueSource(strings = {"T-100", " T-100 ", " T/100?&%#+ Ю ", "T%2F+", " null "})
    void multipartPreservesOpaqueIdFileBytesMetadataAndOperator(String ticketId) throws Exception {
        when(quickActions.sendMediaReply(eq(ticketId), any(MultipartFile.class), eq(" caption "), eq(123L), eq("operator"), any()))
                .thenReturn(Map.of("success", true, "attachment", "stored/key", "messageType", "operator_photo"));
        mockMvc.perform(request("media").param("ticketId", ticketId).param("message", " caption ")
                        .param("replyToTelegramId", "123").with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.attachment").value("stored/key"))
                .andExpect(jsonPath("$.messageType").value("operator_photo"));
        ArgumentCaptor<MultipartFile> uploaded = ArgumentCaptor.forClass(MultipartFile.class);
        verify(quickActions).sendMediaReply(eq(ticketId), uploaded.capture(), eq(" caption "), eq(123L),
                eq("operator"), argThat(auth -> "operator".equals(auth.getName())));
        assertThat(uploaded.getValue().getBytes()).containsExactly(FILE_BYTES);
        assertThat(uploaded.getValue().getOriginalFilename()).isEqualTo("image+ Ю.png");
        assertThat(uploaded.getValue().getContentType()).isEqualTo("image/png");
        verify(authorization).requirePermission(any(), eq("can_reply"), eq("reply_media"), eq(ticketId));
        verify(authorization).logDialogAction("operator", ticketId, "reply_media", "success", "media_sent");
    }

    @Test
    void multipartKeepsOptionalFieldsNull() throws Exception {
        when(quickActions.sendMediaReply(eq(TICKET_ID), any(), isNull(), isNull(), eq("operator"), any()))
                .thenReturn(Map.of("success", true));
        mockMvc.perform(request("media").param("ticketId", TICKET_ID).with(csrf())).andExpect(status().isOk());
        verify(quickActions).sendMediaReply(eq(TICKET_ID), any(), isNull(), isNull(), eq("operator"), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/dialog-ticket/media", "/api/dialogs/T-100/media"})
    void multipartRequiresFileOnBothRoutes(String path) throws Exception {
        mockMvc.perform(multipart(path).param("ticketId", TICKET_ID)
                .with(user("operator")).with(csrf())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MISSING_PART"));
        verifyNoInteractions(quickActions, authorization);
    }

    @Test
    void multipartKeepsIoFailureAndAudit() throws Exception {
        when(quickActions.sendMediaReply(eq(TICKET_ID), any(), isNull(), isNull(), eq("operator"), any()))
                .thenThrow(new IOException("io failure"));
        mockMvc.perform(request("media").param("ticketId", TICKET_ID).with(csrf())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Не удалось обработать файл перед отправкой."));
        verify(authorization).logDialogAction("operator", TICKET_ID, "reply_media", "error", "Не удалось обработать файл перед отправкой.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"T-100", " T-100 ", " T/100?&%#+ Ю ", "T%2F+", " null "})
    void locationPatchUsesExactIdAndRetainsMetadataNormalization(String ticketId) throws Exception {
        stubLocationSuccess(ticketId);
        mockMvc.perform(request("location-business").param("ticketId", ticketId).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(1))
                .andExpect(jsonPath("$.business").value("Business")).andExpect(jsonPath("$.locationName").value("Location"));
        verify(authorization).requirePermission(any(), eq("can_assign"), eq("location_business"), eq(ticketId));
        verify(jdbc).queryForObject(anyString(), eq(Integer.class), eq(ticketId));
        verify(jdbc).update(anyString(), eq("Business"), eq("Office"), eq("City"), eq("Location"), eq("operator"), eq(ticketId));
        verify(authorization).logDialogAction("operator", ticketId, "location_business", "success", "dialog_metadata_updated");
        verifyNoInteractions(catalog);
    }

    private void stubLocationSuccess(String ticketId) {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(ticketId))).thenReturn(1);
        when(jdbc.update(anyString(), eq("Business"), eq("Office"), eq("City"), eq("Location"), eq("operator"), eq(ticketId)))
                .thenReturn(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/dialog-ticket/location-business", "/api/dialogs/T-100/location-business"})
    void locationPatchKeepsPageAuthorityOnBothRoutes(String path) throws Exception {
        mockMvc.perform(patch(path).param("ticketId", TICKET_ID).with(user("no-page")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        verifyNoInteractions(quickActions, authorization, jdbc, catalog);
    }

    @Test
    void legacyLocationRouteStillUpdatesSameMetadata() throws Exception {
        stubLocationSuccess("T-100");
        mockMvc.perform(patch("/api/dialogs/T-100/location-business").with(csrf())
                .with(user("operator").authorities(new SimpleGrantedAuthority("PAGE_DIALOGS")))
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk());
        verify(jdbc).update(anyString(), eq("Business"), eq("Office"), eq("City"), eq("Location"), eq("operator"), eq("T-100"));
    }

    @Test
    void catalogRemainsAtLegacyUrlAndRequiresPageAuthority() throws Exception {
        mockMvc.perform(get("/api/dialogs/location-business/catalog").with(user("no-page"))).andExpect(status().isForbidden());
        verifyNoInteractions(catalog, jdbc, authorization);
    }

    @Test
    void locationPatchKeepsMissingBodyValidation() throws Exception {
        mockMvc.perform(patch("/api/dialog-ticket/location-business").param("ticketId", TICKET_ID)
                .with(user("operator").authorities(new SimpleGrantedAuthority("PAGE_DIALOGS"))).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Параметры бизнеса и локации не переданы"));
        verifyNoInteractions(jdbc);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"locationType\":\"Office\"}", "{\"business\":\"Business\",\"city\":\"City\"}",
            "{\"business\":\"Business\",\"locationType\":\"Office\",\"locationName\":\"Location\"}"})
    void locationHierarchyIsStillValidatedBeforeSql(String body) throws Exception {
        mockMvc.perform(patch("/api/dialog-ticket/location-business").param("ticketId", TICKET_ID).with(csrf())
                .with(user("operator").authorities(new SimpleGrantedAuthority("PAGE_DIALOGS")))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(jdbc, catalog);
    }

    @Test
    void locationDefaultsStillClearMetadataWithoutChangingId() throws Exception {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(TICKET_ID))).thenReturn(1);
        when(jdbc.update(anyString(), isNull(), isNull(), isNull(), isNull(), eq("operator"), eq(TICKET_ID))).thenReturn(1);
        mockMvc.perform(patch("/api/dialog-ticket/location-business").param("ticketId", TICKET_ID).with(csrf())
                .with(user("operator").authorities(new SimpleGrantedAuthority("PAGE_DIALOGS")))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        verify(jdbc).update(anyString(), isNull(), isNull(), isNull(), isNull(), eq("operator"), eq(TICKET_ID));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void unknownLocationTicketDoesNotWrite(int count) throws Exception {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(TICKET_ID))).thenReturn(count);
        mockMvc.perform(request("location-business").param("ticketId", TICKET_ID).with(csrf())).andExpect(status().isNotFound());
        verify(jdbc).queryForObject(anyString(), eq(Integer.class), eq(TICKET_ID));
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void missingMessageCardStillReturnsConflict() throws Exception {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(TICKET_ID))).thenReturn(1);
        mockMvc.perform(request("location-business").param("ticketId", TICKET_ID).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Для диалога отсутствует карточка сообщения с реквизитами локации"));
        verify(authorization).requirePermission(any(), eq("can_assign"), eq("location_business"), eq(TICKET_ID));
    }
}
