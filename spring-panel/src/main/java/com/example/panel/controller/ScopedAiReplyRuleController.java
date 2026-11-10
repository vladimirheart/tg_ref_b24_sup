package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.NavigationService;
import com.example.panel.service.ScopedAiReplyRuleService;
import com.example.panel.service.ScopedAiSourceService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
@RequestMapping("/business-ai-rules")
@PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
public class ScopedAiReplyRuleController {
    private final ScopedAiSourceService sources;
    private final ScopedAiReplyRuleService rules;
    private final MultibusinessFeatureFlags flags;
    private final NavigationService navigation;
    public ScopedAiReplyRuleController(ScopedAiSourceService sources,ScopedAiReplyRuleService rules,
                                      MultibusinessFeatureFlags flags,NavigationService navigation) {
        this.sources=sources;this.rules=rules;this.flags=flags;this.navigation=navigation;
    }
    @GetMapping
    public String view(Authentication auth,Model model,HttpServletResponse response,@RequestParam long businessId,
                       @RequestParam ScopedAiSourceService.Kind kind,@RequestParam String key,@RequestParam(required=false) String before) {
        var source=selected(auth,response,businessId,kind,key);
        if(before!=null && !before.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        model.addAttribute("source",source);model.addAttribute("ruleKey",UUID.randomUUID().toString());
        var rows=rules.list(businessId,auth,source.id(),before);
        var visible=rows.size()>25?rows.subList(0,25):rows;
        model.addAttribute("rules",visible);model.addAttribute("nextBefore",rows.size()>25?visible.get(24).key():null);
        navigation.enrich(model,auth);
        return "businesses/ai-reply-rules";
    }
    @PostMapping("/create")
    public RedirectView create(Authentication auth,HttpServletResponse response,@RequestParam long businessId,
            @RequestParam ScopedAiSourceService.Kind kind,@RequestParam String key,@RequestParam long sourceId,
            @RequestParam long sourceRevision,@RequestParam String ruleKey,@RequestParam String trigger,
            @RequestParam(defaultValue="false") boolean reviewed) {
        var source=selected(auth,response,businessId,kind,key);
        if(!reviewed || trigger.isBlank() || trigger.length()>500) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        if(source.id()!=sourceId || source.revision()!=sourceRevision
            || !rules.create(businessId,auth,ruleKey,trigger,sourceId,sourceRevision,reviewed))
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        return redirect(businessId,kind,key);
    }
    @PostMapping("/revoke")
    public RedirectView revoke(Authentication auth,HttpServletResponse response,@RequestParam long businessId,
            @RequestParam ScopedAiSourceService.Kind kind,@RequestParam String key,@RequestParam String ruleKey,@RequestParam long revision) {
        selected(auth,response,businessId,kind,key);
        if(!rules.revoke(businessId,auth,ruleKey,revision)) throw new ResponseStatusException(HttpStatus.CONFLICT);
        return redirect(businessId,kind,key);
    }
    private ScopedAiSourceService.Source selected(Authentication auth,HttpServletResponse response,long businessId,
                                                 ScopedAiSourceService.Kind kind,String key) {
        response.setHeader("Cache-Control","no-store");
        if(!flags.isFoundationEnabled() || !flags.isUiSelector()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if(!sources.canConfigure(businessId,auth)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return sources.find(businessId,auth,kind,key).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    private RedirectView redirect(long businessId,ScopedAiSourceService.Kind kind,String key) {
        String url=UriComponentsBuilder.fromPath("/business-ai-rules").queryParam("businessId",businessId)
            .queryParam("kind",kind).queryParam("key",key).build().encode().toUriString();
        var view=new RedirectView(url,true);view.setExposeModelAttributes(false);return view;
    }
}
