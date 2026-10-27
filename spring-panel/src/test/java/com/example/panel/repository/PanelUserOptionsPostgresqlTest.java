package com.example.panel.repository;

import com.example.panel.controller.PanelUserOptionsController;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.*;

/** Actual JDBC/Boolean/window query checks in a disposable PostgreSQL schema. Requires Docker. */
class PanelUserOptionsPostgresqlTest {
    private JdbcTemplate jdbc;
    private PanelUserRepository repository;
    private PanelUserOptionsController controller;
    @BeforeEach void schema() {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplate("employee_options");
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT,full_name TEXT,enabled BOOLEAN,is_blocked BOOLEAN)");
        repository=new PanelUserRepository(jdbc);controller=new PanelUserOptionsController(repository);
    }
    private void row(long id,String name,String label,Boolean enabled,Boolean blocked){jdbc.update("INSERT INTO users VALUES(?,?,?,?,?)",id,name,label,enabled,blocked);}
    private List<String> values(){return controller.options().stream().map(PanelUserOptionsController.Option::value).toList();}
    @ParameterizedTest @ValueSource(strings={"active","disabled","blocked"})
    void windowCountsAlsoIncludeIneligibleCaseFoldNeighbours(String state) {
        row(7,"subject","First",true,false);row(99,"SuBjEcT","Second",!state.equals("disabled"),state.equals("blocked"));
        assertThat(repository.findEmployeeOptions()).allSatisfy(option->assertThat(option.identityCount()).isEqualTo(2));assertThat(values()).isEmpty();
    }
    @Test void realBooleanFlagsExcludeDisabledAndBlockedRowsAndMapTheFullUserCorrectly() {
        row(1,"active","Active",true,false);row(2,"disabled",null,false,false);row(3,"blocked",null,true,true);
        assertThat(values()).containsExactly("active");assertThat(repository.findByUsernameIgnoreCase("blocked")).get().satisfies(user->assertThat(user.getBlocked()).isTrue());
        assertThat(repository.findAll()).anySatisfy(user->{assertThat(user.getUsername()).isEqualTo("blocked");assertThat(user.getBlocked()).isTrue();});
    }
    @Test void nullStatusDefaultsAreEnabledAndNotBlocked() {
        row(1,"subject",null,null,null);assertThat(repository.findEmployeeOptions()).singleElement().satisfies(option->{assertThat(option.enabled()).isTrue();assertThat(option.blocked()).isFalse();});assertThat(values()).containsExactly("subject");
    }
    @Test void rawPaddingAndLargeNumericLookingNamesRemainExactStrings() {
        row(1,"subject","First",true,false);row(2," subject ","Second",true,false);row(3,"9007199254740993","Third",true,false);
        assertThat(values()).containsExactly("subject"," subject ","9007199254740993");assertThat(repository.findEmployeeOptions()).allSatisfy(option->assertThat(option.identityCount()).isEqualTo(1));
    }
    @Test void absentCredentialAndProfileColumnsAreNotRequiredByTheProjection() {
        row(1,"subject"," Subject",true,false);assertThat(controller.options()).containsExactly(new PanelUserOptionsController.Option("subject"," Subject · subject"));
    }
    @Test void nullAndBlankNamesAreNotEmployeeOptions() {
        row(1,null,"Null",true,false);row(2,"","Empty",true,false);row(3,"   ","Blank",true,false);assertThat(values()).isEmpty();
    }
}
