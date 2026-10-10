package com.example.panel.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DialogAuthorizationServiceTest {

    private PermissionService permissionService;
    private DialogAuditService dialogAuditService;
    private LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard;
    private DialogAuthorizationService service;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        permissionService = mock(PermissionService.class);
        dialogAuditService = mock(DialogAuditService.class);
        legacyTicketIdJdbcGuard = mock(LegacyTicketIdJdbcGuard.class);
        authentication = mock(Authentication.class);
        service = new DialogAuthorizationService(
                permissionService,
                dialogAuditService,
                legacyTicketIdJdbcGuard
        );
        when(permissionService.hasAuthority(authentication, "PAGE_DIALOGS")).thenReturn(true);
    }

    @Test
    void deniesAmbiguousTicketBeforeActionAudit() {
        when(legacyTicketIdJdbcGuard.hasUniqueTicket("T-AMB")).thenReturn(false);

        ResponseEntity<Map<String, Object>> response = service.requirePermission(
                authentication,
                "can_reply",
                "ai_suggestions",
                "T-AMB"
        );

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("success", false)
                .containsEntry("error", "Диалог не найден");
        verify(legacyTicketIdJdbcGuard).hasUniqueTicket("T-AMB");
        verifyNoInteractions(dialogAuditService);
    }

    @Test
    void permitsUniqueTicket() {
        when(legacyTicketIdJdbcGuard.hasUniqueTicket("T-UNIQUE")).thenReturn(true);

        assertThat(service.requirePermission(authentication, "can_reply", "reply", "T-UNIQUE")).isNull();

        verify(legacyTicketIdJdbcGuard).hasUniqueTicket("T-UNIQUE");
        verifyNoInteractions(dialogAuditService);
    }

    @Test
    void doesNotRequireTicketGuardForGlobalAction() {
        assertThat(service.requirePermission(authentication, "can_reply", "ai_reviews_queue", null)).isNull();

        verifyNoInteractions(legacyTicketIdJdbcGuard, dialogAuditService);
    }

    @Test
    void preservesForbiddenResponseWithoutTicketLookup() {
        Authentication deniedAuthentication = mock(Authentication.class);

        ResponseEntity<Map<String, Object>> response = service.requirePermission(
                deniedAuthentication,
                "can_reply",
                "reply",
                "T-DENIED"
        );

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(legacyTicketIdJdbcGuard);
        verify(dialogAuditService).logDialogActionAudit(
                "T-DENIED",
                "anonymous",
                "reply",
                "forbidden",
                "Недостаточно прав: can_reply"
        );
    }
}
