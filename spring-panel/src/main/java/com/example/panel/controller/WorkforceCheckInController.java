package com.example.panel.controller;

import com.example.panel.service.WorkforceService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Controller
public class WorkforceCheckInController {

    private static final DateTimeFormatter SHIFT_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final WorkforceService workforceService;

    public WorkforceCheckInController(WorkforceService workforceService) {
        this.workforceService = workforceService;
    }

    @GetMapping("/shift-check-in")
    public String checkInPage(Authentication authentication, Model model) {
        WorkforceService.CheckInContext context = workforceService.getCheckInContext(authentication.getName());
        if (!context.enabled() || !context.checkInRequired() || !context.pending()) {
            return "redirect:/post-login";
        }
        model.addAttribute("checkIn", context);
        model.addAttribute("shiftLabel", shiftLabel(context));
        return "workforce/check-in";
    }

    @PostMapping("/shift-check-in")
    public String confirmCheckIn(Authentication authentication, RedirectAttributes redirectAttributes) {
        WorkforceService.CheckInResult result = workforceService.confirmCheckIn(authentication.getName());
        if ("failed".equals(result.notificationStatus())) {
            redirectAttributes.addFlashAttribute(
                    "workforceNotificationWarning",
                    "Смена подтверждена, но оповещение в чат не отправлено. Администратор увидит ошибку в истории смен."
            );
        }
        return "redirect:/post-login";
    }

    private String shiftLabel(WorkforceService.CheckInContext context) {
        if (context.unscheduled() || context.scheduledStartAt() == null || context.scheduledEndAt() == null) {
            return "Вне расписания";
        }
        ZoneId zone = ZoneId.of(context.timeZone());
        String start = SHIFT_FORMAT.format(context.scheduledStartAt().toInstant().atZone(zone));
        String end = SHIFT_FORMAT.format(context.scheduledEndAt().toInstant().atZone(zone));
        return start + " — " + end;
    }
}
