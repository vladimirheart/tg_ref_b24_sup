package com.example.panel.runtime;

import java.util.Arrays;
import java.util.Map;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class RuntimeWorkloadCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attributes = metadata.getAnnotationAttributes(RuntimeWorkload.class.getName());
        if (attributes == null) {
            return true;
        }

        RuntimeRole activeRole = RuntimeRole.from(
            context.getEnvironment().getProperty("app.runtime.role", "all")
        );
        if (activeRole == RuntimeRole.ALL) {
            return true;
        }

        Object rawRoles = attributes.get("roles");
        if (!(rawRoles instanceof RuntimeRole[] roles)) {
            return false;
        }
        return Arrays.stream(roles).anyMatch(role -> role == RuntimeRole.ALL || role == activeRole);
    }
}
