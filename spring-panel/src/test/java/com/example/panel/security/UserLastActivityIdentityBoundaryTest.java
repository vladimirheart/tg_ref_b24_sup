package com.example.panel.security;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserLastActivityIdentityBoundaryTest {
    private static final String SQL = "UPDATE users SET last_portal_activity_at = ? WHERE lower(username) = lower(?)";
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final AtomicInteger chained = new AtomicInteger();
    UserLastActivityFilter filter;

    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class); when(provider.getIfAvailable()).thenReturn(jdbc);
        filter = new UserLastActivityFilter(provider);
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    private void invoke(Authentication actor) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(actor);
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (request,response) -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(actor); chained.incrementAndGet();
        });
    }
    private Authentication actor(String username) { return new UsernamePasswordAuthenticationToken(username,"fixture",List.of()); }

    @ParameterizedTest @ValueSource(strings = {"reader", " reader", "reader ", " reader ", "\treader\t", "\nreader\r", "mem\r\nber", "member,one", "member;one", "9007199254740993", "ReAdEr", " user/%?#&+ Ю ", "<img>"})
    void activityBindsExactAuthenticatedAccountKey(String username) throws Exception {
        invoke(actor(username)); var timestamp = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(eq(SQL), timestamp.capture(), eq(username));
        assertThat(OffsetDateTime.parse(timestamp.getValue()).getOffset()).isEqualTo(ZoneOffset.UTC); assertThat(chained).hasValue(1);
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"", " ", "\t", "\n", "\u2003"})
    void blankPrincipalDoesNotWrite(String username) throws Exception {
        Authentication actor = mock(Authentication.class); when(actor.isAuthenticated()).thenReturn(true); when(actor.getName()).thenReturn(username);
        invoke(actor); verifyNoInteractions(jdbc); assertThat(chained).hasValue(1);
    }
    @Test void absentAuthenticationDoesNotWrite() throws Exception { invoke(null); verifyNoInteractions(jdbc); assertThat(chained).hasValue(1); }
    @Test void anonymousDoesNotWrite() throws Exception { invoke(new AnonymousAuthenticationToken("fixture","anonymous",List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")))); verifyNoInteractions(jdbc); assertThat(chained).hasValue(1); }
    @Test void unauthenticatedDoesNotWrite() throws Exception { invoke(UsernamePasswordAuthenticationToken.unauthenticated("reader","fixture")); verifyNoInteractions(jdbc); assertThat(chained).hasValue(1); }
    @Test @SuppressWarnings("unchecked") void absentDatasourceStillContinuesChain() throws Exception { filter = new UserLastActivityFilter(mock(ObjectProvider.class)); invoke(actor("reader")); verifyNoInteractions(jdbc); assertThat(chained).hasValue(1); }
    @Test void missingColumnRetainsExistingOneFailureCache() throws Exception {
        when(jdbc.update(anyString(), any(Object[].class))).thenThrow(new DataAccessResourceFailureException("column last_portal_activity_at not found"));
        invoke(actor("reader")); invoke(actor("reader")); verify(jdbc, times(1)).update(anyString(), any(Object[].class)); assertThat(chained).hasValue(2);
    }
    @Test void otherDatabaseFailureRemainsBestEffortAndCanRetry() throws Exception {
        when(jdbc.update(anyString(), any(Object[].class))).thenThrow(new DataAccessResourceFailureException("fixture transient failure")).thenReturn(1);
        invoke(actor("reader")); invoke(actor("reader")); verify(jdbc, times(2)).update(eq(SQL), anyString(), eq("reader")); assertThat(chained).hasValue(2);
    }
}
