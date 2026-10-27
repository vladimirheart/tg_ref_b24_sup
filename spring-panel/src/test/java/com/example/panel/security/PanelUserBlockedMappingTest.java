package com.example.panel.security;

import com.example.panel.entity.PanelUser;
import com.example.panel.repository.PanelUserRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PanelUserBlockedMappingTest {
    static Stream<Arguments> flags(){return Stream.of(Arguments.of(true,true),Arguments.of(false,false),Arguments.of(null,false),Arguments.of(1,true),Arguments.of(0,false),Arguments.of(2,false),Arguments.of(1L,true),Arguments.of(-1,false),Arguments.of("1",true),Arguments.of("0",false),Arguments.of("01",true),Arguments.of("+1",true));}
    @ParameterizedTest @MethodSource("flags")
    void postgresBooleansAndExistingNumericRepresentationsKeepTheirMeaning(Object raw,boolean blocked) throws Exception {
        var jdbc=mock(JdbcTemplate.class);var rs=mock(ResultSet.class);when(rs.getLong("id")).thenReturn(7L);when(rs.getString("username")).thenReturn(" reader ");when(rs.getObject("is_blocked")).thenReturn(raw);
        when(rs.findColumn(anyString())).thenAnswer(c->{String key=c.getArgument(0);if(List.of("username","id","is_blocked").contains(key))return 1;throw new SQLException("fixture missing column");});
        when(jdbc.query(anyString(),org.mockito.ArgumentMatchers.<RowMapper<PanelUser>>any())).thenAnswer(c->List.of(c.<RowMapper<PanelUser>>getArgument(1).mapRow(rs,0)));
        when(jdbc.query(anyString(),org.mockito.ArgumentMatchers.<RowMapper<PanelUser>>any(),any(Object[].class))).thenAnswer(c->List.of(c.<RowMapper<PanelUser>>getArgument(1).mapRow(rs,0)));
        var repository=new PanelUserRepository(jdbc);assertThat(repository.findAll()).singleElement().satisfies(user->{assertThat(user.getBlocked()).isEqualTo(blocked);assertThat(user.getUsername()).isEqualTo(" reader ");});
        assertThat(repository.findByUsernameIgnoreCase(" reader ")).get().satisfies(user->assertThat(user.getBlocked()).isEqualTo(blocked));
    }
}
