package com.example.panel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class DialogAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(DialogAuthorizationService.class);

    private final PermissionService permissionService;
    private final DialogAuditService dialogAuditService;

    public DialogAuthorizationService(PermissionService permissionService,
                                      DialogAuditService dialogAuditService) {
        this.permissionService = permissionService;
        this.dialogAuditService = dialogAuditService;
    }

    public Map<String, Object> resolveWorkspacePermissions(Authentication authentication) {
        boolean canDialog = permissionService.hasAuthority(authentication, "PAGE_DIALOGS");
        boolean canBulk = canDialog && (permissionService.hasAuthority(authentication, "DIALOG_BULK_ACTIONS")
                || permissionService.hasAuthority(authentication, "ROLE_ADMIN"));
        return Map.of(
                "can_reply", canDialog,
                "can_assign", canDialog,
                "can_close", canDialog,
                "can_snooze", canDialog,
                "can_bulk", canBulk
        );
    }

    public ResponseEntity<Map<String, Object>> requirePermission(Authentication authentication,
                                                                 String permission,
                                                                 String action,
                                                                 String ticketId) {
        Map<String, Object> permissions = resolveWorkspacePermissions(authentication);
        boolean allowed = Boolean.TRUE.equals(permissions.get(permission));
        if (allowed) {
            return null;
        }
        String operator = authentication != null ? authentication.getName() : null;
        logDialogAction(operator, ticketId, action, "forbidden", "Недостаточно прав: " + permission);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("success", false, "error", "Недостаточно прав для выполнения действия"));
    }

    public void logDialogAction(String actor, String ticketId, String action, String result, String detail) {
        String safeActor = actor != null ? actor : "anonymous";
        String safeDetail = detail != null ? detail : "";
        log.info("Dialog action audit: actor='{}', ticket='{}', action='{}', result='{}', detail='{}'",
                safeActor,
                ticketId,
                action,
                result,
                safeDetail);
        if (ticketId != null) {
            dialogAuditService.logDialogActionAudit(ticketId, safeActor, action, result, safeDetail);
        }
    }
}
