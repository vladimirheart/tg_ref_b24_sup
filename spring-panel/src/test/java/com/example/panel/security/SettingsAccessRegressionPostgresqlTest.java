package com.example.panel.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.panel.controller.AuthManagementApiController;
import com.example.panel.controller.UserPermissionsController;
import com.example.panel.service.PermissionService;
import com.example.panel.service.PanelUserPhotoService;
import com.example.panel.service.SharedConfigService;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class SettingsAccessRegressionPostgresqlTest {
    private record Fixture(JdbcTemplate jdbc, UserRepositoryUserDetailsService details, SecurityBootstrap bootstrap) {
        Authentication actor(long id) {
            var user = details.loadCurrentUser(id);
            return UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
        }
        AuthManagementApiController api() {
            return new AuthManagementApiController(jdbc, mock(SharedConfigService.class), new PermissionService(),
                    new BCryptPasswordEncoder(), mock(PanelUserPhotoService.class), new ObjectMapper());
        }
    }
    private Fixture fixture() {
        var jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("settings_access_318");
        jdbc.execute("CREATE TABLE roles(id BIGSERIAL PRIMARY KEY,name TEXT UNIQUE,description TEXT,permissions TEXT)");
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT,password TEXT,enabled BOOLEAN DEFAULT TRUE,is_blocked BOOLEAN DEFAULT FALSE,role_id BIGINT,session_version BIGINT DEFAULT 0,full_name TEXT,email TEXT,department TEXT,photo TEXT)");
        jdbc.execute("CREATE TABLE user_authorities(user_id BIGINT,authority TEXT,PRIMARY KEY(user_id,authority))");
        jdbc.update("INSERT INTO roles(name,permissions) VALUES (?,?)", "Администратор портала", "{\"pages\":[\"*\"],\"fields\":{\"edit\":[\"*\"],\"view\":[\"*\"]}}");
        jdbc.update("INSERT INTO roles(name,permissions) VALUES ('user','{\"pages\":[\"dialogs\"],\"fields\":{\"edit\":[],\"view\":[]}}')");
        jdbc.update("INSERT INTO roles(name,permissions) VALUES ('Support_role','{\"pages\":[\"*\"],\"fields\":{\"edit\":[],\"view\":[]}}')");
        var encoder = new BCryptPasswordEncoder();
        String credential = encoder.encode("isolated-test-only");
        jdbc.update("INSERT INTO users(id,username,password,role_id) VALUES (1,'admin',?,2),(19,'Akmanov_IH',?,3)", credential, credential);
        jdbc.update("INSERT INTO user_authorities VALUES (1,'ROLE_ADMIN'),(19,'DENY_PAGE_SETTINGS')");
        @SuppressWarnings("unchecked") ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        return new Fixture(jdbc, new UserRepositoryUserDetailsService(jdbc, encoder),
                new SecurityBootstrap(provider, encoder, new PanelSecurityProperties()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void deniedSettingsCannotInheritLegacyPagesOrDirectChildGrants() {
        var f = fixture();
        f.jdbc().update("INSERT INTO user_authorities VALUES (19,'EDIT_SETTINGS_CHANNELS')");
        var actor = f.actor(19);
        assertThat(actor.getAuthorities()).extracting(a -> a.getAuthority()).contains("DENY_PAGE_SETTINGS")
                .doesNotContain("PAGE_SETTINGS", "PAGE_CHANNELS", "PAGE_USERS");
        assertThat(ChannelManagementAccess.isAllowed(actor)).isFalse();
        PanelAccessPolicy.BLOCKS.keySet().forEach(key -> assertThat(PanelAccessPolicy.canEdit(actor,key)).isFalse());
    }
    @Test void bootstrapRepairsOnlySystemAdminAndProducesFullAssignedRole() {
        var f = fixture();
        var before = f.jdbc().queryForMap("SELECT username,role_id,enabled,is_blocked FROM users WHERE id=19");
        f.bootstrap().ensureDefaultAdmin();
        f.bootstrap().ensureDefaultAdmin();
        assertThat(f.jdbc().queryForObject("SELECT r.name FROM users u JOIN roles r ON r.id=u.role_id WHERE u.id=1",String.class)).isEqualTo("admin");
        assertThat(f.jdbc().queryForObject("SELECT count(*) FROM roles WHERE name='admin'",Long.class)).isEqualTo(1);
        assertThat(f.jdbc().queryForMap("SELECT username,role_id,enabled,is_blocked FROM users WHERE id=19")).isEqualTo(before);
        var actor = f.actor(1);
        assertThat(actor.getAuthorities()).extracting(a -> a.getAuthority()).contains("ROLE_ADMIN","PAGE_SETTINGS");
        PanelAccessPolicy.managedAuthorities().forEach(right -> assertThat(PanelAccessPolicy.has(actor,right)).as(right).isTrue());
        assertThat(PanelRolePermissionSnapshot.capture(f.jdbc(),1,"admin").adminRole()).isTrue();
    }
    @Test void adminCannotDemoteRenameBlockOrDeleteItselfButCanEditProfile() {
        var f = fixture(); f.bootstrap().ensureDefaultAdmin();
        var actor = f.actor(1); SecurityContextHolder.getContext().setAuthentication(actor);
        var api = f.api();
        for (var payload : List.<Map<String,Object>>of(Map.of("role_id",2),Map.of("username","former-admin"),Map.of("is_blocked",true),Map.of("enabled",false)))
            assertThatThrownBy(() -> api.updateUser(1,actor,payload)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> api.deleteUser(1,actor)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> new UserPermissionsController(f.jdbc()).write(1,Map.of("PAGE_SETTINGS","deny"),actor))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(api.updateUser(1,actor,Map.of("email","root@example.invalid"))).containsEntry("success",true);
        assertThat(f.jdbc().queryForObject("SELECT email FROM users WHERE id=1",String.class)).isEqualTo("root@example.invalid");
        assertThat(f.jdbc().queryForObject("SELECT username FROM users WHERE id=1",String.class)).isEqualTo("admin");
    }
    @Test void adminCannotWeakenRenameOrDeleteProtectedRole() {
        var f = fixture(); f.bootstrap().ensureDefaultAdmin();
        var actor = f.actor(1); SecurityContextHolder.getContext().setAuthentication(actor);
        Long role = f.jdbc().queryForObject("SELECT role_id FROM users WHERE id=1",Long.class);
        var before = f.jdbc().queryForMap("SELECT name,permissions FROM roles WHERE id=?",role);
        assertThatThrownBy(() -> f.api().updateRole(role,actor,Map.of("permissions",Map.of("pages",List.of())))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> f.api().updateRole(role,actor,Map.of("name","ordinary"))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> f.api().deleteRole(role,actor)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(f.jdbc().queryForMap("SELECT name,permissions FROM roles WHERE id=?",role)).isEqualTo(before);
    }
    @Test void ambiguousAdminNamesAreNotReboundByName() {
        var f = fixture();
        f.jdbc().update("INSERT INTO users(id,username,password,role_id) SELECT 20,'ADMIN',password,3 FROM users WHERE id=1");
        f.bootstrap().ensureDefaultAdmin();
        assertThat(f.jdbc().queryForObject("SELECT role_id FROM users WHERE id=1",Long.class)).isEqualTo(2);
        assertThat(f.jdbc().queryForObject("SELECT count(*) FROM roles WHERE name='admin'",Long.class)).isZero();
    }
}
