package com.example.panel.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrf != null) {
            String token = csrf.getToken();
            Cookie cookie = WebUtils.getCookie(request, "XSRF-TOKEN");
            if (cookie == null || (token != null && !token.equals(cookie.getValue()))) {
                Cookie newCookie = new Cookie("XSRF-TOKEN", token);
                newCookie.setPath("/");
                newCookie.setSecure(false);      // localhost
                newCookie.setHttpOnly(false);    // надо читать из JS
                response.addCookie(newCookie);
            }
        }
        filterChain.doFilter(request, response);
    }
}
