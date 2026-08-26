package com.example.panel.config;

import com.example.panel.runtime.RuntimeWorkload;
import com.example.panel.runtime.RuntimeRole;
import com.example.panel.runtime.RuntimeReplicaPolicy;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Emits a compact warning only when the primary Hikari pool is saturated or
 * callers are waiting for a connection. This keeps normal logs quiet while
 * making future PostgreSQL connection starvation diagnosable from runtime logs.
 */
@RuntimeWorkload(
    id = "hikari-pool-pressure-reporter",
    roles = {RuntimeRole.WEB, RuntimeRole.WORKER},
    replicaPolicy = RuntimeReplicaPolicy.PROCESS_LOCAL
)@Component
public class HikariPoolPressureReporter {

    private static final Logger log = LoggerFactory.getLogger(HikariPoolPressureReporter.class);

    private final DataSource dataSource;

    public HikariPoolPressureReporter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Scheduled(fixedDelayString = "${panel.datasource.pool-diagnostics-interval-ms:5000}")
    public void reportPoolPressure() {
        if (!(dataSource instanceof HikariDataSource hikari) || hikari.isClosed()) {
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
}
