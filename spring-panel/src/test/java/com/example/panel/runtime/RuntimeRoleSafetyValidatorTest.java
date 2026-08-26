package com.example.panel.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeRoleSafetyValidatorTest {

    @Test
    void compatibilityAllAllowsAutoFanout() {
        RuntimeRoleProperties runtime = new RuntimeRoleProperties();
        runtime.setRole("all");

        UiEventFanoutProperties fanout = new UiEventFanoutProperties();
        fanout.setMode("auto");

        assertThatCode(() -> new RuntimeRoleSafetyValidator(runtime, fanout).validate())
            .doesNotThrowAnyException();
    }

    @Test
    void splitWebRequiresRedisFanout() {
        RuntimeRoleProperties runtime = new RuntimeRoleProperties();
        runtime.setRole("web");

        UiEventFanoutProperties fanout = new UiEventFanoutProperties();
        fanout.setMode("auto");

        assertThatThrownBy(() -> new RuntimeRoleSafetyValidator(runtime, fanout).validate())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("APP_UI_EVENT_FANOUT_MODE=redis");
    }

    @Test
    void splitWorkerAcceptsRedisFanout() {
        RuntimeRoleProperties runtime = new RuntimeRoleProperties();
        runtime.setRole("worker");

        UiEventFanoutProperties fanout = new UiEventFanoutProperties();
        fanout.setMode("redis");

        assertThatCode(() -> new RuntimeRoleSafetyValidator(runtime, fanout).validate())
            .doesNotThrowAnyException();
    }
}
