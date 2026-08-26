package com.example.panel.runtime;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RuntimeWorkload(
    id = "runtime-role-safety-validator",
    roles = {RuntimeRole.ALL},
    replicaPolicy = RuntimeReplicaPolicy.PROCESS_LOCAL
)
public class RuntimeRoleSafetyValidator implements ApplicationRunner {

    private final RuntimeRoleProperties runtimeProperties;
    private final UiEventFanoutProperties fanoutProperties;

    public RuntimeRoleSafetyValidator(RuntimeRoleProperties runtimeProperties,
                                      UiEventFanoutProperties fanoutProperties) {
        this.runtimeProperties = runtimeProperties;
        this.fanoutProperties = fanoutProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        validate();
    }

    void validate() {
        RuntimeRole role = runtimeProperties.resolvedRole();
        if (role != RuntimeRole.WEB && role != RuntimeRole.WORKER) {
            return;
        }

        if (fanoutProperties.resolvedMode() != UiEventFanoutMode.REDIS) {
            throw new IllegalStateException(
                "Split Iguana runtime role '" + role.externalName()
                    + "' requires APP_UI_EVENT_FANOUT_MODE=redis. "
                    + "Local/auto fanout is allowed only for compatibility role=all."
            );
        }
    }
}
