package com.example.panel.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.example.panel.config.*;
import com.example.panel.controller.*;
import com.example.panel.entity.Channel;
import com.example.panel.security.*;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SharedQuestionnaireRegistryPostgresTest {
    @TempDir Path configDir;
    private JdbcTemplate jdbc;
    private SharedConfigService settings;
    private BusinessQuestionTemplateRegistryService registry;
    private final ObjectMapper mapper=new ObjectMapper();
    private long a,b;
    @BeforeEach void setup() throws Exception {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("shared_choices");
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT NOT NULL UNIQUE)");
        jdbc.execute("CREATE TABLE channels(id BIGINT PRIMARY KEY,is_active BOOLEAN NOT NULL DEFAULT TRUE,question_template_id TEXT)");
        jdbc.execute("CREATE TABLE tickets(user_id BIGINT NOT NULL,ticket_id TEXT NOT NULL,PRIMARY KEY(user_id,ticket_id))");
        for(String migration:List.of("V51__multibusiness_foundation.sql","V55__business_question_template_bindings.sql","V57__general_channel_question_template_bindings.sql"))
            jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql",migration)));
        a=jdbc.queryForObject("SELECT id FROM businesses WHERE code='sushi'",Long.class);
        b=jdbc.queryForObject("SELECT id FROM businesses WHERE code='bliny'",Long.class);
        settings=new SharedConfigService(mapper,configDir.toString());
        save("Private A","Private B",false);
        registry=new BusinessQuestionTemplateRegistryService(jdbc,settings,new BotSettingsPayloadNormalizer(),mapper);
        jdbc.update("INSERT INTO channels(id,is_active) VALUES(10,TRUE)");
        jdbc.update("INSERT INTO business_channel_bindings(channel_id,business_id,resolution_mode) VALUES(10,?,'EXPLICIT_SELECTION'),(10,?,'EXPLICIT_SELECTION')",a,b);
        registry.bind(a,"q-a");registry.bind(b,"q-b");
    }
    private void save(String textA,String textB,boolean reversed) {
        var templates=new ArrayList<Map<String,Object>>(List.of(template("q-legacy","GLOBAL SECRET"),template("q-a",textA),template("q-b",textB)));
        if(reversed) Collections.reverse(templates);
        settings.saveSettings(Map.of("bot_settings",Map.of("active_template_id","q-legacy","global_secret","not-for-runtime","question_templates",templates)));
    }
    private Map<String,Object> template(String id,String text) {
        return Map.of("id",id,"name",id,"question_flow",List.of(Map.of("id","free-question","type","text","order",1,"text",text)));
    }
    @Test void choicesContainOnlyCurrentLabelsAndSelectedRuntimeContainsOnlyItsOwnTemplate() throws Exception {
        var catalog=registry.choices(10).orElseThrow();
        assertThat(catalog.version()).isEqualTo(1);
        assertThat(catalog.catalogSha256()).matches("[a-f0-9]{64}");
        assertThat(catalog.choices()).extracting(BusinessQuestionTemplateRegistryService.Choice::businessId).containsExactly(a,b);
        assertThat(mapper.writeValueAsString(catalog)).doesNotContain("Private A","Private B","GLOBAL SECRET","global_secret");
        for(long selected:List.of(a,b)) {
            var runtime=registry.select(10,selected,catalog.catalogSha256()).orElseThrow();
            assertThat(runtime.scopeKind()).isEqualTo("BUSINESS");assertThat(runtime.scopeSelectionSource()).isEqualTo("USER_SELECTION");
            assertThat(runtime.businessId()).isEqualTo(selected);
            assertThat(runtime.botSettings().keySet()).containsExactlyInAnyOrder("active_template_id","question_templates");
            assertThat(runtime.locationTree()).isEmpty();assertThat(runtime.presetDefinitions()).isEmpty();
            assertThat(mapper.writeValueAsString(runtime)).contains(selected==a?"Private A":"Private B")
                .doesNotContain(selected==a?"Private B":"Private A","GLOBAL SECRET","global_secret");
        }
        assertThat(registry.select(10,999,catalog.catalogSha256())).isEmpty();
        assertThat(registry.hasActiveChannelBindings(10)).isTrue();
    }
    @ParameterizedTest @ValueSource(strings={"channel","general-only","mode","archived","template-inactive","missing-template","stale-a","stale-b","preset","duplicate-template","duplicate-question","legacy-channel","legacy-active","zero-bindings"})
    void malformedMixedAndStaleCatalogsFailClosed(String mutation) {
        var old=registry.choices(10).orElseThrow();
        switch(mutation) {
            case "channel"->jdbc.update("UPDATE channels SET is_active=FALSE WHERE id=10");
            case "general-only"->jdbc.update("INSERT INTO general_channel_question_template_bindings(channel_id,template_id,template_sha256) VALUES(10,'q-legacy',?)","a".repeat(64));
            case "mode"->jdbc.update("UPDATE business_channel_bindings SET resolution_mode='SINGLE_BUSINESS' WHERE business_id=?",b);
            case "archived"->jdbc.update("UPDATE businesses SET status='archived' WHERE id=?",b);
            case "template-inactive"->registry.deactivate(b);
            case "missing-template"->jdbc.update("DELETE FROM business_question_template_bindings WHERE business_id=?",b);
            case "stale-a"->save("changed A","Private B",false);
            case "stale-b"->save("Private A","changed B",false);
            case "legacy-channel"->jdbc.update("INSERT INTO channels(id,is_active,question_template_id) VALUES(20,TRUE,'q-a')");
            case "legacy-active"->settings.saveSettings(Map.of("bot_settings",Map.of("active_template_id","q-a","question_templates",List.of(template("q-a","Private A"),template("q-b","Private B")))));
            case "zero-bindings"->jdbc.update("UPDATE business_channel_bindings SET is_active=FALSE WHERE channel_id=10");
            case "preset","duplicate-template","duplicate-question"->{
                Map<String,Object> invalid=new LinkedHashMap<>(template("q-a","Private A"));
                if("preset".equals(mutation)) invalid.put("question_flow",List.of(Map.of("id","where","type","preset")));
                if("duplicate-question".equals(mutation)) invalid.put("question_flow",List.of(Map.of("id","same","type","text"),Map.of("id","same","type","text")));
                List<Map<String,Object>> templates=new ArrayList<>(List.of(template("q-legacy","GLOBAL"),invalid,template("q-b","Private B")));
                if("duplicate-template".equals(mutation)) templates.add(template("q-a","duplicate"));
                settings.saveSettings(Map.of("bot_settings",Map.of("active_template_id","q-legacy","question_templates",templates)));
                if("duplicate-question".equals(mutation)) registry.bind(a,"q-a");
            }
        }
        assertThat(registry.choices(10)).isEmpty();
        assertThat(registry.select(10,a,old.catalogSha256())).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"rename","remove","rebind","add"})
    void oldMenuCannotSelectAfterAnyVisibleMembershipOrTemplateChange(String mutation) {
        var old=registry.choices(10).orElseThrow();
        switch(mutation) {
            case "rename"->jdbc.update("UPDATE businesses SET display_name='Renamed' WHERE id=?",b);
            case "remove"->jdbc.update("UPDATE business_channel_bindings SET is_active=FALSE WHERE business_id=?",b);
            case "rebind"->{save("Private A","new B",false);registry.bind(b,"q-b");}
            case "add"->{
                long c=jdbc.queryForObject("INSERT INTO businesses(code,display_name) VALUES('extra','Extra') RETURNING id",Long.class);
                settings.saveSettings(Map.of("bot_settings",Map.of("active_template_id","q-legacy","question_templates",
                    List.of(template("q-legacy","GLOBAL"),template("q-a","Private A"),template("q-b","Private B"),template("q-c","Private C")))));
                registry.bind(c,"q-c");
                jdbc.update("INSERT INTO business_channel_bindings(channel_id,business_id,resolution_mode) VALUES(10,?,'EXPLICIT_SELECTION')",c);
            }
        }
        var current=registry.choices(10).orElseThrow();
        assertThat(current.catalogSha256()).isNotEqualTo(old.catalogSha256());
        assertThat(registry.select(10,a,old.catalogSha256())).isEmpty();
        assertThat(registry.select(10,a,current.catalogSha256())).isPresent();
    }
    @Test void canonicalListReorderDoesNotInvalidateMenuAndReturnedPayloadIsDetached() {
        var catalog=registry.choices(10).orElseThrow();
        save("Private A","Private B",true);
        assertThat(registry.choices(10).orElseThrow().catalogSha256()).isEqualTo(catalog.catalogSha256());
        var selected=registry.select(10,a,catalog.catalogSha256()).orElseThrow();
        @SuppressWarnings("unchecked") var payload=(Map<String,Object>)((List<?>)selected.botSettings().get("question_templates")).get(0);
        payload.put("name","mutated local copy");
        assertThat(registry.select(10,a,catalog.catalogSha256()).orElseThrow().botSettings().toString()).doesNotContain("mutated local copy");
        assertThatThrownBy(()->registry.choices(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->registry.select(10,a,"bad")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void actualHttpSelectionChecksSignatureQueryFlagsStalenessAndNoStore() throws Exception {
        var properties=new InternalBotApiProperties();properties.setToken("test-token");properties.setSignatureSecret("test-secret");properties.setRequireRequestSignature(true);
        @SuppressWarnings("unchecked") ObjectProvider<StringRedisTemplate> provider=mock(ObjectProvider.class);
        var guard=new InternalBotApiRequestGuardService(properties,new RuntimeCoordinationProperties(),mapper,provider);
        var flags=new MultibusinessFeatureFlags();flags.getFoundation().setEnabled(true);flags.setSharedChannelResolution(true);
        var channels=mock(BotRuntimeChannelService.class);Channel channel=new Channel();channel.setId(10L);
        when(channels.findChannel(10L)).thenReturn(Optional.of(channel));
        var runtime=new BotRuntimeConfigService(channels,settings,new BotSettingsPayloadNormalizer(),mapper,
            new GeneralChannelQuestionTemplateService(jdbc,settings,new BotSettingsPayloadNormalizer(),mapper),flags,registry);
        var mvc=MockMvcBuilders.standaloneSetup(new SharedQuestionnaireRuntimeController(registry,guard,flags),
            new BotRuntimeReadApiController(mock(BotRuntimeTicketReadService.class),channels,runtime,mock(BotRuntimeBlacklistService.class),guard)).build();
        String root="/internal/api/bot/channels/10/questionnaire";
        mvc.perform(get(root+"/choices")).andExpect(status().isUnauthorized());
        mvc.perform(get(root+"/choices").header("X-Iguana-Bot-Api-Token","test-token")).andExpect(status().isUnauthorized());
        mvc.perform(signed("/internal/api/bot/channels/10/runtime-config")).andExpect(status().isConflict());
        mvc.perform(signed(root+"/choices")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.choices.length()").value(2));
        String digest=registry.choices(10).orElseThrow().catalogSha256();
        String selection=root+"/selection?businessId="+a+"&catalogSha256="+digest;
        mvc.perform(signed(selection)).andExpect(status().isOk()).andExpect(jsonPath("$.businessId").value(a)).andExpect(jsonPath("$.scopeKind").value("BUSINESS"));
        mvc.perform(signed(root+"/choices?forStart=true")).andExpect(status().isNotFound()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(signed(selection+"&forStart=true")).andExpect(status().isNotFound());
        flags.setTicketWriteRequiresBusiness(true);
        mvc.perform(signed(root+"/choices?forStart=true")).andExpect(status().isOk()).andExpect(jsonPath("$.choices.length()").value(2))
            .andExpect(header().string("X-Iguana-Questionnaire-Writer-Version","1"));
        mvc.perform(signed(selection+"&forStart=true")).andExpect(status().isOk()).andExpect(jsonPath("$.businessId").value(a))
            .andExpect(header().string("X-Iguana-Questionnaire-Writer-Version","1"));
        mvc.perform(signed(selection+"&forStart=true").queryParam("tamper","true")).andExpect(status().isUnauthorized());
        mvc.perform(signed(selection).queryParam("unexpected","tamper")).andExpect(status().isUnauthorized());
        jdbc.update("UPDATE businesses SET display_name='Changed' WHERE id=?",a);
        mvc.perform(signed(selection)).andExpect(status().isConflict()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(signed(root+"/selection?businessId="+a+"&catalogSha256=bad")).andExpect(status().isBadRequest());
        flags.setSharedChannelResolution(false);
        mvc.perform(signed(root+"/choices")).andExpect(status().isNotFound());
    }
    private MockHttpServletRequestBuilder signed(String path) throws Exception {
        String time=Instant.now().toString();
        String bodyHash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new byte[0]));
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        String signature=HexFormat.of().formatHex(mac.doFinal(("GET\n"+path+"\n"+time+"\n"+bodyHash).getBytes(StandardCharsets.UTF_8)));
        return get(path).header("X-Iguana-Bot-Api-Token","test-token").header("X-Iguana-Request-Timestamp",time).header("X-Iguana-Request-Signature",signature);
    }
}
