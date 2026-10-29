package com.example.panel.controller;

import com.example.panel.service.GeneralManualTicketCreationService;
import com.example.panel.service.NavigationService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;

@Controller
@RequestMapping("/general-dialogs/new")
@PreAuthorize("hasAuthority('PAGE_DIALOGS')")
public class GeneralManualTicketController {
    private final GeneralManualTicketCreationService service;
    private final NavigationService navigation;
    public GeneralManualTicketController(GeneralManualTicketCreationService service,NavigationService navigation) {
        this.service=service;this.navigation=navigation;
    }
    @GetMapping public String form(Authentication auth,Model model,HttpServletResponse response) {
        requireAllowed(auth,response);
        model.addAttribute("requestKey",java.util.UUID.randomUUID().toString().replace("-",""));
        navigation.enrich(model,auth);model.addAttribute("activePage","dialogs");return "general/new";
    }
    @PostMapping public RedirectView create(Authentication auth,HttpServletResponse response,
            @RequestParam String requestKey,@RequestParam String clientName,@RequestParam String problem) {
        requireAllowed(auth,response);
        if(!GeneralManualTicketCreationService.validInput(requestKey,clientName,problem))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        String id=service.create(auth,requestKey,clientName,problem)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"Данные или права изменились. Обновите форму."));
        var redirect=new RedirectView("/general-dialog-work/"+id,true);redirect.setExposeModelAttributes(false);return redirect;
    }
    private void requireAllowed(Authentication auth,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");response.setHeader("X-Content-Type-Options","nosniff");
        if(!service.enabled())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if(!service.canCreate(auth))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
}
