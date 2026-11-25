package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GeneralDialogNavigationTest {
    @Test void generalQueueNavigationRequiresItsCurrentExplicitReadGrantAndParentPage() {
        var permission=new PermissionService();
        var flags=new MultibusinessFeatureFlags();flags.getFoundation().setEnabled(true);flags.setGeneralTicketQueue(true);
        var navigation=new NavigationService(permission,null,null,null,flags);
        var access=mock(GeneralTicketAccessService.class);
        ReflectionTestUtils.setField(navigation,"generalTickets",access);
        var actor=UsernamePasswordAuthenticationToken.authenticated("fixture",null,AuthorityUtils.createAuthorityList("PAGE_DIALOGS"));
        when(access.requireAuthenticatedUserId(actor)).thenReturn(19L);
        assertThat(navigation.canViewGeneralDialogs(actor)).isFalse();
        when(access.canRead(19L)).thenReturn(true);
        assertThat(navigation.canViewGeneralDialogs(actor)).isTrue();
        flags.setGeneralTicketQueue(false);
        assertThat(navigation.canViewGeneralDialogs(actor)).isFalse();
        flags.setGeneralTicketQueue(true);
        var denied=UsernamePasswordAuthenticationToken.authenticated("fixture",null,AuthorityUtils.createAuthorityList("PAGE_DIALOGS","DENY_PAGE_DIALOGS"));
        assertThat(navigation.canViewGeneralDialogs(denied)).isFalse();
        verify(access,never()).requireAuthenticatedUserId(denied);
        when(access.requireAuthenticatedUserId(actor)).thenThrow(new org.springframework.security.access.AccessDeniedException("revoked"));
        assertThat(navigation.canViewGeneralDialogs(actor)).isFalse();
    }
}
