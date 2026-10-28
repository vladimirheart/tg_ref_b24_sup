package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.support.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import static com.example.panel.support.BusinessWorkbenchFixture.*;
import static org.assertj.core.api.Assertions.*;

/** Frozen cases for all nine SQL readers; String API reflection is a baseline-only test bridge. */
class BusinessWorkbenchReaderIdentityPostgresTest {
    enum Api { ASSIGNMENT,ASSIGNEES,ASSIGNMENT_EVENTS,PARTICIPANT_REVISION,MEMBERS,MEMBER_CANDIDATES,PARTICIPANT_EVENTS,WORK,WORK_EVENTS }
    private final UiEventTicketScope scope=new UiEventTicketScope(OWNER,RAW,PUBLIC,"BUSINESS",11L);
    private JdbcTemplate jdbc;private MultibusinessFeatureFlags flags;
    private BusinessTicketAssignmentService assignments;private BusinessTicketParticipantService participants;private BusinessTicketWorkService work;
    @BeforeEach void fixture()throws Exception{
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("workbench_reader_identity");schema(jdbc,false);seed(jdbc,"reader","unused");
        flags=new MultibusinessFeatureFlags();flags.getFoundation().setEnabled(true);flags.setUiSelector(true);
        var ids=new UiEventOutboxAppendService(jdbc,new LegacyTicketIdJdbcGuard(jdbc));
        assignments=new BusinessTicketAssignmentService(jdbc,flags,ids);participants=new BusinessTicketParticipantService(jdbc,flags,ids);work=new BusinessTicketWorkService(jdbc,flags,ids,new ObjectMapper());
    }
    private Authentication auth(long id,String name,boolean enabled){var account=new PanelUserPrincipal(id,name,"unused",enabled,AuthorityUtils.createAuthorityList("ROLE_ADMIN","PAGE_DIALOGS"));return UsernamePasswordAuthenticationToken.authenticated(account,null,account.getAuthorities());}
    private Object service(Api api){return switch(api){case ASSIGNMENT,ASSIGNEES,ASSIGNMENT_EVENTS->assignments;case WORK,WORK_EVENTS->work;default->participants;};}
    private String method(Api api){return switch(api){case ASSIGNMENT,WORK->"find";case ASSIGNEES,MEMBER_CANDIDATES->"candidates";case MEMBERS->"members";case PARTICIPANT_REVISION->"revision";default->"recent";};}
    private Class<?> first(Api api){return api==Api.ASSIGNMENT||api==Api.WORK?String.class:UiEventTicketScope.class;}
    private Object read(Api api,Authentication authentication)throws Exception{
        java.lang.reflect.Method method;Object identity=authentication;
        try{method=service(api).getClass().getMethod(method(api),first(api),Authentication.class);}
        catch(NoSuchMethodException baseline){method=service(api).getClass().getMethod(method(api),first(api),String.class);identity=authentication==null?null:authentication.getName();}
        try{return method.invoke(service(api),first(api)==String.class?PUBLIC:scope,identity);}
        catch(InvocationTargetException ex){if(ex.getCause() instanceof Exception cause)throw cause;throw ex;}
    }
    private boolean visible(Object result){return result instanceof Optional<?> optional?optional.isPresent():!((List<?>)result).isEmpty();}
    private void allDenied(Authentication auth)throws Exception{var before=snapshot(jdbc);for(Api api:Api.values())assertThat(visible(read(api,auth))).as(api.name()).isFalse();assertThat(snapshot(jdbc)).isEqualTo(before);}
    private void policy(Authentication auth,boolean operate)throws Exception{
        var before=snapshot(jdbc);for(Api api:Api.values())assertThat(visible(read(api,auth))).as(api.name()).isEqualTo(operate||api!=Api.ASSIGNEES&&api!=Api.MEMBER_CANDIDATES);
        var state=(BusinessTicketAssignmentService.State)((Optional<?>)read(Api.ASSIGNMENT,auth)).orElseThrow();assertThat(state.canOperate()).isEqualTo(operate);
        var workState=(BusinessTicketWorkService.View)((Optional<?>)read(Api.WORK,auth)).orElseThrow();assertThat(workState.canOperate()).isEqualTo(operate);assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    static Stream<Arguments> replacements(){return Stream.of(Api.values()).flatMap(api->Stream.of("rename-reuse","delete-reuse","case-change").map(mode->Arguments.of(api,mode)));}
    @ParameterizedTest @MethodSource("replacements") void eachReaderRejectsReplacementAfterSessionBinding(Api api,String mode)throws Exception{
        var authentication=auth(2,"reader",true);assertThat(visible(read(api,authentication))).isTrue();replace(jdbc,mode,"reader");var before=snapshot(jdbc);
        assertThat(visible(read(api,authentication))).as(api+" "+mode).isFalse();assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"wrong-id","missing-id","zero-id","negative-id","case","padding","unbound","unauthenticated","anonymous","principal-disabled","disabled","blocked","ambiguous","membership","archived","foreign","general-grant","foundation-off","selector-off"})
    void everyReadRequiresTheExactSessionAndCurrentReadPolicy(String mode)throws Exception{
        Authentication principal=auth(2,"reader",true);
        switch(mode){
            case "wrong-id"->principal=auth(3,"reader",true);case "missing-id"->principal=auth(999,"reader",true);case "zero-id"->principal=auth(0,"reader",true);case "negative-id"->principal=auth(-1,"reader",true);
            case "case"->principal=auth(2,"READER",true);case "padding"->principal=auth(2," reader ",true);
            case "unbound"->principal=UsernamePasswordAuthenticationToken.authenticated("reader",null,principal.getAuthorities());case "unauthenticated"->principal=UsernamePasswordAuthenticationToken.unauthenticated(principal.getPrincipal(),null);case "anonymous"->principal=new AnonymousAuthenticationToken("key",principal.getPrincipal(),principal.getAuthorities());case "principal-disabled"->principal=auth(2,"reader",false);
            case "disabled","blocked","membership"->replace(jdbc,mode,"reader");case "ambiguous"->jdbc.update("INSERT INTO users(id,username) VALUES(55,'READER')");case "archived"->jdbc.update("UPDATE businesses SET status='archived' WHERE id=11");case "foreign"->principal=auth(3,"foreign",true);
            case "general-grant"->{jdbc.update("DELETE FROM business_memberships WHERE user_id=2");jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(2,'GENERAL_TICKETS','ADMIN')");}
            case "foundation-off"->flags.getFoundation().setEnabled(false);case "selector-off"->flags.setUiSelector(false);
        }
        allDenied(principal);
    }
    @ParameterizedTest @ValueSource(strings={"reader"," reader ","ReAdEr","имя","anonymousUser","2","9007199254740993","9223372036854775807"})
    void literalNamesAndBigintSessionIdsKeepOwnerAndSnapshots(String name)throws Exception{
        long id=name.equals("9007199254740993")?9007199254740993L:name.equals("9223372036854775807")?Long.MAX_VALUE:2;
        jdbc.update("UPDATE users SET id=?,username=? WHERE id=2",id,name);jdbc.update("UPDATE business_memberships SET user_id=? WHERE user_id=2",id);var authentication=auth(id,name,true);
        for(Api api:Api.values())assertThat(visible(read(api,authentication))).as(api.name()).isTrue();
        var state=(BusinessTicketAssignmentService.State)((Optional<?>)read(Api.ASSIGNMENT,authentication)).orElseThrow();assertThat(state.scope()).isEqualTo(scope);assertThat(state.responsible()).isEqualTo(" private assignee ");assertThat(state.revision()).isEqualTo(1);assertThat(state.canOperate()).isTrue();
        var workState=(BusinessTicketWorkService.View)((Optional<?>)read(Api.WORK,authentication)).orElseThrow();assertThat(workState.scope()).isEqualTo(scope);assertThat(workState.categories()).containsExactly("Own");
    }
    @ParameterizedTest @ValueSource(strings={"VIEWER","OPERATOR","MANAGER","ADMIN","ALL_VIEWER","ALL_OPERATOR","NONE"})
    void readAndOperateRemainDistinctAndGlobalRoleIsNotABusinessGrant(String role)throws Exception{
        if(role.startsWith("ALL_")||role.equals("NONE")){jdbc.update("DELETE FROM business_memberships WHERE user_id=2");if(role.startsWith("ALL_"))jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(2,'ALL_BUSINESSES',?)",role.substring(4));}
        else jdbc.update("UPDATE business_memberships SET role_code=? WHERE user_id=2",role);
        if(role.equals("NONE"))allDenied(auth(2,"reader",true));else policy(auth(2,"reader",true),!role.endsWith("VIEWER"));
    }
    @ParameterizedTest @EnumSource(Api.class) void nullAndNameOnlyApisAreRejected(Api api)throws Exception{
        assertThat(visible(read(api,null))).isFalse();assertThatCode(()->service(api).getClass().getMethod(method(api),first(api),Authentication.class)).doesNotThrowAnyException();
        assertThatThrownBy(()->service(api).getClass().getMethod(method(api),first(api),String.class)).isInstanceOf(NoSuchMethodException.class);
    }
    @ParameterizedTest @ValueSource(strings={"general","no-business","raw-collision","public-replaced","business-transfer"})
    void existingOwnerAndUniqueLegacyRootGuardsRemainRequired(String mode)throws Exception{
        switch(mode){case "general"->jdbc.update("UPDATE tickets SET is_general=TRUE");case "no-business"->jdbc.update("UPDATE tickets SET business_id=NULL");case "raw-collision"->jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES(42,?,?,22)",RAW,"b".repeat(32));case "public-replaced"->jdbc.update("UPDATE tickets SET ticket_public_id=?","b".repeat(32));default->jdbc.update("UPDATE tickets SET business_id=22");}allDenied(auth(2,"reader",true));
    }
}
