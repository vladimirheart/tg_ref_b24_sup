package com.example.panel.controller;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.*;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.*;
import org.springframework.web.util.HtmlUtils;

@SpringBootTest @AutoConfigureMockMvc
class BusinessTicketAssignmentHttpPostgresTest {

    @ParameterizedTest @ValueSource(strings={"prefix","suffix","both","tab"})
    void spacedPrincipalCannotUseAnotherOperatorWorkbenchOrCommands(String variant) throws Exception {
        String forged=switch(variant){case "prefix"->" "+actor;case "suffix"->actor+" ";case "tab"->"\t"+actor+"\t";default->" "+actor+" ";};
        long literalId=account(forged);
        if(variant.equals("both"))jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(?,?,'ADMIN')",literalId,other);
        assertThat(service.change(scope,boundActor(),0,targetId,false)).isTrue();
        assertThat(participants.change(scope,boundActor(),0,viewerId,true)).isTrue();
        var assignment=jdbc.queryForList("SELECT * FROM ticket_responsibles WHERE ticket_id=?",raw);
        var roster=jdbc.queryForList("SELECT * FROM business_ticket_participants WHERE ticket_id=?",raw);
        mvc.perform(auth(get(url),forged,"PAGE_DIALOGS","ROLE_ADMIN")).andExpect(status().isNotFound());
        for(String action:List.of("take","assign"))
            mvc.perform(auth(command(action,1),forged,"PAGE_DIALOGS","ROLE_ADMIN").with(csrf())).andExpect(status().isNotFound());
        for(String action:List.of("add","remove"))
            mvc.perform(auth(memberCommand(action,"1",viewerId),forged,"PAGE_DIALOGS","ROLE_ADMIN").with(csrf())).andExpect(status().isNotFound());
        for(String suffix:List.of("/participants","/assignments/history","/work/history"))
            mvc.perform(auth(get("/api/business-scope/tickets/"+publicId+suffix),forged,"PAGE_DIALOGS")).andExpect(status().isNotFound());
        assertThat(jdbc.queryForList("SELECT * FROM ticket_responsibles WHERE ticket_id=?",raw)).isEqualTo(assignment);
        assertThat(jdbc.queryForList("SELECT * FROM business_ticket_participants WHERE ticket_id=?",raw)).isEqualTo(roster);
        assertThat(events()).isEqualTo(1);assertThat(memberEvents()).isEqualTo(1);assertThat(revision()).isEqualTo(1);assertThat(outbox()).isEqualTo(2);
    }
    @Test void literalSpacedPrincipalKeepsOwnBusinessFormsAndNumericActorAudit() throws Exception {
        String original=actor,spaced=" "+actor+" ";jdbc.update("UPDATE users SET username=? WHERE id=?",spaced,actorId);
        long decoy=account(original);jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(?,?,'ADMIN')",decoy,other);
        mvc.perform(auth(get(url),spaced,"PAGE_DIALOGS")).andExpect(status().isOk());
        mvc.perform(auth(get(url),original,"PAGE_DIALOGS")).andExpect(status().isNotFound());
        mvc.perform(auth(command("take",0),spaced,"PAGE_DIALOGS").with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(auth(memberCommand("add","0",viewerId),spaced,"PAGE_DIALOGS").with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForMap("SELECT actor_user_id,assignee_user_id,actor,assignee FROM business_ticket_assignment_events WHERE ticket_id=?",raw))
            .containsEntry("actor_user_id",actorId).containsEntry("assignee_user_id",actorId).containsEntry("actor",spaced).containsEntry("assignee",spaced);
        assertThat(jdbc.queryForMap("SELECT actor_user_id,actor FROM business_ticket_participant_events WHERE ticket_id=?",raw))
            .containsEntry("actor_user_id",actorId).containsEntry("actor",spaced);
    }

    @Test void savedCloseReopenAndCategoryClearReachPagedHistoryWithImmutableSnapshots() throws Exception {
        jdbc.update("INSERT INTO ticket_categories(ticket_id,category) VALUES(?,'Legacy secret')",raw);
        var old=jdbc.queryForList("SELECT * FROM ticket_categories WHERE ticket_id=?",raw);
        mvc.perform(auth(workCommand("categories",0,"open","Old"),actor,"PAGE_DIALOGS").with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(auth(workCommand("close",1,"open","Billing\n<script>Category</script>"),actor,"PAGE_DIALOGS").with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(auth(workCommand("reopen",2,"resolved",""),actor,"PAGE_DIALOGS").with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(auth(workCommand("categories",3,"pending",""),actor,"PAGE_DIALOGS").with(csrf())).andExpect(status().is3xxRedirection());
        String path="/api/business-scope/tickets/"+publicId+"/work/history";
        var response=mvc.perform(auth(get(path).param("limit","2"),viewer,"PAGE_DIALOGS"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(header().string("X-Content-Type-Options","nosniff"))
            .andExpect(jsonPath("$.owner.ticketId").value(raw)).andExpect(jsonPath("$.owner.userId").value("901"))
            .andExpect(jsonPath("$.revision").value("4")).andExpect(jsonPath("$.latestEventRevision").value("4"))
            .andExpect(jsonPath("$.events[0].action").value("categories")).andExpect(jsonPath("$.events[0].categories",hasSize(0)))
            .andExpect(jsonPath("$.events[0].previousCategories",contains("Billing","<script>Category</script>")))
            .andExpect(jsonPath("$.events[1].action").value("reopen")).andExpect(jsonPath("$.events[1].fromStatus").value("resolved"))
            .andExpect(jsonPath("$.events[1].toStatus").value("pending")).andExpect(jsonPath("$.nextBeforeRevision").value("3"))
            .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("Legacy secret");
        mvc.perform(auth(get(path).param("before","3").param("limit","2"),viewer,"PAGE_DIALOGS"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.events[0].action").value("close"))
            .andExpect(jsonPath("$.events[0].previousCategories",org.hamcrest.Matchers.contains("Old")))
            .andExpect(jsonPath("$.events[0].categories",contains("Billing","<script>Category</script>")))
            .andExpect(jsonPath("$.events[0].actorId").value(Long.toString(actorId)))
            .andExpect(jsonPath("$.events[1].action").value("categories")).andExpect(jsonPath("$.hasMore").value(false));
        assertThat(jdbc.queryForList("SELECT * FROM ticket_categories WHERE ticket_id=?",raw)).isEqualTo(old);
        assertThat(revision()).isZero();assertThat(events()).isZero();assertThat(memberEvents()).isZero();assertThat(outbox()).isEqualTo(4);
        jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=?",viewerId);
        mvc.perform(auth(get(path),viewer,"PAGE_DIALOGS")).andExpect(status().isNotFound());
    }
    @Test void workHistoryReadSurvivesRawCollisionAndDoesNotFollowBusinessTransfer() throws Exception {
        assertThat(work.change(scope,actor,0,"open","categories",List.of("Own"))).isTrue();
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES(902,?,?,?)",raw,UUID.randomUUID().toString().replace("-",""),other);
        String path="/api/business-scope/tickets/"+publicId+"/work/history";
        mvc.perform(auth(get(path),viewer,"PAGE_DIALOGS")).andExpect(status().isOk()).andExpect(jsonPath("$.events",hasSize(1)));
        jdbc.update("UPDATE tickets SET business_id=? WHERE user_id=901 AND ticket_id=?",other,raw);
        mvc.perform(auth(get(path),viewer,"PAGE_DIALOGS")).andExpect(status().isNotFound());
        mvc.perform(auth(get(path),foreign,"PAGE_DIALOGS")).andExpect(status().isOk()).andExpect(jsonPath("$.events",hasSize(0)));
    }
    @Test void manualAndSlaWritersReachPrivatePagedReadWithoutInternalRouteOrLegacySnapshots() throws Exception {
        mvc.perform(auth(command("take",0),actor,"PAGE_DIALOGS").with(csrf())).andExpect(status().is3xxRedirection());
        long channel=jdbc.queryForObject("INSERT INTO channels(token,channel_name,platform) VALUES(?,'Telegram','telegram') RETURNING id",Long.class,UUID.randomUUID().toString());
        jdbc.update("UPDATE tickets SET channel_id=? WHERE user_id=901 AND ticket_id=?",channel,raw);
        assertThat(sla.assign(new DialogLookupReadService.ScopedSlaDialog(null,scope,channel,1,actor),target,"scheduler","Secret source","Secret route")).isTrue();
        String path="/api/business-scope/tickets/"+publicId+"/assignments/history";
        var first=mvc.perform(auth(get(path).param("limit","1"),viewer,"PAGE_DIALOGS"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(header().string("X-Content-Type-Options","nosniff"))
            .andExpect(jsonPath("$.owner.ticketId").value(raw)).andExpect(jsonPath("$.owner.userId").value("901"))
            .andExpect(jsonPath("$.revision").value("2")).andExpect(jsonPath("$.latestEventRevision").value("2"))
            .andExpect(jsonPath("$.events[0].origin").value("SLA")).andExpect(jsonPath("$.events[0].assignee").value(target))
            .andExpect(jsonPath("$.events[0].assigneeId").isEmpty()).andExpect(jsonPath("$.nextBeforeRevision").value("2"))
            .andReturn().getResponse().getContentAsString();
        assertThat(first).doesNotContain("Secret source","Secret route","previousResponsible","source","route");
        mvc.perform(auth(get(path).param("before","2").param("limit","1"),viewer,"PAGE_DIALOGS"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.events[0].origin").value("MANUAL"))
            .andExpect(jsonPath("$.events[0].action").value("take")).andExpect(jsonPath("$.events[0].actorId").value(Long.toString(actorId)))
            .andExpect(jsonPath("$.hasMore").value(false));
        assertThat(revision()).isEqualTo(2);assertThat(events()).isEqualTo(1);assertThat(outbox()).isEqualTo(1);
        jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=?",viewerId);
        mvc.perform(auth(get(path),viewer,"PAGE_DIALOGS")).andExpect(status().isNotFound());
    }
    @Test void assignmentAuditReadSurvivesRawCollisionAndCannotFollowOwnerTransfer() throws Exception {
        assertThat(service.change(scope,boundActor(),0,targetId,false)).isTrue();
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES(902,?,?,?)",raw,UUID.randomUUID().toString().replace("-",""),other);
        String path="/api/business-scope/tickets/"+publicId+"/assignments/history";
        mvc.perform(auth(get(path),viewer,"PAGE_DIALOGS")).andExpect(status().isOk()).andExpect(jsonPath("$.events",hasSize(1)));
        jdbc.update("UPDATE tickets SET business_id=? WHERE user_id=901 AND ticket_id=?",other,raw);
        mvc.perform(auth(get(path),viewer,"PAGE_DIALOGS")).andExpect(status().isNotFound());
        mvc.perform(auth(get(path),foreign,"PAGE_DIALOGS")).andExpect(status().isOk()).andExpect(jsonPath("$.events",hasSize(0)));
    }
    @Test void persistedParticipantRosterAndHistoryReachReadOnlyCardApisWithoutLegacyFallback() throws Exception {
        jdbc.update("INSERT INTO ticket_participants(ticket_id,username,added_by) VALUES(?,'Legacy secret','old')",raw);
        var old=jdbc.queryForList("SELECT * FROM ticket_participants WHERE ticket_id=?",raw);
        mvc.perform(auth(memberCommand("add","0",viewerId),actor,"PAGE_DIALOGS").with(csrf())).andExpect(status().is3xxRedirection());
        String path="/api/business-scope/tickets/"+publicId+"/participants";
        mvc.perform(auth(get(path),viewer,"PAGE_DIALOGS"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.owner.userId").value("901")).andExpect(jsonPath("$.owner.ticketId").value(raw))
            .andExpect(jsonPath("$.revision").value("1")).andExpect(jsonPath("$.members",hasSize(1)))
            .andExpect(jsonPath("$.members[0].id").value(Long.toString(viewerId)))
            .andExpect(jsonPath("$.members[0].username").value(viewer)).andExpect(jsonPath("$.members[0].eligible").value(true));
        mvc.perform(auth(get("/api/business-scope/tickets/"+publicId+"/workspace"),viewer,"PAGE_DIALOGS"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.participants",hasSize(0)));
        mvc.perform(auth(memberCommand("remove","1",viewerId),actor,"PAGE_DIALOGS").with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(auth(get(path+"/history").param("limit","1"),viewer,"PAGE_DIALOGS"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value("2"))
            .andExpect(jsonPath("$.events[0].action").value("remove")).andExpect(jsonPath("$.nextBeforeRevision").value("2"));
        mvc.perform(auth(get(path+"/history").param("before","2").param("limit","1"),viewer,"PAGE_DIALOGS"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.events[0].action").value("add"))
            .andExpect(jsonPath("$.hasMore").value(false));
        assertThat(jdbc.queryForList("SELECT * FROM ticket_participants WHERE ticket_id=?",raw)).isEqualTo(old);
    }
    @Test void participantReadSurvivesRawCollisionButCannotFollowABusinessTransfer() throws Exception {
        assertThat(participants.change(scope,boundActor(),0,targetId,true)).isTrue();
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES(902,?,?,?)",raw,UUID.randomUUID().toString().replace("-",""),other);
        String path="/api/business-scope/tickets/"+publicId+"/participants";
        mvc.perform(auth(get(path),viewer,"PAGE_DIALOGS")).andExpect(status().isOk()).andExpect(jsonPath("$.members",hasSize(1)));
        mvc.perform(auth(get(path+"/history"),viewer,"PAGE_DIALOGS")).andExpect(status().isOk()).andExpect(jsonPath("$.events",hasSize(1)));
        jdbc.update("UPDATE tickets SET business_id=? WHERE user_id=901 AND ticket_id=?",other,raw);
        mvc.perform(auth(get(path),viewer,"PAGE_DIALOGS")).andExpect(status().isNotFound());
        mvc.perform(auth(get(path),foreign,"PAGE_DIALOGS")).andExpect(status().isOk()).andExpect(jsonPath("$.members",hasSize(0)));
        mvc.perform(auth(get(path+"/history"),foreign,"PAGE_DIALOGS")).andExpect(status().isOk()).andExpect(jsonPath("$.events",hasSize(0)));
    }
    @Test void participantReadUsesNumericMemberAfterRenameAndCurrentReaderGrant() throws Exception {
        assertThat(participants.change(scope,boundActor(),0,targetId,true)).isTrue();
        jdbc.update("UPDATE users SET username=? WHERE id=?","<script>"+target+"</script>",targetId);
        jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=?",targetId);
        String path="/api/business-scope/tickets/"+publicId+"/participants";
        mvc.perform(auth(get(path),viewer,"PAGE_DIALOGS"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.members[0].snapshot").value(target))
            .andExpect(jsonPath("$.members[0].username").value("<script>"+target+"</script>"))
            .andExpect(jsonPath("$.members[0].eligible").value(false));
        jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=?",viewerId);
        mvc.perform(auth(get(path),viewer,"PAGE_DIALOGS")).andExpect(status().isNotFound());
        mvc.perform(auth(get(path+"/history"),viewer,"PAGE_DIALOGS")).andExpect(status().isNotFound());
    }
    @Test void participantApiRequiresPageAndFoundationAndValidatesCursor() throws Exception {
        String path="/api/business-scope/tickets/"+publicId+"/participants";
        mvc.perform(auth(get(path),viewer,"PAGE_SETTINGS")).andExpect(status().isForbidden());
        mvc.perform(auth(get(path+"/history").param("before","0"),viewer,"PAGE_DIALOGS")).andExpect(status().isBadRequest());
        mvc.perform(auth(get(path+"/history").param("limit","101"),viewer,"PAGE_DIALOGS")).andExpect(status().isBadRequest());
        flags.getFoundation().setEnabled(false);
        mvc.perform(auth(get(path),viewer,"PAGE_DIALOGS")).andExpect(status().isNotFound());
    }
    @Test void savedCategoriesReachListCardNestedReadsSearchExportAndCategorySummary() throws Exception {
        jdbc.update("INSERT INTO ticket_categories(ticket_id,category) VALUES(?,'Legacy secret')",raw);
        mvc.perform(auth(workCommand("categories",0,"open"," Delivery \nDelivery\n<script>Category</script>"),actor,"PAGE_DIALOGS").with(csrf()))
            .andExpect(status().is3xxRedirection());
        for(String suffix:List.of("","/history","/answers","/workspace","/spans")) {
            String jsonPath=suffix.isEmpty()?"$.categories":"$.ticket.categories";
            mvc.perform(auth(get("/api/business-scope/tickets/"+publicId+suffix),viewer,"PAGE_DIALOGS"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath(jsonPath,contains("Delivery","<script>Category</script>")));
        }
        for(String suffix:List.of("","/page","/export")) {
            mvc.perform(auth(get("/api/business-scope/tickets"+suffix).param("business_id",Long.toString(business))
                .param("category","Delivery").param("q","category"),viewer,"PAGE_DIALOGS"))
                .andExpect(status().isOk()).andExpect(jsonPath(suffix.equals("/page")?"$.tickets[0].categories":"$[0].categories",
                    contains("Delivery","<script>Category</script>")));
            mvc.perform(auth(get("/api/business-scope/tickets"+suffix).param("business_id",Long.toString(business))
                .param("category","Legacy secret"),viewer,"PAGE_DIALOGS"))
                .andExpect(status().isOk()).andExpect(jsonPath(suffix.equals("/page")?"$.tickets":"$",hasSize(0)));
        }
        mvc.perform(auth(get("/api/business-scope/tickets/categories").param("business_id",Long.toString(business)),viewer,"PAGE_DIALOGS"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$",hasSize(2))).andExpect(jsonPath("$[1].category").value("Delivery"))
            .andExpect(jsonPath("$[1].total").value(1));
        assertThat(jdbc.queryForList("SELECT category FROM ticket_categories WHERE ticket_id=?",String.class,raw)).containsExactly("Legacy secret");
    }
    @Test void collidingRawIdStillReadsOnlyCanonicalCategoriesThroughHttp() throws Exception {
        assertThat(work.change(scope,actor,0,"open","categories",List.of("Own"))).isTrue();
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,business_categories) VALUES(902,?,?,?,'[\"Foreign\"]')",
            raw,UUID.randomUUID().toString().replace("-",""),other);
        mvc.perform(auth(get("/api/business-scope/tickets/"+publicId+"/workspace"),viewer,"PAGE_DIALOGS"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.relationVerified").value(false))
            .andExpect(jsonPath("$.categories",org.hamcrest.Matchers.contains("Own"))).andExpect(jsonPath("$.participants",hasSize(0)));
        mvc.perform(auth(get("/api/business-scope/tickets/export").param("business_id",Long.toString(business)),viewer,"PAGE_DIALOGS"))
            .andExpect(status().isOk()).andExpect(jsonPath("$",hasSize(1))).andExpect(jsonPath("$[0].categories",org.hamcrest.Matchers.contains("Own")));
    }
    @Test void categoryReadsAndSummaryRecheckCurrentBusinessAfterOwnerTransfer() throws Exception {
        assertThat(work.change(scope,actor,0,"open","categories",List.of("Old owner"))).isTrue();
        jdbc.update("UPDATE tickets SET business_id=? WHERE ticket_id=?",other,raw);
        mvc.perform(auth(get("/api/business-scope/tickets/"+publicId),viewer,"PAGE_DIALOGS")).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/business-scope/tickets/"+publicId),foreign,"PAGE_DIALOGS"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.categories",hasSize(0)));
        mvc.perform(auth(get("/api/business-scope/tickets/categories").param("business_id",Long.toString(other)),foreign,"PAGE_DIALOGS"))
            .andExpect(status().isOk()).andExpect(jsonPath("$",hasSize(0)));
    }
    @Test void revokedBusinessReaderCannotKeepCategoryReadsThroughAnOldSession() throws Exception {
        assertThat(work.change(scope,actor,0,"open","categories",List.of("Private"))).isTrue();
        jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=?",viewerId);
        mvc.perform(auth(get("/api/business-scope/tickets/"+publicId),viewer,"PAGE_DIALOGS")).andExpect(status().isNotFound());
        for(String suffix:List.of("","/page","/export","/categories"))
            mvc.perform(auth(get("/api/business-scope/tickets"+suffix).param("business_id",Long.toString(business)),viewer,"PAGE_DIALOGS"))
                .andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/business-scope/tickets/categories").param("business_id","all"),viewer,"PAGE_DIALOGS"))
            .andExpect(status().isOk()).andExpect(jsonPath("$",hasSize(0)));
    }
    @ParameterizedTest @ValueSource(strings={""," ","bad\n","bad\u0001"})
    void invalidCategoryFilterCannotBecomeAnUnfilteredRead(String invalid) throws Exception {
        for(String suffix:List.of("","/page","/export"))
            mvc.perform(auth(get("/api/business-scope/tickets"+suffix).param("business_id",Long.toString(business))
                .param("category",invalid),viewer,"PAGE_DIALOGS")).andExpect(status().isBadRequest());
    }
    @SpyBean BusinessTicketWorkService work;
    private MockHttpServletRequestBuilder workCommand(String action,long revision,String status,String categories) {
        return post(url+"/lifecycle/"+action).param("ownerId","901").param("rawId",raw).param("businessId",Long.toString(business))
            .param("workRevision",Long.toString(revision)).param("expectedStatus",status).param("categoryText",categories);
    }
    @Test void renderedWorkFormsSaveCloseReopenReloadAndPreserveLegacyChildren() throws Exception {
        jdbc.update("INSERT INTO ticket_categories(ticket_id,category) VALUES(?,'Legacy untouched')",raw);
        jdbc.update("INSERT INTO ticket_active(ticket_id,user_identity) VALUES(?,'original')",raw);
        var legacy=jdbc.queryForList("SELECT * FROM ticket_categories WHERE ticket_id=?",raw);
        var active=jdbc.queryForList("SELECT * FROM ticket_active WHERE ticket_id=?",raw);
        var opened=mvc.perform(auth(get(url),actor,"PAGE_DIALOGS")).andExpect(status().isOk()).andReturn();
        String html=opened.getResponse().getContentAsString();
        assertThat(hidden(html,"workRevision")).isEqualTo("0");
        assertThat(html).contains("businessCategories","lifecycle/close","lifecycle/categories");
        mvc.perform(auth(post(url+"/lifecycle/categories").param("ownerId",hidden(html,"ownerId")).param("rawId",hidden(html,"rawId"))
            .param("businessId",hidden(html,"businessId")).param("workRevision",hidden(html,"workRevision")).param("expectedStatus",hidden(html,"expectedStatus"))
            .param("categoryText"," Billing \nBilling\nPayment").param("_csrf",hidden(html,"_csrf")).cookie(opened.getResponse().getCookies()),actor,"PAGE_DIALOGS"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(url));
        assertThat(work.find(publicId,actor).orElseThrow().categories()).containsExactly("Billing","Payment");
        mvc.perform(auth(workCommand("close",1,"open","Billing\nPayment"),actor,"PAGE_DIALOGS").with(csrf())).andExpect(status().is3xxRedirection());
        var closed=mvc.perform(auth(get(url),actor,"PAGE_DIALOGS")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(closed).contains("lifecycle/reopen").doesNotContain("name=\"categoryText\"");
        mvc.perform(auth(workCommand("reopen",2,"resolved",""),actor,"PAGE_DIALOGS").with(csrf())).andExpect(status().is3xxRedirection());
        var view=work.find(publicId,actor).orElseThrow();assertThat(view.status()).isEqualTo("pending");assertThat(view.revision()).isEqualTo(3);
        assertThat(view.categories()).containsExactly("Billing","Payment");
        assertThat(work.recent(scope,viewer)).extracting(BusinessTicketWorkService.Event::action).containsExactly("reopen","close","categories");
        assertThat(jdbc.queryForList("SELECT * FROM ticket_categories WHERE ticket_id=?",raw)).isEqualTo(legacy);
        assertThat(jdbc.queryForList("SELECT * FROM ticket_active WHERE ticket_id=?",raw)).isEqualTo(active);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pending_feedback_requests WHERE ticket_id=?",Long.class,raw)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_responsibles WHERE ticket_id=?",Long.class,raw)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dialog_action_audit WHERE ticket_id=?",Long.class,raw)).isZero();
        assertThat(events()).isZero();assertThat(memberEvents()).isZero();assertThat(revision()).isZero();assertThat(outbox()).isEqualTo(3);
    }
    @ParameterizedTest @ValueSource(strings={"close","reopen","categories"})
    void lifecycleRequiresCsrfAndPageAndCurrentBusinessOperate(String action) throws Exception {
        String status=action.equals("reopen")?"resolved":"open";
        jdbc.update("UPDATE tickets SET status=? WHERE ticket_id=?",status,raw);
        long rev=work.find(publicId,actor).orElseThrow().revision();
        mvc.perform(auth(workCommand(action,rev,status,action.equals("reopen")?"":"New"),actor,"PAGE_DIALOGS"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/error/403"));
        mvc.perform(auth(workCommand(action,rev,status,action.equals("reopen")?"":"New"),actor,"ROLE_ADMIN").with(csrf()))
            .andExpect(status().isForbidden());
        mvc.perform(auth(workCommand(action,rev,status,action.equals("reopen")?"":"New"),viewer,"PAGE_DIALOGS","ROLE_ADMIN").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(auth(workCommand(action,rev,status,action.equals("reopen")?"":"New"),foreign,"PAGE_DIALOGS","ROLE_ADMIN").with(csrf())).andExpect(status().isNotFound());
        var read=mvc.perform(auth(get(url),viewer,"PAGE_DIALOGS")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(read).doesNotContain("name=\"workRevision\"");
        assertThat(work.recent(scope,actor)).isEmpty();assertThat(outbox()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"owner","raw","business","revision","status"})
    void forgedLifecycleFormCannotRetargetOrUseAnOldState(String field) throws Exception {
        var command=post(url+"/lifecycle/close").param("ownerId",field.equals("owner")?"902":"901")
            .param("rawId",field.equals("raw")?raw.trim():raw).param("businessId",Long.toString(field.equals("business")?other:business))
            .param("workRevision",field.equals("revision")?"1":"0").param("expectedStatus",field.equals("status")?"pending":"open").param("categoryText","New");
        mvc.perform(auth(command,actor,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());
        assertThat(work.recent(scope,actor)).isEmpty();assertThat(outbox()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"bad","9223372036854775808","-1"})
    void malformedWorkRevisionCannotWrite(String revision) throws Exception {
        var command=post(url+"/lifecycle/close").param("ownerId","901").param("rawId",raw).param("businessId",Long.toString(business))
            .param("workRevision",revision).param("expectedStatus","open").param("categoryText","New");
        mvc.perform(auth(command,actor,"PAGE_DIALOGS").with(csrf()))
            .andExpect(revision.equals("-1")?status().isConflict():status().isBadRequest());
        assertThat(work.recent(scope,actor)).isEmpty();assertThat(outbox()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"revoke","viewer","disabled","blocked","archived","owner","collision","general","unresolved","status","category","flag"})
    void lifecycleServiceRechecksAFormAuthorizedEarlierInTheController(String mutation) throws Exception {
        var once=new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(call->{
            if(once.compareAndSet(false,true))switch(mutation) {
                case "revoke"->jdbc.update("DELETE FROM business_memberships WHERE user_id=?",actorId);
                case "viewer"->jdbc.update("UPDATE business_memberships SET role_code='VIEWER' WHERE user_id=?",actorId);
                case "disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?",actorId);
                case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=?",actorId);
                case "archived"->jdbc.update("UPDATE businesses SET status='archived' WHERE id=?",business);
                case "owner"->jdbc.update("UPDATE tickets SET business_id=? WHERE ticket_id=?",other,raw);
                case "collision"->jdbc.update("INSERT INTO tickets(user_id,ticket_id,business_id) VALUES(902,?,?)",raw,other);
                case "general"->jdbc.update("UPDATE tickets SET business_id=NULL,is_general=TRUE,general_selection_source='MANUAL' WHERE ticket_id=?",raw);
                case "unresolved"->jdbc.update("UPDATE tickets SET business_id=NULL WHERE ticket_id=?",raw);
                case "status"->jdbc.update("UPDATE tickets SET status='pending' WHERE ticket_id=?",raw);
                case "category"->jdbc.update("UPDATE tickets SET business_categories='[\"changed\"]' WHERE ticket_id=?",raw);
                case "flag"->flags.setUiSelector(false);
            }
            return call.callRealMethod();
        }).when(work).change(org.mockito.ArgumentMatchers.any(UiEventTicketScope.class),anyString(),anyLong(),anyString(),anyString(),anyList());
        mvc.perform(auth(workCommand("close",0,"open","New"),actor,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());
        assertThat(once).isTrue();assertThat(jdbc.queryForObject("SELECT count(*) FROM business_ticket_work_events WHERE ticket_id=?",Long.class,raw)).isZero();
        assertThat(outbox()).isZero();
    }
    @Test void categoryMarkupIsEscapedOnReloadAndUnicodeLengthUsesCodePoints() throws Exception {
        String markup="<img src=x onerror=alert(1)>";
        String unicode="😀".repeat(160);
        assertThat(work.change(scope,actor,0,"open","categories",List.of(markup,unicode))).isTrue();
        String html=mvc.perform(auth(get(url),viewer,"PAGE_DIALOGS")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("&lt;img").doesNotContain(markup);
        assertThat(work.find(publicId,viewer).orElseThrow().categories()).containsExactly(markup,unicode);
    }
    @ParameterizedTest @ValueSource(strings={"empty","too-long","too-many","closed-categories"})
    void invalidWorkCategoryFormCannotWrite(String invalid) throws Exception {
        String values=invalid.equals("too-long")?"a".repeat(161):invalid.equals("too-many")?String.join("\n",java.util.stream.IntStream.range(0,51).mapToObj(i->"Category "+i).toList()):"";
        String action="close",status="open";long rev=0;
        if(invalid.equals("closed-categories")){jdbc.update("UPDATE tickets SET status='closed' WHERE ticket_id=?",raw);action="categories";status="closed";rev=work.find(publicId,actor).orElseThrow().revision();}
        mvc.perform(auth(workCommand(action,rev,status,values),actor,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());
        assertThat(work.recent(scope,actor)).isEmpty();assertThat(outbox()).isZero();
    }
    @Test void workViewCannotMixTwoBusinessOwnersAcrossSequentialLookups() throws Exception {
        jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(?,?,'VIEWER')",actorId,other);
        var once=new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(call->{Object result=call.callRealMethod();if(once.compareAndSet(false,true))jdbc.update("UPDATE tickets SET business_id=? WHERE ticket_id=?",other,raw);return result;})
            .when(service).find(publicId,actor);
        String html=mvc.perform(auth(get(url),actor,"PAGE_DIALOGS")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(once).isTrue();assertThat(html).contains("Операции недоступны").doesNotContain("name=\"workRevision\"");
        assertThat(outbox()).isZero();
    }
    @Test void workHistoryDoesNotFollowBusinessOrNewGenerationAndDoesNotGrantRights() {
        assertThat(work.change(scope,actor,0,"open","close",List.of("New"))).isTrue();
        assertThat(work.recent(scope,viewer)).hasSize(1);
        jdbc.update("UPDATE tickets SET business_id=? WHERE ticket_id=?",other,raw);
        assertThat(work.find(publicId,foreign).orElseThrow().categories()).isEmpty();
        assertThat(work.recent(new UiEventTicketScope(901L,raw,publicId,"BUSINESS",other),foreign)).isEmpty();
        assertThat(work.recent(scope,actor)).isEmpty();
        jdbc.update("UPDATE tickets SET business_id=? WHERE ticket_id=?",business,raw);
        jdbc.update("DELETE FROM tickets WHERE ticket_id=?",raw);
        String recreated=UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,status) VALUES(901,?,?,?,'open')",raw,recreated,business);
        assertThat(work.recent(new UiEventTicketScope(901L,raw,recreated,"BUSINESS",business),actor)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_ticket_work_events WHERE ticket_id=?",Long.class,raw)).isEqualTo(1);
    }
    @MockBean BotProcessService botProcessService;
    @MockBean(name="taskScheduler") TaskScheduler taskScheduler;
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry registry) throws Exception {
        var ds=(DriverManagerDataSource)PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("business_assignment_http").getDataSource();
        var config=Files.createTempDirectory("r159-config-");var storage=Files.createTempDirectory("r159-storage-");
        registry.add("app.datasource.mode",()->"postgresql");registry.add("spring.datasource.url",ds::getUrl);
        registry.add("spring.datasource.username",ds::getUsername);registry.add("spring.datasource.password",ds::getPassword);
        registry.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        registry.add("spring.flyway.locations",()->"classpath:db/migration/postgresql");
        registry.add("shared-config.dir",config::toString);
        for(String key:List.of("attachments","knowledge-base","passport-photos","avatars"))
            registry.add("app.storage."+key,()->storage.resolve(key).toString());
        registry.add("app.storage.object.required-for-postgresql",()->"false");
        registry.add("app.coordination.required-for-postgresql",()->"false");
        registry.add("app.bots.auto-start-enabled",()->"false");
        registry.add("app.bots.internal-api.token",()->"r159-test-internal-bot-api-token");
        registry.add("app.security.remember-me-key",()->"r159-test-remember-me-key");
        registry.add("app.security.bootstrap-admin.username",()->"r159_test_bootstrap_admin");
        registry.add("app.security.bootstrap-admin.password",()->"r159-test-bootstrap-password");
        registry.add("monitoring.credentials.master-key",()->"r159-test-monitoring-master-key");
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired MultibusinessFeatureFlags flags;
    @SpyBean BusinessTicketAssignmentService service;
    @Autowired ScopedSlaAssignmentService sla;
    @SpyBean DialogResponsibilityService legacyAssignments;
    @SpyBean BusinessTicketParticipantService participants;
    @Autowired NotificationService notices;
    @Autowired UiEventOutboxAppendService outboxIds;
    private long business,other,actorId,targetId,viewerId,foreignId;
    private String actor,target,viewer,foreign,raw,publicId,url;
    private UiEventTicketScope scope;
    @BeforeEach void fixture(){
        assertThat(jdbc.queryForObject("SELECT current_schema()",String.class)).startsWith("business_assignment_http_");
        flags.getFoundation().setEnabled(true);flags.setUiSelector(true);flags.setStrictReadScope(true);
        String suffix=UUID.randomUUID().toString().replace("-","");
        business=jdbc.queryForObject("INSERT INTO businesses(code,display_name) VALUES(?,'Same label') RETURNING id",Long.class,suffix);
        other=jdbc.queryForObject("INSERT INTO businesses(code,display_name) VALUES(?,'Same label') RETURNING id",Long.class,suffix+"-b");
        actor="operator-"+suffix;target="target-"+suffix;viewer="viewer-"+suffix;foreign="foreign-"+suffix;
        actorId=account(actor);targetId=account(target);viewerId=account(viewer);foreignId=account(foreign);
        jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(?,?,'OPERATOR'),(?,?,'OPERATOR'),(?,?,'VIEWER'),(?,?,'ADMIN')",
            actorId,business,targetId,business,viewerId,business,foreignId,other);
        raw=" Raw/?=поле "+suffix+" ";publicId=suffix;url="/business-dialog-assignment/"+publicId;
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,status) VALUES(901,?,?,?,'open')",raw,publicId,business);
        scope=new UiEventTicketScope(901L,raw,publicId,"BUSINESS",business);
    }
    private MockHttpServletRequestBuilder memberCommand(String action,String revision,long id){
        return post(url+"/participants/"+action).param("ownerId","901").param("rawId",raw).param("businessId",Long.toString(business))
            .param("participantRevision",revision).param("memberId",Long.toString(id));
    }
    private long memberEvents(){return jdbc.queryForObject("SELECT count(*) FROM business_ticket_participant_events WHERE ticket_id=?",Long.class,raw);}
    @Test void renderedMemberFormAddsViewerReloadsRemovesAndDoesNotTouchLegacyAssignmentOrAi() throws Exception {
        jdbc.update("INSERT INTO ticket_participants(ticket_id,username,added_by) VALUES(?,?,'old')",raw,target);
        var oldRows=jdbc.queryForList("SELECT * FROM ticket_participants WHERE ticket_id=?",raw);
        var opened=mvc.perform(auth(get(url),actor,"PAGE_DIALOGS")).andExpect(status().isOk()).andReturn();
        var html=opened.getResponse().getContentAsString();
        assertThat(hidden(html,"participantRevision")).isEqualTo("0");
        assertThat(participants.members(scope,actor)).isEmpty();
        mvc.perform(auth(post(url+"/participants/add").param("ownerId",hidden(html,"ownerId")).param("rawId",hidden(html,"rawId"))
            .param("businessId",hidden(html,"businessId")).param("participantRevision",hidden(html,"participantRevision"))
            .param("memberId",Long.toString(viewerId)).param("_csrf",hidden(html,"_csrf")).cookie(opened.getResponse().getCookies()),actor,"PAGE_DIALOGS"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(url));
        assertThat(participants.members(scope,viewer)).singleElement().satisfies(m->{assertThat(m.id()).isEqualTo(viewerId);assertThat(m.eligible()).isTrue();});
        assertThat(participants.revision(scope,actor)).contains(1L);
        var event=jdbc.queryForMap("SELECT * FROM business_ticket_participant_events WHERE ticket_id=?",raw);
        assertThat(event).containsEntry("ticket_user_id",901L).containsEntry("ticket_public_id",publicId).containsEntry("business_id",business)
            .containsEntry("member_user_id",viewerId).containsEntry("actor_user_id",actorId).containsEntry("revision",1L).containsEntry("action","add");
        assertThat(jdbc.queryForMap("SELECT * FROM ui_event_outbox WHERE ticket_id=?",raw))
            .containsEntry("event_type","business_participants_changed").containsEntry("ticket_user_id",901L)
            .containsEntry("ticket_public_id",publicId).containsEntry("ticket_business_id",business).containsEntry("ticket_scope_kind","BUSINESS");
        var readHtml=mvc.perform(auth(get(url),viewer,"PAGE_DIALOGS")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(readHtml).contains(viewer).doesNotContain("id=\"participantTarget\"","/participants/remove");
        mvc.perform(auth(memberCommand("remove","1",viewerId),actor,"PAGE_DIALOGS").with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(participants.members(scope,actor)).isEmpty();assertThat(participants.revision(scope,actor)).contains(2L);
        assertThat(memberEvents()).isEqualTo(2);assertThat(outbox()).isEqualTo(2);
        assertThat(events()).isZero();assertThat(revision()).isZero();
        assertThat(jdbc.queryForList("SELECT * FROM ticket_participants WHERE ticket_id=?",raw)).isEqualTo(oldRows);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dialog_action_audit WHERE ticket_id=?",Long.class,raw)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_responsibles WHERE ticket_id=?",Long.class,raw)).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"owner","raw","business"})
    void forgedMemberOwnerFieldsDoNotRetargetTheCommand(String field) throws Exception {
        var command=post(url+"/participants/add").param("ownerId",field.equals("owner")?"902":"901")
            .param("rawId",field.equals("raw")?raw.trim():raw).param("businessId",Long.toString(field.equals("business")?other:business))
            .param("participantRevision","0").param("memberId",Long.toString(viewerId));
        mvc.perform(auth(command,actor,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());
        assertThat(memberEvents()).isZero();assertThat(outbox()).isZero();
    }
    @ParameterizedTest @ValueSource(longs={-1,0,9223372036854775807L})
    void invalidMemberIdDoesNotWrite(long id) {
        assertThat(participants.change(scope,boundActor(),0,id,true)).isFalse();
        assertThat(memberEvents()).isZero();assertThat(outbox()).isZero();
    }
    @Test void participantRevisionIsIndependentOfAssignmentRevision() {
        assertThat(service.change(scope,boundActor(),0,targetId,false)).isTrue();
        assertThat(participants.change(scope,boundActor(),0,viewerId,true)).isTrue();
        assertThat(revision()).isEqualTo(1);assertThat(participants.revision(scope,actor)).contains(1L);
        assertThat(service.change(scope,boundActor(),1,null,true)).isTrue();
        assertThat(participants.change(scope,boundActor(),1,viewerId,false)).isTrue();
        assertThat(revision()).isEqualTo(2);assertThat(events()).isEqualTo(2);assertThat(memberEvents()).isEqualTo(2);
    }
    @Test void rosterBoundIsEnforcedBeforeInsertAndRemovalFreesCapacity() {
        jdbc.update("INSERT INTO users(username,password) SELECT ?||generate_series(1,200),'unused'",actor+"-bound-");
        jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) SELECT id,?,'VIEWER' FROM users WHERE username LIKE ?",business,actor+"-bound-%");
        jdbc.update("INSERT INTO business_ticket_participants(ticket_user_id,ticket_id,ticket_public_id,business_id,member_user_id,member,actor_user_id,actor,revision)"
            +" SELECT 901,?,?,?,id,username,?,?,row_number() OVER(ORDER BY id) FROM users WHERE username LIKE ?",
            raw,publicId,business,actorId,actor,actor+"-bound-%");
        jdbc.update("INSERT INTO business_ticket_participant_events(ticket_user_id,ticket_id,ticket_public_id,business_id,revision,member_user_id,member,actor_user_id,actor,action)"
            +" SELECT ticket_user_id,ticket_id,ticket_public_id,business_id,revision,member_user_id,member,actor_user_id,actor,'add'"
            +" FROM business_ticket_participants WHERE ticket_id=?",raw);
        assertThat(participants.members(scope,actor)).hasSize(200);
        assertThat(participants.change(scope,boundActor(),200,viewerId,true)).isFalse();
        long first=participants.members(scope,actor).get(0).id();
        assertThat(participants.change(scope,boundActor(),200,first,false)).isTrue();
        assertThat(participants.change(scope,boundActor(),201,viewerId,true)).isTrue();
        assertThat(participants.members(scope,actor)).hasSize(200);assertThat(participants.revision(scope,actor)).contains(202L);
    }
    @Test void missingRosterSchemaDeniesWritesAndNeverAdoptsLegacyMembers() {
        jdbc.update("INSERT INTO ticket_participants(ticket_id,username,added_by) VALUES(?,?,'legacy')",raw,target);
        jdbc.execute("ALTER TABLE business_ticket_participants RENAME TO r164_hidden_participants");
        try {
            assertThat(participants.change(scope,boundActor(),0,viewerId,true)).isFalse();
            assertThat(participants.members(scope,actor)).isEmpty();
            assertThat(notices.findScopedDialogRecipients(scope)).isEmpty();
            notices.notifyScopedDialogParticipants(scope,"missing schema","/business-dialogs",null);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE ticket_id=?",Long.class,raw)).isZero();
            assertThat(memberEvents()).isZero();assertThat(outbox()).isZero();
        }finally{jdbc.execute("ALTER TABLE r164_hidden_participants RENAME TO business_ticket_participants");}
    }
    @Test void memberCommandsRequirePageCsrfAndOperateEvenForSystemAdmin() throws Exception {
        mvc.perform(auth(memberCommand("add","0",viewerId),actor,"PAGE_DIALOGS")).andExpect(redirectedUrl("/error/403"));
        mvc.perform(auth(memberCommand("add","0",viewerId),actor,"ROLE_ADMIN").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(auth(memberCommand("add","0",viewerId),viewer,"PAGE_DIALOGS","ROLE_ADMIN").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(auth(memberCommand("add","0",viewerId),foreign,"PAGE_DIALOGS","ROLE_ADMIN").with(csrf())).andExpect(status().isNotFound());
        assertThat(memberEvents()).isZero();assertThat(outbox()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"bad","9223372036854775808","-1","1"})
    void malformedOrStaleMemberRevisionCannotWrite(String revision) throws Exception {
        mvc.perform(auth(memberCommand("add",revision,viewerId),actor,"PAGE_DIALOGS").with(csrf()))
            .andExpect(revision.equals("bad")||revision.equals("9223372036854775808")?status().isBadRequest():status().isConflict());
        assertThat(memberEvents()).isZero();assertThat(outbox()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"target-foreign","target-disabled","target-blocked","target-duplicate","target-revoked","actor-viewer",
        "actor-disabled","actor-blocked","actor-duplicate","actor-revoked","business-archived","owner","recreated","collision","general","closed","flag"})
    void memberWriteRechecksOwnerActorAndReadTarget(String mutation) {
        switch(mutation){
            case "target-foreign"->jdbc.update("UPDATE business_memberships SET business_id=? WHERE user_id=?",other,viewerId);
            case "target-disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?",viewerId);
            case "target-blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=?",viewerId);
            case "target-duplicate"->account(viewer.toUpperCase(Locale.ROOT));
            case "target-revoked"->jdbc.update("DELETE FROM business_memberships WHERE user_id=?",viewerId);
            case "actor-viewer"->jdbc.update("UPDATE business_memberships SET role_code='VIEWER' WHERE user_id=?",actorId);
            case "actor-disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?",actorId);
            case "actor-blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=?",actorId);
            case "actor-duplicate"->account(actor.toUpperCase(Locale.ROOT));
            case "actor-revoked"->jdbc.update("DELETE FROM business_memberships WHERE user_id=?",actorId);
            case "business-archived"->jdbc.update("UPDATE businesses SET status='archived' WHERE id=?",business);
            case "owner"->jdbc.update("UPDATE tickets SET business_id=? WHERE ticket_id=?",other,raw);
            case "recreated"->{jdbc.update("DELETE FROM tickets WHERE ticket_id=?",raw);jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,status) VALUES(901,?,?,?,'open')",raw,UUID.randomUUID().toString().replace("-",""),business);}
            case "collision"->jdbc.update("INSERT INTO tickets(user_id,ticket_id,business_id) VALUES(902,?,?)",raw,other);
            case "general"->jdbc.update("UPDATE tickets SET business_id=NULL,is_general=TRUE,general_selection_source='MANUAL' WHERE ticket_id=?",raw);
            case "closed"->jdbc.update("UPDATE tickets SET status='closed' WHERE ticket_id=?",raw);
            case "flag"->flags.setUiSelector(false);
        }
        assertThat(participants.change(scope,boundActor(),0,viewerId,true)).isFalse();
        assertThat(memberEvents()).isZero();assertThat(outbox()).isZero();assertThat(revision()).isZero();
    }
    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"add,actor","add,target","add,owner","add,collision","remove,actor","remove,owner","remove,collision"})
    void memberFinalSqlRepeatsOwnerAndAuthorityAfterEarlierProof(String action,String mutation) {
        boolean add=action.equals("add");
        if(!add)assertThat(participants.change(scope,boundActor(),0,viewerId,true)).isTrue();
        var before=jdbc.queryForList("SELECT * FROM business_ticket_participants WHERE ticket_id=?",raw);
        var observed=spy(jdbc);
        var fired=new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(call->{
            var creator=call.getArgument(0,org.springframework.jdbc.core.PreparedStatementCreator.class);
            String sql=((org.springframework.jdbc.core.SqlProvider)creator).getSql();
            if((sql.startsWith("INSERT INTO business_ticket_participants")||sql.startsWith("DELETE FROM business_ticket_participants"))
                    &&fired.compareAndSet(false,true)) {
                // Deterministic JDBC-boundary injection in the test transaction, not an unlocked grant simulation.
                if(mutation.equals("actor"))jdbc.update("UPDATE business_memberships SET role_code='VIEWER' WHERE user_id=?",actorId);
                if(mutation.equals("target"))jdbc.update("DELETE FROM business_memberships WHERE user_id=?",viewerId);
                if(mutation.equals("owner"))jdbc.update("UPDATE tickets SET business_id=? WHERE ticket_id=?",other,raw);
                if(mutation.equals("collision"))jdbc.update("INSERT INTO tickets(user_id,ticket_id,business_id) VALUES(902,?,?)",raw,other);
            }
            return call.callRealMethod();
        }).when(observed).update(org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.PreparedStatementCreator.class));
        var writer=new BusinessTicketParticipantService(observed,flags,outboxIds);
        assertThat(writer.change(scope,boundActor(),add?0:1,viewerId,add)).isFalse();
        assertThat(fired).isTrue();
        assertThat(jdbc.queryForList("SELECT * FROM business_ticket_participants WHERE ticket_id=?",raw)).isEqualTo(before);
        assertThat(memberEvents()).isEqualTo(add?0:1);assertThat(outbox()).isEqualTo(add?0:1);
    }
    @Test void memberGrantRevokedAfterControllerReadCannotProduceSuccess() throws Exception {
        doAnswer(call->{jdbc.update("DELETE FROM business_memberships WHERE user_id=?",viewerId);return call.callRealMethod();})
            .when(participants).change(eq(scope),org.mockito.ArgumentMatchers.<org.springframework.security.core.Authentication>argThat(a->a!=null
                && a.getPrincipal() instanceof com.example.panel.security.PanelUserPrincipal p && p.getUserId()==actorId && p.getUsername().equals(actor)),eq(0L),eq(viewerId),eq(true));
        mvc.perform(auth(memberCommand("add","0",viewerId),actor,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());
        assertThat(memberEvents()).isZero();assertThat(outbox()).isZero();
    }
    @Test void memberNoOpAbaAndConcurrentFormsHaveOneWinner() throws Exception {
        var start=new CountDownLatch(1);
        java.util.function.Supplier<Boolean> attempt=()->{try{start.await();}catch(InterruptedException ex){throw new IllegalStateException(ex);}return participants.change(scope,boundActor(),0,viewerId,true);};
        var a=CompletableFuture.supplyAsync(attempt);var b=CompletableFuture.supplyAsync(attempt);start.countDown();
        assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        assertThat(participants.change(scope,boundActor(),1,viewerId,true)).isFalse();
        assertThat(participants.change(scope,boundActor(),1,targetId,false)).isFalse();
        assertThat(participants.change(scope,boundActor(),1,viewerId,false)).isTrue();
        assertThat(participants.change(scope,boundActor(),0,viewerId,true)).isFalse();
        assertThat(participants.change(scope,boundActor(),2,viewerId,true)).isTrue();
        assertThat(memberEvents()).isEqualTo(3);assertThat(outbox()).isEqualTo(3);assertThat(revision()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"audit","outbox"})
    void memberInsertOrRemovalFailureRollsBackRosterRevisionAuditAndOutbox(String failure) {
        assertThat(participants.change(scope,boundActor(),0,viewerId,true)).isTrue();
        String table=failure.equals("audit")?"business_ticket_participant_events":"ui_event_outbox";
        jdbc.execute("CREATE FUNCTION fail_r164_insert() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic R164 failure'; END; $$");
        jdbc.execute("CREATE TRIGGER fail_r164_insert BEFORE INSERT ON "+table+" FOR EACH ROW EXECUTE FUNCTION fail_r164_insert()");
        try {
            assertThat(participants.change(scope,boundActor(),1,targetId,true)).isFalse();
            assertThat(participants.change(scope,boundActor(),1,viewerId,false)).isFalse();
            assertThat(participants.members(scope,actor)).extracting(BusinessTicketParticipantService.Member::id).containsExactly(viewerId);
            assertThat(participants.revision(scope,actor)).contains(1L);assertThat(memberEvents()).isEqualTo(1);assertThat(outbox()).isEqualTo(1);
        }finally{jdbc.execute("DROP TRIGGER fail_r164_insert ON "+table);jdbc.execute("DROP FUNCTION fail_r164_insert()");}
        assertThat(participants.change(scope,boundActor(),1,viewerId,false)).isTrue();
    }
    @ParameterizedTest @ValueSource(strings={"revoked","disabled","deleted"})
    void removedAccessDoesNotGrantNotificationsAndDoesNotPreventOperatorRemovingMember(String kind) {
        assertThat(participants.change(scope,boundActor(),0,viewerId,true)).isTrue();
        assertThat(notices.findScopedDialogRecipients(scope)).contains(viewer);
        var captured=notices.findScopedDialogRecipients(scope);
        if(kind.equals("revoked"))jdbc.update("DELETE FROM business_memberships WHERE user_id=?",viewerId);
        if(kind.equals("disabled"))jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?",viewerId);
        if(kind.equals("deleted"))jdbc.update("DELETE FROM users WHERE id=?",viewerId);
        assertThat(notices.findScopedDialogRecipients(scope)).doesNotContain(viewer);
        assertThat(notices.notifyScopedDialogUsers(scope,captured,"revoked","/business-dialogs")).isEmpty();
        assertThat(participants.members(scope,actor)).singleElement().satisfies(m->assertThat(m.eligible()).isFalse());
        assertThat(participants.change(scope,boundActor(),1,viewerId,false)).isTrue();
        assertThat(participants.members(scope,actor)).isEmpty();
    }
    @Test void numericMemberIdentitySurvivesRenameAndCannotBeClaimedByReusedUsername() {
        assertThat(participants.change(scope,boundActor(),0,viewerId,true)).isTrue();
        String renamed=viewer+" renamed";jdbc.update("UPDATE users SET username=? WHERE id=?",renamed,viewerId);
        long reuse=account(viewer);jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(?,?,'VIEWER')",reuse,business);
        assertThat(notices.findScopedDialogRecipients(scope)).containsExactly(renamed);
        assertThat(participants.members(scope,actor)).singleElement().satisfies(m->{assertThat(m.id()).isEqualTo(viewerId);assertThat(m.username()).isEqualTo(renamed);});
        assertThat(notices.notifyScopedDialogUsers(scope,notices.findScopedDialogRecipients(scope),"renamed","/business-dialogs")).containsExactly(renamed);
    }
    @Test void explicitBusinessRosterHasNoRawParticipantOrGlobalOperatorFallback() {
        jdbc.update("INSERT INTO ticket_participants(ticket_id,username,added_by) VALUES(?,?,'legacy')",raw,target);
        assertThat(notices.findScopedDialogRecipients(scope)).isEmpty();
        notices.notifyScopedDialogParticipants(scope,"no broadcast","/business-dialogs",null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE ticket_id=?",Long.class,raw)).isZero();
        assertThat(participants.change(scope,boundActor(),0,viewerId,true)).isTrue();
        notices.notifyScopedDialogParticipants(scope,"explicit roster","/business-dialogs",null);
        assertThat(jdbc.queryForList("SELECT user_identity FROM notifications WHERE ticket_id=?",String.class,raw)).containsExactly(viewer);
    }
    @Test void memberAuditAndIdentityAreImmutableAndDoNotFollowOwnerOrGeneration() {
        assertThat(participants.change(scope,boundActor(),0,viewerId,true)).isTrue();
        assertThatThrownBy(()->jdbc.update("UPDATE business_ticket_participants SET member_user_id=? WHERE ticket_id=?",targetId,raw)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE business_ticket_participant_events SET actor='changed' WHERE ticket_id=?",raw)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM business_ticket_participant_events WHERE ticket_id=?",raw)).isInstanceOf(RuntimeException.class);
        jdbc.update("UPDATE tickets SET business_id=? WHERE ticket_id=?",other,raw);
        var moved=new UiEventTicketScope(901L,raw,publicId,"BUSINESS",other);
        assertThat(participants.members(moved,foreign)).isEmpty();assertThat(participants.recent(moved,foreign)).isEmpty();
        assertThat(participants.members(scope,actor)).isEmpty();assertThat(notices.findScopedDialogRecipients(scope)).isEmpty();
        jdbc.update("DELETE FROM tickets WHERE ticket_id=?",raw);
        String nextId="b".repeat(32);
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,status) VALUES(901,?,?,?,'open')",raw,nextId,business);
        var next=new UiEventTicketScope(901L,raw,nextId,"BUSINESS",business);
        assertThat(participants.members(next,actor)).isEmpty();assertThat(participants.recent(next,actor)).isEmpty();
        assertThat(participants.revision(next,actor)).contains(0L);
    }
    @Test void memberGlobalReadGrantAllowsViewerButDoesNotAllowManagingRoster() {
        jdbc.update("DELETE FROM business_memberships WHERE user_id=?",viewerId);
        jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(?,'ALL_BUSINESSES','VIEWER')",viewerId);
        assertThat(participants.change(scope,boundActor(),0,viewerId,true)).isTrue();
        assertThat(participants.members(scope,viewer)).hasSize(1);assertThat(participants.candidates(scope,viewer)).isEmpty();
        assertThat(participants.change(scope,boundParticipantActor(viewerId,viewer),1,targetId,true)).isFalse();
    }
    @Test void upgradeV78ToV79DoesNotInventMembersOrChangeOldRows() {
        var old=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("r164_upgrade");
        org.flywaydb.core.Flyway.configure().dataSource(old.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").target("78").load().migrate();
        old.update("INSERT INTO tickets(user_id,ticket_id,status) VALUES(809,'old-member','open')");
        old.update("INSERT INTO ticket_participants(ticket_id,username,added_by) VALUES('old-member',' Old Member ','human')");
        var roots=old.queryForList("SELECT * FROM tickets");var members=old.queryForList("SELECT * FROM ticket_participants");
        org.flywaydb.core.Flyway.configure().dataSource(old.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").target("79").load().migrate();
        assertThat(old.queryForList("SELECT * FROM tickets")).isEqualTo(roots);
        assertThat(old.queryForList("SELECT * FROM ticket_participants")).isEqualTo(members);
        assertThat(old.queryForObject("SELECT count(*) FROM business_ticket_participants",Long.class)).isZero();
        assertThat(old.queryForObject("SELECT count(*) FROM business_ticket_participant_events",Long.class)).isZero();
        org.flywaydb.core.Flyway.configure().dataSource(old.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").target("80").load().migrate();
        assertThat(old.queryForList("SELECT * FROM tickets")).isEqualTo(roots);
        String oldColumns=roots.get(0).keySet().stream().map(column->"\""+column.replace("\"","\"\"")+"\"").collect(java.util.stream.Collectors.joining(","));
        org.flywaydb.core.Flyway.configure().dataSource(old.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").load().migrate();
        assertThat(old.queryForList("SELECT "+oldColumns+" FROM tickets")).isEqualTo(roots);
        assertThat(old.queryForMap("SELECT business_work_revision,business_categories::text AS categories FROM tickets"))
            .containsEntry("business_work_revision",0L).containsEntry("categories","[]");
        assertThat(old.queryForList("SELECT * FROM ticket_participants")).isEqualTo(members);
        for(String table:List.of("business_ticket_participants","business_ticket_participant_events","business_ticket_work_events"))
            assertThat(old.queryForObject("SELECT count(*) FROM "+table,Long.class)).isZero();
    }
    private long account(String name){return jdbc.queryForObject("INSERT INTO users(username,password) VALUES(?,'unused') RETURNING id",Long.class,name);}
    /** Fixture setup only: exact literal account and DB authorities captured before the request. */
    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request,String identity,String... authorities){
        var ids=jdbc.queryForList("SELECT id FROM users WHERE username = ?",Long.class,identity);
        assertThat(ids).as("exact fixture account: %s",identity).hasSize(1);
        long id=ids.get(0);
        authorities(id,authorities);
        var granted=Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        var principal=new com.example.panel.security.PanelUserPrincipal(id,identity,"unused",true,granted);
        return request.with(user(principal));
    }
    private void authorities(long id,String... authorities){
        jdbc.update("DELETE FROM user_authorities WHERE user_id=?",id);
        for(String authority:authorities)jdbc.update("INSERT INTO user_authorities(user_id,authority) VALUES(?,?)",id,authority);
    }
    @ParameterizedTest @ValueSource(strings={"disabled","blocked","deleted","renamed","reused-name"})
    void preparedBoundRequestsCannotSurviveAccountRevocation(String change) throws Exception {
        var request=auth(get(url),actor,"PAGE_DIALOGS");
        switch(change){
            case "disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?",actorId);
            case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=?",actorId);
            case "renamed"->jdbc.update("UPDATE users SET username=? WHERE id=?","renamed-"+actor,actorId);
            default->{jdbc.update("DELETE FROM users WHERE id=?",actorId);if(change.equals("reused-name")){long replacement=account(actor);authorities(replacement,"PAGE_DIALOGS");jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(?,?,'OPERATOR')",replacement,business);}}
        }
        mvc.perform(request).andExpect(redirectedUrl("http://localhost/login"));
        verify(service,never()).find(publicId,actor);
        assertThat(events()).isZero();assertThat(memberEvents()).isZero();assertThat(outbox()).isZero();assertThat(revision()).isZero();
        if(change.equals("reused-name"))mvc.perform(auth(get(url),actor,"PAGE_DIALOGS")).andExpect(status().isOk());
    }
    @ParameterizedTest @ValueSource(strings={"unbound","foreign-id","case-changed","padded"})
    void unboundOrMismatchedPrincipalsCannotReachBusinessControllers(String kind) throws Exception {
        authorities(actorId,"PAGE_DIALOGS");
        var granted=List.of(new SimpleGrantedAuthority("PAGE_DIALOGS"));
        var request=get(url);
        if(kind.equals("unbound"))request.with(user(actor).authorities(granted));
        else request.with(user(new com.example.panel.security.PanelUserPrincipal(kind.equals("foreign-id")?foreignId:actorId,
            kind.equals("case-changed")?actor.toUpperCase(Locale.ROOT):kind.equals("padded")?" "+actor+" ":actor,"unused",true,granted)));
        mvc.perform(request).andExpect(redirectedUrl("http://localhost/login"));
        verify(service,never()).find(publicId,actor);
        assertThat(events()).isZero();assertThat(memberEvents()).isZero();assertThat(outbox()).isZero();
    }
    @Test void preparedBoundRequestLosesARevokedPageAuthorityWithoutFixtureRepair() throws Exception {
        var request=auth(get(url),actor,"PAGE_DIALOGS");
        jdbc.update("DELETE FROM user_authorities WHERE user_id=?",actorId);
        mvc.perform(request).andExpect(status().isForbidden());
        verify(service,never()).find(publicId,actor);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_authorities WHERE user_id=?",Long.class,actorId)).isZero();
        assertThat(events()).isZero();assertThat(outbox()).isZero();
    }
    @Test void realFormLoginCanWriteReadCurrentAndHistoryThenLosesThePageRight() throws Exception {
        String password="fixture assignment session";
        jdbc.update("UPDATE users SET password=? WHERE id=?",new com.example.panel.security.LegacyCompatiblePasswordEncoder().encode(password),actorId);
        authorities(actorId,"PAGE_DIALOGS");
        var login=mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
        var signedIn=mvc.perform(post("/login").cookie(login.getResponse().getCookies())
            .param("_csrf",hidden(login.getResponse().getContentAsString(),"_csrf")).param("username",actor).param("password",password))
            .andExpect(redirectedUrl("/post-login")).andReturn();
        var sessionCookie=signedIn.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).as("real JDBC-backed login session cookie").isNotNull();
        var form=mvc.perform(get(url).cookie(sessionCookie)).andExpect(status().isOk()).andReturn();
        String token=hidden(form.getResponse().getContentAsString(),"_csrf");
        var cookieJar=new LinkedHashMap<String,jakarta.servlet.http.Cookie>();
        for(var response:List.of(login,signedIn,form))for(var cookie:response.getResponse().getCookies()){
            if(cookie.getMaxAge()==0)cookieJar.remove(cookie.getName());else cookieJar.put(cookie.getName(),cookie);
        }
        var requestCookies=cookieJar.values().toArray(jakarta.servlet.http.Cookie[]::new);
        String current="/api/business-scope/tickets/"+publicId+"/assignments/current";
        mvc.perform(get(current).cookie(sessionCookie)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNASSIGNED"));
        mvc.perform(command("take",0).cookie(requestCookies).param("_csrf",token)).andExpect(redirectedUrl(url));
        mvc.perform(get(current).cookie(sessionCookie)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ASSIGNED"))
            .andExpect(jsonPath("$.assignee.id").value(Long.toString(actorId))).andExpect(jsonPath("$.assignee.username").value(actor));
        mvc.perform(get("/api/business-scope/tickets/"+publicId+"/assignments/history").cookie(sessionCookie))
            .andExpect(status().isOk()).andExpect(jsonPath("$.events",hasSize(1)));
        jdbc.update("DELETE FROM user_authorities WHERE user_id=?",actorId);
        mvc.perform(get(current).cookie(sessionCookie)).andExpect(status().isForbidden());
        mvc.perform(command("take",1).cookie(requestCookies).param("_csrf",token)).andExpect(status().isForbidden());
        assertThat(events()).isEqualTo(1);assertThat(revision()).isEqualTo(1);assertThat(outbox()).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"renamed","reused-name","disabled","blocked"})
    void actorChangedAfterControllerReadCannotBeReboundByTheWriter(String change) throws Exception {
        var once=new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(call->{Object result=call.callRealMethod();if(once.compareAndSet(false,true)){
            if(change.equals("disabled"))jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?",actorId);
            else if(change.equals("blocked"))jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=?",actorId);
            else {jdbc.update("UPDATE users SET username=? WHERE id=?","renamed-"+actor,actorId);if(change.equals("reused-name")){long replacement=account(actor);jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(?,?,'OPERATOR')",replacement,business);}}
        }return result;}).when(service).find(publicId,actor);
        mvc.perform(auth(command("take",0),actor,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());
        assertThat(once).isTrue();assertThat(events()).isZero();assertThat(memberEvents()).isZero();assertThat(outbox()).isZero();assertThat(revision()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_responsibles WHERE ticket_id=?",Long.class,raw)).isZero();
    }
    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"add,renamed","remove,renamed","add,reused-name","remove,reused-name","add,disabled","remove,disabled","add,blocked","remove,blocked"})
    void participantActorChangedAfterControllerReadCannotBeReboundByTheWriter(String action,String change) throws Exception {
        boolean add=action.equals("add");
        if(!add)mvc.perform(auth(memberCommand("add","0",viewerId),actor,"PAGE_DIALOGS").with(csrf())).andExpect(redirectedUrl(url));
        var before=jdbc.queryForList("SELECT * FROM business_ticket_participants WHERE ticket_id=?",raw);
        long beforeEvents=memberEvents(),beforeOutbox=outbox();
        var once=new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(call->{Object result=call.callRealMethod();if(once.compareAndSet(false,true)){
            if(change.equals("disabled"))jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?",actorId);
            else if(change.equals("blocked"))jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=?",actorId);
            else{jdbc.update("UPDATE users SET username=? WHERE id=?","renamed-"+actor,actorId);if(change.equals("reused-name")){long replacement=account(actor);jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(?,?,'OPERATOR')",replacement,business);}}
        }return result;}).when(service).find(publicId,actor);
        mvc.perform(auth(memberCommand(action,add?"0":"1",viewerId),actor,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());
        assertThat(once).isTrue();assertThat(memberEvents()).isEqualTo(beforeEvents);assertThat(outbox()).isEqualTo(beforeOutbox);
        assertThat(jdbc.queryForList("SELECT * FROM business_ticket_participants WHERE ticket_id=?",raw)).isEqualTo(before);
        assertThat(events()).isZero();assertThat(revision()).isZero();
    }
    private org.springframework.security.core.Authentication boundParticipantActor(long id,String name){
        return org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
            new com.example.panel.security.PanelUserPrincipal(id,name,"unused",true,List.of()),null,List.of());
    }
    private org.springframework.security.core.Authentication boundActor(){
        var granted=List.of(new SimpleGrantedAuthority("PAGE_DIALOGS"));
        return org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
            new com.example.panel.security.PanelUserPrincipal(actorId,actor,"unused",true,granted),null,granted);
    }
    private MockHttpServletRequestBuilder command(String action,long revision){
        return post(url+"/"+action).param("ownerId","901").param("rawId",raw).param("businessId",Long.toString(business))
            .param("revision",Long.toString(revision)).param("targetId",Long.toString(targetId));
    }
    private long revision(){return jdbc.queryForObject("SELECT responsibility_revision FROM tickets WHERE user_id=901 AND ticket_id=?",Long.class,raw);}
    private long events(){return jdbc.queryForObject("SELECT count(*) FROM business_ticket_assignment_events WHERE ticket_id=?",Long.class,raw);}
    private long outbox(){return jdbc.queryForObject("SELECT count(*) FROM ui_event_outbox WHERE ticket_id=?",Long.class,raw);}
    private void legacy(String name){jdbc.update("INSERT INTO ticket_responsibles(ticket_id,responsible) VALUES(?,?) ON CONFLICT(ticket_id) DO UPDATE SET responsible=EXCLUDED.responsible",raw,name);}
    private String hidden(String html,String name){
        var match=Pattern.compile("name=\""+name+"\"[^>]*value=\"([^\"]*)\"").matcher(html);
        assertThat(match.find()).as("hidden "+name).isTrue();return HtmlUtils.htmlUnescape(match.group(1));
    }
    @Test void renderedFormCsrfAssignReloadHistoryAndFrozenOutboxWithoutProviderSideEffects() throws Exception {
        var opened=mvc.perform(auth(get(url),actor,"PAGE_DIALOGS")).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control","no-store")).andReturn();
        String html=opened.getResponse().getContentAsString();
        assertThat(html).contains(actor,target,viewer).doesNotContain(foreign);
        var assignmentSelect=Pattern.compile("<select id=\"assignmentTarget\"[\\s\\S]*?</select>").matcher(html);
        assertThat(assignmentSelect.find()).isTrue();
        assertThat(assignmentSelect.group()).contains(actor,target).doesNotContain(viewer,foreign);
        assertThat(hidden(html,"rawId")).isEqualTo(raw);
        mvc.perform(auth(post(url+"/assign").param("ownerId",hidden(html,"ownerId")).param("rawId",hidden(html,"rawId"))
            .param("businessId",hidden(html,"businessId")).param("revision",hidden(html,"revision"))
            .param("targetId",Long.toString(targetId)).param("_csrf",hidden(html,"_csrf")).cookie(opened.getResponse().getCookies()),actor,"PAGE_DIALOGS"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(url));
        assertThat(revision()).isEqualTo(1);assertThat(events()).isEqualTo(1);assertThat(outbox()).isEqualTo(1);
        var event=jdbc.queryForMap("SELECT * FROM business_ticket_assignment_events WHERE ticket_id=?",raw);
        assertThat(event).containsEntry("business_id",business).containsEntry("actor_user_id",actorId).containsEntry("assignee_user_id",targetId)
            .containsEntry("ticket_user_id",901L).containsEntry("ticket_public_id",publicId).containsEntry("revision",1L);
        var notice=jdbc.queryForMap("SELECT * FROM ui_event_outbox WHERE ticket_id=?",raw);
        assertThat(notice).containsEntry("event_type","business_assignment_changed").containsEntry("ticket_business_id",business)
            .containsEntry("ticket_user_id",901L).containsEntry("ticket_public_id",publicId).containsEntry("ticket_scope_kind","BUSINESS");
        mvc.perform(auth(get(url),viewer,"PAGE_DIALOGS")).andExpect(status().isOk()).andExpect(content().string(containsString(target)));
        assertThat(service.candidates(scope,viewer)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dialog_action_audit WHERE ticket_id=?",Long.class,raw)).isZero();
        mvc.perform(auth(command("assign",0),actor,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());
        assertThat(events()).isEqualTo(1);
    }
    @Test void takeUsesAuthenticatedActorNotSubmittedTargetAndCannotTurnNoOpIntoSuccess() throws Exception {
        mvc.perform(auth(command("take",0),actor,"PAGE_DIALOGS").with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForObject("SELECT responsible FROM ticket_responsibles WHERE ticket_id=?",String.class,raw)).isEqualTo(actor);
        mvc.perform(auth(command("take",1),actor,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());
        assertThat(revision()).isEqualTo(1);assertThat(events()).isEqualTo(1);assertThat(outbox()).isEqualTo(1);
    }
    @Test void pageAuthorityCsrfAndBusinessOperateAreIndependent() throws Exception {
        mvc.perform(auth(get(url),actor,"ROLE_ADMIN")).andExpect(status().isForbidden());
        mvc.perform(auth(command("assign",0),actor,"PAGE_DIALOGS")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/error/403"));
        assertThat(events()).isZero();assertThat(outbox()).isZero();assertThat(revision()).isZero();
        mvc.perform(auth(command("assign",0),viewer,"PAGE_DIALOGS").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(auth(get(url),foreign,"PAGE_DIALOGS","ROLE_ADMIN")).andExpect(status().isNotFound());
        mvc.perform(auth(post(url+"/assign").param("ownerId","901").param("rawId",raw).param("businessId",Long.toString(business))
            .param("revision","0").param("targetId",Long.toString(foreignId)),actor,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());
        assertThat(events()).isZero();assertThat(outbox()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"target-viewer","target-foreign","target-disabled","target-blocked","target-duplicate","target-revoked","actor-viewer","actor-disabled","actor-blocked","actor-duplicate","actor-revoked","business-archived","owner","recreated","collision","general","closed","flag"})
    void finalStatementRejectsLateOwnerOrAccountChanges(String mutation){
        switch(mutation){
            case "target-viewer"->jdbc.update("UPDATE business_memberships SET role_code='VIEWER' WHERE user_id=?",targetId);
            case "target-foreign"->jdbc.update("UPDATE business_memberships SET business_id=? WHERE user_id=?",other,targetId);
            case "target-disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?",targetId);
            case "target-blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=?",targetId);
            case "target-duplicate"->account(target.toUpperCase(Locale.ROOT));
            case "target-revoked"->jdbc.update("DELETE FROM business_memberships WHERE user_id=?",targetId);
            case "actor-viewer"->jdbc.update("UPDATE business_memberships SET role_code='VIEWER' WHERE user_id=?",actorId);
            case "actor-disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?",actorId);
            case "actor-blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=?",actorId);
            case "actor-duplicate"->account(actor.toUpperCase(Locale.ROOT));
            case "actor-revoked"->jdbc.update("DELETE FROM business_memberships WHERE user_id=?",actorId);
            case "business-archived"->jdbc.update("UPDATE businesses SET status='archived' WHERE id=?",business);
            case "owner"->jdbc.update("UPDATE tickets SET business_id=? WHERE ticket_id=?",other,raw);
            case "recreated"->{jdbc.update("DELETE FROM tickets WHERE ticket_id=?",raw);jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,status) VALUES(901,?,?,?,'open')",raw,"f".repeat(32),business);}
            case "collision"->jdbc.update("INSERT INTO tickets(user_id,ticket_id,business_id) VALUES(902,?,?)",raw,other);
            case "general"->jdbc.update("UPDATE tickets SET business_id=NULL,is_general=TRUE,general_selection_source='MANUAL' WHERE ticket_id=?",raw);
            case "closed"->jdbc.update("UPDATE tickets SET status='closed' WHERE ticket_id=?",raw);
            case "flag"->flags.setUiSelector(false);
        }
        assertThat(service.change(scope,boundActor(),0,targetId,false)).isFalse();
        assertThat(events()).isZero();assertThat(outbox()).isZero();assertThat(revision()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_responsibles WHERE ticket_id=?",Long.class,raw)).isZero();
    }
    @Test void revokeAfterControllerReadStillCannotWriteOrAuditSuccess() throws Exception {
        doAnswer(call->{jdbc.update("UPDATE business_memberships SET role_code='VIEWER' WHERE user_id=?",actorId);return call.callRealMethod();})
            .when(service).change(eq(scope),org.mockito.ArgumentMatchers.<org.springframework.security.core.Authentication>argThat(a->a!=null
                && a.getPrincipal() instanceof com.example.panel.security.PanelUserPrincipal p && p.getUserId()==actorId && p.getUsername().equals(actor)),eq(0L),eq(targetId),eq(false));
        mvc.perform(auth(command("assign",0),actor,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());
        assertThat(events()).isZero();assertThat(outbox()).isZero();assertThat(revision()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"aba","delete-aba","new-owner","tuple","revision"})
    void staleOrForgedFormCannotTargetAnotherRootOrOverrideAnotherWriter(String kind) throws Exception {
        var request=command("assign",0);
        if(kind.equals("aba")){legacy(actor);legacy(target);jdbc.update("DELETE FROM ticket_responsibles WHERE ticket_id=?",raw);}
        if(kind.equals("delete-aba")){legacy(actor);jdbc.update("DELETE FROM ticket_responsibles WHERE ticket_id=?",raw);}
        if(kind.equals("new-owner"))request=post(url+"/assign").param("ownerId","902").param("rawId",raw).param("businessId",Long.toString(business)).param("revision","0").param("targetId",Long.toString(targetId));
        if(kind.equals("tuple"))request=post(url+"/assign").param("ownerId","901").param("rawId",raw.trim()).param("businessId",Long.toString(business)).param("revision","0").param("targetId",Long.toString(targetId));
        if(kind.equals("revision"))request=command("assign",9);
        mvc.perform(auth(request,actor,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());
        assertThat(events()).isZero();assertThat(outbox()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"audit","outbox"})
    void anyPostAssignmentFailureRollsBackRevisionProjectionAuditAndInvalidation(String failure){
        String table=failure.equals("audit")?"business_ticket_assignment_events":"ui_event_outbox";
        jdbc.execute("CREATE FUNCTION fail_r159_insert() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic R159 failure'; END; $$");
        jdbc.execute("CREATE TRIGGER fail_r159_insert BEFORE INSERT ON "+table+" FOR EACH ROW EXECUTE FUNCTION fail_r159_insert()");
        try{assertThat(service.change(scope,boundActor(),0,targetId,false)).isFalse();assertThat(revision()).isZero();assertThat(events()).isZero();assertThat(outbox()).isZero();}
        finally{jdbc.execute("DROP TRIGGER fail_r159_insert ON "+table);jdbc.execute("DROP FUNCTION fail_r159_insert()");}
        assertThat(service.change(scope,boundActor(),0,targetId,false)).isTrue();
    }
    @Test void concurrentCommandsCommitOneTransition() throws Exception {
        var start=new CountDownLatch(1);
        java.util.function.Supplier<Boolean> attempt=()->{try{start.await();}catch(InterruptedException ex){throw new IllegalStateException(ex);}return service.change(scope,boundActor(),0,targetId,false);};
        var first=CompletableFuture.supplyAsync(attempt);var second=CompletableFuture.supplyAsync(attempt);start.countDown();
        assertThat(List.of(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        assertThat(events()).isEqualTo(1);assertThat(outbox()).isEqualTo(1);assertThat(revision()).isEqualTo(1);
    }
    @Test void historyIsAppendOnlyAndDoesNotFollowChangedBusinessOrNewGeneration(){
        assertThat(service.change(scope,boundActor(),0,targetId,false)).isTrue();
        assertThat(service.recent(scope,viewer)).hasSize(1);
        assertThatThrownBy(()->jdbc.update("UPDATE business_ticket_assignment_events SET actor='changed' WHERE ticket_id=?",raw)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM business_ticket_assignment_events WHERE ticket_id=?",raw)).isInstanceOf(RuntimeException.class);
        jdbc.update("UPDATE tickets SET business_id=? WHERE ticket_id=?",other,raw);
        var replacement=new UiEventTicketScope(901L,raw,publicId,"BUSINESS",other);
        assertThat(service.recent(replacement,foreign)).isEmpty();assertThat(service.recent(scope,viewer)).isEmpty();
    }
    @Test void allBusinessOperateGrantIsAcceptedButViewerGlobalGrantCannotAssign(){
        jdbc.update("DELETE FROM business_memberships WHERE user_id IN (?,?)",actorId,targetId);
        jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(?,'ALL_BUSINESSES','OPERATOR'),(?,'ALL_BUSINESSES','VIEWER')",actorId,targetId);
        assertThat(service.change(scope,boundActor(),0,targetId,false)).isFalse();
        jdbc.update("UPDATE business_access_grants SET role_code='MANAGER' WHERE user_id=?",targetId);
        assertThat(service.change(scope,boundActor(),0,targetId,false)).isTrue();
    }
    @Test void manualAndSlaShareRevisionWithoutReusingEachOthersAuditOrRawWorkflow(){
        long channel=jdbc.queryForObject("INSERT INTO channels(token,channel_name,platform) VALUES(?,'Telegram','telegram') RETURNING id",Long.class,UUID.randomUUID().toString());
        jdbc.update("UPDATE tickets SET channel_id=? WHERE ticket_id=?",channel,raw);
        var frozen=new DialogLookupReadService.ScopedSlaDialog(null,scope,channel,0,null);
        assertThat(service.change(scope,boundActor(),0,null,true)).isTrue();
        assertThat(sla.assign(frozen,target,"scheduler","rules","r")).isFalse();
        var fresh=new DialogLookupReadService.ScopedSlaDialog(null,scope,channel,1,actor);
        assertThat(sla.assign(fresh,target,"scheduler","rules","r")).isTrue();
        assertThat(service.change(scope,boundActor(),1,null,true)).isFalse();
        assertThat(revision()).isEqualTo(2);assertThat(events()).isEqualTo(1);assertThat(outbox()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM scoped_sla_assignment_events WHERE ticket_id=?",Long.class,raw)).isEqualTo(1);
    }
    @Test void upgradeV77ToV78PreservesExistingRootsAssignmentsAndCreatesNoInventedHistory(){
        var old=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("r159_upgrade");
        org.flywaydb.core.Flyway.configure().dataSource(old.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").target("77").load().migrate();
        old.update("INSERT INTO tickets(user_id,ticket_id,status) VALUES(808,'old-manual','open')");
        old.update("INSERT INTO ticket_responsibles(ticket_id,responsible,assigned_by,last_read_at) VALUES('old-manual',' Original ','human',CURRENT_TIMESTAMP)");
        var roots=old.queryForList("SELECT * FROM tickets");var assignments=old.queryForList("SELECT * FROM ticket_responsibles");
        org.flywaydb.core.Flyway.configure().dataSource(old.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").target("78").load().migrate();
        assertThat(old.queryForList("SELECT * FROM tickets")).isEqualTo(roots);
        assertThat(old.queryForList("SELECT * FROM ticket_responsibles")).isEqualTo(assignments);
        assertThat(old.queryForObject("SELECT count(*) FROM business_ticket_assignment_events",Long.class)).isZero();
        org.flywaydb.core.Flyway.configure().dataSource(old.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").target("80").load().migrate();
        assertThat(old.queryForList("SELECT * FROM tickets")).isEqualTo(roots);
        String oldColumns=roots.get(0).keySet().stream().map(column->"\""+column.replace("\"","\"\"")+"\"").collect(java.util.stream.Collectors.joining(","));
        org.flywaydb.core.Flyway.configure().dataSource(old.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").load().migrate();
        assertThat(old.queryForList("SELECT "+oldColumns+" FROM tickets")).isEqualTo(roots);
        assertThat(old.queryForMap("SELECT business_work_revision,business_categories::text AS categories FROM tickets"))
            .containsEntry("business_work_revision",0L).containsEntry("categories","[]");
        assertThat(old.queryForList("SELECT * FROM ticket_responsibles")).isEqualTo(assignments);
        assertThat(old.queryForObject("SELECT count(*) FROM business_ticket_assignment_events",Long.class)).isZero();
        assertThat(old.queryForObject("SELECT count(*) FROM business_ticket_work_events",Long.class)).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"bad","9223372036854775808","-1"})
    void malformedOrNegativeRevisionCannotWrite(String value) throws Exception {
        var request=post(url+"/assign").param("ownerId","901").param("rawId",raw).param("businessId",Long.toString(business))
            .param("revision",value).param("targetId",Long.toString(targetId));
        mvc.perform(auth(request,actor,"PAGE_DIALOGS").with(csrf()))
            .andExpect(value.equals("-1")?status().isConflict():status().isBadRequest());
        assertThat(events()).isZero();assertThat(outbox()).isZero();assertThat(revision()).isZero();
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource({"business,take,path","business,take,query","business,reassign,path","business,reassign,query",
        "general,take,path","general,take,query","general,reassign,path","general,reassign,query",
        "strict,take,path","strict,take,query","strict,reassign,path","strict,reassign,query"})
    void rawAssignmentRoutesCannotBypassScopedWorkflowEvenWithPageAndSystemAdmin(String kind,String action,String route) throws Exception {
        flags.setStrictReadScope(false);
        if(route.equals("path")){String safe="r160-"+publicId;jdbc.update("UPDATE tickets SET ticket_id=? WHERE ticket_id=?",safe,raw);raw=safe;}
        if(kind.equals("business"))flags.getFoundation().setEnabled(false); // Disabling the foundation cannot authorize a raw BUSINESS writer.
        if(kind.equals("general"))jdbc.update("UPDATE tickets SET business_id=NULL,is_general=TRUE,general_selection_source='MANUAL' WHERE ticket_id=?",raw);
        if(kind.equals("strict")){jdbc.update("UPDATE tickets SET business_id=NULL WHERE ticket_id=?",raw);flags.setStrictReadScope(true);}
        var request=route.equals("path")?post("/api/dialogs/{id}/"+action,raw):post("/api/dialog-ticket/"+action).param("ticketId",raw);
        mvc.perform(auth(request,actor,"PAGE_DIALOGS","ROLE_ADMIN").with(csrf()).contentType("application/json").content("{\"username\":\""+target+"\"}"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_responsibles WHERE ticket_id=?",Long.class,raw)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dialog_action_audit WHERE ticket_id=?",Long.class,raw)).isZero();
        assertThat(events()).isZero();assertThat(outbox()).isZero();assertThat(revision()).isZero();
    }
    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"business,add,path","business,add,query","business,remove,path","business,remove,query",
        "general,add,path","general,add,query","general,remove,path","general,remove,query",
        "strict,add,path","strict,add,query","strict,remove,path","strict,remove,query"})
    void rawParticipantRoutesCannotMutateScopedOrStrictRoot(String kind,String action,String route) throws Exception {
        flags.setStrictReadScope(false);
        if(route.equals("path")){String safe="r163-"+publicId;jdbc.update("UPDATE tickets SET ticket_id=? WHERE ticket_id=?",safe,raw);raw=safe;}
        if(kind.equals("business"))flags.getFoundation().setEnabled(false);
        if(kind.equals("general"))jdbc.update("UPDATE tickets SET business_id=NULL,is_general=TRUE,general_selection_source='MANUAL' WHERE ticket_id=?",raw);
        if(kind.equals("strict")){jdbc.update("UPDATE tickets SET business_id=NULL WHERE ticket_id=?",raw);flags.setStrictReadScope(true);}
        jdbc.update("INSERT INTO ticket_participants(ticket_id,username,added_by) VALUES(?,?,'original')",raw,target);
        var before=jdbc.queryForList("SELECT * FROM ticket_participants WHERE ticket_id=?",raw);
        MockHttpServletRequestBuilder request;
        if(action.equals("add")){
            request=route.equals("path")?post("/api/dialogs/{id}/participants",raw):post("/api/dialog-ticket/participants").param("ticketId",raw);
            request.contentType("application/json").content("{\"username\":\"fresh-member\"}");
        }else request=route.equals("path")?delete("/api/dialogs/{id}/participants/{username}",raw,target)
            :delete("/api/dialog-ticket/participants").param("ticketId",raw).param("username",target);
        mvc.perform(auth(request,actor,"PAGE_DIALOGS","ROLE_ADMIN").with(csrf())).andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false));
        assertThat(jdbc.queryForList("SELECT * FROM ticket_participants WHERE ticket_id=?",raw)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dialog_action_audit WHERE ticket_id=?",Long.class,raw)).isZero();
        assertThat(events()).isZero();assertThat(outbox()).isZero();assertThat(revision()).isZero();
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> legacyLifecycleRoutes() {
        return java.util.stream.Stream.of("business","general","strict").flatMap(kind->
            java.util.stream.Stream.of("resolve","reopen","categories","spam").flatMap(action->
                java.util.stream.Stream.of("path","query").map(route->org.junit.jupiter.params.provider.Arguments.of(kind,action,route))));
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("legacyLifecycleRoutes")
    void rawLifecycleRoutesCannotMutateScopedOrStrictRootEvenForSystemAdmin(String kind,String action,String route) throws Exception {
        flags.setStrictReadScope(false);
        if(route.equals("path")){String safe="r166-"+publicId;jdbc.update("UPDATE tickets SET ticket_id=? WHERE ticket_id=?",safe,raw);raw=safe;}
        if(kind.equals("business"))flags.getFoundation().setEnabled(false);
        if(kind.equals("general"))jdbc.update("UPDATE tickets SET business_id=NULL,is_general=TRUE,general_selection_source='MANUAL' WHERE ticket_id=?",raw);
        if(kind.equals("strict")){jdbc.update("UPDATE tickets SET business_id=NULL WHERE ticket_id=?",raw);flags.setStrictReadScope(true);}
        jdbc.update("UPDATE tickets SET status=? WHERE ticket_id=?",action.equals("reopen")?"closed":"pending",raw);
        jdbc.update("INSERT INTO ticket_categories(ticket_id,category) VALUES(?,'Original')",raw);
        var tables=List.of("tickets","ticket_categories","ticket_active","pending_feedback_requests","ticket_responsibles","dialog_action_audit","notifications","ui_event_outbox");
        var before=new LinkedHashMap<String,List<Map<String,Object>>>();
        for(String table:tables)before.put(table,jdbc.queryForList("SELECT * FROM "+table+" WHERE ticket_id=?",raw));
        var request=route.equals("path")?post("/api/dialogs/{id}/"+action,raw):post("/api/dialog-ticket/"+action).param("ticketId",raw);
        mvc.perform(auth(request,actor,"PAGE_DIALOGS","ROLE_ADMIN").with(csrf()).contentType("application/json").content("{\"categories\":[\"New\"],\"reason\":\"Spam\"}"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false));
        for(String table:tables)assertThat(jdbc.queryForList("SELECT * FROM "+table+" WHERE ticket_id=?",raw)).as(table).isEqualTo(before.get(table));
        assertThat(memberEvents()).isZero();assertThat(events()).isZero();assertThat(outbox()).isZero();assertThat(revision()).isZero();
    }
    @Test void rawCapabilityCannotBeReusedAfterTheRootBecomesBusiness() throws Exception {
        flags.setStrictReadScope(false);jdbc.update("UPDATE tickets SET business_id=NULL WHERE ticket_id=?",raw);
        var once=new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(call->{boolean allowed=(boolean)call.callRealMethod();if(allowed&&once.compareAndSet(false,true))jdbc.update("UPDATE tickets SET business_id=? WHERE ticket_id=?",business,raw);return allowed;})
            .when(legacyAssignments).canAssignLegacyResponsible(raw);
        mvc.perform(auth(post("/api/dialog-ticket/take").param("ticketId",raw),actor,"PAGE_DIALOGS").with(csrf()))
            .andExpect(status().isNotFound());
        assertThat(once).isTrue();assertThat(revision()).isZero();assertThat(events()).isZero();assertThat(outbox()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dialog_action_audit WHERE ticket_id=?",Long.class,raw)).isZero();
    }
    @Test void rawLegacyTakeRemainsAvailableBeforeStrictAndUsesTheSharedRevision() throws Exception {
        flags.setStrictReadScope(false);jdbc.update("UPDATE tickets SET business_id=NULL WHERE ticket_id=?",raw);
        mvc.perform(auth(post("/api/dialog-ticket/take").param("ticketId",raw),actor,"PAGE_DIALOGS").with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.changed").value(true));
        assertThat(jdbc.queryForObject("SELECT responsible FROM ticket_responsibles WHERE ticket_id=?",String.class,raw)).isEqualTo(actor);
        assertThat(revision()).isEqualTo(1);assertThat(events()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dialog_action_audit WHERE ticket_id=? AND action='take' AND result='success'",Long.class,raw)).isEqualTo(1);
    }
}
