package com.example.acceptance;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/** Exercise the actual IT repository adapters, including Java's generic/varargs call boundary. No Docker. */
class MaxMediaRepositoryAdaptersTest {
    @ParameterizedTest @ValueSource(longs={55L,9007199254740995L,Long.MAX_VALUE})
    void channelLookupBindsOneExactLongRatherThanCastingItToVarargsArray(long id)throws Exception {
        var row=mock(ResultSet.class);when(row.getLong(1)).thenReturn(id);when(row.getString(2)).thenReturn("max");
        var channels=MaxMediaChainFixture.channelRepository(database(id,row));
        assertThat(channels.findById(id)).hasValueSatisfying(c->{assertThat(c.getId()).isEqualTo(id);assertThat(c.getPlatform()).isEqualTo("max");});
    }
    @ParameterizedTest @ValueSource(strings={"root"," raw/Ю%?' "," left|right "})
    void activityLookupBindsOneOpaqueStringRatherThanCastingItToVarargsArray(String raw)throws Exception {
        var row=mock(ResultSet.class);when(row.getString(1)).thenReturn(raw);
        var active=MaxMediaChainFixture.activeRepository(database(raw,row));
        assertThat(active.findById(raw)).hasValueSatisfying(a->assertThat(a.getTicketId()).isEqualTo(raw));
    }
    private JdbcTemplate database(Object expected,ResultSet row) {
        return mock(JdbcTemplate.class,invocation->{
            if(!invocation.getMethod().getName().equals("query"))return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
            Object[] arguments=invocation.getRawArguments();assertThat(arguments).hasSize(3);
            assertThat(arguments[2]).isInstanceOf(Object[].class);assertThat((Object[])arguments[2]).containsExactly(expected);
            assertThat(((Object[])arguments[2])[0].getClass()).isEqualTo(expected.getClass());
            var mapping=(RowMapper<?>)arguments[1];return List.of(mapping.mapRow(row,0));
        });
    }
}
