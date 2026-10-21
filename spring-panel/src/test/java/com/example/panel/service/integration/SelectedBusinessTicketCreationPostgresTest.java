package com.example.panel.service.integration;

import static org.assertj.core.api.Assertions.*;
import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.BotProcessService;
import com.example.panel.service.BusinessQuestionTemplateRegistryService;
import com.example.panel.service.SharedConfigService;
import com.example.panel.service.SharedGeneralQuestionnaireService;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.*;

/** Real Spring transaction/repositories/inbox + the complete Flyway chain, never the working DB. */
@SpringBootTest
class SelectedBusinessTicketCreationPostgresTest {
    @MockBean BotProcessService botProcessService;
    @MockBean(name="taskScheduler") TaskScheduler scheduler;
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) throws Exception {
        var source=(DriverManagerDataSource)PostgresqlJdbcTestSupport
            .freshJdbcTemplateDefaultStringType("selected_business_creation").getDataSource();
        var config=Files.createTempDirectory("selected-business-config-");
        var storage=Files.createTempDirectory("selected-business-storage-");
        properties.add("app.datasource.mode",()->"postgresql");
        properties.add("spring.datasource.url",source::getUrl);
        properties.add("spring.datasource.username",source::getUsername);
        properties.add("spring.datasource.password",source::getPassword);
        properties.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        properties.add("shared-config.dir",config::toString);
        for(String key:List.of("attachments","knowledge-base","passport-photos","avatars"))
            properties.add("app.storage."+key,()->storage.resolve(key).toString());
        properties.add("app.storage.object.required-for-postgresql",()->"false");
        properties.add("app.coordination.required-for-postgresql",()->"false");
        properties.add("app.bots.internal-api.token",()->"r144-test-internal-token");
        properties.add("app.security.remember-me-key",()->"r144-test-remember-key");
        properties.add("app.security.bootstrap-admin.username",()->"r144_test_admin");
        properties.add("app.security.bootstrap-admin.password",()->"r144-test-admin-password");
        properties.add("app.bots.auto-start-enabled",()->"false");
        properties.add("monitoring.credentials.master-key",()->"r144-test-monitoring-key");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired SharedConfigService settings;
    @Autowired MultibusinessFeatureFlags flags;
    @Autowired BusinessQuestionTemplateRegistryService registry;
    @Autowired SharedGeneralQuestionnaireService sharedQuestionnaires;
    @Autowired ConversationTicketCreationIngestionService ingestion;
    private long channel,a,b;
    private ConversationTicketCreatedEvent event;

    @BeforeEach void setup() {
        flags.getFoundation().setEnabled(true);
        flags.setTicketWriteRequiresBusiness(true);
        flags.setSharedChannelResolution(true);
        a=jdbc.queryForObject("SELECT id FROM businesses WHERE code='sushi'",Long.class);
        b=jdbc.queryForObject("SELECT id FROM businesses WHERE code='bliny'",Long.class);
        jdbc.update("UPDATE businesses SET status='active' WHERE id IN (?,?)",a,b);
        channel=jdbc.queryForObject("INSERT INTO channels(token,channel_name,is_active,platform) VALUES(?,'R144 test',TRUE,'telegram') RETURNING id",
            Long.class,UUID.randomUUID().toString());
        jdbc.update("INSERT INTO business_channel_bindings(channel_id,business_id,resolution_mode) VALUES(?,?,'EXPLICIT_SELECTION'),(?,?,'EXPLICIT_SELECTION')",
            channel,a,channel,b);
        saveTemplates("Private A");
        registry.bind(a,"r144-a");registry.bind(b,"r144-b");
        var catalog=registry.choices(channel).orElseThrow();
        var selected=registry.select(channel,a,catalog.catalogSha256()).orElseThrow();
        event=new ConversationTicketCreatedEvent(UUID.randomUUID().toString(),"ticket.created.initial_contact","telegram",
            channel,"r144-"+UUID.randomUUID(),901L,"tg-901","client","Client","Not an owner authority",
            null,null,null,"Help",OffsetDateTime.parse("2026-10-20T10:00:00Z"),
            List.of(new ConversationTicketCreatedEvent.TicketAttributePayload("free","free","Free question","text",null,null,"Answer",true)),
            List.of(new ConversationTicketCreatedEvent.ConversationHistoryEntryPayload(901L,"Answer","text",null,null,null,null)),
            a,"BUSINESS","USER_SELECTION",selected.questionTemplateId(),selected.templateSha256(),selected.catalogSha256());
    }
    @AfterEach void flagsOff() {
        flags.getFoundation().setEnabled(false);flags.setTicketWriteRequiresBusiness(false);flags.setSharedChannelResolution(false);
        flags.setGeneralChannelResolution(false);
    }
    private void saveTemplates(String firstText) {
        settings.saveSettings(Map.of("bot_settings",Map.of("active_template_id","r144-legacy","question_templates",
            List.of(template("r144-legacy","Global"),template("r144-a",firstText),template("r144-b","Private B")))));
    }
    private Map<String,Object> template(String id,String text) {
        return Map.of("id",id,"name",id,"question_flow",List.of(Map.of("id","free","type","text","order",1,"text",text)));
    }
    @ParameterizedTest @ValueSource(strings={"telegram","vk","max"})
    void createsChosenOwnerWithFrozenQuestionnaireAndCompositeChildren(String platform) {
        jdbc.update("UPDATE channels SET platform=? WHERE id=?",platform,channel);
        var selected=change(event,event.eventId(),event.channelId(),event.userId(),a,"BUSINESS","USER_SELECTION",
            event.questionTemplateId(),event.templateSha256(),event.catalogSha256(),platform);
        ingest(selected);
        var row=jdbc.queryForMap("SELECT * FROM tickets WHERE ticket_id=?",event.ticketId());
        assertThat(row.get("business_id")).isEqualTo(a);
        assertThat(row.get("is_general")).isEqualTo(false);
        assertThat(row.get("general_selection_source")).isNull();
        assertThat(row.get("business_selection_source")).isEqualTo("USER_SELECTION");
        assertThat(row.get("question_template_id")).isEqualTo(event.questionTemplateId());
        assertThat(row.get("question_template_sha256")).isEqualTo(event.templateSha256());
        assertThat(row.get("questionnaire_catalog_sha256")).isEqualTo(event.catalogSha256());
        assertThat(jdbc.queryForObject("SELECT ticket_owner_user_id FROM chat_history WHERE ticket_id=?",Long.class,event.ticketId())).isEqualTo(901L);
        for(String table:List.of("messages","tickets","ticket_attributes","chat_history","ticket_spans","ticket_active"))
            assertThat(count(table)).as(table).isEqualTo(1);
        assertThat(inboxStatus(event.eventId())).isEqualTo("processed");
    }
    @ParameterizedTest @ValueSource(strings={"template-edit","template-rebind","binding-off","business-archived","channel-off","mixed-mode","name-change","other-template-edit"})
    void currentCatalogIsRequiredBeforeAnyRootOrChildWrite(String mutation) {
        switch(mutation) {
            case "template-edit"->saveTemplates("Changed A");
            case "template-rebind"->{saveTemplates("Changed A");registry.bind(a,"r144-a");}
            case "binding-off"->jdbc.update("UPDATE business_channel_bindings SET is_active=FALSE WHERE channel_id=? AND business_id=?",channel,a);
            case "business-archived"->jdbc.update("UPDATE businesses SET status='archived' WHERE id=?",a);
            case "channel-off"->jdbc.update("UPDATE channels SET is_active=FALSE WHERE id=?",channel);
            case "mixed-mode"->jdbc.update("UPDATE business_channel_bindings SET resolution_mode='SINGLE_BUSINESS' WHERE channel_id=? AND business_id=?",channel,b);
            case "name-change"->jdbc.update("UPDATE businesses SET display_name=display_name||' changed' WHERE id=?",b);
            case "other-template-edit"->settings.saveSettings(Map.of("bot_settings",Map.of("active_template_id","r144-legacy","question_templates",
                List.of(template("r144-legacy","Global"),template("r144-a","Private A"),template("r144-b","Changed B")))));
        }
        assertThatThrownBy(()->ingest(event)).isInstanceOf(IllegalStateException.class);
        emptyWrites();assertThat(inboxStatus(event.eventId())).isEqualTo("failed");
    }
    @ParameterizedTest @ValueSource(strings={"foundation","strict","shared","legacy-explicit"})
    void noDisabledFlagOrLegacyEnvelopeCanBypassExplicitSelection(String mutation) {
        var input=event;
        switch(mutation) {
            case "foundation"->flags.getFoundation().setEnabled(false);
            case "strict"->flags.setTicketWriteRequiresBusiness(false);
            case "shared"->flags.setSharedChannelResolution(false);
            case "legacy-explicit"->{
                flags.setTicketWriteRequiresBusiness(false);
                input=change(event,event.eventId(),channel,event.userId(),null,null,null,null,null,null,event.platform());
            }
        }
        var rejected=input;
        assertThatThrownBy(()->ingest(rejected)).isInstanceOf(IllegalStateException.class);emptyWrites();
    }
    @ParameterizedTest @ValueSource(strings={"owner","source","kind","id","hash","catalog","missing-catalog","platform"})
    void malformedOrSubstitutedSelectionNeverCreatesARoot(String mutation) {
        var rejected=mutate(event,mutation,false);
        assertThatThrownBy(()->ingest(rejected)).isInstanceOf(IllegalStateException.class);emptyWrites();
    }
    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"GENERAL,telegram","GENERAL,vk","GENERAL,max","BUSINESS,telegram","BUSINESS,vk","BUSINESS,max"})
    void v2SelectionCreatesCanonicalBusinessOrGeneralRoot(String kind,String platform) {
        jdbc.update("UPDATE channels SET platform=? WHERE id=?",platform,channel);
        flags.setGeneralChannelResolution(true);
        String generalTemplateId="r148-general-"+channel;
        settings.saveSettings(Map.of("bot_settings",Map.of("active_template_id","r144-legacy","question_templates",
            List.of(template("r144-legacy","Global"),template("r144-a","Private A"),template("r144-b","Private B"),
                template(generalTemplateId,"General only")))));
        sharedQuestionnaires.bind(channel,generalTemplateId);
        var catalog=sharedQuestionnaires.choices(channel).orElseThrow();
        var selected=sharedQuestionnaires.select(channel,kind,"BUSINESS".equals(kind)?a:null,catalog.catalogSha256()).orElseThrow();
        event=change(event,event.eventId(),channel,event.userId(),selected.businessId(),selected.scopeKind(),
            selected.scopeSelectionSource(),selected.questionTemplateId(),selected.templateSha256(),selected.catalogSha256(),platform);
        ingest(event);ingest(event);
        var row=jdbc.queryForMap("SELECT * FROM tickets WHERE ticket_id=?",event.ticketId());
        assertThat(row.get("business_id")).isEqualTo(selected.businessId());
        assertThat(row.get("is_general")).isEqualTo("GENERAL".equals(kind));
        assertThat(row.get("general_selection_source")).isEqualTo("GENERAL".equals(kind)?"USER_SELECTION":null);
        assertThat(row.get("business_selection_source")).isEqualTo("BUSINESS".equals(kind)?"USER_SELECTION":null);
        assertThat(row.get("question_template_id")).isEqualTo(selected.questionTemplateId());
        assertThat(row.get("questionnaire_catalog_sha256")).isEqualTo(selected.catalogSha256());
        for(String table:List.of("messages","tickets","ticket_attributes","chat_history","ticket_spans","ticket_active")) assertThat(count(table)).as(table).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT business FROM messages WHERE ticket_id=?",String.class,event.ticketId()))
            .isEqualTo("GENERAL".equals(kind)?null:event.business());
        assertThat(jdbc.queryForObject("SELECT ticket_owner_user_id FROM chat_history WHERE ticket_id=?",Long.class,event.ticketId())).isEqualTo(event.userId());
        assertThat(inboxStatus(event.eventId())).isEqualTo("processed");
    }

    @Test void activeSharedGeneralCannotBecomeLegacyAfterBusinessBindingsAndStrictFlagAreRemoved() {
        String generalTemplateId="r148-general-"+channel;
        settings.saveSettings(Map.of("bot_settings",Map.of("active_template_id","r144-legacy","question_templates",
            List.of(template("r144-legacy","Global"),template("r144-a","Private A"),template("r144-b","Private B"),
                template(generalTemplateId,"General only")))));
        sharedQuestionnaires.bind(channel,generalTemplateId);
        jdbc.update("UPDATE business_channel_bindings SET is_active=FALSE WHERE channel_id=?",channel);
        flags.setTicketWriteRequiresBusiness(false);flags.setSharedChannelResolution(false);
        var rejected=change(event,event.eventId(),channel,event.userId(),null,null,null,null,null,null,event.platform());
        assertThatThrownBy(()->ingest(rejected)).isInstanceOf(IllegalStateException.class);
        emptyWrites();assertThat(inboxStatus(event.eventId())).isEqualTo("failed");
    }

    @Test void exactReplayUsesFrozenOwnerAndVersionNotTodaysBindings() {
        ingest(event);
        jdbc.update("UPDATE business_channel_bindings SET is_active=FALSE WHERE channel_id=?",channel);
        jdbc.update("UPDATE businesses SET status='archived' WHERE id=?",a);
        jdbc.update("UPDATE channels SET is_active=FALSE WHERE id=?",channel);
        saveTemplates("Changed A");
        ingest(event); // same event ID is checked by the real inbox
        ingest(change(event,UUID.randomUUID().toString(),channel,901L,a,"BUSINESS","USER_SELECTION",
            event.questionTemplateId(),event.templateSha256(),event.catalogSha256(),event.platform())); // new event ID, same tuple
        assertThat(count("tickets")).isEqualTo(1);assertThat(count("chat_history")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT business_id FROM tickets WHERE ticket_id=?",Long.class,event.ticketId())).isEqualTo(a);
    }
    @ParameterizedTest @ValueSource(strings={"owner","source","kind","id","hash","catalog","missing-catalog","channel","user"})
    void sameInboxIdAndNewEventIdCannotChangeFrozenCreationIdentity(String mutation) {
        ingest(event);
        for(boolean newId:List.of(false,true)) {
            var rejected=mutate(event,mutation,newId);
            assertThatThrownBy(()->ingest(rejected)).isInstanceOf(IllegalStateException.class);
        }
        assertThat(count("tickets")).isEqualTo(1);assertThat(count("messages")).isEqualTo(1);
        assertThat(count("chat_history")).isEqualTo(1);assertThat(inboxStatus(event.eventId())).isEqualTo("processed");
    }
    @Test void flagRollbackCannotTurnAnExistingSelectedRootIntoAnUnscopedReplay() {
        ingest(event);flags.setTicketWriteRequiresBusiness(false);flags.setSharedChannelResolution(false);
        var rejected=change(event,UUID.randomUUID().toString(),channel,901L,a,null,null,null,null,null,event.platform());
        assertThatThrownBy(()->ingest(rejected)).isInstanceOf(IllegalStateException.class);
        assertThat(count("tickets")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT questionnaire_catalog_sha256 FROM tickets WHERE ticket_id=?",
            String.class,event.ticketId())).isEqualTo(event.catalogSha256());
    }
    @Test void downstreamFailureRollsBackAllRootsAndChildrenAndTheSameEventCanRetry() {
        jdbc.execute("CREATE FUNCTION reject_r144_child() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic child failure'; END; $$");
        jdbc.execute("CREATE TRIGGER reject_r144_child BEFORE INSERT ON ticket_attributes FOR EACH ROW EXECUTE FUNCTION reject_r144_child()");
        try {assertThatThrownBy(()->ingest(event)).isInstanceOf(RuntimeException.class);emptyWrites();}
        finally {jdbc.execute("DROP TRIGGER reject_r144_child ON ticket_attributes");jdbc.execute("DROP FUNCTION reject_r144_child()");}
        assertThat(inboxStatus(event.eventId())).isEqualTo("failed");
        ingest(event);assertThat(count("tickets")).isEqualTo(1);assertThat(inboxStatus(event.eventId())).isEqualTo("processed");
    }
    @Test void databaseRejectsPartialOrMutableBusinessSelectionAndDoesNotInferOldRows() {
        ingest(event);
        for(String column:List.of("business_selection_source","questionnaire_catalog_sha256"))
            assertThatThrownBy(()->jdbc.update("UPDATE tickets SET "+column+"=NULL WHERE ticket_id=?",event.ticketId())).isInstanceOf(DataAccessException.class);
        // Both NULLs and a different valid hash satisfy CHECK; only the immutable trigger can reject them.
        assertThatThrownBy(()->jdbc.update("UPDATE tickets SET business_selection_source=NULL,questionnaire_catalog_sha256=NULL WHERE ticket_id=?",
            event.ticketId())).isInstanceOf(DataAccessException.class).hasMessageContaining("provenance is immutable");
        assertThatThrownBy(()->jdbc.update("UPDATE tickets SET questionnaire_catalog_sha256=? WHERE ticket_id=?",
            "f".repeat(64),event.ticketId())).isInstanceOf(DataAccessException.class).hasMessageContaining("provenance is immutable");
        assertThatThrownBy(()->jdbc.update("INSERT INTO tickets(user_id,ticket_id,business_selection_source) VALUES(1,?,'USER_SELECTION')",
            "partial-"+UUID.randomUUID())).isInstanceOf(DataAccessException.class);
        String legacy="legacy-"+UUID.randomUUID();
        jdbc.update("INSERT INTO tickets(user_id,ticket_id) VALUES(1,?)",legacy);
        assertThat(jdbc.queryForMap("SELECT business_selection_source,questionnaire_catalog_sha256 FROM tickets WHERE ticket_id=?",legacy))
            .containsEntry("business_selection_source",null).containsEntry("questionnaire_catalog_sha256",null);
    }
    private void sharedGeneralEvent() {
        flags.setGeneralChannelResolution(true);
        String id="r149-general-"+channel;
        settings.saveSettings(Map.of("bot_settings",Map.of("active_template_id","r144-legacy","question_templates",
            List.of(template("r144-legacy","Global"),template("r144-a","Private A"),template("r144-b","Private B"),template(id,"GENERAL free")))));
        sharedQuestionnaires.bind(channel,id);
        var catalog=sharedQuestionnaires.choices(channel).orElseThrow();
        var selected=sharedQuestionnaires.select(channel,"GENERAL",null,catalog.catalogSha256()).orElseThrow();
        event=change(event,event.eventId(),channel,event.userId(),null,"GENERAL","USER_SELECTION",selected.questionTemplateId(),selected.templateSha256(),selected.catalogSha256(),event.platform());
    }
    @ParameterizedTest @ValueSource(strings={"general-flag","shared-flag","strict-flag","foundation","channel","platform","inactive-binding","removed-businesses","name","changed-business-template","changed-general-template","legacy-catalog","missing-catalog","missing-template","foreign-template","hash","fake-zero","fake-owner","general-only-source"})
    void mixedGeneralRequiresCurrentFullCatalogAndCannotDowngradeOrSubstituteOwner(String mutation) {
        var legacy=event;sharedGeneralEvent();
        switch(mutation) {
            case "general-flag"->flags.setGeneralChannelResolution(false);case "shared-flag"->flags.setSharedChannelResolution(false);
            case "strict-flag"->flags.setTicketWriteRequiresBusiness(false);case "foundation"->flags.getFoundation().setEnabled(false);
            case "channel"->jdbc.update("UPDATE channels SET is_active=FALSE WHERE id=?",channel);
            case "platform"->jdbc.update("UPDATE channels SET platform='vk' WHERE id=?",channel);
            case "inactive-binding"->sharedQuestionnaires.deactivate(channel);
            case "removed-businesses"->jdbc.update("UPDATE business_channel_bindings SET is_active=FALSE WHERE channel_id=?",channel);
            case "name"->jdbc.update("UPDATE businesses SET display_name=display_name||' changed' WHERE id=?",a);
            case "changed-business-template","changed-general-template"->settings.saveSettings(Map.of("bot_settings",Map.of(
                "active_template_id","r144-legacy","question_templates",List.of(template("r144-legacy","Global"),
                    template("r144-a",mutation.equals("changed-business-template")?"Changed A":"Private A"),template("r144-b","Private B"),
                    template("r149-general-"+channel,mutation.equals("changed-general-template")?"Changed GENERAL":"GENERAL free")))));
            case "legacy-catalog"->event=change(event,event.eventId(),channel,event.userId(),null,"GENERAL","USER_SELECTION",event.questionTemplateId(),event.templateSha256(),legacy.catalogSha256(),event.platform());
            case "missing-catalog"->event=change(event,event.eventId(),channel,event.userId(),null,"GENERAL","USER_SELECTION",event.questionTemplateId(),event.templateSha256(),null,event.platform());
            case "missing-template"->event=change(event,event.eventId(),channel,event.userId(),null,"GENERAL","USER_SELECTION",null,null,event.catalogSha256(),event.platform());
            case "foreign-template"->event=change(event,event.eventId(),channel,event.userId(),null,"GENERAL","USER_SELECTION",legacy.questionTemplateId(),legacy.templateSha256(),event.catalogSha256(),event.platform());
            case "hash"->event=mutate(event,"hash",false);
            case "fake-zero","fake-owner"->event=change(event,event.eventId(),channel,event.userId(),mutation.equals("fake-zero")?0L:a,"GENERAL","USER_SELECTION",event.questionTemplateId(),event.templateSha256(),event.catalogSha256(),event.platform());
            case "general-only-source"->event=change(event,event.eventId(),channel,event.userId(),null,"GENERAL","GENERAL_CHANNEL",event.questionTemplateId(),event.templateSha256(),event.catalogSha256(),event.platform());
        }
        var rejected=event;assertThatThrownBy(()->ingest(rejected)).isInstanceOf(IllegalStateException.class);emptyWrites();
        jdbc.update("UPDATE businesses SET display_name=replace(display_name,' changed','') WHERE id=?",a);
    }
    @ParameterizedTest @ValueSource(strings={"owner","source","kind","id","hash","catalog","missing-catalog","channel","user"})
    void mixedGeneralReplayAndInboxCannotChangeFrozenIdentity(String mutation) {
        sharedGeneralEvent();ingest(event);
        for(boolean newId:List.of(false,true)) {
            var rejected=mutate(event,mutation,newId);assertThatThrownBy(()->ingest(rejected)).isInstanceOf(IllegalStateException.class);
        }
        assertThat(count("tickets")).isEqualTo(1);assertThat(count("chat_history")).isEqualTo(1);
    }
    @Test void mixedGeneralExactReplayIgnoresTodaysReboundConfigurationAndDatabaseIdentityIsImmutable() {
        sharedGeneralEvent();ingest(event);sharedQuestionnaires.deactivate(channel);saveTemplates("Edited after delivery");
        ingest(event);ingest(change(event,UUID.randomUUID().toString(),channel,event.userId(),null,"GENERAL","USER_SELECTION",event.questionTemplateId(),event.templateSha256(),event.catalogSha256(),event.platform()));
        for(String assignment:List.of("general_selection_source='GENERAL_CHANNEL'","is_general=FALSE,general_selection_source=NULL,business_id="+a,
                "user_id=user_id+1","channel_id=NULL","questionnaire_catalog_sha256=NULL","question_template_id='other'"))
            assertThatThrownBy(()->jdbc.update("UPDATE tickets SET "+assignment+" WHERE ticket_id=?",event.ticketId())).isInstanceOf(DataAccessException.class);
        assertThat(count("tickets")).isEqualTo(1);assertThat(count("chat_history")).isEqualTo(1);
    }
    @Test void mixedGeneralChildFailureRollsBackAllWritesAndFrozenEventCanRetry() {
        sharedGeneralEvent();
        jdbc.execute("CREATE FUNCTION reject_r149_child() RETURNS TRIGGER LANGUAGE plpgsql AS $r149$ BEGIN RAISE EXCEPTION 'synthetic'; END; $r149$");
        jdbc.execute("CREATE TRIGGER reject_r149_child BEFORE INSERT ON ticket_attributes FOR EACH ROW EXECUTE FUNCTION reject_r149_child()");
        try {assertThatThrownBy(()->ingest(event)).isInstanceOf(RuntimeException.class);emptyWrites();}
        finally {jdbc.execute("DROP TRIGGER reject_r149_child ON ticket_attributes");jdbc.execute("DROP FUNCTION reject_r149_child()");}
        ingest(event);assertThat(count("tickets")).isEqualTo(1);
    }
    @Test void activeGeneralReservationRejectsAnOlderBusinessOnlyCatalogBeforeInsert() {
        var old=event;sharedGeneralEvent();event=old;assertThatThrownBy(()->ingest(old)).isInstanceOf(IllegalStateException.class);emptyWrites();
    }

    @Test void v74UpgradePreservesLegacyGeneralAndBusinessRowsWithoutInferringSelection() {
        var upgrade=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("shared_selection_upgrade");
        org.flywaydb.core.Flyway.configure().dataSource(upgrade.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").target("74").load().migrate();
        upgrade.update("INSERT INTO tickets(user_id,ticket_id) VALUES(1,'legacy')");
        upgrade.update("INSERT INTO tickets(user_id,ticket_id,is_general,general_selection_source,question_template_id,question_template_sha256) VALUES(2,'general-only',TRUE,'GENERAL_CHANNEL','old-general',?)","a".repeat(64));
        upgrade.update("INSERT INTO tickets(user_id,ticket_id,business_id,business_selection_source,question_template_id,question_template_sha256,questionnaire_catalog_sha256) VALUES(3,'business',(SELECT id FROM businesses LIMIT 1),'USER_SELECTION','old-business',?,?)","b".repeat(64),"c".repeat(64));
        var original=upgrade.queryForList("SELECT * FROM tickets ORDER BY user_id");
        org.flywaydb.core.Flyway.configure().dataSource(upgrade.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").load().migrate();
        assertThat(upgrade.queryForList("SELECT * FROM tickets ORDER BY user_id")).isEqualTo(original);
    }
    private ConversationTicketCreatedEvent mutate(ConversationTicketCreatedEvent base,String mutation,boolean newId) {
        return change(base,newId?UUID.randomUUID().toString():base.eventId(),
            mutation.equals("channel")?channel+999999:channel,mutation.equals("user")?902L:901L,
            mutation.equals("owner")?Long.valueOf(b):base.businessId(),mutation.equals("kind")?null:base.scopeKind(),
            mutation.equals("source")?null:base.scopeSelectionSource(),mutation.equals("id")?"another":base.questionTemplateId(),
            mutation.equals("hash")?"f".repeat(64):base.templateSha256(),
            mutation.equals("catalog")?"f".repeat(64):mutation.equals("missing-catalog")?null:base.catalogSha256(),
            mutation.equals("platform")?"vk":base.platform());
    }
    private ConversationTicketCreatedEvent change(ConversationTicketCreatedEvent base,String id,Long channelId,Long userId,Long businessId,
            String kind,String source,String template,String hash,String catalog,String platform) {
        return new ConversationTicketCreatedEvent(id,base.eventKind(),platform,channelId,base.ticketId(),userId,base.userIdentity(),
            base.username(),base.clientName(),base.business(),base.locationType(),base.city(),base.locationName(),base.problem(),
            base.occurredAt(),base.attributes(),base.historyEntries(),businessId,kind,source,template,hash,catalog);
    }
    private void ingest(ConversationTicketCreatedEvent input) {ingestion.ingest(input,"integration.ticket."+input.platform());}
    private int count(String table) {return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE ticket_id=?",Integer.class,event.ticketId());}
    private void emptyWrites() {for(String table:List.of("messages","tickets","ticket_attributes","chat_history","ticket_spans","ticket_active"))assertThat(count(table)).as(table).isZero();}
    private String inboxStatus(String id) {return jdbc.queryForObject("SELECT status FROM integration_inbound_event_inbox WHERE event_id=?",String.class,id);}
}
