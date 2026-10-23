package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class NotificationRoutingIdentityPostgresTest {
    @TempDir Path directory;
    private JdbcTemplate panel,users;
    private SharedConfigService config;
    private NotificationService notifications;
    private NotificationRoutingService routing;

    @BeforeEach void prepare() {
        panel=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("route_identity_panel");
        users=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("route_identity_users");
        for(var jdbc:List.of(panel,users))jdbc.execute("CREATE TABLE users(id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,username TEXT UNIQUE,enabled BOOLEAN, is_blocked BOOLEAN, department TEXT,last_portal_activity_at TEXT)");
        config=new SharedConfigService(new ObjectMapper(),directory.toString());config.saveSettings(Map.of("unrelated","keep"));
        notifications=mock(NotificationService.class);routing=new NotificationRoutingService(config,notifications,panel,users);
    }

    @ParameterizedTest @ValueSource(strings={" reader","reader "," reader ","\treader\t","\nreader\r"})
    void baseRecipientsNeverBecomeAnotherAccountsTrimmedKey(String identity) {
        account(panel,"reader","Support",null);account(users,identity,"Other",null);
        store("base_recipients","all_operators","all",List.of(),List.of());
        routing.notify("tasks","task_saved",Set.of(identity.toUpperCase(Locale.ROOT)),"private","/tasks",null);
        assertThat(delivered()).containsExactly(identity);
    }

    @ParameterizedTest @ValueSource(strings={" reader","reader "," reader ","\treader\t","\nreader\r"})
    void arraySelectorCannotBorrowTheTrimmedAccountsDepartment(String identity) {
        account(panel,"reader","Support",null);account(users,identity,"Other",null);
        store("route_only","employees_only","all",List.of(identity.toUpperCase(Locale.ROOT)),List.of());
        routing.notify("tasks","task_saved",Set.of(),"private","/tasks",null);
        verifyNoInteractions(notifications);
        assertThat(routing.findDepartmentRecipients(" Support ")).containsExactly("reader");
        assertThat(routing.findDepartmentRecipients("Other")).containsExactly(identity);
    }

    @ParameterizedTest @ValueSource(strings={"reader"," reader "})
    void exactDepartmentExclusionRemovesOnlyTheNamedAccount(String excluded) {
        account(panel,"reader","Support",null);account(users," reader ","Support",null);
        store("route_only","department_except","all",List.of(),List.of(excluded.toUpperCase(Locale.ROOT)));
        routing.notify("tasks","task_saved",Set.of(),"private","/tasks",null);
        assertThat(delivered()).containsExactly(excluded.equals("reader")?" reader ":"reader");
        assertThat(routing.findDepartmentRecipients("SUPPORT")).containsExactlyInAnyOrder("reader"," reader ");
    }

    @ParameterizedTest @ValueSource(strings={"reader"," reader "})
    void onlineFilterUsesOnlyTheExactAccountsActivity(String online) {
        String other=online.equals("reader")?" reader ":"reader";
        account(panel,other,"Support",OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).toString());
        account(users,online,"Support",OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(2).toString());
        when(notifications.findAllOperatorRecipients()).thenReturn(Set.of("reader"," reader "));
        store("route_only","all_operators","online_only_fallback_all",List.of(),List.of());
        routing.notify("tasks","task_saved",Set.of(),"private","/tasks",null);
        assertThat(delivered()).containsExactly(online);
    }

    @Test void noOnlineRecipientPreservesTheExactFallbackSetAndCaseDeduplication() {
        account(panel,"reader","Support",null);account(users," reader ","Support",null);
        store("base_recipients","department_all","online_only_fallback_all",List.of(),List.of());
        routing.notify("tasks","task_saved",Set.of("reader","READER"," reader "," READER "),"private","/tasks",null);
        assertThat(delivered()).containsExactlyInAnyOrder("reader"," reader ");
    }

    @ParameterizedTest @ValueSource(booleans={true,false})
    void saveLoadAndTopLevelApplyPreserveTypedArrayElements(boolean wrapped) {
        var route=route("route_only","employees_only","all",List.of(" Reader "," READER ","reader","\treader\t","\nreader\r","","  "),List.of(" READER "));
        Map<String,Object> root=Map.of("tasks",Map.of("task_saved",route));
        routing.saveSettingsPayload(wrapped?Map.of(NotificationRoutingService.SETTINGS_KEY,root):root);
        var fresh=new NotificationRoutingService(config,notifications,panel,users);
        var expected=List.of(" reader ","reader","\treader\t","\nreader\r");
        assertThat(fresh.loadRoute("TASKS","task_saved").employeeUsernames()).isEqualTo(expected);
        assertThat(fresh.loadRoute("tasks","task_saved").excludeUsernames()).containsExactly(" reader ");
        assertThat(config.loadSettings()).containsEntry("unrelated","keep");
        Map<String,Object> applied=new LinkedHashMap<>(Map.of("unrelated","keep"));
        var top=new SettingsTopLevelUpdateService(mock(AutoCloseConfigNormalizer.class),mock(BotSettingsPayloadNormalizer.class),
            mock(LocationsIikoServerSourceSettingsService.class),mock(LocationsIikoSyncSettingsService.class),mock(NetBoxSyncSettingsService.class),fresh);
        assertThat(top.applyTopLevelUpdates(Map.of(NotificationRoutingService.SETTINGS_KEY,root),applied)).isTrue();
        config.saveSettings(applied);
        assertThat(fresh.loadRoute("tasks","task_saved").employeeUsernames()).isEqualTo(expected);
        assertThat(fresh.loadSettingsPayload()).isEqualTo(routing.loadSettingsPayload());
    }

    @Test void legacyDelimitedTextStillTrimsTokensAndFoldsCase() {
        account(panel,"alice","Support",null);account(users,"bob","Support",null);
        store("route_only","employees_only","all"," Alice , BOB;\nalice ; \t"," BOB ");
        assertThat(routing.loadRoute("tasks","task_saved").employeeUsernames()).containsExactly("alice","bob");
        assertThat(routing.loadRoute("tasks","task_saved").excludeUsernames()).containsExactly("bob");
        routing.notify("tasks","task_saved",Set.of(),"private","/tasks",null);
        assertThat(delivered()).containsExactlyInAnyOrder("alice","bob");
    }

    @ParameterizedTest @ValueSource(strings={"disabled","blocked","department","deleted"})
    void currentDirectoryChangesCannotRedirectTheLiteralAccount(String mutation) {
        account(panel,"reader","Support",null);account(users," reader ","Support",null);
        store("route_only","employees_only","all",List.of(" READER "),List.of());
        routing.notify("tasks","task_saved",Set.of(),"first","/tasks",null);assertThat(delivered()).containsExactly(" reader ");clearInvocations(notifications);
        switch(mutation) {
            case "disabled"->users.update("UPDATE users SET enabled=FALSE");
            case "blocked"->users.update("UPDATE users SET is_blocked=TRUE");
            case "department"->users.update("UPDATE users SET department='Other'");
            case "deleted"->users.update("DELETE FROM users");
            default->throw new IllegalArgumentException(mutation);
        }
        routing.notify("tasks","task_saved",Set.of(),"late","/tasks",null);verifyNoInteractions(notifications);
        assertThat(routing.findDepartmentRecipients("Support")).containsExactly("reader");
    }

    @ParameterizedTest @ValueSource(strings={"table","department"})
    void missingDirectorySchemaCannotFallBackToAnotherAudience(String missing) {
        for(var jdbc:List.of(panel,users))jdbc.execute(missing.equals("table")?"DROP TABLE users":"ALTER TABLE users DROP COLUMN department");
        store("route_only","employees_only","all",List.of(" reader "),List.of());
        routing.notify("tasks","task_saved",Set.of("reader"),"private","/tasks",null);verifyNoInteractions(notifications);
        assertThat(routing.findDepartmentRecipients("Support")).isEmpty();
    }

    @Test void disabledRouteAndBlankMessageKeepTheirExistingShortCircuit() {
        var route=new LinkedHashMap<>(route("route_only","all_operators","all",List.of(),List.of()));route.put("enabled",false);
        routing.saveSettingsPayload(Map.of("tasks",Map.of("task_saved",route)));
        routing.notify("tasks","task_saved",Set.of(" reader "),"private","/tasks",null);
        routing.notify("tasks","task_saved",Set.of(" reader ")," \t","/tasks",null);verifyNoInteractions(notifications);
    }

    private void account(JdbcTemplate jdbc,String name,String department,String activity) {
        jdbc.update("INSERT INTO users(username,enabled,is_blocked,department,last_portal_activity_at) VALUES (?,TRUE,FALSE,?,?)",name,department,activity);
    }
    private Map<String,Object> route(String strategy,String target,String delivery,Object employees,Object excludes) {
        return Map.of("enabled",true,"audienceStrategy",strategy,"department","Support","targetMode",target,"deliveryMode",delivery,"employeeUsernames",employees,"excludeUsernames",excludes);
    }
    private void store(String strategy,String target,String delivery,Object employees,Object excludes) {
        routing.saveSettingsPayload(Map.of("tasks",Map.of("task_saved",route(strategy,target,delivery,employees,excludes))));
    }
    @SuppressWarnings("unchecked") private Set<String> delivered() {
        var captured=ArgumentCaptor.forClass(Set.class);verify(notifications).notifyUsersExcluding(captured.capture(),any(),anyString(),anyString());return captured.getValue();
    }
}
