package com.example.panel.service;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

class ClientProfileDialogLinksPostgresTest {
    JdbcTemplate jdbc;
    ClientsService service;
    @BeforeEach void setup() {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("profile_links");
        jdbc.execute("CREATE TABLE messages(ticket_id TEXT,group_msg_id BIGINT,user_id BIGINT,channel_id BIGINT,business TEXT,city TEXT,location_type TEXT,location_name TEXT,problem TEXT,created_at TEXT,category TEXT,client_status TEXT)");
        jdbc.execute("CREATE TABLE tickets(ticket_id TEXT,user_id BIGINT,channel_id BIGINT,is_general BOOLEAN DEFAULT FALSE,ticket_public_id TEXT,status TEXT,resolved_at TEXT,PRIMARY KEY(user_id,ticket_id))");
        jdbc.execute("CREATE TABLE channels(id BIGINT PRIMARY KEY,channel_name TEXT,bot_name TEXT)");
        jdbc.update("INSERT INTO channels VALUES(7,'Telegram',NULL),(8,'MAX',NULL)");
        service=new ClientsService(jdbc,null,null);
    }
    void message(long user,String ticket,Long channel) {
        jdbc.update("INSERT INTO messages(ticket_id,group_msg_id,user_id,channel_id) VALUES(?,42,?,?)",ticket,user,channel);
    }
    void root(long user,String ticket,Long channel,boolean general,String publicId,String status) {
        jdbc.update("INSERT INTO tickets(ticket_id,user_id,channel_id,is_general,ticket_public_id,status) VALUES(?,?,?,?,?,?)",ticket,user,channel,general,publicId,status);
    }
    @Test void normalAndOpaqueIdsKeepTheirExactChannelAndEncodeQueryCharacters() {
        String raw=" T/1?&%#+ Ю ";message(55,raw,7L);root(55,raw,7L,false,null,"pending");
        var row=service.loadProfileTickets(55).get(0);
        assertThat(row.ticketId()).isEqualTo(raw);
        assertThat(row.dialogUrl()).isEqualTo("/dialogs?ticketId=%20T/1?%26%25%23%2B%20%D0%AE%20&channelId=7");
        assertThat(row.requestNumber()).isEqualTo("42");
    }
    @Test void generalUsesItsPublicWorkspaceInsteadOfLegacyDialogApi() {
        message(55,"general",7L);root(55,"general",7L,true,"a".repeat(32),"pending");
        assertThat(service.loadProfileTickets(55)).singleElement().satisfies(row ->
            assertThat(row.dialogUrl()).isEqualTo("/general-dialog-work/"+"a".repeat(32)));
    }
    @Test void collisionsCannotDuplicateRowsBorrowStatusOrProduceLegacyLinks() {
        message(55,"same",7L);root(55,"same",7L,false,null,"pending");root(99,"same",8L,false,null,"other-private-status");
        assertThat(service.loadProfileTickets(55)).singleElement().satisfies(row -> {
            assertThat(row.status()).isEqualTo("pending");assertThat(row.dialogUrl()).isNull();
        });
    }
    @Test void wrongOwnerOrChannelOrMissingRootCannotCreateNavigation() {
        message(55,"wrong-owner",7L);root(99,"wrong-owner",7L,false,null,"secret");
        message(55,"wrong-channel",7L);root(55,"wrong-channel",8L,false,null,"secret");
        message(55,"orphan",null);
        assertThat(service.loadProfileTickets(55)).hasSize(3).allSatisfy(row -> {
            assertThat(row.dialogUrl()).isNull();assertThat(row.status()).isNull();
        });
    }
    @Test void nullChannelAndInvalidGeneralPublicIdAreHandledWithoutInventingIdentity() {
        message(55,"no-channel",null);root(55,"no-channel",null,false,null,"pending");
        message(55,"invalid-general",7L);root(55,"invalid-general",7L,true,"invalid/path","pending");
        assertThat(service.loadProfileTickets(55)).anySatisfy(row -> assertThat(row.dialogUrl()).isEqualTo("/dialogs?ticketId=no-channel"))
            .anySatisfy(row -> {assertThat(row.ticketId()).isEqualTo("invalid-general");assertThat(row.dialogUrl()).isNull();});
    }
}
