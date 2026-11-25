package com.example.panel.repository;

import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmployeeProfileProjectionTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PanelUserRepository repository = new PanelUserRepository(jdbc);
    @Test void minimalProjectionPreservesExactIdentityAndMapsWorkFieldsOnly() throws Exception {
        var expected = new PanelUserRepository.EmployeeProfile(9L, "9007199254740993", "Анна", "Поддержка", "9.jpg",
            "anna@example.test", "[]", java.time.OffsetDateTime.parse("2026-11-25T12:00:00Z"), true);
        when(jdbc.query(anyString(), any(RowMapper.class), eq("9007199254740993"))).thenReturn(List.of(expected));
        assertThat(repository.findEmployeeProfile("9007199254740993")).contains(expected);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<RowMapper<PanelUserRepository.EmployeeProfile>> mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbc).query(sql.capture(), mapper.capture(), eq("9007199254740993"));
        assertThat(sql.getValue()).contains("SELECT id, username, full_name, department, photo", "lower(username) = lower(?)", "LIMIT 2")
            .contains("email", "phones", "last_portal_activity_at", "NOT COALESCE(enabled, TRUE)")
            .doesNotContain("password", "birth_date", "SELECT *");
        var rs = mock(ResultSet.class); when(rs.getLong("id")).thenReturn(9L);
        when(rs.getString("username")).thenReturn(expected.username()); when(rs.getString("full_name")).thenReturn(expected.fullName());
        when(rs.getString("department")).thenReturn(expected.department()); when(rs.getString("photo")).thenReturn(expected.photo());
        when(rs.getString("email")).thenReturn(expected.email()); when(rs.getString("phones")).thenReturn(expected.phones());
        when(rs.getObject("last_portal_activity_at", java.time.OffsetDateTime.class)).thenReturn(expected.lastPortalActivityAt());
        when(rs.getBoolean("account_blocked")).thenReturn(true);
        assertThat(mapper.getValue().mapRow(rs, 0)).isEqualTo(expected);
    }
    @Test void collisionsNeverChooseTheFirstLegacyAccount() {
        var first = new PanelUserRepository.EmployeeProfile(1L, "anna", "Анна", null, null, null, null, null, false);
        var second = new PanelUserRepository.EmployeeProfile(2L, "ANNA", "Другая Анна", null, null, null, null, null, true);
        when(jdbc.query(anyString(), any(RowMapper.class), eq("anna"))).thenReturn(List.of(first, second));
        assertThat(repository.findEmployeeProfile("anna")).isEmpty();
    }
    @Test void idProjectionUsesOnlyTheStableNumericKey() {
        var expected = new PanelUserRepository.EmployeeProfile(9007199254740993L, "renamed", "Анна", null, null, null, null, null, true);
        when(jdbc.query(anyString(), any(RowMapper.class), eq(9007199254740993L))).thenReturn(List.of(expected));
        assertThat(repository.findEmployeeProfile(9007199254740993L)).contains(expected);
        verify(jdbc).query(contains("FROM users WHERE id = ?"), any(RowMapper.class), eq(9007199254740993L));
        assertThat(repository.findEmployeeProfile(0L)).isEmpty();
        assertThat(repository.findEmployeeProfile(-1L)).isEmpty();
    }
    @Test void blankAndMissingAccountsHaveNoProfile() {
        assertThat(repository.findEmployeeProfile(null)).isEmpty(); assertThat(repository.findEmployeeProfile(" ")).isEmpty();
        verifyNoInteractions(jdbc);
        when(jdbc.query(anyString(), any(RowMapper.class), eq("missing"))).thenReturn(List.of());
        assertThat(repository.findEmployeeProfile("missing")).isEmpty();
    }
}
