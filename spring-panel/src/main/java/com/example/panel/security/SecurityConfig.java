package com.example.panel.security;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   SecurityHeadersFilter securityHeadersFilter,
                                                   ObjectProvider<UserLastActivityFilter> userLastActivityFilter,
                                                   DaoAuthenticationProvider daoAuthenticationProvider) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
                        .requestMatchers(
                                "/css/**", "/js/**", "/images/**", "/vendor/**", "/webjars/**",
                                "/favicon.ico", "/*.svg", "/*.png",
                                "/login",
                                "/api/password-reset-requests/public",
                                "/public/forms/**", "/api/public/forms/**",
                                "/webhooks/max/**",
                                "/error", "/error/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/post-login", true)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                )
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers("/api/public/forms/**", "/webhooks/max/**")
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .maximumSessions(5)
                        .maxSessionsPreventsLogin(false)
                )
                .exceptionHandling(ex -> ex.accessDeniedHandler((request, response, accessDeniedException) -> {
                    String uri = request.getRequestURI();

                    if (uri.startsWith("/api/")) {
                        response.setStatus(403);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"success\":false,\"error\":\"Forbidden (CSRF or access denied)\"}");
                        return;
                    }

                    response.sendRedirect("/error/403");
                }))
                .addFilterAfter(securityHeadersFilter, org.springframework.security.web.csrf.CsrfFilter.class);

        UserLastActivityFilter activityFilter = userLastActivityFilter.getIfAvailable();
        if (activityFilter != null) {
            http.addFilterAfter(activityFilter, org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class);
        }

        http.authenticationProvider(daoAuthenticationProvider);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserRepositoryUserDetailsService userRepositoryUserDetailsService(
            @org.springframework.beans.factory.annotation.Qualifier("usersJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplate,
            PasswordEncoder passwordEncoder
    ) {
        JdbcTemplate template = jdbcTemplate.getIfAvailable();
        if (template == null) {
            return new UserRepositoryUserDetailsService(new JdbcTemplate(), passwordEncoder);
        }
        return new UserRepositoryUserDetailsService(template, passwordEncoder);
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepositoryUserDetailsService delegate) {
        if (delegate.getJdbcTemplate() == null || delegate.getDataSource() == null) {
            return new InMemoryUserDetailsManager();
        }
        return delegate;
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}




