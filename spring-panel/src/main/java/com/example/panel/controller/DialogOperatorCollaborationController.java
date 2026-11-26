package com.example.panel.controller;

import com.example.panel.service.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/operator-dialogs/{publicId}")
@PreAuthorize("hasAuthority('PAGE_DIALOGS')")
public class DialogOperatorCollaborationController {
    private final DialogOperatorCollaborationService collaboration;
    private final BusinessTicketAssignmentService assignment;
    private final UnresolvedTicketBusinessSelectionService selection;
    public DialogOperatorCollaborationController(DialogOperatorCollaborationService collaboration,
            BusinessTicketAssignmentService assignment,UnresolvedTicketBusinessSelectionService selection) {
        this.collaboration=collaboration;this.assignment=assignment;this.selection=selection;
    }
    @GetMapping("/assignment")
    public ResponseEntity<?> assignment(@PathVariable String publicId,Authentication auth) {
        var state=assignment.find(publicId,auth).orElseThrow(()->error(HttpStatus.NOT_FOUND));
        var result=new LinkedHashMap<String,Object>();
        result.put("owner",owner(state.scope()));result.put("revision",Long.toString(state.revision()));
        result.put("canManage",state.canOperate());result.put("responsible",state.responsible());
        return ok(result);
    }
    @PostMapping("/take")
    public ResponseEntity<?> take(@PathVariable String publicId,@RequestBody Command command,Authentication auth) {
        var state=assignment.find(publicId,auth).orElseThrow(()->error(HttpStatus.NOT_FOUND));
        if(!state.canOperate())throw error(HttpStatus.FORBIDDEN);
        if(!matches(publicId,command,state.scope())||decimal(command.revision(),false)!=state.revision()
            ||!assignment.change(state.scope(),auth,state.revision(),null,true))throw error(HttpStatus.CONFLICT);
        return ok(Map.of("success",true,"responsible",auth.getName()));
    }
    @GetMapping("/business")
    public ResponseEntity<?> businesses(@PathVariable String publicId,Authentication auth) {
        var state=find(publicId,auth);
        if(!"BUSINESS".equals(state.scope().scopeKind()))throw error(HttpStatus.NOT_FOUND);
        boolean can=state.canWrite()&&List.of("open","pending").contains(state.status());
        return ok(Map.of("owner",owner(state.scope()),"revision",Long.toString(state.revision()),"canManage",can,
            "businesses",can?selection.availableBusinesses(auth).stream()
                .map(b->Map.of("id",Long.toString(b.id()),"displayName",b.displayName())).toList():List.of()));
    }
    @PostMapping("/business")
    public ResponseEntity<?> transfer(@PathVariable String publicId,@RequestBody Command command,Authentication auth) {
        var state=find(publicId,auth);
        if(!state.canWrite())throw error(HttpStatus.FORBIDDEN);
        if(!matches(publicId,command,state.scope())||decimal(command.revision(),false)!=state.revision()
            ||!collaboration.transfer(state,decimal(command.businessTargetId(),true),auth))throw error(HttpStatus.CONFLICT);
        return ok(Map.of("success",true));
    }
    @GetMapping("/internal-messages")
    public ResponseEntity<?> messages(@PathVariable String publicId,Authentication auth,
            @RequestParam(defaultValue="0") String before) {
        var state=find(publicId,auth);
        return ok(Map.of("owner",owner(state.scope()),"canWrite",state.canWrite(),
            "messages",collaboration.messages(publicId,auth,decimal(before,false))));
    }
    @PostMapping("/internal-messages")
    public ResponseEntity<?> post(@PathVariable String publicId,@RequestBody Command command,Authentication auth) {
        var state=find(publicId,auth);
        if(!state.canWrite())throw error(HttpStatus.FORBIDDEN);
        UUID requestId;
        try {requestId=UUID.fromString(command.requestId());}
        catch(Exception ex){throw error(HttpStatus.BAD_REQUEST);}
        if(command.body()==null||command.body().isBlank()||command.body().length()>10000||command.body().indexOf('\0')>=0)
            throw error(HttpStatus.BAD_REQUEST);
        if(!matches(publicId,command,state.scope())||!collaboration.post(state,auth,requestId,command.body()))
            throw error(HttpStatus.CONFLICT);
        return ok(Map.of("success",true));
    }
    private DialogOperatorCollaborationService.Context find(String publicId,Authentication auth) {
        return collaboration.find(publicId,auth).orElseThrow(()->error(HttpStatus.NOT_FOUND));
    }
    @PostMapping("/internal-messages/read")
    public ResponseEntity<?> markRead(@PathVariable String publicId,@RequestBody Command command,Authentication auth) {
        var state=find(publicId,auth);
        if(!matches(publicId,command,state.scope())
            ||!collaboration.markRead(state,auth,decimal(command.lastReadId(),true)))throw error(HttpStatus.CONFLICT);
        return ok(Map.of("success",true));
    }
    static boolean matches(String publicId,Command c,UiEventTicketScope s) {
        return c!=null&&Objects.equals(publicId,c.ticketPublicId())&&Objects.equals(c.ticketId(),s.ticketId())
            &&Objects.equals(c.userId(),Long.toString(s.ticketUserId()))
            &&Objects.equals(c.businessId(),s.businessId()==null?"":Long.toString(s.businessId()))
            &&Objects.equals(c.scopeKind(),s.scopeKind());
    }
    private Map<String,String> owner(UiEventTicketScope s) {
        return Map.of("userId",s.ticketUserId().toString(),"ticketId",s.ticketId(),"ticketPublicId",s.ticketPublicId(),
            "scopeKind",s.scopeKind(),"businessId",s.businessId()==null?"":s.businessId().toString());
    }
    private long decimal(String value,boolean positive) {
        try {if(value==null||!value.matches(positive?"[1-9][0-9]{0,18}":"0|[1-9][0-9]{0,18}"))throw new NumberFormatException();
            return Long.parseLong(value);}
        catch(NumberFormatException ex){throw error(HttpStatus.BAD_REQUEST);}
    }
    private ResponseStatusException error(HttpStatus status) {
        return new ResponseStatusException(status,"Данные или права изменились. Обновите диалог.");
    }
    private ResponseEntity<?> ok(Object body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
    public record Command(String userId,String ticketId,String ticketPublicId,String scopeKind,String businessId,
            String revision,String businessTargetId,String requestId,String body,String lastReadId) {}
}

