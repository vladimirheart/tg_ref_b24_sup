package com.example.panel.service;

import com.example.panel.security.PanelSecurityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AutomationCredentialRegistryIdentityTest {
    private static final String TYPE="employee_discount_automation_credentials.account.v2";
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private Map<String,Object> discover(List<Map<String,Object>> rows) {
        when(jdbc.queryForList(anyString(),anyString())).thenReturn(rows);
        var service=new CredentialRotationRegistryService(null,null,null,null,null,null,null,jdbc,new PanelSecurityProperties(),new ObjectMapper(),null,null,null,java.time.Clock.systemUTC());
        Map<String,Object> target=new LinkedHashMap<>();ReflectionTestUtils.invokeMethod(service,"addAutomationCredentials",target);return target;
    }
    private Map<String,Object> row(String key){return Map.of("value",key,"extra_json","{\"bitrix24\":{\"portal_url\":\"fixture-portal\",\"webhook_url\":\"fixture-secret\"}}");}
    @ParameterizedTest @ValueSource(strings={"7","9007199254740993","9223372036854775807"})
    void discoveryUsesExactNumericIdentityAndTheNewNamespace(String key) {
        var target=discover(List.of(row(key)));assertThat(target).containsOnlyKeys("employee-discount.account-"+key+".bitrix24.webhook_url");
        assertThat(target.values().iterator().next().toString()).contains(TYPE+"["+key+"]").doesNotContain("fixture-secret");
        verify(jdbc).queryForList(anyString(),eq(TYPE));
    }
    @ParameterizedTest @ValueSource(strings={"007","+7"," 7 ","0","-7","9223372036854775808","reader"})
    void noncanonicalOrUnboundKeysAreNotAttributedToAnAccount(String key){assertThat(discover(List.of(row(key)))).isEmpty();}
    @Test void twoAccountsCannotMergeTheirRegistryMetadata(){assertThat(discover(List.of(row("7"),row("99")))).containsOnlyKeys("employee-discount.account-7.bitrix24.webhook_url","employee-discount.account-99.bitrix24.webhook_url");}
    @Test void duplicateOwnedRowsAreNotSilentlyClassifiedAsOneHealthySource(){assertThat(discover(List.of(row("7"),row("7")))).isEmpty();}
    @Test void distinctProfileUrlsWithTheSameSlugRemainDistinctSources(){var row=Map.<String,Object>of("value","7","extra_json","{\"iiko_profiles\":{\"https://iiko.test/a_b\":{\"base_url\":\"https://iiko.test/a_b\",\"api_secret\":\"one\"},\"https://iiko.test/a-b\":{\"base_url\":\"https://iiko.test/a-b\",\"api_secret\":\"two\"}}}");var target=discover(List.of(row));assertThat(target).hasSize(2);assertThat(target.keySet()).allSatisfy(key -> assertThat(key).matches("employee-discount\\.account-7\\.iiko\\.[a-f0-9]{64}\\.api_secret"));}
    @Test void corruptPayloadDoesNotCreateASource(){assertThat(discover(List.of(Map.of("value","7","extra_json","broken")))).isEmpty();}
}
