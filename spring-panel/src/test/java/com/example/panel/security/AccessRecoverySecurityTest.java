package com.example.panel.security;

import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccessRecoverySecurityTest {
    @Test void springForwardedHeaderWrappersCannotBypassProxyTrust() throws Exception {
        var request = new MockHttpServletRequest(); request.setRemoteAddr("10.0.0.2");
        request.addHeader("X-Forwarded-For","198.51.100.99");
        var resolver = new RequestClientAddressResolver(new AccessRecoveryProperties());
        new org.springframework.web.filter.ForwardedHeaderFilter().doFilter(request,new org.springframework.mock.web.MockHttpServletResponse(),(wrapped,response) -> {
            var http = (jakarta.servlet.http.HttpServletRequest) wrapped;
            assertThat(http.getRemoteAddr()).isEqualTo("198.51.100.99");
            assertThat(resolver.resolve(http)).isEqualTo("10.0.0.2");
            assertThat(resolver.connectionAddress(http)).isEqualTo("10.0.0.2");
        });
    }
    @Test void forwardedHeadersRequireExplicitTrustedPeersAndAreWalkedRightToLeft() {
        var properties = new AccessRecoveryProperties();
        var request = new MockHttpServletRequest(); request.setRemoteAddr("10.0.0.2");
        request.addHeader("X-Forwarded-For","198.51.100.99, 192.0.2.5, 10.0.0.3");
        assertThat(new RequestClientAddressResolver(properties).resolve(request)).isEqualTo("10.0.0.2");
        properties.setTrustedProxies(List.of("10.0.0.0/24"));
        assertThat(new RequestClientAddressResolver(properties).resolve(request)).isEqualTo("192.0.2.5");
        request.removeHeader("X-Forwarded-For"); request.addHeader("X-Forwarded-For","attacker.example.test");
        assertThat(new RequestClientAddressResolver(properties).resolve(request)).isEqualTo("10.0.0.2");
    }
    @Test void disabledAccountsStillPerformPasswordCheckAndCannotAuthenticate() {
        var encoder = spy(new BCryptPasswordEncoder(4));
        String hash = encoder.encode("known-password");
        var provider = new UniformAuthenticationProvider(name -> User.withUsername(name).password(hash).authorities("ROLE_USER").disabled(true).build(),encoder);
        assertThatThrownBy(() -> provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("blocked","known-password")))
                .isInstanceOf(org.springframework.security.authentication.DisabledException.class);
        verify(encoder).matches("known-password",hash);
    }
    @Test void warmedFailureTimingsHaveComparableDistributions() {
        var encoder = new BCryptPasswordEncoder(4);
        String hash = encoder.encode("known-password");
        var provider = new UniformAuthenticationProvider(name -> {
            if (name.equals("unknown")) throw new UsernameNotFoundException("missing");
            return User.withUsername(name).password(hash).authorities("ROLE_USER").disabled(name.equals("blocked")).build();
        },encoder);
        var properties = new AccessRecoveryProperties(); properties.setMinimumResponseTime(Duration.ofMillis(80));
        var timing = new PublicAuthenticationGuard(mock(JdbcTemplate.class),properties);
        List<Double> medians = new ArrayList<>();
        // Warm lazy dummy-hash creation before collecting repeated, alternating samples.
        for (String name : List.of("unknown","existing","blocked")) attempt(provider,name);
        Map<String,List<Double>> samples = new LinkedHashMap<>();
        for (String name : List.of("unknown","existing","blocked")) samples.put(name,new ArrayList<>());
        for (int i=0;i<10;i++) for (var entry : samples.entrySet()) {
            long start = System.nanoTime(); attempt(provider,entry.getKey()); timing.finish(start);
            entry.getValue().add((System.nanoTime()-start)/1_000_000.0);
        }
        samples.forEach((name,values) -> {
            Collections.sort(values); double median=values.get(values.size()/2); medians.add(median);
            System.out.printf(Locale.ROOT,"AUTH_TIMING %s n=%d median=%.2fms min=%.2fms max=%.2fms%n",name,values.size(),median,values.get(0),values.get(values.size()-1));
            assertThat(median).isGreaterThanOrEqualTo(79.0);
        });
        assertThat(Collections.max(medians)-Collections.min(medians)).isLessThan(35.0);
    }
    private void attempt(UniformAuthenticationProvider provider, String name) {
        assertThatThrownBy(() -> provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(name,"wrong-password")))
                .isInstanceOf(org.springframework.security.core.AuthenticationException.class);
    }
    @Test void actionRightsAreInTheExistingDelegationCatalogAndRespectPersonalDenies() {
        assertThat(PanelAccessPolicy.managedAuthorities()).contains("EDIT_CLIENT_UNBLOCK","EDIT_ACCESS_RECOVER");
        assertThat(PanelAccessPolicy.roleAuthorities("{\"fields\":{\"edit\":[\"access.recover\"]}}"))
                .contains("EDIT_ACCESS_RECOVER").doesNotContain("EDIT_CLIENT_UNBLOCK");
        assertThat(PanelAccessPolicy.effective(Set.of("ROLE_ADMIN"),Set.of("DENY_EDIT_ACCESS_RECOVER")))
                .contains("EDIT_CLIENT_UNBLOCK","DENY_EDIT_ACCESS_RECOVER").doesNotContain("EDIT_ACCESS_RECOVER");
    }
}
