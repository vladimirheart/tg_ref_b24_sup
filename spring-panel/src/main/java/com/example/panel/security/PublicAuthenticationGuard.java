package com.example.panel.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.locks.LockSupport;
import org.springframework.jdbc.core.JdbcTemplate;

public class PublicAuthenticationGuard {
    private final JdbcTemplate jdbc;
    private final AccessRecoveryProperties properties;

    public PublicAuthenticationGuard(JdbcTemplate jdbc, AccessRecoveryProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    public boolean allow(String purpose, String ip, String login) {
        boolean signIn = "login".equals(purpose);
        boolean addressAllowed = increment(purpose + ":ip", ip,
                signIn ? properties.getLoginAttemptsPerIp() : properties.getRequestsPerIp());
        boolean loginAllowed = increment(purpose + ":account", login.toLowerCase(Locale.ROOT),
                signIn ? properties.getLoginAttemptsPerLogin() : properties.getRequestsPerLogin());
        return addressAllowed && loginAllowed;
    }

    private boolean increment(String scope, String key, int limit) {
        long seconds = Math.max(60, properties.getRateWindow().toSeconds());
        long window = Instant.now().getEpochSecond() / seconds * seconds;
        Integer count = jdbc.queryForObject("""
                INSERT INTO public_auth_rate_limits(scope, key_digest, window_start) VALUES (?, ?, ?)
                ON CONFLICT(scope, key_digest, window_start)
                DO UPDATE SET hits = LEAST(public_auth_rate_limits.hits + 1, 1000000)
                RETURNING hits
                """, Integer.class, scope, digest(key), window);
        return count != null && count <= Math.max(1, limit);
    }

    /** Common processing paths do the work; the floor absorbs ordinary DB/hash jitter. */
    public void finish(long startedNanos) {
        long budget = Math.max(0, properties.getMinimumResponseTime().toNanos());
        long remaining;
        while (!Thread.currentThread().isInterrupted()
                && (remaining = budget - (System.nanoTime() - startedNanos)) > 0) {
            LockSupport.parkNanos(remaining);
        }
    }

    public static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public void recordFailure(String result, String ip, String login, String agent) {
        try {
            String safeAgent = agent == null ? "" : agent.replaceAll("[\\p{Cntrl}]", "");
            jdbc.update("INSERT INTO public_auth_events(result, principal_digest, source_ip, user_agent) VALUES (?, ?, ?, ?)",
                    result, digest(login == null ? "" : login.toLowerCase(Locale.ROOT)), ip,
                    safeAgent.substring(0, Math.min(512, safeAgent.length())));
        } catch (org.springframework.dao.DataAccessException unavailable) {
            org.slf4j.LoggerFactory.getLogger(PublicAuthenticationGuard.class).warn("Authentication failure audit unavailable");
        }
    }
}
