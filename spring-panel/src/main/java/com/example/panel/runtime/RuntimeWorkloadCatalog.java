package com.example.panel.runtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

@Component
public class RuntimeWorkloadCatalog {

    private final ApplicationContext applicationContext;

    public RuntimeWorkloadCatalog(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public List<WorkloadDescriptor> enabledWorkloads() {
        List<WorkloadDescriptor> result = new ArrayList<>();
        for (String beanName : applicationContext.getBeanNamesForAnnotation(RuntimeWorkload.class)) {
            Class<?> beanType = applicationContext.getType(beanName);
            if (beanType == null) {
                continue;
            }
            RuntimeWorkload workload = AnnotatedElementUtils.findMergedAnnotation(beanType, RuntimeWorkload.class);
            if (workload == null) {
                continue;
            }
            result.add(new WorkloadDescriptor(
                workload.id(),
                Arrays.asList(workload.roles()),
                workload.replicaPolicy(),
                beanType.getName()
            ));
        }
        result.sort(Comparator.comparing(WorkloadDescriptor::id));
        return List.copyOf(result);
    }

    public record WorkloadDescriptor(
        String id,
        List<RuntimeRole> roles,
        RuntimeReplicaPolicy replicaPolicy,
        String beanType
    ) {
    }
}
