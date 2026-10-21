package com.example.panel.controller;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.BusinessQuestionnaireResourcesService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/business-admin/businesses/{businessId}/questionnaire-presets")
@PreAuthorize("hasAuthority('PAGE_SETTINGS') and hasAuthority('ROLE_ADMIN')")
public class BusinessQuestionnairePresetsAdminController {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(BusinessQuestionnairePresetsAdminController.class);
    private final BusinessQuestionnaireResourcesService service;
    private final MultibusinessFeatureFlags flags;
    public BusinessQuestionnairePresetsAdminController(BusinessQuestionnaireResourcesService service,MultibusinessFeatureFlags flags){this.service=service;this.flags=flags;}
    @GetMapping public BusinessQuestionnaireResourcesService.Presets get(@PathVariable long businessId){
        enabled();try{return service.presets(businessId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
        catch(IllegalArgumentException ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,ex.getMessage());}
    }
    @PutMapping public BusinessQuestionnaireResourcesService.Presets put(@PathVariable long businessId,@RequestBody Request request,Authentication actor){
        enabled();if(request==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);try{
            var saved=service.replacePresets(businessId,request.expectedSha256(),request.groups()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
            log.info("Business questionnaire presets changed: actor={}, businessId={}, sha256={}",actor.getName(),businessId,saved.sha256());return saved;
        }catch(IllegalArgumentException ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,ex.getMessage());}
        catch(IllegalStateException ex){throw new ResponseStatusException(HttpStatus.CONFLICT,ex.getMessage());}
    }
    private void enabled(){if(!flags.isFoundationEnabled())throw new ResponseStatusException(HttpStatus.NOT_FOUND);}
    public record Request(String expectedSha256,Map<String,Object> groups){}
}
