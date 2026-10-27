package com.example.panel.controller;

import com.example.panel.service.PanelUserPhotoService;
import com.example.panel.service.PermissionService;
import com.example.panel.service.SharedConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Conservative collision query contract; no database or existing-key lookup is normalized here. */
class AuthLegacyLoginCollisionTest {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    AuthManagementApiController controller;
    PasswordEncoder encoder;
    final ObjectMapper json = new ObjectMapper();
    final UsernamePasswordAuthenticationToken actor = new UsernamePasswordAuthenticationToken("admin", "fixture", List.of());

    @BeforeEach void setup() {
        var permissions = mock(PermissionService.class); when(permissions.isSuperUser(any())).thenReturn(true);
        encoder = mock(PasswordEncoder.class); when(encoder.encode(anyString())).thenReturn("fixture-hash");
        controller = new AuthManagementApiController(jdbc, mock(SharedConfigService.class), permissions, encoder, mock(PanelUserPhotoService.class), json);
        ReflectionTestUtils.setField(controller, "userColumns", Set.of("username", "password", "full_name"));
        when(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE lower(trim(username)) = lower(?)", Integer.class, "reader")).thenReturn(1);
        when(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE lower(trim(username)) = lower(?) AND id <> ?", Integer.class, "reader", 7L)).thenReturn(1);
    }

    @ParameterizedTest @ValueSource(strings = {"create", "put", "patch"})
    void newCanonicalLoginCannotCollideWithTrimmedLegacyAccount(String mode) throws Exception {
        Map<String,Object> payload = Map.of("username", " reader ", "password", "secret", "full_name", "Changed");
        Map<String,Object> response;
        if (mode.equals("create")) {
            var request = new MockHttpServletRequest("POST", "/api/users"); request.setContentType("application/json"); request.setContent(json.writeValueAsBytes(payload));
            response = controller.createUser(actor, request);
        } else response = controller.updateUser(7L, actor, payload);
        assertThat(response.get("success")).isEqualTo(false);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verify(encoder, never()).encode(any());
    }

    @Test void explicitRenameCanRemoveOwnEdgeSpacesWithoutAnotherCollision() {
        when(jdbc.queryForList("SELECT username FROM users WHERE id = ?", 7L)).thenReturn(List.of(Map.of("username", " old ")));
        var response = controller.updateUser(7L, actor, Map.of("username", "old"));
        assertThat(response.get("success")).isEqualTo(true);
        verify(jdbc).update("UPDATE users SET username = ? WHERE id = ?", "old", 7L);
    }
}
