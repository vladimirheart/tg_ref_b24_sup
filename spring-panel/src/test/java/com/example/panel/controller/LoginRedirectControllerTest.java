package com.example.panel.controller;

import com.example.panel.service.PermissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginRedirectControllerTest {

    @Mock
    private PermissionService permissionService;

    @Mock
    private Authentication authentication;

    @Test
    void redirectsToClientsWhenDialogsPermissionIsMissing() {
        when(permissionService.hasAuthority(authentication, "PAGE_DIALOGS")).thenReturn(false);
        when(permissionService.hasAuthority(authentication, "PAGE_CLIENTS")).thenReturn(true);

        LoginRedirectController controller = new LoginRedirectController(permissionService);

        String redirect = controller.postLogin(authentication);

        assertThat(redirect).isEqualTo("redirect:/clients");
    }

    @Test
    void redirectsTo403WhenNoPagePermissionsExist() {
        when(permissionService.hasAuthority(eq(authentication), org.mockito.ArgumentMatchers.anyString())).thenReturn(false);

        LoginRedirectController controller = new LoginRedirectController(permissionService);

        String redirect = controller.postLogin(authentication);

        assertThat(redirect).isEqualTo("redirect:/error/403");
    }
}
