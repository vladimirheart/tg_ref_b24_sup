package com.example.panel.controller;

import com.example.panel.service.GeneralTicketWorkService;
import com.example.panel.service.GeneralTicketReplyService;
import com.example.panel.service.GeneralTicketReplyChangeService;
import com.example.panel.service.GeneralTicketDocumentReplyService;
import org.springframework.web.multipart.MultipartFile;
import com.example.panel.service.GeneralTicketWorkTimeService;
import com.example.panel.service.GeneralTicketAssignmentService;
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
@RequestMapping("/general-dialog-work/{publicId}")
@PreAuthorize("hasAuthority('PAGE_DIALOGS')")
public class GeneralTicketWorkController {
    private final GeneralTicketWorkService work;
    private final NavigationService navigation;
    private final GeneralTicketReplyService replies;
    private final GeneralTicketWorkTimeService time;
    private final GeneralTicketAssignmentService assignments;
    private final GeneralTicketReplyChangeService changes;
    private final GeneralTicketDocumentReplyService documents;
    public GeneralTicketWorkController(GeneralTicketWorkService work,NavigationService navigation,GeneralTicketReplyService replies,
            GeneralTicketWorkTimeService time,GeneralTicketAssignmentService assignments,GeneralTicketReplyChangeService changes,GeneralTicketDocumentReplyService documents) {
        this.work=work;this.navigation=navigation;this.replies=replies;this.time=time;this.assignments=assignments;this.changes=changes;this.documents=documents;
    }
    @GetMapping
    public String view(@PathVariable String publicId,Authentication auth,Model model,HttpServletResponse response) {
        var state=find(publicId,auth,response);
        model.addAttribute("publicId",publicId);model.addAttribute("status",state.status());
        model.addAttribute("revision",state.revision());model.addAttribute("canOperate",state.canOperate());
        model.addAttribute("replyRequestKey",java.util.UUID.randomUUID().toString().replace("-",""));
        model.addAttribute("replyDestination",state.canOperate()
            ?replies.destinationFingerprint(state.scope(),auth.getName(),state.revision()).orElse(null):null);
        model.addAttribute("replies",replies.recent(state.scope(),auth.getName()));
        model.addAttribute("replyChangeTargets",changes.targets(state.scope(),auth.getName(),state.revision()));
        model.addAttribute("replyChanges",changes.recent(state.scope(),auth.getName()));
        model.addAttribute("documentRequestKey",java.util.UUID.randomUUID().toString().replace("-",""));
        model.addAttribute("documentDestination",state.canOperate()?documents.destinationFingerprint(state.scope(),auth.getName(),state.revision()).orElse(null):null);
        model.addAttribute("documentReplies",documents.recent(state.scope(),auth.getName()));
        model.addAttribute("workTime",time.summary(state.scope(),auth.getName()).orElse(null));
        model.addAttribute("workIntervals",time.recent(state.scope(),auth.getName()));
        model.addAttribute("assignment",assignments.current(state.scope(),auth.getName()).orElse(null));
        model.addAttribute("assignmentCandidates",assignments.candidates(state.scope(),auth.getName()));
        model.addAttribute("assignmentEvents",assignments.recent(state.scope(),auth.getName()));
        navigation.enrich(model,auth);return "general/work";
    }
    @PostMapping("/status")
    public RedirectView status(@PathVariable String publicId,Authentication auth,HttpServletResponse response,
            @RequestParam String revision,@RequestParam String expectedStatus,@RequestParam String nextStatus) {
        var state=find(publicId,auth,response);
        if (!state.canOperate()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        long version=version(revision);
        if (!work.transition(state.scope(),auth.getName(),version,expectedStatus,nextStatus))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Данные или права изменились. Обновите страницу.");
        var redirect=new RedirectView("/general-dialog-work/"+publicId,true);redirect.setExposeModelAttributes(false);return redirect;
    }
    @PostMapping("/reply")
    public RedirectView reply(@PathVariable String publicId,Authentication auth,HttpServletResponse response,
            @RequestParam String revision,@RequestParam String requestKey,@RequestParam String message,@RequestParam String destination) {
        var state=find(publicId,auth,response);
        if (!state.canOperate()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (!GeneralTicketReplyService.validInput(requestKey,message)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        if (!replies.submit(state.scope(),auth.getName(),version(revision),requestKey,message,destination))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Данные, канал или права изменились. Обновите страницу.");
        var redirect=new RedirectView("/general-dialog-work/"+publicId,true);redirect.setExposeModelAttributes(false);return redirect;
    }
    @PostMapping(value="/document",consumes="multipart/form-data")
    public RedirectView document(@PathVariable String publicId,Authentication auth,HttpServletResponse response,
            @RequestParam String revision,@RequestParam String requestKey,@RequestParam(defaultValue="") String caption,
            @RequestParam String destination,@RequestParam MultipartFile file) {
        var state=find(publicId,auth,response);
        if(!state.canOperate())throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if(!GeneralTicketDocumentReplyService.validInput(requestKey,caption,file))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        if(!documents.submit(state.scope(),auth.getName(),version(revision),requestKey,caption,file,destination))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Файл, данные, канал или права изменились. Обновите страницу.");
        var redirect=new RedirectView("/general-dialog-work/"+publicId,true);redirect.setExposeModelAttributes(false);return redirect;
    }
    @PostMapping("/reply-change")
    public RedirectView replyChange(@PathVariable String publicId,Authentication auth,HttpServletResponse response,
            @RequestParam String revision,@RequestParam String requestKey,@RequestParam String sourceKey,
            @RequestParam String expected,@RequestParam String action,@RequestParam(defaultValue="") String message) {
        var state=find(publicId,auth,response);
        if (!state.canOperate()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (!GeneralTicketReplyChangeService.validInput(requestKey,sourceKey,expected,action,message))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        if (!changes.submit(state.scope(),auth.getName(),version(revision),requestKey,sourceKey,expected,action,message))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Сообщение, данные или права изменились. Обновите страницу.");
        var redirect=new RedirectView("/general-dialog-work/"+publicId,true);redirect.setExposeModelAttributes(false);return redirect;
    }
    @PostMapping("/assignment")
    public RedirectView assignment(@PathVariable String publicId,Authentication auth,HttpServletResponse response,
            @RequestParam String revision,@RequestParam String assignmentRevision,@RequestParam String assigneeId) {
        var state=find(publicId,auth,response);
        if(!state.canOperate()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        long target=version(assigneeId);
        if(!assignments.assign(state.scope(),auth.getName(),version(revision),version(assignmentRevision),target==0?null:target))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Назначение, данные или права изменились. Обновите страницу.");
        var redirect=new RedirectView("/general-dialog-work/"+publicId,true);redirect.setExposeModelAttributes(false);return redirect;
    }
    private long version(String revision) {
        try {
            if (!revision.matches("0|[1-9][0-9]{0,18}")) throw new NumberFormatException();
            return Long.parseLong(revision);
        } catch (NumberFormatException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST); }
    }
    private GeneralTicketWorkService.View find(String id,Authentication auth,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        return work.find(id,auth==null?null:auth.getName()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
