package com.example.panel.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Configures diagnostics before the external Hikari datasource is initialized.
 */
@Component
public class HikariRuntimeDiagnostics implements BeanPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(HikariRuntimeDiagnostics.class);
    private static final long DEFAULT_LEAK_DETECTION_MS = 15_000L;

    private final Environment environment;

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
        log.info(
                "Hikari diagnostics enabled for {}: maxPoolSize={}, connectionTimeoutMs={}, leakDetectionMs={}",
                beanName,
                hikari.getMaximumPoolSize(),
                hikari.getConnectionTimeout(),
                hikari.getLeakDetectionThreshold()
        );
        return bean;
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
