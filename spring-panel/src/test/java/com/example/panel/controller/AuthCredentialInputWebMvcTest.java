package com.example.panel.controller;

import com.example.panel.service.PanelUserPhotoService;
import com.example.panel.service.PermissionService;
import com.example.panel.service.SharedConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthManagementApiController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthCredentialInputWebMvcTest {
    @Autowired MockMvc mvc;
    @Autowired AuthManagementApiController controller;
    @MockBean(name = "usersJdbcTemplate") JdbcTemplate jdbc;
    @MockBean SharedConfigService shared;
    @MockBean PermissionService permissions;
    @MockBean PasswordEncoder encoder;
    @MockBean PanelUserPhotoService photos;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(controller, "userColumns", Set.of("username", "password", "password_hash", "full_name", "enabled"));
        when(permissions.isSuperUser(any())).thenReturn(true);
        when(encoder.encode(anyString())).thenReturn("fixture-hash");
    }

    static Stream<String> invalidLogins() {
        return Stream.of("", "   ", "re ader", "\treader", "reader\t", "reader\n", "\rreader", "иван", "张三", "a/b", "a,b", "a;b", "a\\b", "a!", "a🙂", "a\u00a0b", "\u00a0reader\u00a0");
    }

    static Stream<String> validPasswords() {
        return Stream.of("secret", " secret ", "p a s s", "Привет", "🔑 пароль", "a".repeat(72), "я".repeat(36), "quote'\"\\@-_");
    }

    static Stream<String> invalidPasswords() {
        return Stream.of("   ", "a\0b", "a\tb", "a\nb", "a\rb", "a\u007fb", "a\u0085b", "a".repeat(73), "я".repeat(37));
    }

    private void submit(MockHttpServletRequestBuilder request, Map<String, Object> body, boolean success) throws Exception {
        mvc.perform(request.contentType("application/json").content(json.writeValueAsBytes(body)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(success));
    }

    private void noWrite() {
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verify(encoder, never()).encode(any());
        verifyNoInteractions(photos);
    }

    private Object[] updateArguments() {
        var args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), args.capture());
        return args.getValue();
    }

    @ParameterizedTest @MethodSource("invalidLogins")
    void createRejectsInvalidLoginBeforeEncodingOrWriting(String login) throws Exception {
        submit(post("/api/users"), Map.of("username", login, "password", "secret"), false); noWrite();
    }

    @ParameterizedTest @MethodSource("invalidLogins")
    void renameRejectsInvalidLoginBeforeAnyWrite(String login) throws Exception {
        submit(patch("/api/users/7"), Map.of("username", login, "password", "secret", "full_name", "Changed"), false); noWrite();
    }

    @ParameterizedTest @ValueSource(strings = {"a", "A_Z-9.@", " reader ", "  reader  "})
    void creationUsesTrimmedAsciiLogin(String login) throws Exception {
        submit(post("/api/users"), Map.of("username", login, "password", "secret"), true);
        assertThat(updateArguments()[0]).isEqualTo(login.trim());
    }

    @ParameterizedTest @ValueSource(strings = {"a", "A_Z-9.@", " reader ", "  reader  "})
    void renameUsesTrimmedAsciiLogin(String login) throws Exception {
        submit(put("/api/users/7"), Map.of("username", login), true);
        assertThat(updateArguments()).containsExactly(login.trim(), 7L);
    }

    @ParameterizedTest @MethodSource("validPasswords")
    void creationHashesExactPasswordIncludingSpacesAndUnicode(String password) throws Exception {
        submit(post("/api/users"), Map.of("username", "reader", "password", password), true);
        verify(encoder).encode(password);
        assertThat(updateArguments()).contains("fixture-hash");
    }

    @ParameterizedTest @MethodSource("validPasswords")
    void changeHashesExactPasswordIncludingSpacesAndUnicode(String password) throws Exception {
        submit(put("/api/users/7"), Map.of("password", password), true);
        verify(encoder).encode(password);
        assertThat(updateArguments()).containsExactly("fixture-hash", "fixture-hash", 7L);
    }

    @ParameterizedTest @MethodSource("invalidPasswords")
    void creationRejectsUnusablePasswordBeforeEncodingOrWriting(String password) throws Exception {
        submit(post("/api/users"), Map.of("username", "reader", "password", password), false); noWrite();
    }

    @ParameterizedTest @MethodSource("invalidPasswords")
    void changeRejectsUnusablePasswordBeforeAnyWrite(String password) throws Exception {
        submit(put("/api/users/7"), Map.of("password", password, "full_name", "Changed"), false); noWrite();
    }

    @Test void creationRejectsDuplicateAfterTrimmingAndCaseFoldLookup() throws Exception {
        when(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE lower(username) = lower(?)", Integer.class, "READER")).thenReturn(1);
        submit(post("/api/users"), Map.of("username", " READER ", "password", "secret"), false); noWrite();
    }

    @Test void renameRejectsDuplicateBeforeProfileAndPasswordWrites() throws Exception {
        when(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE lower(username) = lower(?) AND id <> ?", Integer.class, "READER", 7L)).thenReturn(1);
        submit(patch("/api/users/7"), Map.of("username", " READER ", "password", "secret", "full_name", "Changed"), false); noWrite();
    }

    @Test void unchangedLegacyLoginDoesNotGetRenamedWhenProfileIsSaved() throws Exception {
        when(jdbc.queryForList("SELECT username FROM users WHERE id = ?", 7L)).thenReturn(List.of(Map.of("username", " legacy ")));
        submit(patch("/api/users/7"), Map.of("username", " legacy ", "full_name", "Changed"), true);
        verify(jdbc).update("UPDATE users SET full_name = ? WHERE id = ?", "Changed", 7L);
        verify(encoder, never()).encode(any());
    }

    @Test void profileWithoutUsernameDoesNotTouchLegacyAccountKey() throws Exception {
        submit(patch("/api/users/7"), Map.of("full_name", "Changed"), true);
        verify(jdbc).update("UPDATE users SET full_name = ? WHERE id = ?", "Changed", 7L);
        verify(jdbc, never()).queryForList(eq("SELECT username FROM users WHERE id = ?"), any(Object[].class));
    }

    @Test void unauthorizedCreationStillDoesNotWriteOrEncode() throws Exception {
        when(permissions.isSuperUser(any())).thenReturn(false);
        submit(post("/api/users"), Map.of("username", "reader", "password", "secret"), false); noWrite();
    }

    @Test void unauthorizedCredentialChangeStillDoesNotWriteOrEncode() throws Exception {
        when(permissions.isSuperUser(any())).thenReturn(false);
        submit(patch("/api/users/7"), Map.of("username", "reader", "password", "secret"), false); noWrite();
    }
}
