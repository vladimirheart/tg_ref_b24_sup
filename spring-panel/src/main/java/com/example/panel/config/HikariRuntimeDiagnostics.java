package com.example.panel.config;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPoolMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Diagnostics for the shared external-database Hikari pool.
 *
 * <p>The application has several logical JdbcTemplate/DataSource aliases which
 * all point at the same physical PostgreSQL datasource. When web requests or
 * background tasks hold those connections too long, failures otherwise appear
 * only as generic "Connection is not available" timeouts. This component adds
 * a bounded leak detector and emits pool state only while callers are waiting.</p>
 */
@Component
public class HikariRuntimeDiagnostics implements BeanPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(HikariRuntimeDiagnostics.class);
    private static final long DEFAULT_LEAK_DETECTION_MS = 15_000L;

    private final Environment environment;
    private volatile HikariDataSource hikariDataSource;

    public HikariRuntimeDiagnostics(Environment environment) {
        this.environment = environment;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (!(bean instanceof HikariDataSource hikari)) {
            return bean;
        }

        long leakDetectionMs = readLongSetting("APP_DB_LEAK_DETECTION_MS", DEFAULT_LEAK_DETECTION_MS);
        if (leakDetectionMs > 0L && leakDetectionMs < 2_000L) {
            leakDetectionMs = 2_000L;
        }
        if (leakDetectionMs >= 2_000L) {
            hikari.setLeakDetectionThreshold(leakDetectionMs);
        }
        this.hikariDataSource = hikari;
        log.info(
                "Hikari diagnostics enabled for {}: maxPoolSize={}, connectionTimeoutMs={}, leakDetectionMs={}",
                beanName,
                hikari.getMaximumPoolSize(),
                hikari.getConnectionTimeout(),
                hikari.getLeakDetectionThreshold()
        );
        return bean;
    }

    @Scheduled(fixedDelayString = "${panel.datasource.pool-diagnostics-interval-ms:5000}")
    public void reportPoolPressure() {
        HikariDataSource hikari = hikariDataSource;
        if (hikari == null || hikari.isClosed()) {
            return;
        }
        HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
        if (pool == null) {
            return;
        }

        int active = pool.getActiveConnections();
        int idle = pool.getIdleConnections();
        int total = pool.getTotalConnections();
        int waiting = pool.getThreadsAwaitingConnection();
        if (waiting <= 0 && (total <= 0 || active < total)) {
            return;
        }

        log.warn(
                "[DB-POOL] Hikari pressure: active={}, idle={}, total={}, waiting={}, max={}",
                active,
                idle,
                total,
                waiting,
                hikari.getMaximumPoolSize()
        );
    }

    private long readLongSetting(String name, long defaultValue) {
        String raw = environment.getProperty(name);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}
