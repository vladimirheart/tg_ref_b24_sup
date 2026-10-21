package com.example.panel.controller;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.BusinessQuestionnaireResourcesService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BusinessQuestionnairePresetsAdminController.class)
@Import(BusinessQuestionnairePresetsAdminControllerWebMvcTest.MethodSecurity.class)
class BusinessQuestionnairePresetsAdminControllerWebMvcTest {
    @TestConfiguration @EnableMethodSecurity static class MethodSecurity {}
    @Autowired MockMvc mvc;
    @MockBean BusinessQuestionnaireResourcesService service;
    @MockBean MultibusinessFeatureFlags flags;
    private final String path="/api/business-admin/businesses/7/questionnaire-presets";
    private final String body="{\"expectedSha256\":\""+"a".repeat(64)+"\",\"groups\":{\"department\":{\"name\":[\"Kitchen\"]}}}";
    @ParameterizedTest @ValueSource(strings={"PAGE_SETTINGS","ROLE_ADMIN","BUSINESS_ADMIN","ALL_BUSINESSES"})
    void businessGrantsAndPartialSystemAuthorityCannotReadOrWrite(String authority) throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);
        mvc.perform(get(path).with(user("limited").authorities(()->authority))).andExpect(status().isForbidden());
        mvc.perform(get(path+"/resources").with(user("limited").authorities(()->authority))).andExpect(status().isForbidden());
        mvc.perform(put(path).with(user("limited").authorities(()->authority)).with(csrf()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void disabledFoundationAndMissingCsrfNeverCallTheWriter() throws Exception {
        var admin=user("admin").authorities(()->"PAGE_SETTINGS",()->"ROLE_ADMIN");
        mvc.perform(get(path).with(admin)).andExpect(status().isNotFound());
        mvc.perform(get(path+"/resources").with(admin)).andExpect(status().isNotFound());
        mvc.perform(put(path).with(admin).with(csrf()).contentType("application/json").content(body)).andExpect(status().isNotFound());
        when(flags.isFoundationEnabled()).thenReturn(true);
        mvc.perform(put(path).with(admin).contentType("application/json").content(body)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void resourcePreviewUsesTheExactOwnerAndReportsMissingOrUnavailableSnapshots() throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);var admin=user("admin").authorities(()->"PAGE_SETTINGS",()->"ROLE_ADMIN");
        var resources=new BusinessQuestionnaireResourcesService.Resources(Map.of("businessId",7,"locations",List.of()),Map.of("businessId",7,"groups",Map.of()),"a".repeat(64));
        when(service.snapshot(7)).thenReturn(Optional.of(resources));
        mvc.perform(get(path+"/resources").with(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.locationTree.businessId").value(7));
        verify(service).snapshot(7);verify(service,never()).snapshot(8);
        when(service.snapshot(7)).thenReturn(Optional.empty());mvc.perform(get(path+"/resources").with(admin)).andExpect(status().isNotFound());
        when(service.snapshot(7)).thenThrow(new IllegalStateException("Unavailable"));mvc.perform(get(path+"/resources").with(admin)).andExpect(status().isConflict());
    }
    @Test void administratorReadsAndWritesOnlyTheExplicitOwnerAndVersion() throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);var admin=user("admin").authorities(()->"PAGE_SETTINGS",()->"ROLE_ADMIN");
        var groups=Map.<String,Object>of("department",Map.of("name",List.of("Kitchen")));
        var snapshot=new BusinessQuestionnaireResourcesService.Presets(7,"a".repeat(64),groups);
        when(service.presets(7)).thenReturn(Optional.of(snapshot));when(service.replacePresets(7,"a".repeat(64),groups)).thenReturn(Optional.of(snapshot));
        mvc.perform(get(path).with(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.businessId").value(7));
        mvc.perform(put(path).with(admin).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.groups.department.name[0]").value("Kitchen"));
        verify(service).replacePresets(7,"a".repeat(64),groups);verify(service,never()).replacePresets(eq(8L),any(),any());
    }
    @ParameterizedTest @ValueSource(strings={"invalid","stale","missing"})
    void errorsCannotMasqueradeAsSuccessfulWrites(String reason) throws Exception {
        when(flags.isFoundationEnabled()).thenReturn(true);var admin=user("admin").authorities(()->"PAGE_SETTINGS",()->"ROLE_ADMIN");
        int expected;
        if(reason.equals("invalid")){when(service.replacePresets(anyLong(),any(),any())).thenThrow(new IllegalArgumentException("Invalid"));expected=400;}
        else if(reason.equals("stale")){when(service.replacePresets(anyLong(),any(),any())).thenThrow(new IllegalStateException("Stale"));expected=409;}
        else {when(service.replacePresets(anyLong(),any(),any())).thenReturn(Optional.empty());expected=404;}
        mvc.perform(put(path).with(admin).with(csrf()).contentType("application/json").content(body)).andExpect(status().is(expected));
    }
}
