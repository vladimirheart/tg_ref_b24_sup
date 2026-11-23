package com.example.panel.security;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.authentication.rememberme.InvalidCookieException;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VersionedRememberMeServicesTest {
    UserRepositoryUserDetailsService users;
    VersionedRememberMeServices service;
    PanelUserPrincipal account(long version) {
        return new PanelUserPrincipal(7,"reader","fixture-stored-hash",true,AuthorityUtils.createAuthorityList("PAGE_USERS"),version);
    }
    @BeforeEach void setup() {
        users=mock(UserRepositoryUserDetailsService.class);service=new VersionedRememberMeServices("fixture-key",users);
    }
    @Test void stockPreviouslyIssuedCookieWorksOnlyBeforeFirstRevocation() {
        when(users.loadUserByUsername("reader")).thenAnswer(call -> account(0));
        var stock=new TokenBasedRememberMeServices("fixture-key",users);var issued=new MockHttpServletResponse();var principal=account(0);
        stock.onLoginSuccess(new MockHttpServletRequest(),issued,
            UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities()));
        var request=new MockHttpServletRequest();request.setCookies(issued.getCookie("remember-me"));
        var restored=service.autoLogin(request,new MockHttpServletResponse());
        assertThat(restored).isNotNull();assertThat(((PanelUserPrincipal)restored.getPrincipal()).getPassword()).isNull();
        when(users.loadUserByUsername("reader")).thenAnswer(call -> account(1));
        assertThat(service.autoLogin(request,new MockHttpServletResponse())).isNull();
    }
    @Test void loginStartedBeforeRevocationCannotMintCookieForNewGeneration() {
        when(users.loadCurrentUser(7)).thenReturn(account(1));var response=new MockHttpServletResponse();var stale=account(0);
        service.onLoginSuccess(new MockHttpServletRequest(),response,
            UsernamePasswordAuthenticationToken.authenticated(stale,null,stale.getAuthorities()));
        assertThat(response.getCookie("remember-me")).isNotNull();assertThat(response.getCookie("remember-me").getMaxAge()).isZero();
    }
    @Test void revocationAfterCookieIssuanceRejectsThatCookie() {
        when(users.loadCurrentUser(7)).thenReturn(account(1));var response=new MockHttpServletResponse();var current=account(1);
        service.onLoginSuccess(new MockHttpServletRequest(),response,
            UsernamePasswordAuthenticationToken.authenticated(current,null,current.getAuthorities()));
        var request=new MockHttpServletRequest();request.setCookies(response.getCookie("remember-me"));
        when(users.loadUserByUsername("reader")).thenAnswer(call -> account(1));
        assertThat(service.autoLogin(request,new MockHttpServletResponse())).isNotNull();
        when(users.loadUserByUsername("reader")).thenAnswer(call -> account(2));
        assertThat(service.autoLogin(request,new MockHttpServletResponse())).isNull();
    }
    @Test void signatureRejectionDoesNotExposeExpectedOrReceivedTokenMaterial() {
        when(users.loadUserByUsername("reader")).thenReturn(account(2));
        assertThatThrownBy(() -> service.processAutoLoginCookie(new String[]{"reader",Long.toString(System.currentTimeMillis()+60000),"SHA256","injected-signature"},
            new MockHttpServletRequest(),new MockHttpServletResponse())).isInstanceOf(InvalidCookieException.class)
            .hasMessage("Remember-me token is invalid or revoked").hasNoCause();
    }
}
