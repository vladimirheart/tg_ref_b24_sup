package com.example.panel.controller;

import com.example.panel.security.SecurityConfig;
import com.example.panel.service.AdminStorageInventoryService;
import com.example.panel.service.PermissionService;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminStorageInventoryApiController.class)
@Import({SecurityConfig.class, PermissionService.class})
class AdminStorageInventoryIdentityWebMvcTest {
    private static final String ENDPOINT = "/api/admin/storage-inventory/run";
    @Autowired private MockMvc mvc;
    @MockBean private AdminStorageInventoryService inventory;

    @BeforeEach void response() throws Exception {
        Path fixture = Path.of("fixture");
        when(inventory.runInventory(any())).thenReturn(new AdminStorageInventoryService.StorageInventoryRunResult(
            7, 1L, fixture, fixture, "fixture report", fixture, fixture, fixture, fixture, Map.of("fixture", true)));
    }

    @ParameterizedTest
    @ValueSource(strings = {" admin", "admin ", " admin ", "\tadmin", "\nadmin", "\radmin", "AdMiN\t", "admin\r\n",
        "\u2003admin\u2003", "administrator", "admin-user", "9007199254740993"})
    void pageAccessAndValidCsrfDoNotAuthorizeAnotherLiteralAccount(String username) throws Exception {
        mvc.perform(post(ENDPOINT).with(user(username).authorities(new SimpleGrantedAuthority("PAGE_SETTINGS")))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"top\":7}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(inventory);
    }

    static Stream<Arguments> administrators() {
        return Stream.concat(Stream.of("admin", "ADMIN", "AdMiN").map(name -> Arguments.of(name, "PAGE_SETTINGS")),
            Stream.of("ROLE_ADMIN", "ROLE_PORTAL_ADMIN").flatMap(role ->
                Stream.of("operator", " admin ", "9007199254740993").map(name -> Arguments.of(name, role))));
    }

    @ParameterizedTest @MethodSource("administrators")
    void establishedAdministratorsRetainPageAndCsrfProtectedDelegation(String username, String role) throws Exception {
        mvc.perform(post(ENDPOINT).with(user(username).authorities(new SimpleGrantedAuthority("PAGE_SETTINGS"), new SimpleGrantedAuthority(role)))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"top\":7}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.top").value(7)).andExpect(jsonPath("$.report.fixture").value(true));
        verify(inventory).runInventory(7);
        verifyNoMoreInteractions(inventory);
    }

    @ParameterizedTest @ValueSource(strings = {"ROLE_ADMIN", "ROLE_PORTAL_ADMIN", "BUSINESS_ADMIN", "ALL_BUSINESSES"})
    void systemOrBusinessRoleDoesNotReplaceExplicitPageAuthority(String role) throws Exception {
        mvc.perform(post(ENDPOINT).with(user("admin").authorities(new SimpleGrantedAuthority(role))).with(csrf()))
            .andExpect(status().isForbidden());
        verifyNoInteractions(inventory);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void administratorStillNeedsValidCsrf(boolean invalid) throws Exception {
        var request = post(ENDPOINT).with(user("admin").authorities(new SimpleGrantedAuthority("PAGE_SETTINGS")));
        if (invalid) request.with(csrf().useInvalidToken());
        mvc.perform(request).andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(inventory);
    }

    @Test void anonymousRequestWithCsrfCannotRunInventory() throws Exception {
        mvc.perform(post(ENDPOINT).with(csrf())).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
        verifyNoInteractions(inventory);
    }
}
