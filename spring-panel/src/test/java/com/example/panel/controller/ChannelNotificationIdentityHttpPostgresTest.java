package com.example.panel.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.service.*;
import com.example.panel.support.PostgresqlIntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest @AutoConfigureMockMvc
class ChannelNotificationIdentityHttpPostgresTest extends PostgresqlIntegrationTestSupport {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ChannelRepository channels;
    @Autowired private AlertQueueService queue;
    @Autowired private NotificationService notices;
    @Autowired private MultibusinessFeatureFlags flags;
    @Autowired private ObjectMapper mapper;
    @Autowired private MockMvc mvc;
    @MockBean private UiEventStreamService stream;
    private String reader,spaced,department;
    private long spacedId,businessA,businessB,channelId;
    private UiEventTicketScope plainOwner,spacedOwner;
    private boolean previousStrict;
    private final List<UiEventTicketScope> roots=new ArrayList<>();

    @BeforeEach void prepare() {
        previousStrict=flags.isStrictReadScope();flags.setStrictReadScope(true);jdbc.update("DELETE FROM notifications");
        reader="channel-r175-"+UUID.randomUUID();spaced=" "+reader+" ";department="team-"+UUID.randomUUID();
        long plainId=account(reader);spacedId=account(spaced);businessA=business();businessB=business();
        jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES (?,?,'VIEWER'),(?,?,'VIEWER')",plainId,businessA,spacedId,businessB);
        channelId=jdbc.queryForObject("INSERT INTO channels(token,channel_name,platform,questions_cfg) VALUES (?,'R175','web','{}') RETURNING id",Long.class,UUID.randomUUID().toString());
        plainOwner=owner(businessA);spacedOwner=owner(businessB);clearInvocations(stream);
    }
    @AfterEach void restore() {
        flags.setStrictReadScope(previousStrict);
        jdbc.update("DELETE FROM notifications WHERE user_identity IN (?,?)",reader,spaced);
        for(var scope:roots) {
            jdbc.update("DELETE FROM business_ticket_participants WHERE ticket_id=?",scope.ticketId());
            jdbc.update("DELETE FROM tickets WHERE ticket_id=?",scope.ticketId());
        }
        channels.deleteById(channelId);
    }

    @ParameterizedTest @ValueSource(strings={"prefix","suffix","both","tab","newline"})
    void saveReloadDispatchAndBellReadsKeepTheLiteralAccount(String variant) throws Exception {
        String identity=switch(variant){case "prefix"->" "+reader;case "suffix"->reader+" ";case "tab"->"\t"+reader+"\t";case "newline"->"\n"+reader+"\r";default->spaced;};
        jdbc.update("UPDATE users SET username=? WHERE id=?",identity,spacedId);spaced=identity;
        save("nested","employees_only");
        Channel reloaded=channels.findById(channelId).orElseThrow();
        assertThat(queue.notifyScopedQueueForNewPublicAppealRecipients(reloaded,spacedOwner,"literal private notice")).containsExactly(spaced);
        assertThat(jdbc.queryForList("SELECT user_identity FROM notifications",String.class)).containsExactly(spaced);
        verify(stream).publishNotificationsChanged(spaced,"notification_created");
        assertThat(notices.findForUser(reader)).isEmpty();assertThat(notices.findForUser(spaced)).hasSize(1);
        mvc.perform(auth(get("/api/notifications"),reader,"PAGE_DIALOGS")).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(auth(get("/api/notifications"),spaced,"PAGE_DIALOGS")).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(auth(post("/api/notifications/read-all"),reader,"PAGE_DIALOGS").with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(0));
        mvc.perform(auth(post("/api/notifications/read-all"),spaced,"PAGE_DIALOGS").with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(1));
    }

    @ParameterizedTest @ValueSource(strings={"nested","direct","legacy"})
    void supportedConfigShapesRoundTripThroughHttpAndTheDatabase(String shape) throws Exception {
        save(shape,"department_except");
        assertThat(queue.notifyScopedQueueForNewPublicAppealRecipients(channels.findById(channelId).orElseThrow(),spacedOwner,"private")).containsExactly(spaced);
        assertThat(jdbc.queryForList("SELECT user_identity FROM notifications",String.class)).containsExactly(spaced);
        verify(stream).publishNotificationsChanged(spaced,"notification_created");
    }

    @Test void configuredForeignAccountCannotRedirectToTheTrimmedAccountWithRead() throws Exception {
        save("nested","employees_only");
        assertThat(queue.notifyScopedQueueForNewPublicAppealRecipients(channels.findById(channelId).orElseThrow(),plainOwner,"foreign private")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications",Long.class)).isZero();verifyNoInteractions(stream);
    }

    @ParameterizedTest @ValueSource(strings={"revoked","disabled","blocked","archived","duplicate","public-id","collision","general"})
    void deliveryRechecksCurrentReadAndFrozenOwnerAfterSavingSettings(String mutation) throws Exception {
        save("nested","employees_only");Channel channel=channels.findById(channelId).orElseThrow();
        assertThat(queue.notifyScopedQueueForNewPublicAppealRecipients(channel,spacedOwner,"first")).containsExactly(spaced);
        jdbc.update("DELETE FROM notifications");clearInvocations(stream);
        switch(mutation) {
            case "revoked"->jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=?",spacedId);
            case "disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?",spacedId);
            case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=?",spacedId);
            case "archived"->jdbc.update("UPDATE businesses SET status='archived' WHERE id=?",businessB);
            case "duplicate"->account(spaced.toUpperCase(Locale.ROOT));
            case "public-id"->{
                // Public IDs are immutable: replace the root through valid DELETE/INSERT operations.
                assertThat(jdbc.update("DELETE FROM tickets WHERE user_id=? AND ticket_id=?",spacedOwner.ticketUserId(),spacedOwner.ticketId())).isEqualTo(1);
                String replacement=UUID.randomUUID().toString().replace("-","");
                jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,channel_id) VALUES (?,?,?,?,?)",spacedOwner.ticketUserId(),spacedOwner.ticketId(),replacement,businessB,channelId);
                assertThat(jdbc.queryForObject("SELECT ticket_public_id FROM tickets WHERE ticket_id=?",String.class,spacedOwner.ticketId())).isEqualTo(replacement).isNotEqualTo(spacedOwner.ticketPublicId());
            }
            case "collision"->{
                // A second root without a channel is valid and still makes the bare ticket ID ambiguous.
                jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES (?,?,?,?)",System.nanoTime(),spacedOwner.ticketId(),UUID.randomUUID().toString().replace("-",""),businessA);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM tickets WHERE ticket_id=?",Long.class,spacedOwner.ticketId())).isEqualTo(2L);
            }
            case "general"->{
                jdbc.update("UPDATE tickets SET business_id=NULL,is_general=TRUE,general_selection_source='MANUAL' WHERE ticket_id=?",spacedOwner.ticketId());
                assertThat(jdbc.queryForObject("SELECT is_general FROM tickets WHERE ticket_id=?",Boolean.class,spacedOwner.ticketId())).isTrue();
            }
        }
        assertThat(queue.notifyScopedQueueForNewPublicAppealRecipients(channel,spacedOwner,"late private")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications",Long.class)).isZero();verifyNoInteractions(stream);
    }

    @Test void channelWritesStillRequireAuthenticationAndCsrf() throws Exception {
        String before=channels.findById(channelId).orElseThrow().getQuestionsCfg();String body=payload("nested","employees_only");
        mvc.perform(patch("/api/channels/"+channelId).contentType(MediaType.APPLICATION_JSON).content(body).with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(auth(patch("/api/channels/"+channelId).contentType(MediaType.APPLICATION_JSON).content(body),reader,"PAGE_SETTINGS")).andExpect(status().isForbidden());
        assertThat(channels.findById(channelId).orElseThrow().getQuestionsCfg()).isEqualTo(before);verifyNoInteractions(stream);
    }

    private void save(String shape,String mode) throws Exception {
        String field=shape.equals("legacy")?"alertQueue":"panelNotifications.routing";
        mvc.perform(auth(patch("/api/channels/"+channelId).contentType(MediaType.APPLICATION_JSON).content(payload(shape,mode)),"channel-admin","PAGE_SETTINGS").with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.channel.questions_cfg."+field+".employeeUsernames[0]").value(spaced))
            .andExpect(jsonPath("$.channel.questions_cfg."+field+".excludeUsernames[0]").value(reader));
        var saved=mapper.readTree(channels.findById(channelId).orElseThrow().getQuestionsCfg());
        var route=shape.equals("legacy")?saved.path("alertQueue"):saved.path("panelNotifications").path("routing");
        assertThat(route.path("employeeUsernames").get(0).asText()).isEqualTo(spaced);
        assertThat(route.path("excludeUsernames").get(0).asText()).isEqualTo(reader);
    }
    private String payload(String shape,String mode) throws Exception {
        Map<String,Object> route=new LinkedHashMap<>(Map.of("enabled",true,"department",department,"targetMode",mode,"deliveryMode","all",
            "employeeUsernames",List.of(spaced.toUpperCase(Locale.ROOT)),"excludeUsernames",List.of(reader.toUpperCase(Locale.ROOT))));
        Object cfg=shape.equals("legacy")?Map.of("alertQueue",route):Map.of("panelNotifications",shape.equals("direct")?route:
            Map.of("routing",route,"events",Map.of("newPublicAppeal",true)));
        return mapper.writeValueAsString(Map.of("questions_cfg",cfg));
    }
    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request,String identity,String authority) {
        return request.with(user(identity).authorities(new SimpleGrantedAuthority(authority)));
    }
    private long account(String username) {return jdbc.queryForObject("INSERT INTO users(username,password,department) VALUES (?,'unused',?) RETURNING id",Long.class,username,department);}
    private long business() {return jdbc.queryForObject("INSERT INTO businesses(code,display_name) VALUES (?,'R175') RETURNING id",Long.class,"r175-"+UUID.randomUUID());}
    private UiEventTicketScope owner(long business) {
        long user=System.nanoTime();String id=" r175/"+UUID.randomUUID()+" ",pub=UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,channel_id) VALUES (?,?,?,?,?)",user,id,pub,business,channelId);
        var scope=new UiEventTicketScope(user,id,pub,"BUSINESS",business);roots.add(scope);return scope;
    }
}
