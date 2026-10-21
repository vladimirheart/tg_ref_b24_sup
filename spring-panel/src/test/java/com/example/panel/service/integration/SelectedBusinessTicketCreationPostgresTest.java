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
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;
    @Autowired SharedConfigService settings;
    @Autowired MultibusinessFeatureFlags flags;
    @Autowired BusinessQuestionTemplateRegistryService registry;
    @Autowired SharedGeneralQuestionnaireService sharedQuestionnaires;
    @Autowired ConversationTicketCreationIngestionService ingestion;
    @Autowired InboundClientMessageIngestionService followupIngestion;
    @Autowired SelectedTicketFollowupMediaService followupMedia;
    @Autowired com.example.panel.storage.AttachmentObjectStorageService mediaStorage;
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
        var upgraded=upgrade.queryForList("SELECT * FROM tickets ORDER BY user_id");
        for(var row:upgraded){assertThat(row).containsEntry("questionnaire_version",null);row.remove("questionnaire_version");}
        assertThat(upgraded).isEqualTo(original);
    }
    private InboundClientMessageEvent r156Batch(long provider,int size) throws Exception {
        var items=new java.util.ArrayList<InboundClientMessageEvent.MediaItem>();
        for(int slot=1;slot<=size;slot++) {
            String key=r154Keys.get(0).replace("/33/1/","/"+provider+"/"+slot+"/");r153Write(key,R153_BYTES);
            items.add(new InboundClientMessageEvent.MediaItem(slot%3==1?"photo":slot%3==2?"document":"audio_message",key,"proof.bin"));
        }
        String id=InboundClientMessageEvent.mediaBatchId(channel,901,event.ticketId(),Long.toString(provider));
        return new InboundClientMessageEvent(id,InboundClientMessageEvent.MEDIA_BATCH_KIND,"vk",channel,event.ticketId(),901L,
            event.userIdentity(),"r156-client","R156 Client","Batch caption","selected_media_batch.v1",items.get(0).attachmentPath(),
            items.get(0).attachmentName(),Long.toString(provider),null,null,event.occurredAt().plusSeconds(10),items);
    }
    private InboundClientMessageEvent r156Change(InboundClientMessageEvent input,java.util.function.Consumer<com.fasterxml.jackson.databind.node.ObjectNode> change) throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode json=mapper.valueToTree(input);change.accept(json);
        return mapper.treeToValue(json,InboundClientMessageEvent.class);
    }
    private void r156Ingest(InboundClientMessageEvent input) {followupIngestion.ingest(input,"integration.ticket.vk");}

    @ParameterizedTest @ValueSource(booleans={false,true})
    void vkFollowupBatchRetainsFrozenOwnerReadProofAndExactReplay(boolean general) throws Exception {
        r154VkMedia(general);ingest(event);var input=r156Batch(88,3);
        jdbc.update("UPDATE business_channel_bindings SET is_active=FALSE WHERE channel_id=?",channel);
        settings.saveSettings(Map.of("bot_settings",Map.of("question_templates",List.of(template("r156-changed","Changed")))));
        assertThat(followupMedia.vkBatchAdmission(channel,901,event.ticketId()).version()).isEqualTo(2);
        assertThat(followupMedia.vkBatchAdmission(channel,901,event.ticketId()).remainingFiles()).isEqualTo(7);
        assertThatThrownBy(()->followupMedia.admission(channel,901,event.ticketId())).isInstanceOf(IllegalStateException.class);
        r156Ingest(input);assertThat(count("chat_history")).isEqualTo(6);assertThat(count("chat_attachment_metadata")).isEqualTo(6);
        var ids=jdbc.queryForList("SELECT id FROM chat_history WHERE ticket_id=? AND tg_message_id=88 ORDER BY id",Long.class,event.ticketId());assertThat(ids).hasSize(3);
        long reader=r153Reader(general);
        for(int slot=0;slot<3;slot++) {
            String key=input.mediaBatch().get(slot).attachmentPath();
            if(general) assertThat(generalRead.attachment(reader,r153PublicId(),ids.get(slot)).orElseThrow().storageKey()).isEqualTo(key);
            else assertThat(businessRead.findAttachmentByPublicId(reader,r153PublicId(),ids.get(slot)).orElseThrow().storageKey()).isEqualTo(key);
            var metadata=jdbc.queryForMap("SELECT size,content_hash FROM chat_attachment_metadata WHERE chat_history_id=?",ids.get(slot));
            assertThat(((Number)metadata.get("size")).longValue()).isEqualTo(R153_BYTES.length);assertThat(metadata.get("content_hash")).isNotNull();
        }
        if(general){generalAccess.revokeRead(reader);assertThat(generalRead.attachment(reader,r153PublicId(),ids.get(0))).isEmpty();}
        else{jdbc.update("UPDATE business_access_grants SET is_active=FALSE WHERE user_id=?",reader);assertThat(businessRead.findAttachmentByPublicId(reader,r153PublicId(),ids.get(0))).isEmpty();}
        jdbc.update("UPDATE tickets SET status='closed' WHERE ticket_id=?",event.ticketId());flags.setTicketWriteRequiresBusiness(false);
        for(var item:input.mediaBatch())r153Write(item.attachmentPath(),new byte[]{1,2});
        r156Ingest(input);assertThat(count("chat_history")).isEqualTo(6);assertThat(inboxStatus(input.eventId())).isEqualTo("processed");
        var changed=r156Change(input,node->node.put("text","Changed replay"));
        assertThatThrownBy(()->r156Ingest(changed)).hasMessageContaining("identity mismatch");assertThat(count("chat_history")).isEqualTo(6);
    }

    @ParameterizedTest @ValueSource(strings={"owner","channel-key","user-key","catalog","provider-key","slot","slot-order","duplicate-slot","legacy","type","name","top-key","provider","event-id","kind","platform","closed","inactive","strict","general-flag","bytes","missing","oversize","quota","provider-reuse"})
    void invalidVkFollowupBatchNeverWritesAPartialMessageOrMetadata(String mutation) throws Exception {
        r154VkMedia(mutation.equals("general-flag"));ingest(event);var input=r156Batch(mutation.equals("provider-reuse")?33:88,mutation.equals("quota")?8:3);
        var json=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.valueToTree(input);
        var item=(com.fasterxml.jackson.databind.node.ObjectNode)json.path("mediaBatch").get(1);String key=item.path("attachmentPath").asText();
        switch(mutation) {
            case "owner"->key=key.replace("/business-"+a+"/","/business-"+b+"/");
            case "channel-key"->key=key.replace("/vk/"+channel+"/","/vk/999999/");
            case "user-key"->key=key.replace("/901/","/902/");
            case "catalog"->key=key.replace(event.catalogSha256(),"f".repeat(64));
            case "provider-key"->key=key.replace("/88/2/","/89/2/");
            case "slot"->key=key.replace("/88/2/","/88/11/");
            case "slot-order"->key=key.replace("/88/2/","/88/3/");
            case "duplicate-slot"->key=json.at("/mediaBatch/0/attachmentPath").asText();
            case "legacy"->key=event.ticketId()+"/proof.bin";
            case "type"->item.put("messageType","html");
            case "name"->item.put("attachmentName","../unsafe");
            case "top-key"->json.put("attachmentPath",key);
            case "provider"->json.put("providerMessageId","0");
            case "event-id"->json.put("eventId",UUID.randomUUID().toString());
            case "kind"->json.put("eventKind","client_message.active_ticket");
            case "platform"->json.put("platform","telegram");
            case "closed"->jdbc.update("UPDATE tickets SET status='closed' WHERE ticket_id=?",event.ticketId());
            case "inactive"->jdbc.update("DELETE FROM ticket_active WHERE ticket_id=?",event.ticketId());
            case "strict"->flags.setTicketWriteRequiresBusiness(false);
            case "general-flag"->flags.setGeneralChannelResolution(false);
        }
        item.put("attachmentPath",key);
        if(!mutation.equals("missing"))r153Write(key,mutation.equals("bytes")?new byte[]{1,2}:mutation.equals("oversize")?new byte[10*1024*1024+1]:R153_BYTES);
        else {int slash=key.lastIndexOf('/');mediaStorage.deleteDialogAttachment(key.substring(0,slash),key.substring(slash+1));}
        var rejected=mapper.treeToValue(json,InboundClientMessageEvent.class);
        var profile=jdbc.queryForMap("SELECT username,client_name,updated_at FROM messages WHERE ticket_id=?",event.ticketId());
        assertThatThrownBy(()->r156Ingest(rejected)).isInstanceOf(RuntimeException.class);
        assertThat(count("chat_history")).isEqualTo(3);assertThat(count("chat_attachment_metadata")).isEqualTo(3);
        assertThat(jdbc.queryForMap("SELECT username,client_name,updated_at FROM messages WHERE ticket_id=?",event.ticketId())).isEqualTo(profile);
        assertThat(inboxStatus(rejected.eventId())).isEqualTo("failed");
    }

    @Test void vkBatchQuotaIncludesDraftAndAllowsOnlyAnExactProcessedReplayAtTen() throws Exception {
        r154VkMedia(false);ingest(event);var input=r156Batch(88,7);r156Ingest(input);
        assertThat(count("chat_history")).isEqualTo(10);r156Ingest(input);assertThat(count("chat_history")).isEqualTo(10);
        assertThatThrownBy(()->followupMedia.vkBatchAdmission(channel,901,event.ticketId())).isInstanceOf(IllegalStateException.class);
        var next=r156Batch(89,1);assertThatThrownBy(()->r156Ingest(next)).isInstanceOf(IllegalStateException.class);assertThat(count("chat_history")).isEqualTo(10);
    }
    @Test void vkBatchSecondMetadataFailureRollsBackAllFilesAndRetriesOnlyTheIdenticalPayload() throws Exception {
        r154VkMedia(false);ingest(event);var input=r156Batch(88,3);
        jdbc.execute("CREATE FUNCTION reject_r156_metadata() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN IF NEW.storage_key LIKE '%/88/2/%' THEN RAISE EXCEPTION 'synthetic r156 metadata failure'; END IF; RETURN NEW; END; $$");
        jdbc.execute("CREATE TRIGGER reject_r156_metadata BEFORE INSERT ON chat_attachment_metadata FOR EACH ROW EXECUTE FUNCTION reject_r156_metadata()");
        try {assertThatThrownBy(()->r156Ingest(input)).isInstanceOf(RuntimeException.class);assertThat(count("chat_history")).isEqualTo(3);assertThat(count("chat_attachment_metadata")).isEqualTo(3);}
        finally {jdbc.execute("DROP TRIGGER reject_r156_metadata ON chat_attachment_metadata");jdbc.execute("DROP FUNCTION reject_r156_metadata()");}
        var changed=r156Change(input,node->((com.fasterxml.jackson.databind.node.ObjectNode)node.path("mediaBatch").get(2)).put("attachmentName","changed.bin"));
        assertThatThrownBy(()->r156Ingest(changed)).hasMessageContaining("identity mismatch");
        r156Ingest(input);assertThat(count("chat_history")).isEqualTo(6);assertThat(count("chat_attachment_metadata")).isEqualTo(6);
    }

    @Test void vkBatchCommitFailureCannotLeaveAnAlreadyProcessedInboxWithMissingFiles() throws Exception {
        r154VkMedia(false);ingest(event);var input=r156Batch(88,3);
        jdbc.execute("CREATE FUNCTION reject_r156_commit() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN IF NEW.tg_message_id=88 THEN RAISE EXCEPTION 'synthetic r156 deferred failure'; END IF; RETURN NEW; END; $$");
        jdbc.execute("CREATE CONSTRAINT TRIGGER reject_r156_commit AFTER INSERT ON chat_history DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION reject_r156_commit()");
        try {
            assertThatThrownBy(()->r156Ingest(input)).isInstanceOf(RuntimeException.class);
            assertThat(count("chat_history")).isEqualTo(3);assertThat(count("chat_attachment_metadata")).isEqualTo(3);
            assertThat(inboxStatus(input.eventId())).isEqualTo("processing");
        } finally {jdbc.execute("DROP TRIGGER reject_r156_commit ON chat_history");jdbc.execute("DROP FUNCTION reject_r156_commit()");}
        jdbc.update("UPDATE integration_inbound_event_inbox SET processing_started_at=CURRENT_TIMESTAMP-INTERVAL '16 minutes' WHERE event_id=?",input.eventId());
        r156Ingest(input);assertThat(count("chat_history")).isEqualTo(6);assertThat(inboxStatus(input.eventId())).isEqualTo("processed");
    }

    private InboundClientMessageEvent r155Followup(long provider,String key) {
        return new InboundClientMessageEvent("r155-"+UUID.randomUUID(),"client_message.active_ticket","telegram",channel,event.ticketId(),901L,
            event.userIdentity(),event.username(),event.clientName(),"Follow-up","document",key,"followup.bin",Long.toString(provider),null,null,event.occurredAt().plusSeconds(5));
    }
    private String r155Key(long provider) {return "questionnaires/telegram/"+channel+"/"+(event.businessId()==null?"general":"business-"+event.businessId())
        +"/901/"+event.catalogSha256()+"/"+provider+"/"+r153Key.substring(r153Key.lastIndexOf('/')+1);}
    private void r155Ingest(InboundClientMessageEvent input) {followupIngestion.ingest(input,"integration.ticket.telegram");}

    @ParameterizedTest @ValueSource(booleans={false,true})
    void selectedFollowupRetainsFrozenOwnerMetadataReadAndExactReplayAfterRebind(boolean general) throws Exception {
        r153MediaEvent(general);ingest(event);
        assertThat(jdbc.queryForObject("SELECT questionnaire_version FROM tickets WHERE ticket_id=?",Integer.class,event.ticketId())).isEqualTo(4);
        jdbc.update("UPDATE business_channel_bindings SET is_active=FALSE WHERE channel_id=?",channel);
        settings.saveSettings(Map.of("bot_settings",Map.of("question_templates",List.of(template("changed","New global")))));
        assertThat(followupMedia.admission(channel,901,event.ticketId()).remainingFiles()).isEqualTo(9);
        String key=r155Key(88);r153Write(key,R153_BYTES);var input=r155Followup(88,key);r155Ingest(input);
        var row=jdbc.queryForMap("SELECT history.id,history.ticket_owner_user_id,history.channel_id,metadata.size,metadata.content_hash FROM chat_history history JOIN chat_attachment_metadata metadata ON metadata.chat_history_id=history.id WHERE history.ticket_id=? AND history.tg_message_id=88",event.ticketId());
        assertThat(row.get("ticket_owner_user_id")).isEqualTo(901L);assertThat(row.get("channel_id")).isEqualTo(channel);
        assertThat(((Number)row.get("size")).longValue()).isEqualTo(R153_BYTES.length);assertThat(row.get("content_hash")).isNotNull();
        long reader=r153Reader(general),history=((Number)row.get("id")).longValue();
        if(general) assertThat(generalRead.attachment(reader,r153PublicId(),history).orElseThrow().storageKey()).isEqualTo(key);
        else assertThat(businessRead.findAttachmentByPublicId(reader,r153PublicId(),history).orElseThrow().storageKey()).isEqualTo(key);
        if(general) {generalAccess.revokeRead(reader);assertThat(generalRead.attachment(reader,r153PublicId(),history)).isEmpty();}
        else {jdbc.update("UPDATE business_access_grants SET is_active=FALSE WHERE user_id=?",reader);assertThat(businessRead.findAttachmentByPublicId(reader,r153PublicId(),history)).isEmpty();}
        jdbc.update("UPDATE tickets SET status='closed' WHERE ticket_id=?",event.ticketId());flags.setTicketWriteRequiresBusiness(false);
        r153Write(key,"changed bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8));r155Ingest(input);
        assertThat(count("chat_history")).isEqualTo(2);assertThat(inboxStatus(input.eventId())).isEqualTo("processed");
    }

    @ParameterizedTest @ValueSource(strings={"owner","channel","user-key","catalog","legacy-path","type","name","closed","inactive","strict","platform","provider","bytes","missing","oversize","duplicate-provider","general-flag"})
    void invalidSelectedFollowupRollsBackHistoryMetadataAndActivity(String mutation) throws Exception {
        r153MediaEvent(mutation.equals("general-flag"));ingest(event);String key=r155Key(mutation.equals("duplicate-provider")?33:88);
        if(mutation.equals("owner")) key=key.replace("/business-"+a+"/","/business-"+b+"/");
        if(mutation.equals("channel")) key=key.replace("/telegram/"+channel+"/","/telegram/999999/");
        if(mutation.equals("user-key")) key=key.replace("/901/","/902/");
        if(mutation.equals("catalog")) key=key.replace(event.catalogSha256(),"f".repeat(64));
        if(mutation.equals("legacy-path")) key=event.ticketId()+"/old.bin";
        if(!mutation.equals("missing")) r153Write(key,mutation.equals("oversize")?new byte[10*1024*1024+1]:mutation.equals("bytes")?new byte[]{1,2,3}:R153_BYTES);
        var input=r155Followup(mutation.equals("duplicate-provider")?33:88,key);
        if(mutation.equals("type") || mutation.equals("name") || mutation.equals("provider") || mutation.equals("platform")) input=new InboundClientMessageEvent(input.eventId(),input.eventKind(),mutation.equals("platform")?"vk":"telegram",channel,input.ticketId(),901L,input.userIdentity(),input.username(),input.clientName(),input.text(),mutation.equals("type")?"html":input.messageType(),key,mutation.equals("name")?"../unsafe":input.attachmentName(),mutation.equals("provider")?null:input.providerMessageId(),null,null,input.occurredAt());
        if(mutation.equals("closed")) jdbc.update("UPDATE tickets SET status='closed' WHERE ticket_id=?",event.ticketId());
        if(mutation.equals("inactive")) jdbc.update("DELETE FROM ticket_active WHERE ticket_id=?",event.ticketId());
        if(mutation.equals("strict")) flags.setTicketWriteRequiresBusiness(false);
        if(mutation.equals("general-flag")) flags.setGeneralChannelResolution(false);
        var rejected=input;assertThatThrownBy(()->r155Ingest(rejected)).isInstanceOf(RuntimeException.class);
        assertThat(count("chat_history")).isEqualTo(1);assertThat(count("chat_attachment_metadata")).isEqualTo(1);
        assertThat(inboxStatus(input.eventId())).isEqualTo("failed");
    }

    @Test void followupEnforcesTenFilesAcrossDraftAndRegisteredHistoryAndRejectsProviderReuse() throws Exception {
        r153MediaEvent(false);ingest(event);
        InboundClientMessageEvent last=null;
        for(long id=88;id<97;id++){String key=r155Key(id);r153Write(key,R153_BYTES);last=r155Followup(id,key);r155Ingest(last);}
        assertThat(count("chat_history")).isEqualTo(10);
        assertThatThrownBy(()->followupMedia.admission(channel,901,event.ticketId())).isInstanceOf(IllegalStateException.class);
        r155Ingest(last);assertThat(count("chat_history")).isEqualTo(10);
        String key=r155Key(97);r153Write(key,R153_BYTES);var rejected=r155Followup(97,key);
        assertThatThrownBy(()->r155Ingest(rejected)).isInstanceOf(IllegalStateException.class);assertThat(count("chat_history")).isEqualTo(10);
    }

    @Test void frozenVersionCannotBeChangedOrAssignedToHistoricalRows() throws Exception {
        ingest(event);assertThat(jdbc.queryForObject("SELECT questionnaire_version FROM tickets WHERE ticket_id=?",Integer.class,event.ticketId())).isEqualTo(1);
        assertThatThrownBy(()->followupMedia.admission(channel,901,event.ticketId())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE tickets SET questionnaire_version=4 WHERE ticket_id=?",event.ticketId())).isInstanceOf(DataAccessException.class);
        String legacy=event.ticketId()+"-legacy";
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,channel_id,status,is_general) VALUES(901,?,?,?,'open',FALSE)",legacy,UUID.randomUUID().toString().replace("-",""),channel);
        assertThat(jdbc.queryForObject("SELECT questionnaire_version FROM tickets WHERE ticket_id=?",Integer.class,legacy)).isNull();
        assertThatThrownBy(()->jdbc.update("UPDATE tickets SET questionnaire_version=4 WHERE ticket_id=?",legacy)).isInstanceOf(DataAccessException.class);
    }

    @Test void followupMetadataFailureRollsBackAndExactFailedEventCanRetry() throws Exception {
        r153MediaEvent(false);ingest(event);String key=r155Key(88);r153Write(key,R153_BYTES);var input=r155Followup(88,key);
        jdbc.execute("CREATE FUNCTION reject_r155_metadata() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic r155 metadata failure'; END; $$");
        jdbc.execute("CREATE TRIGGER reject_r155_metadata BEFORE INSERT ON chat_attachment_metadata FOR EACH ROW EXECUTE FUNCTION reject_r155_metadata()");
        try{assertThatThrownBy(()->r155Ingest(input)).isInstanceOf(RuntimeException.class);assertThat(count("chat_history")).isEqualTo(1);}
        finally{jdbc.execute("DROP TRIGGER reject_r155_metadata ON chat_attachment_metadata");jdbc.execute("DROP FUNCTION reject_r155_metadata()");}
        r155Ingest(input);assertThat(count("chat_history")).isEqualTo(2);assertThat(count("chat_attachment_metadata")).isEqualTo(2);
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

    private void r150Resources() {
        jdbc.update("UPDATE businesses SET settings='{}'::jsonb WHERE id IN (?,?)",a,b);
        jdbc.update("UPDATE business_locations SET status='archived' WHERE business_id IN (?,?)",a,b);
        jdbc.update("INSERT INTO business_locations(business_id,location_name,normalized_location_name,location_type,city) VALUES(?,'R150 A','r150-a','Store','City') ON CONFLICT(business_id,normalized_location_name,location_type,city) DO UPDATE SET status='active'",a);
        var catalog=sharedQuestionnaires.choicesWithResources(channel).orElseThrow();
        var selected=sharedQuestionnaires.selectWithResources(channel,"BUSINESS",a,catalog.catalogSha256()).orElseThrow();
        event=change(event,event.eventId(),channel,event.userId(),a,"BUSINESS","USER_SELECTION",selected.questionTemplateId(),selected.templateSha256(),selected.catalogSha256(),event.platform());
        event=r150Attributes(List.of(new ConversationTicketCreatedEvent.TicketAttributePayload("free","free","Private A","text",null,null,"Answer",false)),null);
    }
    @ParameterizedTest @ValueSource(strings={"telegram","vk","max"})
    void v3ResourceSelectionCreatesOnceAndReplaysItsFrozenCatalogAfterResourceChanges(String platform) {
        r150Resources();jdbc.update("UPDATE channels SET platform=? WHERE id=?",platform,channel);
        event=change(event,event.eventId(),channel,event.userId(),a,"BUSINESS","USER_SELECTION",event.questionTemplateId(),event.templateSha256(),event.catalogSha256(),platform);
        ingest(event);
        jdbc.update("UPDATE business_locations SET status='archived' WHERE business_id=?",a);
        var replay=change(event,UUID.randomUUID().toString(),channel,event.userId(),a,"BUSINESS","USER_SELECTION",event.questionTemplateId(),event.templateSha256(),event.catalogSha256(),platform);
        ingest(replay);
        assertThat(jdbc.queryForObject("SELECT questionnaire_catalog_sha256 FROM tickets WHERE ticket_id=?",String.class,event.ticketId())).isEqualTo(event.catalogSha256());
        assertThat(jdbc.queryForObject("SELECT business_id FROM tickets WHERE ticket_id=?",Long.class,event.ticketId())).isEqualTo(a);
        for(String table:List.of("messages","tickets","ticket_attributes","chat_history","ticket_spans","ticket_active"))assertThat(count(table)).as(table).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"own-location","other-location","own-presets","other-presets"})
    void staleV3ResourcesAreRejectedBeforeAnyRootOrChildWrite(String kind) {
        r150Resources();long owner=kind.startsWith("own")?a:b;
        if(kind.endsWith("location"))jdbc.update("INSERT INTO business_locations(business_id,location_name,normalized_location_name) VALUES(?,'Added','added')",owner);
        else jdbc.update("UPDATE businesses SET settings=jsonb_set(settings,'{questionnaire_presets}','{\"g\":{\"f\":[\"New\"]}}'::jsonb) WHERE id=?",owner);
        assertThatThrownBy(()->ingest(event)).isInstanceOf(IllegalStateException.class);emptyWrites();assertThat(inboxStatus(event.eventId())).isEqualTo("failed");
    }


    private void r150PresetEvent() {
        r150Resources();
        jdbc.update("INSERT INTO business_locations(business_id,location_name,normalized_location_name,location_type,city) VALUES(?,'South site','r150-south','Store','South') ON CONFLICT(business_id,normalized_location_name,location_type,city) DO UPDATE SET status='active'",a);
        jdbc.update("UPDATE businesses SET settings='{\"questionnaire_presets\":{\"department\":{\"name\":[\"Kitchen\"]}}}'::jsonb WHERE id=?",a);
        var scoped=Map.<String,Object>of("id","r144-a","name","A","question_flow",List.of(
            Map.of("id","city-q","binding_key","city_answer","text","City","include_in_dashboard",true,"type","preset","preset",Map.of("group","locations","field","city")),
            Map.of("id","spot","text","Location","include_in_dashboard",true,"type","preset","preset",Map.of("group","locations","field","location_name")),
            Map.of("id","team","text","Department","include_in_dashboard",true,"type","preset","preset",Map.of("group","department","field","name"))));
        settings.saveSettings(Map.of("bot_settings",Map.of("active_template_id","r144-legacy","question_templates",List.of(
            template("r144-legacy","Global"),scoped,template("r144-b","Private B")))));
        registry.bind(a,"r144-a");var catalog=sharedQuestionnaires.choicesWithResources(channel).orElseThrow();
        var selected=sharedQuestionnaires.selectWithResources(channel,"BUSINESS",a,catalog.catalogSha256()).orElseThrow();
        event=change(event,event.eventId(),channel,event.userId(),a,"BUSINESS","USER_SELECTION",selected.questionTemplateId(),selected.templateSha256(),selected.catalogSha256(),event.platform());
        event=r150Attributes(List.of(
            new ConversationTicketCreatedEvent.TicketAttributePayload("city-q","city_answer","City","preset",null,"City","City",true),
            new ConversationTicketCreatedEvent.TicketAttributePayload("spot","spot","Location","preset",null,"R150 A","R150 A",true),
            new ConversationTicketCreatedEvent.TicketAttributePayload("team","team","Department","preset",null,"Kitchen","Kitchen",true)),null);
    }
    private ConversationTicketCreatedEvent r150Attributes(List<ConversationTicketCreatedEvent.TicketAttributePayload> attributes,String city) {
        return new ConversationTicketCreatedEvent(event.eventId(),event.eventKind(),event.platform(),channel,event.ticketId(),event.userId(),event.userIdentity(),event.username(),event.clientName(),
            event.business(),null,city,null,event.problem(),event.occurredAt(),attributes,event.historyEntries(),a,"BUSINESS","USER_SELECTION",event.questionTemplateId(),event.templateSha256(),event.catalogSha256());
    }
    @Test void resourceAnswersAreValidatedInsideTheActualSpringWriterTransaction() {
        r150PresetEvent();ingest(event);
        assertThat(count("tickets")).isEqualTo(1);assertThat(count("ticket_attributes")).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT value_text FROM ticket_attributes WHERE ticket_id=? AND question_id='spot'",String.class,event.ticketId())).isEqualTo("R150 A");
    }
    @ParameterizedTest @ValueSource(strings={"foreign-location","wrong-city","foreign-preset","forged-key","duplicate","unknown-question","label","projection","missing-preset"})
    void invalidResourceAnswersNeverReachTheRootOrChildren(String mutation) {
        r150PresetEvent();var attributes=new java.util.ArrayList<>(event.attributes());String city=null;
        switch(mutation) {
            case "foreign-location"->attributes.set(1,new ConversationTicketCreatedEvent.TicketAttributePayload("spot","spot","Location","preset",null,"Foreign B","Foreign B",true));
            case "wrong-city"->attributes.set(1,new ConversationTicketCreatedEvent.TicketAttributePayload("spot","spot","Location","preset",null,"South site","South site",true));
            case "foreign-preset"->attributes.set(2,new ConversationTicketCreatedEvent.TicketAttributePayload("team","team","Department","preset",null,"Secret B","Secret B",true));
            case "forged-key"->attributes.set(2,new ConversationTicketCreatedEvent.TicketAttributePayload("team","other","Department","preset",null,"Kitchen","Kitchen",true));
            case "duplicate"->attributes.add(attributes.get(1));
            case "unknown-question"->attributes.add(new ConversationTicketCreatedEvent.TicketAttributePayload("unknown","unknown","?","text",null,null,"Secret",true));
            case "label"->attributes.set(2,new ConversationTicketCreatedEvent.TicketAttributePayload("team","team","Department","preset",null,"Secret B","Kitchen",true));
            case "missing-preset"->attributes.remove(2);
            default->city="Forged city";
        }
        var rejected=r150Attributes(attributes,city);
        assertThatThrownBy(()->ingest(rejected)).isInstanceOf(IllegalStateException.class);emptyWrites();assertThat(inboxStatus(event.eventId())).isEqualTo("failed");
    }

    private void r151RouteEvent(String platform) {
        r150Resources();jdbc.update("UPDATE channels SET platform=? WHERE id=?",platform,channel);
        var scoped=Map.<String,Object>of("id","r144-a","name","A","question_flow",List.of(
            Map.of("id","last","type","custom","order",30,"text","Last"),
            Map.of("id","branch","type","select","order",10,"text","Choose","options",List.of(Map.of("id","skip","label","Skip"),Map.of("id","more","label","More")),
                "routes",List.of(Map.of("value_id","skip","next_question_id","last"))),
            Map.of("id","middle","type","text","order",20,"text","Required only on More")));
        settings.saveSettings(Map.of("bot_settings",Map.of("active_template_id","r144-legacy","question_templates",List.of(template("r144-legacy","Global"),scoped,template("r144-b","Private B")))));
        registry.bind(a,"r144-a");var catalog=sharedQuestionnaires.choicesWithResources(channel).orElseThrow();
        var selected=sharedQuestionnaires.selectWithResources(channel,"BUSINESS",a,catalog.catalogSha256()).orElseThrow();
        event=change(event,event.eventId(),channel,event.userId(),a,"BUSINESS","USER_SELECTION",selected.questionTemplateId(),selected.templateSha256(),selected.catalogSha256(),platform);
        event=r150Attributes(List.of(new ConversationTicketCreatedEvent.TicketAttributePayload("branch","branch","Choose","select","skip","Skip","Skip",false),
            new ConversationTicketCreatedEvent.TicketAttributePayload("last","last","Last","custom",null,null,"Help",false)),null);
    }
    @ParameterizedTest @ValueSource(strings={"telegram","vk","max"})
    void actualWriterAcceptsOnlyTheChosenForwardBranch(String platform) {
        r151RouteEvent(platform);ingest(event);assertThat(count("tickets")).isEqualTo(1);assertThat(count("ticket_attributes")).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT question_id FROM ticket_attributes WHERE ticket_id=? ORDER BY question_id",String.class,event.ticketId())).containsExactly("branch","last");
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource({"telegram,missing","vk,missing","max,missing","telegram,off-route","vk,off-route","max,off-route","telegram,option","vk,option","max,option"})
    void invalidV3PathRollsBackEveryRootAndChild(String platform,String mutation) {
        r151RouteEvent(platform);var answers=new java.util.ArrayList<>(event.attributes());
        if(mutation.equals("missing"))answers.remove(1);
        else if(mutation.equals("off-route"))answers.add(new ConversationTicketCreatedEvent.TicketAttributePayload("middle","middle","Required only on More","text",null,null,"Stale",false));
        else answers.set(0,new ConversationTicketCreatedEvent.TicketAttributePayload("branch","branch","Choose","select","more","Skip","Skip",false));
        var rejected=r150Attributes(answers,null);assertThatThrownBy(()->ingest(rejected)).isInstanceOf(IllegalStateException.class);
        emptyWrites();assertThat(inboxStatus(event.eventId())).isEqualTo("failed");
    }


    @ParameterizedTest @ValueSource(strings={"telegram","vk","max"})
    void v3GeneralWriterKeepsANullOwnerAndFrozenReplayAfterBusinessResourcesChange(String platform) {
        r150Resources();flags.setGeneralChannelResolution(true);jdbc.update("UPDATE channels SET platform=? WHERE id=?",platform,channel);
        String id="r150-general-"+channel;
        settings.saveSettings(Map.of("bot_settings",Map.of("active_template_id","r144-legacy","question_templates",List.of(
            template("r144-legacy","Global"),template("r144-a","Private A"),template("r144-b","Private B"),template(id,"General")))));
        sharedQuestionnaires.bind(channel,id);var catalog=sharedQuestionnaires.choicesWithResources(channel).orElseThrow();
        var general=sharedQuestionnaires.selectWithResources(channel,"GENERAL",null,catalog.catalogSha256()).orElseThrow();
        event=change(event,event.eventId(),channel,event.userId(),null,"GENERAL","USER_SELECTION",general.questionTemplateId(),general.templateSha256(),general.catalogSha256(),platform);
        event=new ConversationTicketCreatedEvent(event.eventId(),event.eventKind(),event.platform(),channel,event.ticketId(),event.userId(),event.userIdentity(),event.username(),event.clientName(),event.business(),null,null,null,event.problem(),event.occurredAt(),
            List.of(new ConversationTicketCreatedEvent.TicketAttributePayload("free","free","General","text",null,null,"Answer",false)),event.historyEntries(),null,"GENERAL","USER_SELECTION",event.questionTemplateId(),event.templateSha256(),event.catalogSha256());
        ingest(event);jdbc.update("UPDATE business_locations SET status='archived' WHERE business_id=?",a);
        ingest(change(event,UUID.randomUUID().toString(),channel,event.userId(),null,"GENERAL","USER_SELECTION",general.questionTemplateId(),general.templateSha256(),general.catalogSha256(),platform));
        var row=jdbc.queryForMap("SELECT * FROM tickets WHERE ticket_id=?",event.ticketId());
        assertThat(row.get("business_id")).isNull();assertThat(row.get("business_selection_source")).isNull();
        assertThat(row.get("is_general")).isEqualTo(true);assertThat(row.get("general_selection_source")).isEqualTo("USER_SELECTION");
        assertThat(row.get("questionnaire_catalog_sha256")).isEqualTo(general.catalogSha256());
        for(String table:List.of("messages","tickets","ticket_attributes","chat_history","ticket_spans","ticket_active"))assertThat(count(table)).as(table).isEqualTo(1);
    }

    private void r152LargeEvent(String platform,boolean general) throws Exception {
        r150Resources();jdbc.update("UPDATE channels SET platform=? WHERE id=?",platform,channel);
        var ids=jdbc.queryForList("INSERT INTO businesses(code,display_name) SELECT 'r152-"+channel+"-'||n,'Large '||n FROM generate_series(1,103) n RETURNING id",Long.class);
        var templates=new java.util.ArrayList<Map<String,Object>>(List.of(template("r144-legacy","Global"),template("r144-a","Private A"),template("r144-b","Private B"),template("r152-general-"+channel,"General")));
        for(long id:ids)templates.add(template("r152-q-"+id,"Private "+id));
        settings.saveSettings(Map.of("bot_settings",Map.of("active_template_id","r144-legacy","question_templates",templates)));
        var normalized=new com.example.panel.service.BotSettingsPayloadNormalizer().normalize(settings.loadSettings().get("bot_settings"));
        @SuppressWarnings("unchecked") var stored=(List<Map<String,Object>>)normalized.get("question_templates");
        var hashes=new java.util.HashMap<String,String>();for(var t:stored)hashes.put((String)t.get("id"),java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(t))));
        var bindings=new java.util.ArrayList<Object[]>();var channelBindings=new java.util.ArrayList<Object[]>();
        for(long id:ids){bindings.add(new Object[]{id,"r152-q-"+id,hashes.get("r152-q-"+id)});channelBindings.add(new Object[]{channel,id});}
        jdbc.batchUpdate("INSERT INTO business_question_template_bindings(business_id,template_id,template_sha256) VALUES(?,?,?)",bindings);
        jdbc.batchUpdate("INSERT INTO business_channel_bindings(channel_id,business_id,resolution_mode) VALUES(?,?,'EXPLICIT_SELECTION')",channelBindings);
        if(general){flags.setGeneralChannelResolution(true);sharedQuestionnaires.bind(channel,"r152-general-"+channel);}
        var catalog=sharedQuestionnaires.choicesExpanded(channel).orElseThrow();
        var selected=sharedQuestionnaires.selectExpanded(channel,general?"GENERAL":"BUSINESS",general?null:ids.get(ids.size()-1),catalog.catalogSha256()).orElseThrow();
        event=change(event,event.eventId(),channel,event.userId(),selected.businessId(),selected.scopeKind(),"USER_SELECTION",selected.questionTemplateId(),selected.templateSha256(),selected.catalogSha256(),platform);
        String questionText=general?"General":"Private "+selected.businessId();
        event=new ConversationTicketCreatedEvent(event.eventId(),event.eventKind(),platform,channel,event.ticketId(),event.userId(),event.userIdentity(),event.username(),event.clientName(),event.business(),null,null,null,event.problem(),event.occurredAt(),
            List.of(new ConversationTicketCreatedEvent.TicketAttributePayload("free","free",questionText,"text",null,null,"Answer",false)),event.historyEntries(),selected.businessId(),selected.scopeKind(),"USER_SELECTION",selected.questionTemplateId(),selected.templateSha256(),selected.catalogSha256());
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource({"telegram,false","vk,false","max,false","telegram,true","vk,true","max,true"})
    void v4SelectionBeyondOneHundredCreatesOnceAndReplaysWithoutCurrentCatalog(String platform,boolean general) throws Exception {
        r152LargeEvent(platform,general);ingest(event);
        assertThat(jdbc.queryForObject("SELECT business_id FROM tickets WHERE ticket_id=?",Long.class,event.ticketId())).isEqualTo(event.businessId());
        jdbc.update("UPDATE business_channel_bindings SET is_active=FALSE WHERE channel_id=?",channel);
        ingest(change(event,UUID.randomUUID().toString(),channel,event.userId(),event.businessId(),event.scopeKind(),"USER_SELECTION",event.questionTemplateId(),event.templateSha256(),event.catalogSha256(),platform));
        for(String table:List.of("messages","tickets","ticket_attributes","chat_history","ticket_spans","ticket_active"))assertThat(count(table)).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"telegram","vk","max"})
    void v4MissingRequiredAnswerRollsBackBeforeTheFirstWrite(String platform) throws Exception {
        r152LargeEvent(platform,false);
        var invalid=new ConversationTicketCreatedEvent(event.eventId(),event.eventKind(),platform,channel,event.ticketId(),event.userId(),event.userIdentity(),event.username(),event.clientName(),event.business(),null,null,null,event.problem(),event.occurredAt(),
            List.of(),event.historyEntries(),event.businessId(),event.scopeKind(),"USER_SELECTION",event.questionTemplateId(),event.templateSha256(),event.catalogSha256());
        assertThatThrownBy(()->ingest(invalid)).isInstanceOf(IllegalStateException.class);emptyWrites();assertThat(inboxStatus(event.eventId())).isEqualTo("failed");
    }


    private static final byte[] R153_BYTES="owned R153 attachment".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private String r153Key;
    private void r153MediaEvent(boolean general) throws Exception {
        jdbc.update("UPDATE businesses SET settings='{}'::jsonb WHERE id IN (?,?)",a,b);
        jdbc.update("UPDATE business_locations SET status='archived' WHERE business_id IN (?,?)",a,b);
        if(general) {
            String generalId="r153-general-"+channel;
            settings.saveSettings(Map.of("bot_settings",Map.of("active_template_id","r144-legacy","question_templates",List.of(
                template("r144-legacy","Global"),template("r144-a","Private A"),template("r144-b","Private B"),template(generalId,"General")))));
            flags.setGeneralChannelResolution(true);sharedQuestionnaires.bind(channel,generalId);
        }
        var catalog=sharedQuestionnaires.choicesExpanded(channel).orElseThrow();
        var selected=sharedQuestionnaires.selectExpanded(channel,general?"GENERAL":"BUSINESS",general?null:a,catalog.catalogSha256()).orElseThrow();
        event=change(event,event.eventId(),channel,event.userId(),selected.businessId(),selected.scopeKind(),"USER_SELECTION",selected.questionTemplateId(),selected.templateSha256(),selected.catalogSha256(),"telegram");
        event=new ConversationTicketCreatedEvent(event.eventId(),event.eventKind(),event.platform(),channel,event.ticketId(),event.userId(),event.userIdentity(),event.username(),event.clientName(),event.business(),null,null,null,event.problem(),event.occurredAt(),
            List.of(new ConversationTicketCreatedEvent.TicketAttributePayload("free","free",general?"General":"Private A","text",null,null,"Answer",false)),event.historyEntries(),event.businessId(),event.scopeKind(),"USER_SELECTION",event.questionTemplateId(),event.templateSha256(),event.catalogSha256());
        String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(R153_BYTES));
        r153Key="questionnaires/telegram/"+channel+"/"+(general?"general":"business-"+a)+"/901/"+event.catalogSha256()+"/33/"+hash+".bin";
        r153Write(r153Key,R153_BYTES);
        event=r153History(List.of(new ConversationTicketCreatedEvent.ConversationHistoryEntryPayload(901L,"Answer","document",r153Key,"proof.bin","33",event.occurredAt())));
    }
    private void r153Write(String key,byte[] bytes) throws Exception {
        int slash=key.lastIndexOf('/');
        try(var stored=mediaStorage.storeDialogAttachment(key.substring(0,slash),key.substring(slash+1),"application/octet-stream",new java.io.ByteArrayInputStream(bytes))) {assertThat(stored.size()).isEqualTo(bytes.length);}
    }
    private ConversationTicketCreatedEvent r153History(List<ConversationTicketCreatedEvent.ConversationHistoryEntryPayload> history) {
        return new ConversationTicketCreatedEvent(event.eventId(),event.eventKind(),event.platform(),channel,event.ticketId(),event.userId(),event.userIdentity(),event.username(),event.clientName(),event.business(),event.locationType(),event.city(),event.locationName(),event.problem(),event.occurredAt(),
            event.attributes(),history,event.businessId(),event.scopeKind(),event.scopeSelectionSource(),event.questionTemplateId(),event.templateSha256(),event.catalogSha256());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void v4OwnedMediaCreatesCanonicalMetadataAndFrozenReplayNeverReopensBytes(boolean general) throws Exception {
        r153MediaEvent(general);ingest(event);
        var meta=jdbc.queryForMap("SELECT * FROM chat_attachment_metadata WHERE ticket_id=?",event.ticketId());
        assertThat(meta.get("content_hash")).isEqualTo(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(R153_BYTES)));
        assertThat(((Number)meta.get("size")).longValue()).isEqualTo(R153_BYTES.length);
        assertThat(meta.get("storage_key")).isEqualTo(r153Key);assertThat(((Number)meta.get("channel_id")).longValue()).isEqualTo(channel);
        assertThat(jdbc.queryForObject("SELECT ticket_owner_user_id FROM chat_history WHERE ticket_id=?",Long.class,event.ticketId())).isEqualTo(901L);
        assertThat(jdbc.queryForObject("SELECT business_id FROM tickets WHERE ticket_id=?",Long.class,event.ticketId())).isEqualTo(general?null:a);
        int slash=r153Key.lastIndexOf('/');mediaStorage.deleteDialogAttachment(r153Key.substring(0,slash),r153Key.substring(slash+1));
        jdbc.update("UPDATE business_channel_bindings SET is_active=FALSE WHERE channel_id=?",channel);
        ingest(change(event,UUID.randomUUID().toString(),channel,event.userId(),event.businessId(),event.scopeKind(),"USER_SELECTION",event.questionTemplateId(),event.templateSha256(),event.catalogSha256(),"telegram"));
        assertThat(count("chat_history")).isEqualTo(1);assertThat(count("chat_attachment_metadata")).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"channel","business","general","user","catalog","provider","external","traversal","name","history-owner","type","missing","bytes","empty","oversize","duplicate","budget"})
    void v4ForeignUnavailableOrChangedMediaRollsBackEveryRootAndChild(String mutation) throws Exception {
        r153MediaEvent(false);var entry=event.historyEntries().get(0);String key=r153Key,name=entry.attachmentName(),provider=entry.providerMessageId(),type=entry.messageType();Long user=901L;
        switch(mutation) {
            case "channel"->key=key.replace("/telegram/"+channel+"/","/telegram/999999/");
            case "business"->key=key.replace("/business-"+a+"/","/business-"+b+"/");
            case "general"->key=key.replace("/business-"+a+"/","/general/");
            case "user"->key=key.replace("/901/","/999/");
            case "catalog"->key=key.replace(event.catalogSha256(),"f".repeat(64));
            case "provider"->provider="34";case "external"->key="https://example.invalid/file";
            case "traversal"->key=key.replace("/33/","/33/../33/");case "name"->name="../proof.bin";
            case "history-owner"->user=999L;case "type"->type="text";
            case "missing"->{int slash=key.lastIndexOf('/');mediaStorage.deleteDialogAttachment(key.substring(0,slash),key.substring(slash+1));}
            case "bytes"->r153Write(key,"changed bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            case "empty"->r153Write(key,new byte[0]);case "oversize"->r153Write(key,new byte[10*1024*1024+1]);
        }
        var entries=new java.util.ArrayList<ConversationTicketCreatedEvent.ConversationHistoryEntryPayload>();
        entries.add(new ConversationTicketCreatedEvent.ConversationHistoryEntryPayload(user,entry.text(),type,key,name,provider,entry.occurredAt()));
        if(mutation.equals("duplicate")) entries.add(entries.get(0));
        if(mutation.equals("budget")) for(int index=1;index<=10;index++) {
            String another=key.replace("/33/","/"+(33+index)+"/");r153Write(another,R153_BYTES);
            entries.add(new ConversationTicketCreatedEvent.ConversationHistoryEntryPayload(901L,"Answer","document",another,"proof.bin",Integer.toString(33+index),entry.occurredAt()));
        }
        var invalid=r153History(entries);assertThatThrownBy(()->ingest(invalid)).isInstanceOf(IllegalStateException.class);
        emptyWrites();assertThat(count("chat_attachment_metadata")).isZero();assertThat(inboxStatus(event.eventId())).isEqualTo("failed");
    }

    @Autowired com.example.panel.service.BusinessScopedTicketReadService businessRead;
    @Autowired com.example.panel.service.GeneralTicketReadService generalRead;
    @Autowired com.example.panel.service.GeneralTicketAccessService generalAccess;
    private long r153Reader(boolean general) {
        long id=jdbc.queryForObject("INSERT INTO users(username,password) VALUES(?,'unused') RETURNING id",Long.class,"r153-reader-"+UUID.randomUUID());
        if(general)assertThat(generalAccess.grantRead(id)).isTrue();
        else jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(?,'ALL_BUSINESSES','VIEWER')",id);
        return id;
    }
    private String r153PublicId(){return jdbc.queryForObject("SELECT ticket_public_id FROM tickets WHERE ticket_id=?",String.class,event.ticketId());}
    private long r153HistoryId(){return jdbc.queryForObject("SELECT id FROM chat_history WHERE ticket_id=? ORDER BY id LIMIT 1",Long.class,event.ticketId());}
    private boolean r153Readable(boolean general,long reader) {
        return general?generalRead.attachment(reader,r153PublicId(),r153HistoryId()).isPresent():businessRead.findAttachmentByPublicId(reader,r153PublicId(),r153HistoryId()).isPresent();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void v4MediaIsReadableOnlyThroughCurrentScopedGrantWithItsFrozenProof(boolean general) throws Exception {
        r153MediaEvent(general);ingest(event);long reader=r153Reader(general);assertThat(r153Readable(general,reader)).isTrue();
        if(general) {
            var ref=generalRead.attachment(reader,r153PublicId(),r153HistoryId()).orElseThrow();assertThat(ref.storageKey()).isEqualTo(r153Key);assertThat(ref.sha256()).isNotNull();assertThat(ref.size()).isEqualTo(R153_BYTES.length);
            generalAccess.revokeRead(reader);
        } else {
            var ref=businessRead.findAttachmentByPublicId(reader,r153PublicId(),r153HistoryId()).orElseThrow();assertThat(ref.storageKey()).isEqualTo(r153Key);assertThat(ref.sha256()).isNotNull();assertThat(ref.size()).isEqualTo(R153_BYTES.length);
            jdbc.update("UPDATE business_access_grants SET is_active=FALSE WHERE user_id=?",reader);
        }
        assertThat(r153Readable(general,reader)).isFalse();
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource({"false,provider","true,provider","false,history-channel","true,history-channel","false,metadata-channel","true,metadata-channel","false,hash","true,hash","false,size","true,size","false,duplicate","true,duplicate"})
    void v4ReadRejectsDamagedOrAmbiguousMetadataEvenWithCurrentReadGrant(boolean general,String mutation) throws Exception {
        r153MediaEvent(general);ingest(event);long reader=r153Reader(general);assertThat(r153Readable(general,reader)).isTrue();
        switch(mutation) {
            case "provider"->jdbc.update("UPDATE chat_history SET tg_message_id=34 WHERE id=?",r153HistoryId());
            case "history-channel"->jdbc.update("UPDATE chat_history SET channel_id=NULL WHERE id=?",r153HistoryId());
            case "metadata-channel"->jdbc.update("UPDATE chat_attachment_metadata SET channel_id=NULL WHERE chat_history_id=?",r153HistoryId());
            case "hash"->jdbc.update("UPDATE chat_attachment_metadata SET content_hash=? WHERE chat_history_id=?","f".repeat(64),r153HistoryId());
            case "size"->jdbc.update("UPDATE chat_attachment_metadata SET size=0 WHERE chat_history_id=?",r153HistoryId());
            case "duplicate"->{
                long another=jdbc.queryForObject("INSERT INTO chat_history(user_id,ticket_id,ticket_owner_user_id,sender,message,message_type,attachment,channel_id) VALUES(?,?,?,'client','duplicate','document',?,?) RETURNING id",Long.class,event.userId(),event.ticketId(),event.userId(),r153Key,channel);
                jdbc.update("INSERT INTO chat_attachment_metadata(chat_history_id,ticket_id,storage_key,storage_provider,storage_class,normalization_status,availability_status) VALUES(?,?,?,'local_fs','dialog_attachment','normalized','available')",another,event.ticketId(),r153Key);
            }
        }
        assertThat(r153Readable(general,reader)).isFalse();
    }
    private java.util.List<String> r154Keys;
    private void r154VkMedia(boolean general) throws Exception {
        r153MediaEvent(general);jdbc.update("UPDATE channels SET platform='vk' WHERE id=?",channel);
        event=change(event,event.eventId(),channel,event.userId(),event.businessId(),event.scopeKind(),"USER_SELECTION",event.questionTemplateId(),event.templateSha256(),event.catalogSha256(),"vk");
        var entries=new java.util.ArrayList<ConversationTicketCreatedEvent.ConversationHistoryEntryPayload>();r154Keys=new java.util.ArrayList<>();
        for(int slot=1;slot<=3;slot++) {
            String key=r153Key.replace("/telegram/","/vk/").replace("/33/","/33/"+slot+"/");r153Write(key,R153_BYTES);r154Keys.add(key);
            entries.add(new ConversationTicketCreatedEvent.ConversationHistoryEntryPayload(901L,"Answer",slot==1?"photo":slot==2?"document":"audio_message",key,"proof.bin","33",event.occurredAt()));
        }
        event=r153History(entries);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void vkV4MediaCreatesMetadataAndFrozenReplayForMultipleSlots(boolean general) throws Exception {
        r154VkMedia(general);ingest(event);
        assertThat(count("chat_history")).isEqualTo(3);assertThat(count("chat_attachment_metadata")).isEqualTo(3);
        assertThat(jdbc.queryForList("SELECT storage_key FROM chat_attachment_metadata WHERE ticket_id=?",String.class,event.ticketId())).containsExactlyInAnyOrderElementsOf(r154Keys);
        assertThat(jdbc.queryForList("SELECT tg_message_id FROM chat_history WHERE ticket_id=?",Long.class,event.ticketId())).containsOnly(33L);
        assertThat(jdbc.queryForList("SELECT content_hash FROM chat_attachment_metadata WHERE ticket_id=?",String.class,event.ticketId())).containsOnly(r154Keys.get(0).substring(r154Keys.get(0).lastIndexOf('/')+1,r154Keys.get(0).lastIndexOf('.')));
        assertThat(jdbc.queryForList("SELECT size FROM chat_attachment_metadata WHERE ticket_id=?",Long.class,event.ticketId())).containsOnly((long)R153_BYTES.length);
        for(String key:r154Keys){int slash=key.lastIndexOf('/');mediaStorage.deleteDialogAttachment(key.substring(0,slash),key.substring(slash+1));}
        jdbc.update("UPDATE business_channel_bindings SET is_active=FALSE WHERE channel_id=?",channel);
        ingest(change(event,UUID.randomUUID().toString(),channel,event.userId(),event.businessId(),event.scopeKind(),"USER_SELECTION",event.questionTemplateId(),event.templateSha256(),event.catalogSha256(),"vk"));
        assertThat(count("chat_history")).isEqualTo(3);assertThat(count("chat_attachment_metadata")).isEqualTo(3);
    }
    @ParameterizedTest @ValueSource(strings={"channel","business","general","user","catalog","provider","telegram","external","traversal","slot-zero","slot-eleven","slot-leading-zero","name","history-owner","type","missing","bytes","empty","oversize","duplicate","same-slot","budget","v3"})
    void vkV4MediaRejectsForeignUnavailableAndAmbiguousBatch(String mutation) throws Exception {
        r154VkMedia(false);var entry=event.historyEntries().get(0);String key=entry.attachmentPath(),name=entry.attachmentName(),provider=entry.providerMessageId(),type=entry.messageType();Long user=901L;
        switch(mutation) {
            case "channel"->key=key.replace("/vk/"+channel+"/","/vk/999999/");
            case "business"->key=key.replace("/business-"+a+"/","/business-"+b+"/");
            case "general"->key=key.replace("/business-"+a+"/","/general/");case "user"->key=key.replace("/901/","/999/");
            case "catalog"->key=key.replace(event.catalogSha256(),"f".repeat(64));case "provider"->provider="34";
            case "telegram"->key=key.replace("/vk/","/telegram/");case "external"->key="https://cdn.userapi.com/file";
            case "traversal"->key=key.replace("/33/1/","/33/../1/");case "slot-zero"->key=key.replace("/33/1/","/33/0/");
            case "slot-eleven"->key=key.replace("/33/1/","/33/11/");case "slot-leading-zero"->key=key.replace("/33/1/","/33/01/");
            case "name"->name="../proof.bin";case "history-owner"->user=999L;case "type"->type="video";
            case "missing"->{int slash=key.lastIndexOf('/');mediaStorage.deleteDialogAttachment(key.substring(0,slash),key.substring(slash+1));}
            case "bytes"->r153Write(key,new byte[]{1,2,3});case "empty"->r153Write(key,new byte[0]);case "oversize"->r153Write(key,new byte[10*1024*1024+1]);
            case "v3"->{var catalog=sharedQuestionnaires.choicesWithResources(channel).orElseThrow();var selected=sharedQuestionnaires.selectWithResources(channel,"BUSINESS",a,catalog.catalogSha256()).orElseThrow();
                event=change(event,event.eventId(),channel,event.userId(),a,"BUSINESS","USER_SELECTION",selected.questionTemplateId(),selected.templateSha256(),selected.catalogSha256(),"vk");
                key=key.replace(entry.attachmentPath().split("/")[5],selected.catalogSha256());r153Write(key,R153_BYTES);}
        }
        // These foreign/malformed keys exist and have correct bytes: rejecting a missing object would not prove ownership/slot validation.
        if(java.util.Set.of("channel","business","general","user","catalog","telegram","slot-zero","slot-eleven","slot-leading-zero").contains(mutation))r153Write(key,R153_BYTES);
        var entries=new java.util.ArrayList<ConversationTicketCreatedEvent.ConversationHistoryEntryPayload>();
        entries.add(new ConversationTicketCreatedEvent.ConversationHistoryEntryPayload(user,entry.text(),type,key,name,provider,entry.occurredAt()));
        if(mutation.equals("duplicate"))entries.add(entries.get(0));
        if(mutation.equals("same-slot")) {
            byte[] anotherBytes="different valid bytes in the same VK slot".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String anotherSha=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(anotherBytes));
            String another=key.substring(0,key.lastIndexOf('/')+1)+anotherSha+".bin";r153Write(another,anotherBytes);
            entries.add(new ConversationTicketCreatedEvent.ConversationHistoryEntryPayload(user,"Answer",type,another,name,provider,entry.occurredAt()));
        }
        if(mutation.equals("budget"))for(int index=1;index<=10;index++) {
            String another=key.replace("/33/1/","/"+(33+index)+"/1/");r153Write(another,R153_BYTES);
            entries.add(new ConversationTicketCreatedEvent.ConversationHistoryEntryPayload(user,"Answer",type,another,name,Integer.toString(33+index),entry.occurredAt()));
        }
        var invalid=r153History(entries);assertThatThrownBy(()->ingest(invalid)).isInstanceOf(IllegalStateException.class);emptyWrites();assertThat(count("chat_attachment_metadata")).isZero();assertThat(inboxStatus(event.eventId())).isEqualTo("failed");
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void vkV4MediaReadUsesFrozenProofAndCurrentGrant(boolean general) throws Exception {
        r154VkMedia(general);ingest(event);long reader=r153Reader(general);assertThat(r153Readable(general,reader)).isTrue();
        jdbc.update("UPDATE business_channel_bindings SET is_active=FALSE WHERE channel_id=?",channel);assertThat(r153Readable(general,reader)).isTrue();
        if(general){var ref=generalRead.attachment(reader,r153PublicId(),r153HistoryId()).orElseThrow();assertThat(ref.storageKey()).isEqualTo(r154Keys.get(0));assertThat(ref.sha256()).isNotNull();generalAccess.revokeRead(reader);}
        else {var ref=businessRead.findAttachmentByPublicId(reader,r153PublicId(),r153HistoryId()).orElseThrow();assertThat(ref.storageKey()).isEqualTo(r154Keys.get(0));assertThat(ref.sha256()).isNotNull();jdbc.update("UPDATE business_access_grants SET is_active=FALSE WHERE user_id=?",reader);}
        assertThat(r153Readable(general,reader)).isFalse();
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource({"false,provider","true,provider","false,platform","true,platform","false,hash","true,hash","false,size","true,size","false,duplicate","true,duplicate"})
    void vkV4MediaReadRejectsDamagedMetadata(boolean general,String mutation) throws Exception {
        r154VkMedia(general);ingest(event);long reader=r153Reader(general);assertThat(r153Readable(general,reader)).isTrue();
        switch(mutation) {
            case "provider"->jdbc.update("UPDATE chat_history SET tg_message_id=34 WHERE id=?",r153HistoryId());
            case "platform"->jdbc.update("UPDATE channels SET platform='telegram' WHERE id=?",channel);
            case "hash"->jdbc.update("UPDATE chat_attachment_metadata SET content_hash=? WHERE chat_history_id=?","f".repeat(64),r153HistoryId());
            case "size"->jdbc.update("UPDATE chat_attachment_metadata SET size=0 WHERE chat_history_id=?",r153HistoryId());
            case "duplicate"->{String key=r154Keys.get(0);long another=jdbc.queryForObject("INSERT INTO chat_history(user_id,ticket_id,ticket_owner_user_id,sender,message,message_type,attachment,channel_id) VALUES(?,?,?,'client','duplicate','photo',?,?) RETURNING id",Long.class,event.userId(),event.ticketId(),event.userId(),key,channel);
                jdbc.update("INSERT INTO chat_attachment_metadata(chat_history_id,ticket_id,storage_key,storage_provider,storage_class,normalization_status,availability_status) VALUES(?,?,?,'local_fs','dialog_attachment','normalized','available')",another,event.ticketId(),key);}
        }
        assertThat(r153Readable(general,reader)).isFalse();
    }
}
