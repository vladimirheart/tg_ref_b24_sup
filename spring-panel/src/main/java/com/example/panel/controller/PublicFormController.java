package com.example.panel.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/public/forms")
@Validated
public class PublicFormController {

    @GetMapping("/{channelId}")
    public String view(@PathVariable String channelId, Model model) {
        model.addAttribute("channelId", channelId);
        model.addAttribute("token", UUID.randomUUID().toString());
        return "public/form";
    }

    @RestController
    @RequestMapping("/api/public/forms")
    public static class Api {

        @GetMapping("/{channelId}/config")
        public Map<String, Object> config(@PathVariable String channelId) {
            return Map.of(
                    "success", true,
                    "channel", Map.of("id", channelId, "name", "Demo Channel")
            );
        }

        @PostMapping("/{channelId}/sessions")
        public ResponseEntity<?> createSession(@PathVariable String channelId, @Valid @RequestBody PublicFormRequest request) {
            if (request.message().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Опишите проблему"));
            }
            String token = UUID.randomUUID().toString();
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "token", token,
                    "ticketId", UUID.randomUUID().toString(),
                    "createdAt", OffsetDateTime.now().toString()
            ));
        }
    }

    public record PublicFormRequest(@NotBlank String message, String clientName, String contact) {
    }
}
