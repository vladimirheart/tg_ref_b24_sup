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
    private final BusinessTicketParticipantService participants;
    private final BusinessTicketWorkService work;
    public BusinessTicketAssignmentController(BusinessTicketAssignmentService service,NavigationService navigation,
            BusinessTicketParticipantService participants,BusinessTicketWorkService work){
        this.service=service;this.navigation=navigation;this.participants=participants;this.work=work;
    }
    @GetMapping
    public String view(@PathVariable String publicId,Authentication auth,Model model,HttpServletResponse response){
        var state=find(publicId,auth,response);
        model.addAttribute("state",state);
        model.addAttribute("candidates",state.canOperate()?service.candidates(state.scope(),auth.getName()):java.util.List.of());
        model.addAttribute("events",service.recent(state.scope(),auth.getName()));
        model.addAttribute("participantRevision",participants.revision(state.scope(),auth.getName()).orElse(null));
        model.addAttribute("members",participants.members(state.scope(),auth.getName()));
        model.addAttribute("memberCandidates",state.canOperate()?participants.candidates(state.scope(),auth.getName()):java.util.List.of());
        model.addAttribute("participantEvents",participants.recent(state.scope(),auth.getName()));
        var workState=work.find(publicId,auth.getName()).orElse(null);
        if(workState!=null&&!state.scope().equals(workState.scope()))workState=null;
        model.addAttribute("work",workState);
        model.addAttribute("workCategoryText",workState==null?"":String.join("\n",workState.categories()));
        model.addAttribute("workEvents",workState==null?java.util.List.of():work.recent(workState.scope(),auth.getName()));
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
                ||!service.change(expected,auth,revision,targetId,action.equals("take")))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Данные или права изменились. Обновите страницу.");
        var redirect=new RedirectView("/business-dialog-assignment/"+publicId,true);
        redirect.setExposeModelAttributes(false);return redirect;
    }
    @PostMapping("/participants/{action:add|remove}")
    public RedirectView participants(@PathVariable String publicId,@PathVariable String action,Authentication auth,HttpServletResponse response,
            @RequestParam Long ownerId,@RequestParam String rawId,@RequestParam Long businessId,
            @RequestParam long participantRevision,@RequestParam Long memberId){
        var state=find(publicId,auth,response);
        if(!state.canOperate())throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var expected=new UiEventTicketScope(ownerId,rawId,publicId,"BUSINESS",businessId);
        if(!state.scope().equals(expected)||!participants.change(expected,auth,participantRevision,memberId,action.equals("add")))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Список или права изменились. Обновите страницу.");
        var redirect=new RedirectView("/business-dialog-assignment/"+publicId,true);
        redirect.setExposeModelAttributes(false);return redirect;
    }
    @PostMapping("/lifecycle/{action:close|reopen|categories}")
    public RedirectView lifecycle(@PathVariable String publicId,@PathVariable String action,Authentication auth,HttpServletResponse response,
            @RequestParam Long ownerId,@RequestParam String rawId,@RequestParam Long businessId,
            @RequestParam long workRevision,@RequestParam String expectedStatus,@RequestParam(defaultValue="") String categoryText) {
        response.setHeader("Cache-Control","no-store");
        if(auth==null||!auth.isAuthenticated())throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var current=work.find(publicId,auth.getName()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        if(!current.canOperate())throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if(categoryText.length()>20000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        var expected=new UiEventTicketScope(ownerId,rawId,publicId,"BUSINESS",businessId);
        if(!current.scope().equals(expected)||current.revision()!=workRevision||!java.util.Objects.equals(current.status(),expectedStatus)
                ||!work.change(expected,auth,workRevision,expectedStatus,action,categoryText.lines().toList()))
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
