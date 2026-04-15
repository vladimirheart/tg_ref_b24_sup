package com.example.panel.controller;

import com.example.panel.service.DialogMacroService;
import com.fasterxml.jackson.annotation.JsonAlias;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/dialogs")
public class DialogMacroController {

    private final DialogMacroService dialogMacroService;

    public DialogMacroController(DialogMacroService dialogMacroService) {
        this.dialogMacroService = dialogMacroService;
    }

    @PostMapping("/macro/dry-run")
    public ResponseEntity<?> dryRunMacro(@RequestBody(required = false) MacroDryRunRequest request,
                                         Authentication authentication) {
        if (request == null || !StringUtils.hasText(request.templateText())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "template_text is required"
            ));
        }
        String operator = authentication != null ? authentication.getName() : null;
        DialogMacroService.MacroDryRunResponse result = dialogMacroService.dryRun(
                request.ticketId(),
                request.templateText(),
                operator,
                request.variables()
        );
        return ResponseEntity.ok(Map.of(
                "success", true,
                "rendered_text", result.renderedText(),
                "used_variables", result.usedVariables(),
                "missing_variables", result.missingVariables()
        ));
    }

    @GetMapping("/macro/variables")
    public Map<String, Object> macroVariables(@RequestParam(value = "ticketId", required = false) String ticketId,
                                              Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : null;
        return Map.of(
                "success", true,
                "variables", dialogMacroService.loadVariables(ticketId, operator)
        );
    }

    public record MacroDryRunRequest(@JsonAlias({"ticket_id", "ticketId"}) String ticketId,
                                     @JsonAlias({"template_text", "templateText", "text"}) String templateText,
                                     Map<String, String> variables) {
    }
}
