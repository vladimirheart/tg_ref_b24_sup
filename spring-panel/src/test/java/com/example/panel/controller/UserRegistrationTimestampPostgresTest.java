package com.example.panel.controller;

import com.example.panel.service.*;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserRegistrationTimestampPostgresTest {
    static JdbcTemplate jdbc;
    static MockMvc mvc;
    static final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder();
    @BeforeAll static void start(){
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("user_registration_timestamp");
        String schema=jdbc.queryForObject("SELECT current_schema()",String.class);
        Flyway.configure().dataSource(jdbc.getDataSource()).schemas(schema).defaultSchema(schema)
            .locations("classpath:db/migration/postgresql").target("89").load().migrate();
        var permissions=mock(PermissionService.class);when(permissions.isSuperUser(any())).thenReturn(true);
        var controller=new AuthManagementApiController(jdbc,mock(SharedConfigService.class),permissions,encoder,
            mock(PanelUserPhotoService.class),new ObjectMapper());
        mvc=MockMvcBuilders.standaloneSetup(controller).build();
    }
    @BeforeEach void clean(){jdbc.execute("TRUNCATE users CASCADE");}
    @Test void createsUserWithRealTimestamptzUsingDefaultPostgresStringBinding()throws Exception{
        var before=OffsetDateTime.now().minusSeconds(2);
        mvc.perform(post("/api/users").principal(new TestingAuthenticationToken("admin","unused","ROLE_ADMIN"))
            .contentType("application/json").content("{\"username\":\"r305_user\",\"password\":\"TestR305Safe+\",\"full_name\":\"Проверка\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        var date=jdbc.queryForObject("SELECT registration_date FROM users WHERE username='r305_user'",OffsetDateTime.class);
        assertThat(date).isAfter(before).isBefore(OffsetDateTime.now().plusSeconds(2));
        assertThat(encoder.matches("TestR305Safe+",jdbc.queryForObject("SELECT password FROM users WHERE username='r305_user'",String.class))).isTrue();
        assertThat(jdbc.queryForObject("SELECT enabled FROM users WHERE username='r305_user'",Boolean.class)).isTrue();
    }
    @Test void blocksAndUnblocksUsingRealPostgresBooleanBinding()throws Exception{
        var admin=new TestingAuthenticationToken("admin","unused","ROLE_ADMIN");
        mvc.perform(post("/api/users").principal(admin).contentType("application/json")
            .content("{\"username\":\"r309_target\",\"password\":\"TestR309Safe+\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        long id=jdbc.queryForObject("SELECT id FROM users WHERE username='r309_target'",Long.class);
        for(boolean blocked:new boolean[]{true,false}){
            mvc.perform(patch("/api/users/"+id).principal(admin).contentType("application/json")
                .content("{\"is_blocked\":"+blocked+"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
            assertThat(jdbc.queryForObject("SELECT is_blocked FROM users WHERE id=?",Boolean.class,id)).isEqualTo(blocked);
            assertThat(jdbc.queryForObject("SELECT enabled FROM users WHERE id=?",Boolean.class,id)).isEqualTo(!blocked);
        }
    }
    @Test void timestampFixKeepsCaseInsensitiveDuplicateProtection()throws Exception{
        for(String name:new String[]{"R305_Duplicate","r305_duplicate"}){
            var result=mvc.perform(post("/api/users").principal(new TestingAuthenticationToken("admin","unused","ROLE_ADMIN"))
                .contentType("application/json").content("{\"username\":\""+name+"\",\"password\":\"TestR305Safe+\"}"))
                .andExpect(status().isOk()).andReturn();
            boolean success=new ObjectMapper().readTree(result.getResponse().getContentAsByteArray()).path("success").asBoolean();
            assertThat(success).isEqualTo(name.startsWith("R305"));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users",Integer.class)).isEqualTo(1);
    }
}
