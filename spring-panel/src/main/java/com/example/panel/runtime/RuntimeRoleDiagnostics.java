package com.example.panel.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

@Component
public class RuntimeRoleDiagnostics implements ApplicationRunner, InfoContributor {

    private static final Logger log = LoggerFactory.getLogger(RuntimeRoleDiagnostics.class);

    private final RuntimeRoleProperties properties;
    private final RuntimeWorkloadCatalog workloadCatalog;

    public RuntimeRoleDiagnostics(RuntimeRoleProperties properties,
                                  RuntimeWorkloadCatalog workloadCatalog) {
        this.properties = properties;
        this.workloadCatalog = workloadCatalog;
    }

    @Override
    public void run(ApplicationArguments args) {
        RuntimeRole role = properties.resolvedRole();
        List<RuntimeWorkloadCatalog.WorkloadDescriptor> workloads = workloadCatalog.enabledWorkloads();
        long singletonCount = workloads.stream()
            .filter(item -> item.replicaPolicy() == RuntimeReplicaPolicy.SINGLETON)
            .count();

        log.info(
            "Iguana runtime role selected: role={}, instanceId={}, enabledWorkloads={}, singletonWorkloads={}",
            role.externalName(),
            properties.resolvedInstanceId(),
            workloads.size(),
            singletonCount
        );

        if (role == RuntimeRole.WORKER && singletonCount > 0) {
            log.warn(
                "ops-worker contains {} SINGLETON workload(s); do not scale worker replicas above 1 until those workloads are lease/claim hardened",
                singletonCount
            );
        }
    }

    @Override
    public void contribute(Info.Builder builder) {
        List<RuntimeWorkloadCatalog.WorkloadDescriptor> workloads = workloadCatalog.enabledWorkloads();
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("role", properties.resolvedRole().externalName());
        runtime.put("instanceId", properties.resolvedInstanceId());
        runtime.put("roleEnforcement", true);
        runtime.put("enabledWorkloads", workloads.stream().map(RuntimeWorkloadCatalog.WorkloadDescriptor::id).toList());
        runtime.put("singletonWorkloads", workloads.stream()
            .filter(item -> item.replicaPolicy() == RuntimeReplicaPolicy.SINGLETON)
            .map(RuntimeWorkloadCatalog.WorkloadDescriptor::id)
            .toList());
        builder.withDetail("iguanaRuntime", runtime);
    }
}
