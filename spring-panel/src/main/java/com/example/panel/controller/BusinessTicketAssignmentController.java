package com.example.panel.controller;

import com.example.panel.service.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;

@Controller
@RequestMapping("/business-dialog-assignment/{publicId}")
@PreAuthorize("hasAuthority('PAGE_DIALOGS')")
public class BusinessTicketAssignmentController {
    private final BusinessTicketAssignmentService service;
    private final NavigationService navigation;
    public BusinessTicketAssignmentController(BusinessTicketAssignmentService service,NavigationService navigation){
        this.service=service;this.navigation=navigation;
    }
    @GetMapping
    public String view(@PathVariable String publicId,Authentication auth,Model model,HttpServletResponse response){
        var state=find(publicId,auth,response);
        model.addAttribute("state",state);
        model.addAttribute("candidates",state.canOperate()?service.candidates(state.scope(),auth.getName()):java.util.List.of());
        model.addAttribute("events",service.recent(state.scope(),auth.getName()));
        navigation.enrich(model,auth);
        return "businesses/dialog-assignment";
    }
    @PostMapping("/{action:assign|take}")
    public RedirectView change(@PathVariable String publicId,@PathVariable String action,Authentication auth,HttpServletResponse response,
            @RequestParam Long ownerId,@RequestParam String rawId,@RequestParam Long businessId,@RequestParam long revision,
            @RequestParam(required=false) Long targetId){
        var state=find(publicId,auth,response);
        if(!state.canOperate())throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var expected=new UiEventTicketScope(ownerId,rawId,publicId,"BUSINESS",businessId);
        if(!state.scope().equals(expected)||state.revision()!=revision
                ||!service.change(expected,auth.getName(),revision,targetId,action.equals("take")))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Данные или права изменились. Обновите страницу.");
        var redirect=new RedirectView("/business-dialog-assignment/"+publicId,true);
        redirect.setExposeModelAttributes(false);return redirect;
    }
    private BusinessTicketAssignmentService.State find(String id,Authentication auth,HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");
        if(auth==null||!auth.isAuthenticated())throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return service.find(id,auth.getName()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<String> malformed(){return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body("Некорректные параметры назначения");}
}
