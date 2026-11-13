package com.example.panel.service;

import static org.assertj.core.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChildProcessEnvironmentTest {
    @ParameterizedTest @ValueSource(strings={"APP_INTERNAL_BOT_API_TOKEN","APP_INTERNAL_BOT_API_SIGNATURE_SECRET",
        "DATABASE_URL","SPRING_DATASOURCE_PASSWORD","SPRING_APPLICATION_JSON","SPRING_CONFIG_LOCATION","JAVA_TOOL_OPTIONS",
        "JDK_JAVA_OPTIONS","MAVEN_OPTS","SPRING_RABBITMQ_ADDRESSES","SPRING_RABBITMQ_URI","APP_STORAGE_OBJECT_UNEXPECTED_SECRET",
        "APP_BOTS_INFRASTRUCTURE_RABBITMQ_MANAGEMENT_URL","APP_BOTS_INFRASTRUCTURE_RABBITMQ_MANAGEMENT_USERNAME",
        "APP_BOTS_INFRASTRUCTURE_RABBITMQ_MANAGEMENT_PASSWORD"})
    void inheritedControlPlaneAndOverrideInputsAreNotChildCredentials(String key) {
        var process=new ProcessBuilder("not-started");process.environment().put(key,"foreign-secret");
        var contract=Map.of("APP_PANEL_INTERNAL_API_TOKEN","iguana-child-v1:max:22:"+"a".repeat(64),
            "SPRING_DATASOURCE_USERNAME","iguana_child_22","SPRING_DATASOURCE_PASSWORD","sql-owned",
            "SPRING_RABBITMQ_USERNAME","iguana_child_22","SPRING_RABBITMQ_PASSWORD","rabbit-owned","JAVA_TOOL_OPTIONS","-Dfile.encoding=UTF-8");
        BotProcessService.applyBotProcessEnvironment(process,contract);
        assertThat(process.environment().get(key)).isNotEqualTo("foreign-secret");
        assertThat(process.environment()).containsAllEntriesOf(contract);
    }
    @Test void explicitInfrastructureInputsArePreservedAndContractWins() {
        var process=new ProcessBuilder("not-started");process.environment().put("SPRING_RABBITMQ_HOST","broker");
        process.environment().put("SPRING_DATA_REDIS_HOST","redis");process.environment().put("APP_COORDINATION_MODE","redis");
        process.environment().put("SPRING_RABBITMQ_USERNAME","control-plane");
        BotProcessService.applyBotProcessEnvironment(process,Map.of("APP_PANEL_INTERNAL_API_TOKEN","iguana-child-v1:max:22:"+"a".repeat(64),"SPRING_RABBITMQ_USERNAME","iguana_child_22"));
        assertThat(process.environment()).containsEntry("SPRING_RABBITMQ_HOST","broker").containsEntry("SPRING_DATA_REDIS_HOST","redis")
            .containsEntry("APP_COORDINATION_MODE","redis").containsEntry("SPRING_RABBITMQ_USERNAME","iguana_child_22");
    }
}
