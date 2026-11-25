package com.example.panel.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.List;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

/** Only the explicitly trusted ingress may supply a forwarded address. No DNS lookups on headers. */
public class RequestClientAddressResolver {
    private final List<IpAddressMatcher> proxies;

    public RequestClientAddressResolver(AccessRecoveryProperties properties) {
        proxies = properties.getTrustedProxies().stream().map(IpAddressMatcher::new).toList();
    }

    public String resolve(HttpServletRequest request) {
        request = original(request);
        String peer = numeric(request.getRemoteAddr());
        if (!trusted(peer)) return peer;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.length() > 2048) return peer;
        String[] chain = forwarded.split(",", -1);
        if (chain.length > 16) return peer;
        String current = peer;
        for (int i = chain.length - 1; i >= 0 && trusted(current); i--) {
            String candidate = numeric(chain[i].trim());
            if (candidate.isEmpty()) return peer;
            current = candidate;
        }
        return current;
    }

    public String connectionAddress(HttpServletRequest request) {
        return numeric(original(request).getRemoteAddr());
    }

    private static HttpServletRequest original(HttpServletRequest request) {
        // ForwardedHeaderFilter rewrites getRemoteAddr() and hides forwarded headers in wrappers.
        // Authorization of the proxy must use the underlying connection, never that rewritten value.
        jakarta.servlet.ServletRequest current = request;
        while (current instanceof jakarta.servlet.ServletRequestWrapper wrapper) current = wrapper.getRequest();
        return (HttpServletRequest) current;
    }

    private boolean trusted(String ip) {
        return !ip.isEmpty() && proxies.stream().anyMatch(proxy -> proxy.matches(ip));
    }

    private static String numeric(String value) {
        if (value == null || value.length() > 45 || !value.matches("[0-9a-fA-F:.]+")) return "";
        try { return InetAddress.getByName(value).getHostAddress(); }
        catch (Exception ignored) { return ""; }
    }
}
