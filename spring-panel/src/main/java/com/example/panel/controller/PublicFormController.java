package com.example.panel.controller;

import com.example.panel.model.publicform.PublicFormConfig;
import com.example.panel.service.PublicFormService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Controller
@RequestMapping("/public/forms")
@Validated
public class PublicFormController {

    private final PublicFormService publicFormService;

    public PublicFormController(PublicFormService publicFormService) {
        this.publicFormService = publicFormService;
    }

    @GetMapping("/{channelId}")
    public String view(@PathVariable String channelId,
                       @RequestParam(value = "token", required = false) String token,
                       @RequestParam(value = "dialog", required = false) String dialog,
                       Model model) {
        Optional<PublicFormConfig> config = publicFormService.loadConfig(channelId);
        if (config.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        String initialToken = Optional.ofNullable(token).filter(t -> !t.isBlank()).orElse(dialog);
        model.addAttribute("channelId", config.get().channelId());
        model.addAttribute("channelRef", channelId);
        model.addAttribute("channelName", config.get().channelName());
        model.addAttribute("initialToken", Optional.ofNullable(initialToken).orElse(""));
        return "public/form";
    }
}