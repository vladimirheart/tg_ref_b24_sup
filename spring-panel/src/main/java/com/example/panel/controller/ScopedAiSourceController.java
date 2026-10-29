package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.NavigationService;
import com.example.panel.service.ScopedAiSourceService;
import com.example.panel.service.ScopedAiSourceService.BusinessOption;
import com.example.panel.service.ScopedAiSourceService.Kind;
import com.example.panel.service.ScopedAiSourceService.Status;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
@RequestMapping("/business-ai-sources")
@PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
public class ScopedAiSourceController {
    private static final int PAGE_SIZE=25;
    private final ScopedAiSourceService sources;
    private final NavigationService navigation;
    private final MultibusinessFeatureFlags flags;

    public ScopedAiSourceController(ScopedAiSourceService sources,NavigationService navigation,MultibusinessFeatureFlags flags) {
        this.sources=sources;
        this.navigation=navigation;
        this.flags=flags;
    }

    @GetMapping
    public String catalog(Authentication authentication,Model model,HttpServletResponse response,
            @RequestParam(required=false) Long businessId,@RequestParam(required=false) Kind kind,
            @RequestParam(required=false) Status status,@RequestParam(defaultValue="") String q,
            @RequestParam(required=false) Long before) {
        enabled(response);
        if (q.length()>200 || (before!=null && before<1)) badRequest();
        var businesses=sources.availableBusinesses(actor(authentication));
        model.addAttribute("businesses",businesses);
        model.addAttribute("q",q);
        model.addAttribute("kind",kind);
        model.addAttribute("status",status);
        if (businessId!=null) {
            var business=businesses.stream().filter(b->b.id()==businessId).findFirst()
                .orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN));
            var rows=sources.catalog(businessId,actor(authentication),kind,status,q,before,PAGE_SIZE+1);
            boolean more=rows.size()>PAGE_SIZE;
            var visible=more ? rows.subList(0,PAGE_SIZE) : rows;
            model.addAttribute("business",business);
            model.addAttribute("sources",visible);
            model.addAttribute("nextBefore",more ? visible.get(visible.size()-1).id() : null);
            model.addAttribute("canConfigure",sources.canConfigure(businessId,actor(authentication)));
        }
        navigation.enrich(model,authentication);
        return "businesses/ai-sources";
    }

    @GetMapping("/new")
    public String createForm(Authentication authentication,Model model,HttpServletResponse response,@RequestParam Long businessId) {
        enabled(response);
        var business=selected(businessId,authentication);
        configure(businessId,authentication);
        model.addAttribute("business",business);
        model.addAttribute("isNew",true);
        model.addAttribute("canConfigure",true);
        model.addAttribute("sourceKey",UUID.randomUUID().toString());
        model.addAttribute("sourceTitle","");
        model.addAttribute("sourceBody","");
        navigation.enrich(model,authentication);
        return "businesses/ai-source";
    }

    @GetMapping("/edit")
    public String edit(Authentication authentication,Model model,HttpServletResponse response,
            @RequestParam Long businessId,@RequestParam Kind kind,@RequestParam String key) {
        enabled(response);
        validKey(key);
        var business=selected(businessId,authentication);
        var source=sources.find(businessId,actor(authentication),kind,key)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("business",business);
        model.addAttribute("isNew",false);
        model.addAttribute("canConfigure",sources.canConfigure(businessId,actor(authentication)));
        model.addAttribute("sourceId",source.id());
        model.addAttribute("sourceKey",source.key());
        model.addAttribute("sourceKind",source.kind());
        model.addAttribute("sourceStatus",source.status());
        model.addAttribute("revision",source.revision());
        model.addAttribute("sourceTitle",source.title());
        model.addAttribute("sourceBody",source.body());
        model.addAttribute("history",sources.history(businessId,actor(authentication),kind,key,10));
        navigation.enrich(model,authentication);
        return "businesses/ai-source";
    }

    @PostMapping("/create")
    public RedirectView create(Authentication authentication,HttpServletResponse response,
            @RequestParam Long businessId,@RequestParam String key,@RequestParam String title,@RequestParam String body) {
        enabled(response);
        selected(businessId,authentication);
        configure(businessId,authentication);
        validContent(key,title,body);
        if (!sources.createKnowledge(businessId,authentication,key,title,body)) conflict();
        return redirect(businessId,Kind.KNOWLEDGE,key);
    }

    @PostMapping("/revise")
    public RedirectView revise(Authentication authentication,HttpServletResponse response,
            @RequestParam Long businessId,@RequestParam Kind kind,@RequestParam String key,
            @RequestParam long sourceId,@RequestParam long revision,@RequestParam String title,
            @RequestParam String body,@RequestParam Status status) {
        enabled(response);
        selected(businessId,authentication);
        configure(businessId,authentication);
        validContent(key,title,body);
        if (sourceId<1 || revision<1) badRequest();
        if (!sources.reviseExpected(businessId,authentication,kind,key,sourceId,revision,title,body,status)) conflict();
        return redirect(businessId,kind,key);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<String> malformedParameter() {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body("Некорректные параметры материала");
    }

    private BusinessOption selected(Long businessId,Authentication authentication) {
        if (businessId==null || businessId<1) badRequest();
        return sources.availableBusinesses(actor(authentication)).stream().filter(b->b.id()==businessId)
            .findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN));
    }

    private void configure(Long businessId,Authentication authentication) {
        if (!sources.canConfigure(businessId,actor(authentication))) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    private void enabled(HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        if (!flags.isFoundationEnabled() || !flags.isUiSelector()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private Authentication actor(Authentication authentication) {
        if (authentication==null || !authentication.isAuthenticated()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return authentication;
    }

    private void validKey(String key) {
        if (key==null || key.isBlank() || key.length()>200 || key.chars().anyMatch(Character::isISOControl)) badRequest();
    }
    private void validContent(String key,String title,String body) {
        validKey(key);
        if (title==null || title.isBlank() || title.length()>300 || body==null || body.isBlank() || body.length()>16000
                || title.indexOf('\0')>=0 || body.indexOf('\0')>=0) badRequest();
    }
    private void badRequest() { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Некорректные данные материала"); }
    private void conflict() { throw new ResponseStatusException(HttpStatus.CONFLICT,"Материал не сохранён. Обновите страницу и проверьте версию и доступ."); }
    private RedirectView redirect(Long businessId,Kind kind,String key) {
        String url=UriComponentsBuilder.fromPath("/business-ai-sources/edit")
            .queryParam("businessId",businessId).queryParam("kind",kind.name()).queryParam("key","{key}")
            .encode().buildAndExpand(key).toUriString();
        RedirectView view=new RedirectView(url,true);
        view.setExposeModelAttributes(false);
        return view;
    }
}
