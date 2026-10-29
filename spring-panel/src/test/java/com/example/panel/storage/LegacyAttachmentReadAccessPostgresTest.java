package com.example.panel.storage;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import static com.example.panel.support.LegacyAttachmentIdentityFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real metadata/owner/account/business SQL, before any local/S3 storage open. */
class LegacyAttachmentReadAccessPostgresTest {
    private static JdbcTemplate jdbc;
    private static SingleConnectionDataSource connection;
    @TempDir Path root;
    private AttachmentObjectStorageService storage;
    private MultibusinessFeatureFlags flags;
    @BeforeAll static void schemaOnce()throws Exception {
        var fresh=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("legacy_attachment_read_access");
        connection=new SingleConnectionDataSource(fresh.getDataSource().getConnection(),true);jdbc=new JdbcTemplate(connection);schema(jdbc,false);
    }
    @AfterAll static void close(){if(connection!=null)connection.destroy();}
    @BeforeEach void setup()throws Exception {
        seed(jdbc,"caller","unused");flags=new MultibusinessFeatureFlags();storage=mock(AttachmentObjectStorageService.class);
        when(storage.openDialogAttachment(anyString(),anyString())).thenAnswer(c->binary());
        when(storage.openDialogAttachmentByStorageKey(anyString())).thenAnswer(c->binary());
    }
    private AttachmentObjectStorageService.StoredBinary binary(){return new AttachmentObjectStorageService.StoredBinary(KEY,"video/mp4",BYTES.length,new ByteArrayInputStream(BYTES));}
    private void check(Route route,Authentication actor,boolean allowed)throws Exception {
        var before=snapshot(jdbc);var service=service(jdbc,jdbc,flags,storage,root);var response=download(service,actor,route,null);
        assertThat(response.getStatusCode().value()).isEqualTo(allowed?200:404);
        if(allowed){try(var stream=response.getBody().getInputStream()){assertThat(stream.readAllBytes()).containsExactly(BYTES);}}
        else {assertThat(response.hasBody()).isFalse();verifyNoInteractions(storage);}
        assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    private static Stream<Arguments> cross(String... values){return Arrays.stream(values).flatMap(value->Arrays.stream(Route.values()).map(route->Arguments.of(value,route)));}
    static Stream<Arguments> roles(){return cross("VIEWER","OPERATOR","MANAGER","ADMIN","all:VIEWER","all:OPERATOR","all:MANAGER","all:ADMIN");}
    @ParameterizedTest @MethodSource("roles") void currentReadRolesAndAllBusinessesCanRead(String role,Route route)throws Exception {
        jdbc.update("DELETE FROM business_memberships");
        if(role.startsWith("all:"))jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(7,'ALL_BUSINESSES',?)",role.substring(4));
        else jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(7,11,?)",role);
        check(route,actor(7,"caller"),true);
    }
    static Stream<Arguments> refusals(){return cross("no-grant","foreign","inactive-member","unknown-role","inactive-all","unknown-all-role","paused-business","negative-business","general","null-general","collision","missing-owner");}
    @ParameterizedTest @MethodSource("refusals") void pageAndValidMetadataNeverSubstituteForBusinessRead(String change,Route route)throws Exception {
        switch(change){
            case "no-grant"->jdbc.update("DELETE FROM business_memberships");
            case "foreign"->jdbc.update("UPDATE business_memberships SET business_id=22");
            case "inactive-member"->jdbc.update("UPDATE business_memberships SET is_active=FALSE");
            case "unknown-role"->jdbc.update("UPDATE business_memberships SET role_code='OWNER'");
            case "inactive-all","unknown-all-role"->{jdbc.update("DELETE FROM business_memberships");jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code,is_active) VALUES(7,'ALL_BUSINESSES',?,?)",change.equals("inactive-all")?"ADMIN":"OWNER",!change.equals("inactive-all"));}
            case "paused-business"->jdbc.update("UPDATE businesses SET status='inactive' WHERE id=11");
            case "negative-business"->jdbc.update("UPDATE tickets SET business_id=-11");
            case "null-general"->jdbc.update("UPDATE tickets SET is_general=NULL");
            case "missing-owner"->jdbc.update("DELETE FROM tickets");
            default->replace(jdbc,change);
        }
        check(route,actor(7,"caller"),false);
    }
    static Stream<Arguments> identities(){return cross("rename-reuse","delete-reuse","case-change","disabled","blocked");}
    @ParameterizedTest @MethodSource("identities") void boundAccountCannotBeReplacedByAReusedName(String change,Route route)throws Exception {replace(jdbc,change);check(route,actor(7,"caller"),false);}
    static Stream<Arguments> invalid(){return cross("null","unbound","anonymous","unauthenticated","disabled-principal","zero-id","wrong-id","wrong-name");}
    @ParameterizedTest @MethodSource("invalid") void invalidPrincipalsHaveNoNameFallback(String kind,Route route)throws Exception {
        Authentication value=switch(kind){
            case "null"->null;
            case "unbound"->UsernamePasswordAuthenticationToken.authenticated("caller",null,List.of(()->"PAGE_DIALOGS"));
            case "anonymous"->new AnonymousAuthenticationToken("key","caller",List.of(()->"PAGE_DIALOGS"));
            case "unauthenticated"->UsernamePasswordAuthenticationToken.unauthenticated(new PanelUserPrincipal(7,"caller","unused",true,List.of()),null);
            case "disabled-principal"->UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(7,"caller","unused",false,List.of()),null,List.of(()->"PAGE_DIALOGS"));
            case "zero-id"->actor(0,"caller");case "wrong-id"->actor(55,"caller");default->actor(7,"Caller");
        };
        // Preserve the old page-authority exception: data authorization starts only after PAGE_DIALOGS.
        if(value==null || !value.isAuthenticated())assertThatThrownBy(()->download(service(jdbc,jdbc,flags,storage,root),value,route,null)).isInstanceOf(SecurityException.class);
        else check(route,value,false);
    }
    static Stream<Arguments> names(){return cross("caller","Caller"," caller ","имя","\u0130","i\u0307");}
    @ParameterizedTest @MethodSource("names") void exactRawNamesAndBigintAccountIdsKeepOwnRead(String name,Route route)throws Exception {
        long id=9007199254740993L;jdbc.update("UPDATE users SET id=?,username=? WHERE id=7",id,name);jdbc.update("UPDATE business_memberships SET user_id=?",id);check(route,actor(id,name),true);
    }
    @ParameterizedTest @EnumSource(Route.class) void unicodeNeighbourCannotSupplyAnotherIdsRead(Route route)throws Exception {
        jdbc.update("UPDATE users SET username=? WHERE id=7","\u0130");jdbc.update("DELETE FROM business_memberships");
        jdbc.update("INSERT INTO users(id,username) VALUES(55,?)","i\u0307");jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(55,11,'ADMIN')");check(route,actor(7,"\u0130"),false);
    }
    @ParameterizedTest @EnumSource(Route.class) void sqlFoldAmbiguityRemainsClosed(Route route)throws Exception {jdbc.update("INSERT INTO users(id,username) VALUES(55,'CALLER')");check(route,actor(7,"caller"),false);}
    static Stream<Arguments> switches(){return Stream.of(false,true).flatMap(foundation->Stream.of(false,true).flatMap(strict->Arrays.stream(Route.values()).map(route->Arguments.of(foundation,strict,route))));}
    @ParameterizedTest @MethodSource("switches") void unresolvedRequiresStrictReadOff(boolean foundation,boolean strict,Route route)throws Exception {
        flags.getFoundation().setEnabled(foundation);flags.setStrictReadScope(strict);jdbc.update("UPDATE tickets SET business_id=NULL");jdbc.update("DELETE FROM business_memberships");check(route,actor(7,"caller"),!strict);
    }
    @ParameterizedTest @MethodSource("switches") void flagsNeverPermitBusinessWithoutRead(boolean foundation,boolean strict,Route route)throws Exception {
        flags.getFoundation().setEnabled(foundation);flags.setStrictReadScope(strict);jdbc.update("DELETE FROM business_memberships");check(route,actor(7,"caller"),false);
    }
    static Stream<Arguments> boundaries(){return cross("rename-reuse","delete-reuse","case-change","disabled","blocked","membership","business","collision","general");}
    @ParameterizedTest @MethodSource("boundaries") void authorizationRechecksChangesAfterMetadataResolution(String change,Route route)throws Exception {
        var fired=new AtomicBoolean();var expected=new AtomicReference<Map<String,String>>();var boundary=spy(jdbc);
        doAnswer(call->{if(fired.compareAndSet(false,true)){replace(jdbc,change);expected.set(snapshot(jdbc));}return call.callRealMethod();})
            .when(boundary).queryForObject(anyString(),eq(Boolean.class),any(Object[].class));
        var response=download(service(jdbc,boundary,flags,storage,root),actor(7,"caller"),route,null);
        assertThat(response.getStatusCode().value()).isEqualTo(404);assertThat(fired).isTrue();verifyNoInteractions(storage);assertThat(snapshot(jdbc)).isEqualTo(expected.get());
    }
    @ParameterizedTest @EnumSource(Route.class) void accessStorageFailureNeverOpensTheBinary(Route route)throws Exception {
        var broken=spy(jdbc);doThrow(new DataAccessResourceFailureException("fixture unavailable")).when(broken).queryForObject(anyString(),eq(Boolean.class),any(Object[].class));
        var response=download(service(jdbc,broken,flags,storage,root),actor(7,"caller"),route,null);assertThat(response.getStatusCode().value()).isEqualTo(404);verifyNoInteractions(storage);
    }
    @ParameterizedTest @EnumSource(Route.class) void authorizedRangesKeepExactBytes(Route route)throws Exception {
        var response=download(service(jdbc,jdbc,flags,storage,root),actor(7,"caller"),route,"bytes=1-3");
        assertThat(response.getStatusCode().value()).isEqualTo(206);assertThat(response.getHeaders().getFirst("Content-Range")).isEqualTo("bytes 1-3/6");try(var input=response.getBody().getInputStream()){assertThat(input.readAllBytes()).containsExactly(1,2,3);}
    }
}
