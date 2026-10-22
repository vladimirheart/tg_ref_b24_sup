package com.example.panel.ui;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Проверяем реальный Thymeleaf-render публичных экранов, без изменения рабочих данных. */
@WebMvcTest(NeutralOpsPublicScreensWebMvcTest.PublicViewController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(NeutralOpsPublicScreensWebMvcTest.PublicViewController.class)
class NeutralOpsPublicScreensWebMvcTest {
    @Autowired private MockMvc mvc;

    @Test void publicScreensRenderWithThemeAndAccessibleActions() throws Exception {
        for (String view : List.of("auth/login", "error", "error/403", "error/404", "error/500", "workforce/check-in")) {
            mvc.perform(get("/__visual-public").param("view", view).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/css/app.css")))
                .andExpect(content().string(containsString("data-ui-page=")));
        }
    }

    @Controller static class PublicViewController {
        @GetMapping("/__visual-public") String render(@RequestParam String view, Model model) {
            if (!List.of("auth/login", "error", "error/403", "error/404", "error/500", "workforce/check-in").contains(view))
                throw new IllegalArgumentException("Unknown fixture");
            model.addAttribute("checkIn", Map.of("fullName", "Александр Константинопольский", "positionName", "Оператор поддержки", "timeZone", "Europe/Moscow", "unscheduled", false));
            model.addAttribute("shiftLabel", "09:00–18:00");
            model.addAttribute("status", "500");
            model.addAttribute("error", "Ошибка сервера");
            model.addAttribute("message", "Пожалуйста, повторите попытку позже.");
            return view;
        }
    }
}
