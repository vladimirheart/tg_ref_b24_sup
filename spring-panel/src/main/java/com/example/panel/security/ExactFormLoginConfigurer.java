package com.example.panel.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractAuthenticationFilterConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/** Normalization belongs to explicit account writes; login must retain existing account keys. */
final class ExactFormLoginConfigurer extends AbstractAuthenticationFilterConfigurer<
        HttpSecurity, ExactFormLoginConfigurer, UsernamePasswordAuthenticationFilter> {

    ExactFormLoginConfigurer() {
        super(new ExactUsernamePasswordAuthenticationFilter(), null);
    }

    @Override
    public ExactFormLoginConfigurer loginPage(String loginPage) {
        return super.loginPage(loginPage);
    }

    @Override
    protected RequestMatcher createLoginProcessingUrlMatcher(String loginProcessingUrl) {
        return new AntPathRequestMatcher(loginProcessingUrl, "POST");
    }

    private static final class ExactUsernamePasswordAuthenticationFilter extends UsernamePasswordAuthenticationFilter {
        @Override
        public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) {
            if (!"POST".equals(request.getMethod())) {
                throw new AuthenticationServiceException("Authentication method not supported: " + request.getMethod());
            }
            String username = obtainUsername(request);
            String password = obtainPassword(request);
            var token = UsernamePasswordAuthenticationToken.unauthenticated(
                username != null ? username : "", password != null ? password : "");
            setDetails(request, token);
            return getAuthenticationManager().authenticate(token);
        }
    }
}
