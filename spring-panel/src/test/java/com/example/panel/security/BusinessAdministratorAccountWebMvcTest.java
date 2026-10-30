package com.example.panel.security;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.controller.BusinessAdministrationApiController;
import com.example.panel.service.*;
import java.util.*;
import java.util.stream.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.example.panel.service.BusinessAdministratorAccountPostgresTest.*;

/** Full HTTP security chain and actual administration SQL; no working datasource or Redis. */
@SpringBootTest(classes=BusinessAdministratorAccountWebMvcTest.Application.class,properties={"spring.session.store-type=none","management.endpoint.health.group.readiness.include=ping"})
@AutoConfigureMockMvc
class BusinessAdministratorAccountWebMvcTest {
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;
    @Configuration(proxyBeanMethods=false)
    @Import({PreferenceIdentityWebMvcTest.Application.class,BusinessAdministrationApiController.class,BusinessAdministrationService.class})
    static class Application {
        @Bean MultibusinessFeatureFlags flags(){var flags=new MultibusinessFeatureFlags();flags.getFoundation().setEnabled(true);return flags;}
        @Bean org.springframework.beans.factory.InitializingBean businessSchema(JdbcTemplate jdbc){return ()->{
            jdbc.execute("CREATE TABLE channels(id BIGINT PRIMARY KEY,channel_name TEXT,platform TEXT,is_active BOOLEAN DEFAULT TRUE)");
            jdbc.execute("CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,PRIMARY KEY(user_id,ticket_id))");
            for(String sql:java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/db/migration/postgresql/V51__multibusiness_foundation.sql")).split(";"))if(!sql.isBlank())jdbc.execute(sql);
        };}
    }
    @BeforeEach void seed(){jdbc.execute("TRUNCATE users,user_authorities,roles,businesses,channels CASCADE");
        jdbc.update("INSERT INTO users(id,username,password) VALUES(7,'caller','unused'),(55,'target','unused')");
        jdbc.update("INSERT INTO user_authorities VALUES(7,'ROLE_ADMIN'),(7,'PAGE_SETTINGS')");
        jdbc.update("INSERT INTO businesses(id,code,display_name) OVERRIDING SYSTEM VALUE VALUES(1,'sushi','Sushi')");jdbc.update("INSERT INTO channels(id) VALUES(10)");
        jdbc.update("INSERT INTO business_locations(id,business_id,location_name,normalized_location_name) OVERRIDING SYSTEM VALUE VALUES(20,1,'Old','old')");
        jdbc.update("INSERT INTO business_channel_bindings(channel_id,business_id,resolution_mode) VALUES(10,1,'SINGLE_BUSINESS')");}
    MockHttpServletRequestBuilder request(String op){String root="/api/business-admin";return switch(op){
        case "overview"->get(root+"/overview");case "listChannels"->get(root+"/channels");case "previewChannelBinding"->get(root+"/channels/10/binding-preview");
        case "saveChannelBinding"->put(root+"/channels/10/bindings/1").content("{\"resolutionMode\":\"SINGLE_BUSINESS\"}");
        case "deactivateChannelBinding"->delete(root+"/channels/10/bindings/1");case "createBusiness"->post(root+"/businesses").content("{\"code\":\"new-business\",\"displayName\":\"New\"}");
        case "renameBusiness"->put(root+"/businesses/1").content("{\"displayName\":\"New\"}");case "setBusinessStatus"->put(root+"/businesses/1/status").content("{\"status\":\"archived\"}");
        case "grantMembership"->put(root+"/businesses/1/memberships/55").content("{\"roleCode\":\"VIEWER\"}");case "revokeMembership"->delete(root+"/businesses/1/memberships/55");
        case "grantAllBusinesses"->put(root+"/all-grants/55").content("{\"roleCode\":\"VIEWER\"}");case "revokeAllBusinesses"->delete(root+"/all-grants/55");
        case "createLocation"->post(root+"/businesses/1/locations").content("{\"locationName\":\"New\"}");case "setLocationStatus"->put(root+"/businesses/1/locations/20/status").content("{\"status\":\"archived\"}");default->throw new AssertionError(op);};}
    static Stream<Arguments> requests(){return OPERATIONS.stream().flatMap(op->Stream.of("valid","rename-reuse","case","admin-revoked","page-revoked","unbound").map(mode->Arguments.of(op,mode)));}
    @ParameterizedTest @MethodSource("requests") void accountAndCurrentRightsProtectEveryHttpRoute(String op,String mode)throws Exception{
        Authentication actor=auth(7,"caller");switch(mode){
            case "rename-reuse"->{jdbc.update("UPDATE users SET username='retired' WHERE id=7");jdbc.update("INSERT INTO users(id,username) VALUES(56,'caller')");jdbc.update("INSERT INTO user_authorities VALUES(56,'ROLE_ADMIN'),(56,'PAGE_SETTINGS')");}
            case "case"->jdbc.update("UPDATE users SET username='CALLER' WHERE id=7");case "admin-revoked"->jdbc.update("DELETE FROM user_authorities WHERE user_id=7 AND authority='ROLE_ADMIN'");case "page-revoked"->jdbc.update("DELETE FROM user_authorities WHERE user_id=7 AND authority='PAGE_SETTINGS'");
            case "unbound"->actor=org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated("caller",null,actor.getAuthorities());default->{}
        }
        mvc.perform(request(op).contentType("application/json").with(authentication(actor)).with(csrf()))
            .andExpect(mode.equals("valid")?status().is2xxSuccessful():Set.of("rename-reuse","case","unbound").contains(mode)?status().is3xxRedirection():status().isForbidden());
    }
}
