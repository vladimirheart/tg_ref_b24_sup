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
    private long waitingSince;
    private long lastDump;

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
        long now=System.nanoTime();
        if(waiting<=0)waitingSince=0;
        else {
            if(waitingSince==0)waitingSince=now;
            if(now-waitingSince>=java.util.concurrent.TimeUnit.SECONDS.toNanos(15)
                    &&(lastDump==0||now-lastDump>=java.util.concurrent.TimeUnit.MINUTES.toNanos(5))) {
                lastDump=now;
                var dump=new StringBuilder("[DB-POOL] sustained waiting thread dump");
                for(var info:java.lang.management.ManagementFactory.getThreadMXBean().dumpAllThreads(true,true)) {
                    if(info==null)continue;
                    dump.append('\n').append(info.getThreadName()).append(" state=").append(info.getThreadState())
                        .append(" lockOwner=").append(info.getLockOwnerId());
                    var stack=info.getStackTrace();
                    for(int i=0;i<Math.min(32,stack.length);i++)dump.append("\n at ").append(stack[i]);
                }
                log.warn("{}",dump);
            }
        }
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
