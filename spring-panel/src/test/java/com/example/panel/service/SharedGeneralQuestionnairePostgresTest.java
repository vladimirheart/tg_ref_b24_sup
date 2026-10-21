package com.example.panel.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.panel.config.*;
import com.example.panel.controller.*;
import com.example.panel.entity.Channel;
import com.example.panel.service.integration.BusinessTicketWriteScopeResolver;
import com.example.panel.security.InternalBotApiRequestGuardService;
import com.example.panel.security.InternalBotApiProperties;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

class SharedGeneralQuestionnairePostgresTest {
    @TempDir Path configDir;
    private JdbcTemplate jdbc;
    private SharedConfigService settings;
    private BusinessQuestionTemplateRegistryService businesses;
    private GeneralChannelQuestionTemplateService generalOnly;
    private SharedGeneralQuestionnaireService shared;
    private final ObjectMapper mapper = new ObjectMapper();
    private long a, b;

    @BeforeEach void setup() throws Exception {
        jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("shared_general");
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT NOT NULL UNIQUE)");
        jdbc.execute("CREATE TABLE channels(id BIGINT PRIMARY KEY,is_active BOOLEAN NOT NULL DEFAULT TRUE,question_template_id TEXT)");
        jdbc.execute("CREATE TABLE tickets(user_id BIGINT NOT NULL,ticket_id TEXT NOT NULL,PRIMARY KEY(user_id,ticket_id))");
        for (String migration : List.of("V51__multibusiness_foundation.sql", "V55__business_question_template_bindings.sql",
                "V57__general_channel_question_template_bindings.sql", "V74__shared_channel_general_questionnaire.sql"))
            jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql", migration)));
        a = jdbc.queryForObject("SELECT id FROM businesses WHERE code='sushi'", Long.class);
        b = jdbc.queryForObject("SELECT id FROM businesses WHERE code='bliny'", Long.class);
        settings = new SharedConfigService(mapper, configDir.toString());
        save("General first", "Private A");
        var normalizer = new BotSettingsPayloadNormalizer();
        businesses = transactional(new BusinessQuestionTemplateRegistryService(jdbc, settings, normalizer, mapper));
        generalOnly = transactional(new GeneralChannelQuestionTemplateService(jdbc, settings, normalizer, mapper));
        shared = transactional(new SharedGeneralQuestionnaireService(jdbc, businesses, settings, normalizer, mapper));
        jdbc.update("INSERT INTO channels(id,is_active) VALUES(10,TRUE),(11,TRUE)");
        jdbc.update("INSERT INTO business_channel_bindings(channel_id,business_id,resolution_mode) VALUES(10,?,'EXPLICIT_SELECTION'),(10,?,'EXPLICIT_SELECTION')", a, b);
        businesses.bind(a, "q-a"); businesses.bind(b, "q-b");
    }

    @SuppressWarnings("unchecked") private <T> T transactional(T target) {
        var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(jdbc.getDataSource()),
            new AnnotationTransactionAttributeSource()));
        return (T) proxy.getProxy();
    }
    private Map<String, Object> template(String id, String text) {
        return Map.of("id", id, "name", id, "question_flow", List.of(
            Map.of("id", "second", "type", "text", "order", 2, "text", "Second " + text),
            Map.of("id", "first", "type", "text", "order", 1, "text", text)));
    }
    private void save(String generalText, String aText) {
        settings.saveSettings(Map.of("bot_settings", Map.of("active_template_id", "q-legacy", "global_secret", "GLOBAL SECRET",
            "question_templates", List.of(template("q-legacy", "GLOBAL SECRET"), template("q-a", aText),
                template("q-b", "Private B"), template("q-general", generalText), template("q-other", "Another general")))));
    }

    @Test void optInAddsTypedNullBusinessChoiceAndOnlyTheSelectedFrozenTemplate() throws Exception {
        var v1 = businesses.choices(10).orElseThrow();
        assertThat(shared.findBinding(10)).isEmpty();
        assertThat(shared.choices(10).orElseThrow().choices()).hasSize(2);
        assertThat(shared.select(10, "GENERAL", null, shared.choices(10).orElseThrow().catalogSha256())).isEmpty();
        var binding = shared.bind(10, "q-general").orElseThrow();
        var catalog = shared.choices(10).orElseThrow();
        assertThat(catalog.version()).isEqualTo(2);
        assertThat(catalog.catalogSha256()).isNotEqualTo(v1.catalogSha256());
        assertThat(catalog.choices()).extracting(SharedGeneralQuestionnaireService.Choice::scopeKind)
            .containsExactly("BUSINESS", "BUSINESS", "GENERAL");
        assertThat(catalog.choices().get(2).businessId()).isNull();
        assertThat(mapper.writeValueAsString(catalog)).doesNotContain("General first", "Private A", "GLOBAL SECRET");
        var selected = shared.select(10, "GENERAL", null, catalog.catalogSha256()).orElseThrow();
        assertThat(selected.scopeSelectionSource()).isEqualTo("USER_SELECTION");
        assertThat(selected.businessId()).isNull();
        assertThat(selected.templateSha256()).isEqualTo(binding.templateSha256());
        assertThat(selected.botSettings().keySet()).containsExactlyInAnyOrder("active_template_id", "question_templates");
        assertThat(selected.locationTree()).isEmpty(); assertThat(selected.presetDefinitions()).isEmpty();
        String payload = mapper.writeValueAsString(selected);
        assertThat(payload).contains("General first").doesNotContain("Private A", "Private B", "GLOBAL SECRET", "global_secret");
        @SuppressWarnings("unchecked") var template = (Map<String, Object>) ((List<?>) selected.botSettings().get("question_templates")).get(0);
        @SuppressWarnings("unchecked") var flow = (List<Map<String, Object>>) template.get("question_flow");
        assertThat(flow).extracting(q -> q.get("id")).containsExactly("second", "first");
        assertThat(flow).extracting(q -> q.get("order")).containsExactly(2, 1);
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(template))))
            .isEqualTo(binding.templateSha256());
        template.put("name", "local mutation");
        assertThat(shared.select(10, "GENERAL", null, catalog.catalogSha256()).orElseThrow().botSettings().toString())
            .doesNotContain("local mutation");
        var business = shared.select(10, "BUSINESS", a, catalog.catalogSha256()).orElseThrow();
        assertThat(mapper.writeValueAsString(business)).contains("Private A").doesNotContain("General first", "Private B", "GLOBAL SECRET");
        assertThat(businesses.choices(10).orElseThrow()).isEqualTo(v1);
        assertThat(businesses.select(10, a, v1.catalogSha256())).isPresent();
        assertThat(businesses.select(10, a, catalog.catalogSha256())).isEmpty();
        assertThat(shared.select(10, "BUSINESS", a, v1.catalogSha256())).isEmpty();
        assertThat(generalOnly.resolve(10)).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings={"general-edit", "general-missing", "general-inactive", "general-rebind",
        "business-edit", "business-rename", "business-remove", "channel-inactive", "business-archived", "channel-template-conflict",
        "business-template-conflict", "general-only-conflict", "duplicate-question", "preset", "duplicate-template"})
    void staleOrConflictingFullCatalogNeverSelectsAnOldGeneralOrBusinessChoice(String mutation) {
        shared.bind(10, "q-general"); var old = shared.choices(10).orElseThrow();
        switch (mutation) {
            case "general-edit" -> save("Changed general", "Private A");
            case "general-missing" -> settings.saveSettings(Map.of("bot_settings", Map.of("question_templates", List.of(template("q-a", "Private A"), template("q-b", "Private B")))));
            case "general-inactive" -> shared.deactivate(10);
            case "general-rebind" -> shared.bind(10, "q-other");
            case "business-edit" -> save("General first", "Changed A");
            case "business-rename" -> jdbc.update("UPDATE businesses SET display_name='Renamed' WHERE id=?", a);
            case "business-remove" -> jdbc.update("UPDATE business_channel_bindings SET is_active=FALSE WHERE business_id=?", b);
            case "channel-inactive" -> jdbc.update("UPDATE channels SET is_active=FALSE WHERE id=10");
            case "business-archived" -> jdbc.update("UPDATE businesses SET status='archived' WHERE id=?", b);
            case "channel-template-conflict" -> jdbc.update("UPDATE channels SET question_template_id='q-general' WHERE id=11");
            case "business-template-conflict" -> jdbc.update("UPDATE business_question_template_bindings SET template_id='q-general' WHERE business_id=?", a);
            case "general-only-conflict" -> jdbc.update("INSERT INTO general_channel_question_template_bindings(channel_id,template_id,template_sha256) VALUES(11,'q-general',?)", "a".repeat(64));
            default -> {
                var invalid = new LinkedHashMap<>(template("q-general", "General first"));
                if (mutation.equals("preset")) invalid.put("question_flow", List.of(Map.of("id", "where", "type", "preset")));
                if (mutation.equals("duplicate-question")) invalid.put("question_flow", List.of(Map.of("id", "same", "type", "text"), Map.of("id", "same", "type", "text")));
                var templates = new ArrayList<>(List.of(template("q-legacy", "GLOBAL SECRET"), template("q-a", "Private A"), template("q-b", "Private B"), invalid));
                if (mutation.equals("duplicate-template")) templates.add(template("q-general", "duplicate"));
                settings.saveSettings(Map.of("bot_settings", Map.of("active_template_id", "q-legacy", "question_templates", templates)));
            }
        }
        assertThat(shared.select(10, "GENERAL", null, old.catalogSha256())).isEmpty();
        assertThat(shared.select(10, "BUSINESS", a, old.catalogSha256())).isEmpty();
        if (!Set.of("general-inactive", "general-rebind", "business-rename", "business-remove").contains(mutation))
            assertThat(shared.choices(10)).isEmpty();
        else assertThat(shared.choices(10).orElseThrow().catalogSha256()).isNotEqualTo(old.catalogSha256());
    }

    @ParameterizedTest @ValueSource(strings={"q-a", "q-b", "q-legacy", "missing", "", " q-general"})
    void bindingNeverInfersOrReusesBusinessAndLegacyTemplates(String templateId) {
        assertThatThrownBy(() -> shared.bind(10, templateId)).isInstanceOfAny(IllegalStateException.class, IllegalArgumentException.class);
        assertThat(shared.findBinding(10)).isEmpty();
    }

    @Test void bothBindingDirectionsReserveEvenInactiveSharedTemplatesAndKeepGeneralOnlyRules() {
        shared.bind(10, "q-general"); shared.deactivate(10);
        assertThatThrownBy(() -> businesses.bind(a, "q-general")).isInstanceOf(IllegalArgumentException.class);
        assertThat(businesses.availableTemplates()).extracting(BusinessQuestionTemplateRegistryService.TemplateSummary::id).doesNotContain("q-general");
        jdbc.update("UPDATE channels SET question_template_id='q-general' WHERE id=11");
        assertThatThrownBy(() -> generalOnly.bind(11, "q-general")).isInstanceOf(IllegalStateException.class);
        jdbc.update("UPDATE channels SET question_template_id='q-other' WHERE id=11");
        generalOnly.bind(11, "q-other"); generalOnly.deactivate(11);
        assertThatThrownBy(() -> shared.bind(10, "q-other")).isInstanceOf(IllegalStateException.class);
        jdbc.update("UPDATE channels SET question_template_id='q-a' WHERE id=10");
        assertThatThrownBy(() -> generalOnly.bind(10, "q-a")).isInstanceOf(IllegalStateException.class);
        assertThat(shared.bind(999, "q-general")).isEmpty();
        assertThatThrownBy(() -> shared.bind(11, "q-general")).isInstanceOf(IllegalStateException.class);
    }

    @Test void missingAndMalformedChoiceIdentityIsNotCoercedIntoGeneral() {
        shared.bind(10, "q-general"); String hash = shared.choices(10).orElseThrow().catalogSha256();
        for (String kind : List.of("", "general", "UNRESOLVED", "OTHER"))
            assertThatThrownBy(() -> shared.select(10, kind, null, hash)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> shared.select(10, "GENERAL", 0L, hash)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> shared.select(10, "GENERAL", a, hash)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> shared.select(10, "BUSINESS", null, hash)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> shared.select(10, "BUSINESS", 0L, hash)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> shared.select(10, "GENERAL", null, "bad")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> shared.choices(0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(shared.select(10, "BUSINESS", 999L, hash)).isEmpty();
        assertThat(shared.choices(11)).isEmpty();
    }

    @Test void removingBusinessesCannotReopenLegacyRuntimeOrAllowGeneralOnlyWithoutExplicitDeactivation() {
        shared.bind(10,"q-general");
        jdbc.update("UPDATE business_channel_bindings SET is_active=FALSE WHERE channel_id=10");
        jdbc.update("UPDATE channels SET question_template_id='q-other' WHERE id=10");
        assertThat(shared.choices(10)).isEmpty();
        assertThat(businesses.choices(10)).isEmpty();
        assertThat(businesses.hasActiveChannelBindings(10)).isTrue();
        var resolver=new BusinessTicketWriteScopeResolver(jdbc);
        assertThat(resolver.requiresExplicitSelection(10)).isTrue();
        var channels=mock(BotRuntimeChannelService.class); var channel=new Channel(); channel.setId(10L);
        when(channels.findChannel(10L)).thenReturn(Optional.of(channel));
        var runtime=new BotRuntimeConfigService(channels,settings,new BotSettingsPayloadNormalizer(),mapper,generalOnly,
            new MultibusinessFeatureFlags(),businesses);
        assertThatThrownBy(()->runtime.findRuntimeConfig(10L)).isInstanceOf(BotRuntimeConfigService.ScopedRuntimeConfigUnavailableException.class);
        assertThatThrownBy(()->generalOnly.bind(10,"q-other")).isInstanceOf(IllegalStateException.class);
        assertThat(shared.deactivate(10)).isTrue();
        assertThat(businesses.hasActiveChannelBindings(10)).isFalse();
        assertThat(resolver.requiresExplicitSelection(10)).isFalse();
        assertThat(runtime.findRuntimeConfig(10L)).isPresent();
        assertThat(generalOnly.bind(10,"q-other")).isPresent();
    }

    @Test void previewHasOneHundredTotalChoiceBudgetAndDoesNotLoadEveryBusinessPayload() {
        var delegate = mock(BusinessQuestionTemplateRegistryService.class);
        var preview = new SharedGeneralQuestionnaireService(jdbc, delegate, settings, new BotSettingsPayloadNormalizer(), mapper);
        var choices = new ArrayList<BusinessQuestionTemplateRegistryService.Choice>();
        for (long id=1; id<=100; id++) choices.add(new BusinessQuestionTemplateRegistryService.Choice(
            "BUSINESS", id, "b-"+id, "Business "+id, "template-"+id, "a".repeat(64)));
        when(delegate.choices(10)).thenReturn(Optional.of(new BusinessQuestionTemplateRegistryService.ChoiceCatalog(1,10,"a".repeat(64),List.copyOf(choices))));
        assertThatThrownBy(() -> preview.bind(10,"q-general")).isInstanceOf(IllegalStateException.class);
        shared.bind(10,"q-general");
        assertThat(preview.choices(10)).isEmpty();
        choices.remove(99);
        when(delegate.choices(10)).thenReturn(Optional.of(new BusinessQuestionTemplateRegistryService.ChoiceCatalog(1,10,"a".repeat(64),List.copyOf(choices))));
        assertThat(preview.choices(10).orElseThrow().choices()).hasSize(100);
        verify(delegate,never()).select(anyLong(),anyLong(),anyString());
    }

    @Test void migrationKeepsLegacyDataAndRejectsInvalidBindingRows() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shared_channel_general_question_template_bindings",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM general_channel_question_template_bindings",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_question_template_bindings",Long.class)).isEqualTo(2);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO shared_channel_general_question_template_bindings(channel_id,template_id,template_sha256) VALUES(10,' ',?)","a".repeat(64)))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO shared_channel_general_question_template_bindings(channel_id,template_id,template_sha256) VALUES(10,'q-general','bad')"))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO shared_channel_general_question_template_bindings(channel_id,template_id,template_sha256) VALUES(999,'q-general',?)","a".repeat(64)))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        shared.bind(10,"q-general");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO shared_channel_general_question_template_bindings(channel_id,template_id,template_sha256) VALUES(11,'q-general',?)","a".repeat(64)))
            .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test void concurrentBusinessBindWaitsForSharedReservationAndCannotTakeItsTemplate() throws Exception {
        var bound = new CountDownLatch(1); var release = new CountDownLatch(1); var attempting = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())).execute(status -> {
                shared.bind(10, "q-general"); bound.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test release timeout"); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
                return true;
            }));
            assertThat(bound.await(10, TimeUnit.SECONDS)).isTrue();
            var second = pool.submit(() -> { attempting.countDown(); return businesses.bind(a, "q-general"); });
            assertThat(attempting.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
            assertThat(businesses.findBinding(a).orElseThrow().templateId()).isEqualTo("q-a");
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test void signedPreviewIsNoStoreAndNeverAcknowledgesAWriterEvenWithAllFlags() throws Exception {
        shared.bind(10, "q-general");
        var props = new InternalBotApiProperties(); props.setToken("test-token"); props.setSignatureSecret("test-secret"); props.setRequireRequestSignature(true);
        @SuppressWarnings("unchecked") ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        var guard = new InternalBotApiRequestGuardService(props, new RuntimeCoordinationProperties(), mapper, provider);
        var flags = new MultibusinessFeatureFlags(); flags.getFoundation().setEnabled(true); flags.setSharedChannelResolution(true);
        flags.setGeneralChannelResolution(true); flags.setTicketWriteRequiresBusiness(true);
        var mvc = MockMvcBuilders.standaloneSetup(new SharedGeneralQuestionnaireRuntimeController(shared, guard, flags)).build();
        String root = "/internal/api/bot/channels/10/questionnaire/v2";
        mvc.perform(get(root + "/choices")).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get(root + "/choices").header("X-Iguana-Bot-Api-Token", "test-token")).andExpect(status().isUnauthorized());
        mvc.perform(signed(root + "/choices")).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2))
            .andExpect(jsonPath("$.choices.length()").value(3)).andExpect(jsonPath("$.choices[2].businessId").isEmpty())
            .andExpect(header().doesNotExist("X-Iguana-Questionnaire-Writer-Version"));
        String selection = root + "/selection?scopeKind=GENERAL&catalogSha256=" + shared.choices(10).orElseThrow().catalogSha256();
        mvc.perform(signed(selection)).andExpect(status().isOk()).andExpect(jsonPath("$.scopeKind").value("GENERAL"))
            .andExpect(jsonPath("$.businessId").isEmpty()).andExpect(jsonPath("$.scopeSelectionSource").value("USER_SELECTION"));
        flags.setTicketWriteRequiresBusiness(false);
        for (String path : List.of(root + "/choices?forStart=true", selection + "&forStart=true"))
            mvc.perform(signed(path)).andExpect(status().isNotFound()).andExpect(header().doesNotExist("X-Iguana-Questionnaire-Writer-Version"));
        flags.setTicketWriteRequiresBusiness(true);
        for (String path : List.of(root + "/choices?forStart=true", selection + "&forStart=true"))
            mvc.perform(signed(path)).andExpect(status().isOk()).andExpect(header().string("X-Iguana-Questionnaire-Writer-Version","2"));
        mvc.perform(signed(selection).queryParam("businessId", Long.toString(a))).andExpect(status().isUnauthorized());
        mvc.perform(signed(selection + "&businessId=" + a)).andExpect(status().isBadRequest());
        shared.deactivate(10);
        mvc.perform(signed(selection)).andExpect(status().isConflict()).andExpect(header().string("Cache-Control", "no-store"));
        flags.setGeneralChannelResolution(false);
        mvc.perform(signed(root + "/choices")).andExpect(status().isNotFound());
        mvc.perform(signed(root + "/choices?forStart=true")).andExpect(status().isOk())
            .andExpect(jsonPath("$.choices.length()").value(2)).andExpect(header().string("X-Iguana-Questionnaire-Writer-Version","2"));
        String businessOnly=root+"/selection?scopeKind=BUSINESS&businessId="+a+"&catalogSha256="
            +shared.choices(10).orElseThrow().catalogSha256()+"&forStart=true";
        mvc.perform(signed(businessOnly)).andExpect(status().isOk()).andExpect(jsonPath("$.businessId").value(a))
            .andExpect(header().string("X-Iguana-Questionnaire-Writer-Version","2"));
        flags.setGeneralChannelResolution(true); flags.setSharedChannelResolution(false);
        mvc.perform(signed(root + "/choices")).andExpect(status().isNotFound());
        flags.setSharedChannelResolution(true); flags.getFoundation().setEnabled(false);
        mvc.perform(signed(root + "/choices")).andExpect(status().isNotFound());
    }

    private MockHttpServletRequestBuilder signed(String path) throws Exception {
        String time = Instant.now().toString();
        String bodyHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new byte[0]));
        Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal(("GET\n" + path + "\n" + time + "\n" + bodyHash).getBytes(StandardCharsets.UTF_8)));
        return get(path).header("X-Iguana-Bot-Api-Token", "test-token").header("X-Iguana-Request-Timestamp", time).header("X-Iguana-Request-Signature", signature);
    }

    private BusinessQuestionnaireResourcesService r150Resources() {return transactional(new BusinessQuestionnaireResourcesService(jdbc,mapper));}
    private void r150Location(long owner,String name) {
        jdbc.update("INSERT INTO business_locations(business_id,location_name,normalized_location_name,location_type,city) VALUES(?, ?,lower(?),'Store','City')",owner,name,name);
    }
    @Test void v3ContainsOnlyCanonicalChosenResourcesAndGeneralRemainsEmpty() throws Exception {
        r150Location(a,"Only A");r150Location(b,"Secret B");
        var resources=r150Resources();var before=resources.presets(a).orElseThrow();
        resources.replacePresets(a,before.sha256(),Map.of("department",Map.of("name",List.of("Kitchen","Delivery"))));
        shared.bind(10,"q-general");
        var v2=shared.choices(10).orElseThrow();var catalog=shared.choicesWithResources(10).orElseThrow();
        assertThat(catalog.version()).isEqualTo(3);assertThat(catalog.catalogSha256()).isNotEqualTo(v2.catalogSha256());
        assertThat(mapper.writeValueAsString(catalog)).doesNotContain("Only A","Secret B","Kitchen");
        var selected=shared.selectWithResources(10,"BUSINESS",a,catalog.catalogSha256()).orElseThrow();
        assertThat(selected.locationTree()).containsEntry("businessId",a);
        assertThat(selected.presetDefinitions()).containsEntry("businessId",a);
        assertThat(mapper.writeValueAsString(selected)).contains("Only A","Kitchen").doesNotContain("Secret B","GLOBAL SECRET");
        assertThat(catalog.choices().get(0).resourcesSha256()).isEqualTo(BusinessQuestionnaireResourcesService.resourceSha256(selected.locationTree(),selected.presetDefinitions()));
        var general=shared.selectWithResources(10,"GENERAL",null,catalog.catalogSha256()).orElseThrow();
        assertThat(general.locationTree()).isEmpty();assertThat(general.presetDefinitions()).isEmpty();
        assertThat(shared.select(10,"BUSINESS",a,catalog.catalogSha256())).isEmpty();
        assertThat(shared.selectWithResources(10,"BUSINESS",a,v2.catalogSha256())).isEmpty();
        r150Location(b,"New B");
        assertThat(shared.choices(10).orElseThrow()).isEqualTo(v2);
        assertThat(shared.choicesWithResources(10).orElseThrow().catalogSha256()).isNotEqualTo(catalog.catalogSha256());
        assertThat(shared.selectWithResources(10,"BUSINESS",a,catalog.catalogSha256())).isEmpty();
        assertThat(shared.selectWithResources(10,"GENERAL",null,catalog.catalogSha256())).isEmpty();
    }
    @Test void businessPresetsUseCompareAndSetPreserveOtherSettingsAndStableKeyOrder() {
        jdbc.update("UPDATE businesses SET settings='{"+"\"other\":\"keep\"}'::jsonb WHERE id=?",a);
        var resources=r150Resources();var initial=resources.presets(a).orElseThrow();
        var groups=new LinkedHashMap<String,Object>();groups.put("z",Map.of("v",List.of("Last")));groups.put("a",Map.of("v",List.of("First")));
        var saved=resources.replacePresets(a,initial.sha256(),groups).orElseThrow();
        assertThat(jdbc.queryForObject("SELECT settings->>'other' FROM businesses WHERE id=?",String.class,a)).isEqualTo("keep");
        assertThat(resources.presets(b).orElseThrow().groups()).isEmpty();
        assertThatThrownBy(()->resources.replacePresets(a,initial.sha256(),Map.of())).isInstanceOf(IllegalStateException.class);
        var reordered=new LinkedHashMap<String,Object>();reordered.put("a",groups.get("a"));reordered.put("z",groups.get("z"));
        assertThat(resources.replacePresets(a,saved.sha256(),reordered).orElseThrow().sha256()).isEqualTo(saved.sha256());
        jdbc.update("UPDATE businesses SET status='archived' WHERE id=?",a);
        assertThat(resources.snapshot(a)).isEmpty();assertThat(resources.replacePresets(a,saved.sha256(),Map.of())).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"locations","blank","duplicate","object","oversize","control","key"})
    void invalidBusinessPresetsNeverChangeTheStoredConfiguration(String kind) {
        var resources=r150Resources();var initial=resources.presets(a).orElseThrow();
        Map<String,Object> groups=switch(kind) {
            case "locations"->Map.of("locations",Map.of("city",List.of("Fake")));
            case "blank"->Map.of("g",Map.of("f",List.of(" ")));
            case "duplicate"->Map.of("g",Map.of("f",List.of("A","A")));
            case "object"->Map.of("g",Map.of("f",List.of(Map.of("secret","value"))));
            case "oversize"->Map.of("g",Map.of("f",java.util.stream.IntStream.range(0,101).mapToObj(i->"V"+i).toList()));
            case "control"->Map.of("g",Map.of("f",List.of("bad\nvalue")));
            default->Map.of("../global",Map.of("f",List.of("A")));
        };
        assertThatThrownBy(()->resources.replacePresets(a,initial.sha256(),groups)).isInstanceOf(IllegalArgumentException.class);
        assertThat(resources.presets(a).orElseThrow()).isEqualTo(initial);
    }
    @Test void onlyV3CanRunTemplatesWithExplicitBusinessPresetReferences() {
        r150Location(a,"A location");var resources=r150Resources();
        resources.replacePresets(a,resources.presets(a).orElseThrow().sha256(),Map.of("department",Map.of("name",List.of("Kitchen"))));
        var scoped=Map.<String,Object>of("id","q-a","name","A","question_flow",List.of(
            Map.of("id","where","type","preset","preset",Map.of("group","locations","field","city")),
            Map.of("id","team","type","preset","preset",Map.of("group","department","field","name"))));
        settings.saveSettings(Map.of("bot_settings",Map.of("active_template_id","q-legacy","question_templates",List.of(
            template("q-legacy","GLOBAL SECRET"),scoped,template("q-b","Private B"),template("q-general","General")))));
        businesses.bind(a,"q-a");shared.bind(10,"q-general");
        assertThat(businesses.availableTemplates()).extracting(BusinessQuestionTemplateRegistryService.TemplateSummary::id).contains("q-a");
        assertThat(businesses.choices(10)).isEmpty();assertThat(shared.choices(10)).isEmpty();
        var catalog=shared.choicesWithResources(10).orElseThrow();
        assertThat(shared.selectWithResources(10,"BUSINESS",a,catalog.catalogSha256())).isPresent();
        jdbc.update("UPDATE business_locations SET status='archived' WHERE business_id=?",a);
        assertThat(shared.choicesWithResources(10)).isEmpty();
    }
    @Test void v3ResourcesHandshakeSignsThePathAndAcknowledgesOnlyTheMatchingWriter() throws Exception {
        shared.bind(10, "q-general");
        var props = new InternalBotApiProperties(); props.setToken("test-token"); props.setSignatureSecret("test-secret"); props.setRequireRequestSignature(true);
        @SuppressWarnings("unchecked") ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        var guard = new InternalBotApiRequestGuardService(props, new RuntimeCoordinationProperties(), mapper, provider);
        var flags = new MultibusinessFeatureFlags(); flags.getFoundation().setEnabled(true); flags.setSharedChannelResolution(true);
        flags.setGeneralChannelResolution(true); flags.setTicketWriteRequiresBusiness(true);
        var mvc = MockMvcBuilders.standaloneSetup(new SharedResourceQuestionnaireRuntimeController(shared, guard, flags)).build();
        String root = "/internal/api/bot/channels/10/questionnaire/v3";
        mvc.perform(get(root + "/choices")).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get(root + "/choices").header("X-Iguana-Bot-Api-Token", "test-token")).andExpect(status().isUnauthorized());
        mvc.perform(signed(root + "/choices")).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(3))
            .andExpect(jsonPath("$.choices.length()").value(3)).andExpect(jsonPath("$.choices[2].businessId").isEmpty())
            .andExpect(header().doesNotExist("X-Iguana-Questionnaire-Writer-Version"));
        String selection = root + "/selection?scopeKind=GENERAL&catalogSha256=" + shared.choicesWithResources(10).orElseThrow().catalogSha256();
        mvc.perform(signed(selection)).andExpect(status().isOk()).andExpect(jsonPath("$.scopeKind").value("GENERAL"))
            .andExpect(jsonPath("$.businessId").isEmpty()).andExpect(jsonPath("$.scopeSelectionSource").value("USER_SELECTION"));
        flags.setTicketWriteRequiresBusiness(false);
        for (String path : List.of(root + "/choices?forStart=true", selection + "&forStart=true"))
            mvc.perform(signed(path)).andExpect(status().isNotFound()).andExpect(header().doesNotExist("X-Iguana-Questionnaire-Writer-Version"));
        flags.setTicketWriteRequiresBusiness(true);
        for (String path : List.of(root + "/choices?forStart=true", selection + "&forStart=true"))
            mvc.perform(signed(path)).andExpect(status().isOk()).andExpect(header().string("X-Iguana-Questionnaire-Writer-Version","3"));
        mvc.perform(signed(selection).queryParam("businessId", Long.toString(a))).andExpect(status().isUnauthorized());
        mvc.perform(signed(selection + "&businessId=" + a)).andExpect(status().isBadRequest());
        shared.deactivate(10);
        mvc.perform(signed(selection)).andExpect(status().isConflict()).andExpect(header().string("Cache-Control", "no-store"));
        flags.setGeneralChannelResolution(false);
        mvc.perform(signed(root + "/choices")).andExpect(status().isNotFound());
        mvc.perform(signed(root + "/choices?forStart=true")).andExpect(status().isOk())
            .andExpect(jsonPath("$.choices.length()").value(2)).andExpect(header().string("X-Iguana-Questionnaire-Writer-Version","3"));
        String businessOnly=root+"/selection?scopeKind=BUSINESS&businessId="+a+"&catalogSha256="
            +shared.choicesWithResources(10).orElseThrow().catalogSha256()+"&forStart=true";
        mvc.perform(signed(businessOnly)).andExpect(status().isOk()).andExpect(jsonPath("$.businessId").value(a))
            .andExpect(header().string("X-Iguana-Questionnaire-Writer-Version","3"));
        flags.setGeneralChannelResolution(true); flags.setSharedChannelResolution(false);
        mvc.perform(signed(root + "/choices")).andExpect(status().isNotFound());
        flags.setSharedChannelResolution(true); flags.getFoundation().setEnabled(false);
        mvc.perform(signed(root + "/choices")).andExpect(status().isNotFound());
    }


    @Test void resourceDigestHasAStableCrossModuleVector() {
        var tree=Map.<String,Object>of("businessId",7L,"locations",List.of(Map.of("id",91L,"name","Only A","locationType","Store","city","North")));
        var groups=Map.<String,Object>of("businessId",7L,"groups",Map.of("department",Map.of("name",List.of("Kitchen"))));
        assertThat(BusinessQuestionnaireResourcesService.resourceSha256(tree,groups)).isEqualTo("0d234a0b5df12624860061454ecf3f3b82734a3387ab864a7339653210745735");
    }

    @Test void concurrentPresetUpdatesWithOneVersionCannotOverwriteEachOther() throws Exception {
        var resource=r150Resources();var initial=resource.presets(a).orElseThrow();
        var begin=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            java.util.function.Function<String,Boolean> update=value->{
                try {if(!begin.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Start timeout");}
                catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IllegalStateException(ex);}
                try {resource.replacePresets(a,initial.sha256(),Map.of("g",Map.of("f",List.of(value))));return true;}
                catch(IllegalStateException conflict){return false;}
            };
            var first=pool.submit(()->update.apply("First"));var second=pool.submit(()->update.apply("Second"));begin.countDown();
            assertThat(List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(resource.presets(a).orElseThrow().sha256()).isNotEqualTo(initial.sha256());
        }finally{begin.countDown();pool.shutdownNow();}
    }

}
