package com.example.panel.controller;

import com.example.panel.service.PermissionService;
import com.example.panel.service.WorkforceService;
import org.junit.jupiter.api.BeforeEach;
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
    private WorkforceService workforceService;

    @Mock
    private Authentication authentication;

    private LoginRedirectController controller;

    @BeforeEach
    void setUp() {
        when(authentication.getName()).thenReturn("operator");
        when(workforceService.requiresCheckIn("operator")).thenReturn(false);
        controller = new LoginRedirectController(permissionService, workforceService);
    }

    @Test
    void redirectsToCheckInBeforeNormalPageRoutingWhenShiftIsPending() {
        when(workforceService.requiresCheckIn("operator")).thenReturn(true);

        String redirect = controller.postLogin(authentication);

        assertThat(redirect).isEqualTo("redirect:/shift-check-in");
    }

    @Test
    void redirectsToClientsWhenDialogsPermissionIsMissing() {
        when(permissionService.hasAuthority(authentication, "PAGE_DIALOGS")).thenReturn(false);
        when(permissionService.hasAuthority(authentication, "PAGE_CLIENTS")).thenReturn(true);

        String redirect = controller.postLogin(authentication);

        assertThat(redirect).isEqualTo("redirect:/clients");
    }

    @Test
    void redirectsTo403WhenNoPagePermissionsExist() {
        when(permissionService.hasAuthority(eq(authentication), org.mockito.ArgumentMatchers.anyString())).thenReturn(false);

        String redirect = controller.postLogin(authentication);

        assertThat(redirect).isEqualTo("redirect:/error/403");
    }

    @Test
    void redirectsToUsersWhenOnlyUsersPermissionExists() {
        when(permissionService.hasAuthority(authentication, "PAGE_DIALOGS")).thenReturn(false);
        when(permissionService.hasAuthority(authentication, "PAGE_CLIENTS")).thenReturn(false);
        when(permissionService.hasAuthority(authentication, "PAGE_TASKS")).thenReturn(false);
        when(permissionService.hasAuthority(authentication, "PAGE_ANALYTICS")).thenReturn(false);
        when(permissionService.hasAuthority(authentication, "PAGE_SETTINGS")).thenReturn(false);
        when(permissionService.hasAuthority(authentication, "PAGE_USERS")).thenReturn(true);

        String redirect = controller.postLogin(authentication);

        assertThat(redirect).isEqualTo("redirect:/users");
    }
}
