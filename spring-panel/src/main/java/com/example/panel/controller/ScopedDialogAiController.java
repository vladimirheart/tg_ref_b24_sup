package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.*;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;

@Controller
@RequestMapping("/business-dialog-ai/{publicId}")
@PreAuthorize("hasAuthority('PAGE_DIALOGS')")
public class ScopedDialogAiController {
    private final ScopedDialogAiWorkbenchService workbench;
    private final ScopedDialogAiPersistenceService persistence;
    private final ScopedAiSourceService sources;
    private final ScopedAiHandoffKnowledgeService knowledge;
    private final MultibusinessFeatureFlags flags;
    private final NavigationService navigation;
    public ScopedDialogAiController(ScopedDialogAiWorkbenchService workbench,ScopedDialogAiPersistenceService persistence,
            ScopedAiSourceService sources,ScopedAiHandoffKnowledgeService knowledge,MultibusinessFeatureFlags flags,NavigationService navigation) {
        this.workbench=workbench; this.persistence=persistence; this.sources=sources; this.knowledge=knowledge;
        this.flags=flags; this.navigation=navigation;
    }
    @GetMapping
    public String view(@PathVariable String publicId,Authentication auth,Model model,HttpServletResponse response) {
        UiEventTicketScope scope=scope(publicId,auth,response,null);
        var control=persistence.findControl(scope,auth).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("publicId",publicId); model.addAttribute("businessId",scope.businessId());
        model.addAttribute("control",control);
        model.addAttribute("canOperate",persistence.canOperate(scope,auth));
        model.addAttribute("state",persistence.findState(scope,auth).orElse(null));
        model.addAttribute("events",persistence.findEvents(scope,auth,10));
        boolean library=auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("PAGE_KNOWLEDGE_BASE"));
        model.addAttribute("library",library);
        model.addAttribute("canCurate",library && sources.canConfigure(scope.businessId(),auth.getName()));
        model.addAttribute("messages",library ? workbench.messages(scope,auth) : java.util.List.of());
        model.addAttribute("candidates",library ? knowledge.candidates(scope,auth.getName()) : java.util.List.of());
        model.addAttribute("sourceKey",UUID.randomUUID().toString());
        navigation.enrich(model,auth);
        return "businesses/dialog-ai";
    }
    @PostMapping("/control")
    public RedirectView control(@PathVariable String publicId,Authentication auth,HttpServletResponse response,
            @RequestParam Long businessId,@RequestParam(defaultValue="") String expectedUpdatedAt,
            @RequestParam(defaultValue="false") boolean aiDisabled,@RequestParam(defaultValue="false") boolean autoReplyBlocked,
            @RequestParam(defaultValue="") String reason) {
        var scope=scope(publicId,auth,response,businessId);
        if (reason.length()>500 || expectedUpdatedAt.length()>100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        if (!persistence.setControlExpected(scope,auth,expectedUpdatedAt,aiDisabled,autoReplyBlocked,reason)) conflict();
        return redirect(publicId);
    }
    @PostMapping("/memory")
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE') and hasAuthority('PAGE_DIALOGS')")
    public RedirectView memory(@PathVariable String publicId,Authentication auth,HttpServletResponse response,
            @RequestParam Long businessId,@RequestParam String key,@RequestParam String question,@RequestParam String answer,
            @RequestParam String title,@RequestParam String body,
            @RequestParam(defaultValue="false") boolean reviewed) {
        var scope=scope(publicId,auth,response,businessId);
        if (!sources.canConfigure(scope.businessId(),auth.getName())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var q=evidence(question); var a=evidence(answer);
        if (!workbench.createMemory(scope,auth,key,q.id(),a.id(),q.hash(),a.hash(),title,body,reviewed)) conflict();
        return redirect(publicId);
    }
    private UiEventTicketScope scope(String id,Authentication auth,HttpServletResponse response,Long expectedBusiness) {
        response.setHeader("Cache-Control","no-store");
        if (!flags.isFoundationEnabled() || !flags.isUiSelector()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (auth==null || !auth.isAuthenticated()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var s=workbench.resolve(id,auth).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (expectedBusiness!=null && !expectedBusiness.equals(s.businessId())) conflict();
        return s;
    }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<String> malformed() { return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body("Некорректные параметры AI"); }
    private void conflict() { throw new ResponseStatusException(HttpStatus.CONFLICT,"Изменились данные или доступ. Обновите страницу."); }
    private Evidence evidence(String value) {
        if (value==null || !value.matches("[1-9][0-9]{0,18}:[a-f0-9]{64}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        try { int i=value.indexOf(':'); return new Evidence(Long.parseLong(value.substring(0,i)),value.substring(i+1)); }
        catch (NumberFormatException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST); }
    }
    private record Evidence(long id,String hash) { }
    private RedirectView redirect(String id) {
        RedirectView view=new RedirectView("/business-dialog-ai/"+id,true);
        view.setExposeModelAttributes(false); return view;
    }
}
