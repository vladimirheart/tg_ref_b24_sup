package com.example.panel.controller;

import static com.example.panel.service.ScopedAiSourceService.Kind.KNOWLEDGE;
import static com.example.panel.service.ScopedAiSourceService.Kind.MEMORY;
import static com.example.panel.service.ScopedAiSourceService.Status.APPROVED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.ScopedAiSourceService;
import com.example.panel.service.ScopedAiSourceService.Source;
import com.example.panel.service.UiEventTicketScope;
import com.example.panel.support.PostgresqlIntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.util.HtmlUtils;

@SpringBootTest
@AutoConfigureMockMvc
class ScopedAiSourceHttpPostgresTest extends PostgresqlIntegrationTestSupport {
    private static final AtomicLong IDS=new AtomicLong(9320000);
    private static final String ROOT="/business-ai-sources";
    private static final String KEY=" opaque/Ю?%#&=+ ";
    private static final String BODY="PRIVATE_A </textarea><script>window.pwned=1</script>";
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MultibusinessFeatureFlags flags;
    @SpyBean private ScopedAiSourceService sources;
    private long businessA,businessB,adminId,viewerId;
    private String admin,viewer,operator,manager,foreign,global,all;
    private Source sourceA,sourceB;
    private UiEventTicketScope origin;

    @BeforeEach
    void setUp() {
        flags.getFoundation().setEnabled(true);
        flags.setUiSelector(true);
        flags.setStrictReadScope(false);
        String prefix="ai-r132-"+IDS.incrementAndGet();
        businessA=business(prefix+"-a","Business A <private>");
        businessB=business(prefix+"-b","Business B secret");
        admin=prefix+"-admin"; viewer=prefix+"-viewer"; operator=prefix+"-operator";
        manager=prefix+"-manager"; foreign=prefix+"-foreign"; global=prefix+"-global"; all=prefix+"-all";
        adminId=account(admin); viewerId=account(viewer);
        membership(adminId,businessA,"ADMIN"); membership(viewerId,businessA,"VIEWER");
        membership(account(operator),businessA,"OPERATOR"); membership(account(manager),businessA,"MANAGER");
        membership(account(foreign),businessB,"ADMIN");
        jdbc.update("INSERT INTO user_authorities(user_id,authority) VALUES(?,'ROLE_ADMIN')",account(global));
        jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(?,'ALL_BUSINESSES','ADMIN')",account(all));
        assertThat(sources.createKnowledge(businessA,admin,KEY,"Title A <unsafe>",BODY)).isTrue();
        assertThat(sources.createKnowledge(businessB,foreign,KEY,"Title B secret","PRIVATE_B_SECRET")).isTrue();
        sourceA=sources.find(businessA,admin,KNOWLEDGE,KEY).orElseThrow();
        sourceB=sources.find(businessB,foreign,KNOWLEDGE,KEY).orElseThrow();
        String raw=" private origin/Ю?% "+prefix;
        String publicId=UUID.randomUUID().toString().replace("-","");
        long owner=IDS.incrementAndGet();
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,is_general,business_id) VALUES(?,?,?,FALSE,?)",owner,raw,publicId,businessA);
        origin=new UiEventTicketScope(owner,raw,publicId,"BUSINESS",businessA);
    }

    @AfterEach
    void restoreFlags() { flags.getFoundation().setEnabled(false); flags.setUiSelector(false); flags.setStrictReadScope(false); }

    @Test
    void catalogRequiresExplicitBusinessAndEscapesTitlesWithoutExposingOtherBusinessOrBody() throws Exception {
        mvc.perform(auth(get(ROOT),viewer)).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control",containsString("no-store")))
            .andExpect(content().string(containsString("Выберите один доступный бизнес")))
            .andExpect(content().string(not(containsString("Title A"))));
        var result=mvc.perform(auth(get(ROOT).param("businessId",Long.toString(businessA)),viewer))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Title A &lt;unsafe&gt;")))
            .andExpect(content().string(not(containsString("PRIVATE_B_SECRET"))))
            .andExpect(content().string(not(containsString("Title B secret"))))
            .andExpect(content().string(not(containsString(BODY))))
            .andExpect(content().string(not(containsString("data-ai-source-create")))).andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("data-page-key=\"ai-sources\"");
        mvc.perform(auth(get(ROOT).param("businessId",Long.toString(businessB)),all))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Title B secret")))
            .andExpect(content().string(not(containsString("Title A"))));
    }

    @ParameterizedTest @ValueSource(strings={"VIEWER","OPERATOR","MANAGER"})
    void nonConfigureRolesCanReadButCannotOpenCreateOrSubmitMutations(String role) throws Exception {
        String identity=switch(role) { case "VIEWER"->viewer; case "OPERATOR"->operator; default->manager; };
        mvc.perform(auth(edit(sourceA),identity)).andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"sourceReadOnly\"")))
            .andExpect(content().string(not(containsString("data-ai-source-form"))));
        mvc.perform(auth(get(ROOT+"/new").param("businessId",Long.toString(businessA)),identity)).andExpect(status().isForbidden());
        mvc.perform(auth(create("new"),identity).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(auth(revise(sourceA),identity).with(csrf())).andExpect(status().isForbidden());
        assertUnchanged(sourceA);
    }

    @Test
    void pageAuthorityAnonymousAndCsrfUseTheActualSecurityFilterChain() throws Exception {
        mvc.perform(get(ROOT)).andExpect(status().is3xxRedirection());
        mvc.perform(get(ROOT).with(user(admin).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isForbidden());
        mvc.perform(create("no-page").with(user(admin).authorities(new SimpleGrantedAuthority("PAGE_DIALOGS"))).with(csrf()))
            .andExpect(status().isForbidden());
        mvc.perform(auth(create("no-csrf"),admin)).andExpect(redirectedUrl("/error/403"));
        mvc.perform(auth(create("bad-csrf"),admin).with(csrf()).with(r->{r.setParameter("_csrf","invalid");return r;}))
            .andExpect(redirectedUrl("/error/403"));
        assertThat(sources.find(businessA,admin,KNOWLEDGE,"no-csrf")).isEmpty();
        assertUnchanged(sourceA);
    }

    @ParameterizedTest @ValueSource(strings={"foreign","global","missing"})
    void pageOrGlobalAdminAuthorityDoesNotGrantBusinessData(String actor) throws Exception {
        String identity=actor.equals("foreign")?foreign:actor.equals("global")?global:"absent-canonical-account";
        mvc.perform(auth(get(ROOT).param("businessId",Long.toString(businessA)),identity)).andExpect(status().isForbidden());
        mvc.perform(auth(edit(sourceA),identity)).andExpect(status().isForbidden());
        mvc.perform(auth(create("denied"),identity).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(auth(revise(sourceA),identity).with(csrf())).andExpect(status().isForbidden());
        assertUnchanged(sourceA);
    }

    @Test
    void actualRenderedFormTokenCreatesDraftThenApprovalAndEditReloadPersistNewVersions() throws Exception {
        var opened=mvc.perform(auth(get(ROOT+"/new").param("businessId",Long.toString(businessA)),admin)).andExpect(status().isOk()).andReturn();
        String form=opened.getResponse().getContentAsString();
        var cookieJar=new LinkedHashMap<String,Cookie>();
        Cookie[] cookies=captureCookies(cookieJar,opened);
        assertThat(cookieJar).containsKey("XSRF-TOKEN");
        String token=hidden(form,"_csrf");
        String key=hidden(form,"key");
        var created=mvc.perform(auth(create(key),admin).cookie(cookies).param("_csrf",token)
                .param("status","APPROVED").param("kind","MEMORY").param("actor",foreign))
            .andExpect(status().is3xxRedirection()).andReturn();
        Source saved=sources.find(businessA,admin,KNOWLEDGE,key).orElseThrow();
        assertThat(saved.status()).isEqualTo(ScopedAiSourceService.Status.DRAFT);
        assertThat(saved.revision()).isEqualTo(1);
        assertThat(sources.history(businessA,admin,KNOWLEDGE,key,1).get(0)).containsEntry("actor",admin);
        String location=created.getResponse().getRedirectedUrl();
        cookies=captureCookies(cookieJar,created);
        var rendered=mvc.perform(auth(get(URI.create(location)),admin).cookie(cookies))
            .andExpect(status().isOk()).andReturn();
        String html=rendered.getResponse().getContentAsString();
        assertThat(hidden(html,"sourceId")).isEqualTo(Long.toString(saved.id()));
        assertThat(hidden(html,"revision")).isEqualTo("1");
        assertThat(html).contains("&lt;/textarea&gt;&lt;script&gt;window.pwned=1&lt;/script&gt;")
            .doesNotContain("<script>window.pwned=1");
        assertThat(html.indexOf("data-ai-source-draft")).isGreaterThan(0).isLessThan(html.indexOf("data-ai-source-approve"));
        cookies=captureCookies(cookieJar,rendered);
        var reviewed=mvc.perform(auth(revise(saved),admin).cookie(cookies).param("_csrf",hidden(html,"_csrf")))
            .andExpect(redirectedUrl(location)).andReturn();
        Source approved=sources.find(businessA,admin,KNOWLEDGE,key).orElseThrow();
        assertThat(approved.status()).isEqualTo(APPROVED);
        assertThat(approved.revision()).isEqualTo(2);
        cookies=captureCookies(cookieJar,reviewed);
        var reopened=mvc.perform(auth(get(URI.create(location)),admin).cookie(cookies)).andExpect(status().isOk()).andReturn();
        cookies=captureCookies(cookieJar,reopened);
        mvc.perform(auth(replace(revise(approved),"status","DRAFT"),admin).cookie(cookies)
            .param("_csrf",hidden(reopened.getResponse().getContentAsString(),"_csrf"))).andExpect(redirectedUrl(location));
        Source draft=sources.find(businessA,admin,KNOWLEDGE,key).orElseThrow();
        assertThat(draft.revision()).isEqualTo(3);
        assertThat(draft.status()).isEqualTo(ScopedAiSourceService.Status.DRAFT);
        Files.writeString(Path.of("..","..","r132-ai-source-preview.html"),html);
    }

    @Test
    void opaqueKeysSurviveRedirectsAndStaleRevisionOrCrossBusinessFormCannotOverwrite() throws Exception {
        var result=mvc.perform(auth(revise(sourceA),admin).with(csrf())).andExpect(status().is3xxRedirection()).andReturn();
        assertThat(result.getResponse().getRedirectedUrl()).contains("key=%20opaque%2F").doesNotContain("uiPreferences","PRIVATE_A");
        mvc.perform(auth(get(URI.create(result.getResponse().getRedirectedUrl())),admin)).andExpect(status().isOk());
        mvc.perform(auth(revise(sourceA),admin).with(csrf())).andExpect(status().isConflict());
        mvc.perform(auth(replace(revise(sourceA),"businessId",Long.toString(businessB)),all).with(csrf())).andExpect(status().isConflict());
        assertUnchanged(sourceB);
        assertThat(sources.find(businessA,admin,KNOWLEDGE,KEY).orElseThrow().revision()).isEqualTo(2);
    }

    @ParameterizedTest @ValueSource(strings={"revoke","disabled","blocked","collision","archive"})
    void staleAuthenticatedSessionsLoseCatalogAndWritesOnTheNextSqlOperation(String mutation) throws Exception {
        mvc.perform(auth(edit(sourceA),admin)).andExpect(status().isOk());
        mutate(mutation);
        mvc.perform(auth(edit(sourceA),admin)).andExpect(status().isForbidden());
        mvc.perform(auth(create("late"),admin).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(auth(revise(sourceA),admin).with(csrf())).andExpect(status().isForbidden());
        assertUnchanged(sourceA);
    }

    @ParameterizedTest @ValueSource(strings={"revoke","disabled","blocked","archive","recreated"})
    void finalMutationReprovesAuthorityAndSourceIdentityAfterTheControllerPrecheck(String mutation) throws Exception {
        doAnswer(call->{
            boolean allowed=(Boolean)call.callRealMethod();
            if (mutation.equals("recreated")) {
                jdbc.update("DELETE FROM ai_scoped_sources WHERE id=?",sourceA.id());
                assertThat(sources.createKnowledge(businessA,admin,KEY,"Replacement","Replacement body")).isTrue();
            } else mutate(mutation);
            return allowed;
        }).when(sources).canConfigure(eq(businessA),eq(admin));
        mvc.perform(auth(revise(sourceA),admin).with(csrf())).andExpect(status().isConflict());
        var row=jdbc.queryForMap("SELECT title,body,revision FROM ai_scoped_sources WHERE business_id=? AND source_key=?",businessA,KEY);
        assertThat(row).containsEntry("revision",1L).containsEntry("body",mutation.equals("recreated")?"Replacement body":BODY);
    }

    @Test
    void memoryCanBeReviewedButOriginChangeClosesExistingPageAndMutation() throws Exception {
        assertThat(sources.createMemory(origin,admin,"memory","Memory title","Curated solution")).isTrue();
        Source memory=sources.find(businessA,admin,MEMORY,"memory").orElseThrow();
        mvc.perform(auth(edit(memory),admin)).andExpect(status().isOk())
            .andExpect(content().string(containsString("Решение из обращения")))
            .andExpect(content().string(not(containsString(origin.ticketId()))));
        mvc.perform(auth(revise(memory),admin).with(csrf())).andExpect(status().is3xxRedirection());
        jdbc.update("UPDATE tickets SET business_id=? WHERE user_id=? AND ticket_id=?",businessB,origin.ticketUserId(),origin.ticketId());
        mvc.perform(auth(edit(memory),admin)).andExpect(status().isNotFound());
        mvc.perform(auth(replace(revise(memory),"revision","2"),admin).with(csrf())).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT revision FROM ai_scoped_sources WHERE id=?",Long.class,memory.id())).isEqualTo(2);
    }

    @Test
    void catalogPaginationAndFiltersKeepOneBusinessAndDoNotShowDraftBody() throws Exception {
        for (int i=0;i<27;i++) assertThat(sources.createKnowledge(businessA,admin,"page-"+i,"Page title "+i,"Not catalog body")).isTrue();
        var first=mvc.perform(auth(get(ROOT).param("businessId",Long.toString(businessA)).param("q","Page title"),viewer))
            .andExpect(status().isOk()).andReturn();
        String html=first.getResponse().getContentAsString();
        assertThat(html.split("data-ai-source-row",-1)).hasSize(26);
        assertThat(html).contains("data-ai-source-next").doesNotContain("Not catalog body","Title B secret");
        mvc.perform(auth(get(ROOT).param("businessId",Long.toString(businessA)).param("status","APPROVED"),viewer))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Материалы не найдены")));
    }

    @ParameterizedTest @ValueSource(strings={"foundation","selector"})
    void existingFlagsCloseAllNewEntrypointsWithoutChangingTheirValuesInRuntime(String disabled) throws Exception {
        if (disabled.equals("foundation")) flags.getFoundation().setEnabled(false); else flags.setUiSelector(false);
        mvc.perform(auth(get(ROOT),admin)).andExpect(status().isNotFound());
        mvc.perform(auth(edit(sourceA),admin)).andExpect(status().isNotFound());
        mvc.perform(auth(get(ROOT+"/new").param("businessId",Long.toString(businessA)),admin)).andExpect(status().isNotFound());
        mvc.perform(auth(create("disabled"),admin).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(auth(revise(sourceA),admin).with(csrf())).andExpect(status().isNotFound());
        assertUnchanged(sourceA);
    }

    @Test
    void malformedBoundsEnumsAndContentAreRejectedWithoutWrites() throws Exception {
        mvc.perform(auth(get(ROOT).param("businessId","all"),admin)).andExpect(status().isBadRequest());
        mvc.perform(auth(get(ROOT).param("businessId",Long.toString(businessA)).param("before","0"),admin)).andExpect(status().isBadRequest());
        mvc.perform(auth(get(ROOT).param("businessId",Long.toString(businessA)).param("kind","GLOBAL"),admin)).andExpect(status().isBadRequest());
        mvc.perform(auth(replace(create("invalid"),"title"," "),admin).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(auth(replace(revise(sourceA),"sourceId","0"),admin).with(csrf())).andExpect(status().isBadRequest());
        assertUnchanged(sourceA);
    }

    // Exercise the production cookie repository and XOR handler without replacing the filter's repository,
    // which SecurityMockMvcRequestPostProcessors.csrf() does. The rendered-form test also uses an actual GET token.
    private RequestPostProcessor csrf() {
        return request->{
            CsrfToken token=CookieCsrfTokenRepository.withHttpOnlyFalse().generateToken(request);
            request.setCookies(new Cookie("XSRF-TOKEN",token.getToken()));
            new XorCsrfTokenRequestAttributeHandler().handle(request,new MockHttpServletResponse(),()->token);
            CsrfToken masked=(CsrfToken)request.getAttribute(CsrfToken.class.getName());
            request.setParameter(masked.getParameterName(),masked.getToken());
            return request;
        };
    }

    private Cookie[] captureCookies(Map<String,Cookie> jar,MvcResult result) {
        for (Cookie cookie:result.getResponse().getCookies()) {
            if (cookie.getMaxAge()==0) jar.remove(cookie.getName()); else jar.put(cookie.getName(),cookie);
        }
        return jar.values().toArray(Cookie[]::new);
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request,String identity) {
        return request.with(user(identity).authorities(identity.equals(global)
            ? List.of(new SimpleGrantedAuthority("PAGE_KNOWLEDGE_BASE"),new SimpleGrantedAuthority("ROLE_ADMIN"))
            : List.of(new SimpleGrantedAuthority("PAGE_KNOWLEDGE_BASE"))));
    }
    private MockHttpServletRequestBuilder replace(MockHttpServletRequestBuilder request,String name,String value) {
        return request.with(r->{r.setParameter(name,value);return r;});
    }
    private MockHttpServletRequestBuilder create(String key) {
        return post(ROOT+"/create").param("businessId",Long.toString(businessA)).param("key",key).param("title","New title").param("body",BODY);
    }
    private MockHttpServletRequestBuilder edit(Source source) {
        return get(ROOT+"/edit").param("businessId",source.businessId().toString()).param("kind",source.kind().name()).param("key",source.key());
    }
    private MockHttpServletRequestBuilder revise(Source source) {
        return post(ROOT+"/revise").param("businessId",source.businessId().toString()).param("kind",source.kind().name())
            .param("key",source.key()).param("sourceId",Long.toString(source.id())).param("revision",Long.toString(source.revision()))
            .param("title",source.title()).param("body",source.body()).param("status","APPROVED");
    }
    private String hidden(String html,String name) {
        var matcher=Pattern.compile("<input[^>]*name=\""+name+"\"[^>]*value=\"([^\"]*)\"").matcher(html);
        assertThat(matcher.find()).as("rendered hidden field %s",name).isTrue();
        return HtmlUtils.htmlUnescape(matcher.group(1));
    }
    private long account(String username) {
        return jdbc.queryForObject("INSERT INTO users(username,password,enabled) VALUES(?,'not-used',TRUE) RETURNING id",Long.class,username);
    }
    private long business(String code,String name) {
        return jdbc.queryForObject("INSERT INTO businesses(code,display_name) VALUES(?,?) RETURNING id",Long.class,code,name);
    }
    private void membership(long userId,long businessId,String role) {
        jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(?,?,?)",userId,businessId,role);
    }
    private void assertUnchanged(Source source) {
        assertThat(jdbc.queryForObject("SELECT revision FROM ai_scoped_sources WHERE id=?",Long.class,source.id())).isEqualTo(source.revision());
    }
    private void mutate(String mutation) {
        switch (mutation) {
            case "revoke" -> jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=? AND business_id=?",adminId,businessA);
            case "disabled" -> jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?",adminId);
            case "blocked" -> jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=?",adminId);
            case "collision" -> jdbc.update("INSERT INTO users(username,password,enabled) VALUES(?,'not-used',FALSE)",admin.toUpperCase(java.util.Locale.ROOT));
            case "archive" -> jdbc.update("UPDATE businesses SET status='archived' WHERE id=?",businessA);
            default -> throw new IllegalArgumentException(mutation);
        }
    }
}
