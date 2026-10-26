package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.DialogListReadService;
import com.example.panel.service.NavigationService;
import com.example.panel.service.SharedConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@Controller
public class DialogsController {

    private static final Logger log = LoggerFactory.getLogger(DialogsController.class);

    private final NavigationService navigationService;
    private final SharedConfigService sharedConfigService;
    private final MultibusinessFeatureFlags multibusinessFlags;
    private final DialogListReadService dialogListReadService;

    public DialogsController(NavigationService navigationService,
                             SharedConfigService sharedConfigService,
                             MultibusinessFeatureFlags multibusinessFlags,
                             DialogListReadService dialogListReadService) {
        this.navigationService = navigationService;
        this.sharedConfigService = sharedConfigService;
        this.multibusinessFlags = multibusinessFlags;
        this.dialogListReadService = dialogListReadService;
    }

    @GetMapping("/dialogs")
    @PreAuthorize("hasAuthority('PAGE_DIALOGS')")
    public String dialogsList(@RequestParam(name = "ticketId", required = false) String ticketId,
                              Authentication authentication,
                              Model model) {
        return renderDialogsPage(authentication, model, ticketId);
    }

    @GetMapping("/dialogs/{ticketId}")
    @PreAuthorize("hasAuthority('PAGE_DIALOGS')")
    public String dialogsWorkspaceTicket(@PathVariable String ticketId,
                                         Authentication authentication,
                                         Model model) {
        return renderDialogsPage(authentication, model, ticketId);
    }

    @GetMapping("/ai-ops")
    @PreAuthorize("hasAuthority('PAGE_DIALOGS')")
    public String aiOps(Authentication authentication, Model model) {
        String operatorIdentity = authentication != null ? authentication.getName() : "anonymous";
        navigationService.enrich(model, authentication);
        model.addAttribute("activePage", "ai-ops");
        model.addAttribute("operatorIdentity", operatorIdentity);
        return "dialogs/ai-ops";
    }

    private String renderDialogsPage(Authentication authentication, Model model, String initialDialogTicketId) {
        String operatorIdentity = authentication != null ? authentication.getName() : "anonymous";
        navigationService.enrich(model, authentication);
        model.addAttribute("activePage", "dialogs");
        model.addAttribute("initialDialogTicketId", initialDialogTicketId);
        model.addAttribute("operatorIdentity", operatorIdentity);
        model.addAttribute("businessSelectorAvailable",
                multibusinessFlags.isFoundationEnabled() && multibusinessFlags.isUiSelector());
        try {
            var payload = dialogListReadService.loadListPayload(operatorIdentity);
            model.addAttribute("summary", payload.get("summary"));
            model.addAttribute("dialogs", payload.get("dialogs"));
            Map<?, ?> myDialogs = (Map<?, ?>) payload.get("my_dialogs");
            model.addAttribute("myNewDialogs", myDialogs.get("new"));
            model.addAttribute("myUnansweredDialogs", myDialogs.get("unanswered"));
            model.addAttribute("myInWorkDialogs", myDialogs.get("in_work"));
            model.addAttribute("myDialogsCounts", myDialogs.get("counts"));
            model.addAttribute("dialogListPagination", payload.get("pagination"));
            model.addAttribute("settingsPayload", sharedConfigService.loadSettings());
            log.info("Loaded dialogs page for user {}", operatorIdentity);
        } catch (Exception ex) {
            log.error("Failed to load dialogs page for user {}", operatorIdentity, ex);
            throw ex;
        }
        return "dialogs/index";
    }
}
