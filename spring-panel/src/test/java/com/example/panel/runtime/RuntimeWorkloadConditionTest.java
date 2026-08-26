package com.example.panel.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeWorkloadConditionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(FixtureConfiguration.class);

    @Test
    void defaultAllRoleKeepsCompatibilityAndLoadsEveryWorkload() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("webWorkload");
            assertThat(context).hasBean("workerWorkload");
            assertThat(context).hasBean("sharedWorkload");
        });
    }

    @Test
    void panelWebAliasLoadsOnlyWebCompatibleWorkloads() {
        contextRunner
            .withPropertyValues("app.runtime.role=panel-web")
            .run(context -> {
                assertThat(context).hasBean("webWorkload");
                assertThat(context).doesNotHaveBean("workerWorkload");
                assertThat(context).hasBean("sharedWorkload");
            });
    }

    @Test
    void opsWorkerAliasLoadsOnlyWorkerCompatibleWorkloads() {
        contextRunner
            .withPropertyValues("app.runtime.role=ops-worker")
            .run(context -> {
                assertThat(context).doesNotHaveBean("webWorkload");
                assertThat(context).hasBean("workerWorkload");
                assertThat(context).hasBean("sharedWorkload");
            });
    }

    @Test
    void runtimeRoleParserAcceptsDeploymentAliases() {
        assertThat(RuntimeRole.from("all")).isEqualTo(RuntimeRole.ALL);
        assertThat(RuntimeRole.from("web")).isEqualTo(RuntimeRole.WEB);
        assertThat(RuntimeRole.from("panel-web")).isEqualTo(RuntimeRole.WEB);
        assertThat(RuntimeRole.from("worker")).isEqualTo(RuntimeRole.WORKER);
        assertThat(RuntimeRole.from("ops-worker")).isEqualTo(RuntimeRole.WORKER);
    }

    @Configuration(proxyBeanMethods = false)
    static class FixtureConfiguration {

        @Bean
        @RuntimeWorkload(
            id = "test-web",
            roles = {RuntimeRole.WEB},
            replicaPolicy = RuntimeReplicaPolicy.PROCESS_LOCAL
        )
        String webWorkload() {
            return "web";
        }

        @Bean
        @RuntimeWorkload(
            id = "test-worker",
            roles = {RuntimeRole.WORKER},
            replicaPolicy = RuntimeReplicaPolicy.SINGLETON
        )
        Integer workerWorkload() {
            return 1;
        }

        @Bean
        @RuntimeWorkload(
            id = "test-shared",
            roles = {RuntimeRole.WEB, RuntimeRole.WORKER},
            replicaPolicy = RuntimeReplicaPolicy.PROCESS_LOCAL
        )
        Long sharedWorkload() {
            return 1L;
        }
    }
}
