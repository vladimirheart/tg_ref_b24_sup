package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.*;
import com.example.supportbot.config.IntegrationPanelApiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SharedQuestionnaireClientTest {
    private String lastStartPath;
    private String writerVersion="1",mediaVersion="1",vkMediaVersion="1";
    private HttpServer server;
    private PanelRuntimeConfigClient client;
    private final ObjectMapper mapper=new ObjectMapper();
    private ObjectNode catalog,selected;
    private volatile int status=200;
    private volatile boolean signatureValid;
    private final AtomicInteger requests=new AtomicInteger();
    @BeforeEach void setup() throws Exception {
        Map<String,Object> template=new LinkedHashMap<>();
        template.put("id","q-a");template.put("question_flow",List.of(Map.of("id","free","type","text","text","Private A")));
        String templateHash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(template)));
        catalog=mapper.valueToTree(Map.of("version",1,"channelId",8,"catalogSha256","c".repeat(64),"choices",List.of(
            Map.of("scopeKind","BUSINESS","businessId",7,"code","business-a","displayName","Business A","questionTemplateId","q-a","templateSha256",templateHash))));
        selected=mapper.createObjectNode();selected.put("version",1).put("channelId",8).put("scopeKind","BUSINESS")
            .put("scopeSelectionSource","USER_SELECTION").put("businessId",7).put("questionTemplateId","q-a")
            .put("templateSha256",templateHash).put("catalogSha256","c".repeat(64));
        selected.set("botSettings",mapper.valueToTree(Map.of("active_template_id","q-a","question_templates",List.of(template))));
        selected.set("locationTree",mapper.createObjectNode());selected.set("presetDefinitions",mapper.createObjectNode());
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/api/bot/channels/8/questionnaire",exchange->{
            lastStartPath=exchange.getRequestURI().toString();
            if(lastStartPath.contains("forStart=true") && !"missing".equals(writerVersion))
                exchange.getResponseHeaders().set("X-Iguana-Questionnaire-Writer-Version",writerVersion);
            if(lastStartPath.contains("forStart=true") && !"missing".equals(vkMediaVersion)) exchange.getResponseHeaders().set("X-Iguana-Questionnaire-VK-Media-Version",vkMediaVersion);
            if(lastStartPath.contains("forStart=true") && !"missing".equals(mediaVersion)) exchange.getResponseHeaders().set("X-Iguana-Questionnaire-Media-Version",mediaVersion);
            requests.incrementAndGet();
            try {
                String time=exchange.getRequestHeaders().getFirst("X-Iguana-Request-Timestamp");
                String canonical="GET\n"+exchange.getRequestURI()+"\n"+time+"\n"
                    +HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new byte[0]));
                Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
                String expected=HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
                signatureValid=expected.equals(exchange.getRequestHeaders().getFirst("X-Iguana-Request-Signature"))
                    && "test-token".equals(exchange.getRequestHeaders().getFirst("X-Iguana-Bot-Api-Token"));
                byte[] body=mapper.writeValueAsBytes(exchange.getRequestURI().getPath().endsWith("/choices")?catalog:selected);
                exchange.sendResponseHeaders(status,body.length);try(var output=exchange.getResponseBody()){output.write(body);}
            } catch(Exception ex) {exchange.close();}
        });
        server.start();
        var properties=new IntegrationPanelApiProperties();properties.setBaseUrl("http://127.0.0.1:"+server.getAddress().getPort());
        properties.setToken("test-token");properties.setRequestSigningEnabled(true);properties.setSignatureSecret("test-secret");
        client=new PanelRuntimeConfigClient(properties,mapper,new PanelApiRequestHeadersFactory(properties));
    }
    @AfterEach void stop(){server.stop(0);}
    @Test void startHandshakeSignsTheWriterGateInBothRequests() {
        var choices=client.choicesForStart(8);
        assertThat(signatureValid).isTrue();assertThat(lastStartPath).endsWith("/choices?forStart=true");
        client.selectForStart(choices,7);
        assertThat(signatureValid).isTrue();assertThat(lastStartPath).contains("&forStart=true");
    }
    @ParameterizedTest @ValueSource(strings={"missing","0","2"})
    void anOlderOrIncompatibleBackendCannotPretendThatItAcceptedTheStartGate(String version) {
        writerVersion=version;
        var catalog=client.choices(8); // The read-only R143 handshake remains compatible.
        assertThatThrownBy(()->client.choicesForStart(8)).isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);
        assertThatThrownBy(()->client.selectForStart(catalog,7)).isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);
    }
    @Test void signedChoiceAndSelectionRoundtripPreservesScopeAndTemplateBytes() {
        var choices=client.choices(8);assertThat(signatureValid).isTrue();
        var runtime=client.select(choices,7);
        assertThat(signatureValid).isTrue();assertThat(runtime.scopeKind()).isEqualTo("BUSINESS");
        assertThat(runtime.scopeSelectionSource()).isEqualTo("USER_SELECTION");
        assertThat(runtime.businessId()).isEqualTo(7);assertThat(runtime.botSettings().toString()).contains("Private A");
        assertThat(runtime.locationTree()).isEmpty();assertThat(runtime.presetDefinitions()).isEmpty();
        int before=requests.get();
        assertThatThrownBy(()->client.select(choices,999)).isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);
        assertThat(requests.get()).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"version","channel","hash","empty","duplicate-business","duplicate-template","scope","business","template","code","name","limit"})
    void invalidMenusFailClosed(String mutation) {
        ObjectNode choice=(ObjectNode)catalog.withArray("choices").get(0);
        switch(mutation) {
            case "version"->catalog.put("version",2);
            case "channel"->catalog.put("channelId",9);
            case "hash"->catalog.put("catalogSha256","bad");
            case "empty"->catalog.set("choices",mapper.createArrayNode());
            case "duplicate-business"->catalog.withArray("choices").add(choice.deepCopy().put("questionTemplateId","q-b"));
            case "duplicate-template"->catalog.withArray("choices").add(choice.deepCopy().put("businessId",9));
            case "scope"->choice.put("scopeKind","GENERAL");
            case "business"->choice.put("businessId",0);
            case "template"->choice.put("questionTemplateId","");
            case "code"->choice.put("code","");
            case "name"->choice.remove("displayName");
            case "limit"->{for(int i=0;i<100;i++)catalog.withArray("choices").add(choice.deepCopy().put("businessId",i+100).put("questionTemplateId","q-"+i));}
        }
        assertThatThrownBy(()->client.choices(8)).isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);
    }
    @ParameterizedTest @ValueSource(strings={"version","channel","business","scope","source","catalog","template-hash","template-id","payload","extra-template","active-id","locations","presets","missing-settings","extra-settings"})
    void selectedRuntimeMustMatchMenuScopeHashAndActualPayload(String mutation) {
        var menu=client.choices(8);
        switch(mutation) {
            case "version"->selected.put("version",2);
            case "channel"->selected.put("channelId",9);
            case "business"->selected.put("businessId",9);
            case "scope"->selected.put("scopeKind","GENERAL");
            case "source"->selected.put("scopeSelectionSource","GENERAL_CHANNEL");
            case "catalog"->selected.put("catalogSha256","b".repeat(64));
            case "template-hash"->selected.put("templateSha256","b".repeat(64));
            case "template-id"->selected.put("questionTemplateId","q-b");
            case "payload"->((ObjectNode)selected.path("botSettings").path("question_templates").get(0)).put("name","changed");
            case "extra-template"->((ArrayNode)selected.path("botSettings").path("question_templates")).add(mapper.createObjectNode().put("id","q-b"));
            case "active-id"->((ObjectNode)selected.path("botSettings")).put("active_template_id","q-b");
            case "locations"->selected.set("locationTree",mapper.valueToTree(Map.of("global","secret")));
            case "presets"->selected.set("presetDefinitions",mapper.valueToTree(Map.of("global","secret")));
            case "missing-settings"->selected.remove("botSettings");
            case "extra-settings"->((ObjectNode)selected.path("botSettings")).put("global_secret","not-for-this-business");
        }
        assertThatThrownBy(()->client.select(menu,7)).isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);
    }
    @ParameterizedTest @ValueSource(ints={401,403,404,409,500})
    void selectionOrMenuHttpFailureNeverCreatesFallback(int responseStatus) {
        var menu=client.choices(8);status=responseStatus;
        assertThatThrownBy(()->client.select(menu,7)).isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);
        assertThatThrownBy(()->client.choices(8)).isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);
    }

    private void v2(String kind) throws Exception {
        writerVersion="2";catalog.put("version",2);selected.put("version",2);
        Map<String,Object> template=Map.of("id","q-general","question_flow",List.of(Map.of("id","free","type","text","text","General private")));
        String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(template)));
        ObjectNode general=mapper.createObjectNode().put("scopeKind","GENERAL").putNull("businessId").put("code","GENERAL")
            .put("displayName","Без бизнеса").put("questionTemplateId","q-general").put("templateSha256",sha);
        catalog.withArray("choices").add(general);
        if("GENERAL".equals(kind)) {
            selected.put("scopeKind","GENERAL").putNull("businessId").put("questionTemplateId","q-general").put("templateSha256",sha);
            selected.set("botSettings",mapper.valueToTree(Map.of("active_template_id","q-general","question_templates",List.of(template))));
        }
    }
    @Test void v2GeneralSignsTheNullOwnerSelectionAndKeepsItAcrossJsonAndScope() throws Exception {
        v2("GENERAL");var choices=client.choicesV2ForStart(8);assertThat(signatureValid).isTrue();
        assertThat(lastStartPath).contains("/questionnaire/v2/choices?forStart=true");
        var result=client.selectGeneralForStart(choices);assertThat(signatureValid).isTrue();
        assertThat(lastStartPath).contains("/v2/selection?scopeKind=GENERAL&catalogSha256=").doesNotContain("businessId");
        assertThat(result.businessId()).isNull();assertThat(result.botSettings().toString()).contains("General private").doesNotContain("Private A");
        assertThat(QuestionnaireScope.fromSelection(mapper.readValue(mapper.writeValueAsBytes(result),PanelRuntimeConfigClient.SelectedQuestionnaire.class)))
            .isEqualTo(new QuestionnaireScope("GENERAL","USER_SELECTION","q-general",result.templateSha256(),null,choices.catalogSha256()));
    }
    @Test void v2BusinessUsesTheSameMixedCatalogWithoutReadingGeneralPayload() throws Exception {
        v2("BUSINESS");var choices=client.choicesV2ForStart(8);var result=client.selectForStart(choices,7);
        assertThat(lastStartPath).contains("/v2/selection?scopeKind=BUSINESS&businessId=7&");
        assertThat(result.businessId()).isEqualTo(7L);assertThat(result.botSettings().toString()).doesNotContain("General private");
    }
    @ParameterizedTest @ValueSource(strings={"missing","0","1","3"})
    void v2NeverFallsBackToAnOlderWriterOrAnotherProtocol(String ack) throws Exception {
        v2("GENERAL");var choices=client.choicesV2ForStart(8);writerVersion=ack;
        int count=requests.get();assertThatThrownBy(()->client.choicesV2ForStart(8)).isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);
        assertThat(requests.get()).isEqualTo(count+1);assertThat(lastStartPath).contains("/v2/");
        assertThatThrownBy(()->client.selectGeneralForStart(choices)).isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);
        assertThat(lastStartPath).contains("scopeKind=GENERAL").doesNotContain("businessId");
    }
    @ParameterizedTest @ValueSource(strings={"fake-zero","fake-owner","duplicate-general","wrong-version","kind","missing-hash","duplicate-template","no-business"})
    void v2GeneralMenuRejectsFakeOwnersAndAmbiguousOrDowngradedIdentity(String mutation) throws Exception {
        v2("GENERAL");ObjectNode general=(ObjectNode)catalog.withArray("choices").get(1);
        switch(mutation) {
            case "fake-zero"->general.put("businessId",0);case "fake-owner"->general.put("businessId",7);
            case "duplicate-general"->catalog.withArray("choices").add(general.deepCopy().put("questionTemplateId","other"));
            case "wrong-version"->catalog.put("version",1);case "kind"->general.put("scopeKind","UNRESOLVED");
            case "missing-hash"->general.remove("templateSha256");case "duplicate-template"->general.put("questionTemplateId","q-a");
            case "no-business"->catalog.withArray("choices").remove(0);
        }
        assertThatThrownBy(()->client.choicesV2ForStart(8)).isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);
    }
    @ParameterizedTest @ValueSource(strings={"fake-zero","fake-owner","source","kind","version","catalog","template","bytes","locations","presets"})
    void v2SelectedGeneralRequiresExactSnapshotAndNoGlobalSideData(String mutation) throws Exception {
        v2("GENERAL");var choices=client.choicesV2ForStart(8);
        switch(mutation) {
            case "fake-zero"->selected.put("businessId",0);case "fake-owner"->selected.put("businessId",7);
            case "source"->selected.put("scopeSelectionSource","GENERAL_CHANNEL");case "kind"->selected.put("scopeKind","BUSINESS");
            case "version"->selected.put("version",1);case "catalog"->selected.put("catalogSha256","f".repeat(64));
            case "template"->selected.put("questionTemplateId","q-a");
            case "bytes"->((ObjectNode)selected.path("botSettings").path("question_templates").get(0)).put("changed",true);
            case "locations"->selected.set("locationTree",mapper.valueToTree(Map.of("foreign","location")));
            case "presets"->selected.set("presetDefinitions",mapper.valueToTree(Map.of("foreign","preset")));
        }
        assertThatThrownBy(()->client.selectGeneralForStart(choices)).isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);
    }


    private void v3(String kind) throws Exception {
        v2(kind);writerVersion="3";catalog.put("version",3);selected.put("version",3);
        var tree=Map.<String,Object>of("businessId",7L,"locations",List.of(Map.of("id",91L,"name","Only A","locationType","Store","city","North")));
        var presets=Map.<String,Object>of("businessId",7L,"groups",Map.of("department",Map.of("name",List.of("Kitchen"))));
        ((ObjectNode)catalog.withArray("choices").get(0)).put("resourcesSha256",SelectedQuestionnaireOptions.resourceSha256(tree,presets));
        ((ObjectNode)catalog.withArray("choices").get(1)).put("resourcesSha256",SelectedQuestionnaireOptions.resourceSha256(Map.of(),Map.of()));
        if("BUSINESS".equals(kind)) {selected.set("locationTree",mapper.valueToTree(tree));selected.set("presetDefinitions",mapper.valueToTree(presets));}
    }
    @Test void v3SignsTheResourceProtocolAndBindsSelectedBytesToTheSavedMenu() throws Exception {
        v3("BUSINESS");var choices=client.choicesV3ForStart(8);assertThat(signatureValid).isTrue();
        assertThat(lastStartPath).endsWith("/v3/choices?forStart=true");
        var result=client.selectForStart(choices,7);assertThat(signatureValid).isTrue();
        assertThat(lastStartPath).contains("/v3/selection?scopeKind=BUSINESS&businessId=7&");
        assertThat(result.locationTree().toString()).contains("Only A");
        var restored=mapper.readValue(mapper.writeValueAsBytes(result),PanelRuntimeConfigClient.SelectedQuestionnaire.class);
        SelectedQuestionnaireOptions.validate(restored,choices);assertThat(restored).isEqualTo(result);
    }
    @Test void v3GeneralKeepsANullOwnerAndNoBusinessResources() throws Exception {
        v3("GENERAL");var choices=client.choicesV3ForStart(8);var result=client.selectGeneralForStart(choices);
        assertThat(result.businessId()).isNull();assertThat(result.locationTree()).isEmpty();assertThat(result.presetDefinitions()).isEmpty();
        assertThat(lastStartPath).contains("/v3/selection?scopeKind=GENERAL").doesNotContain("businessId");
    }
    @ParameterizedTest @ValueSource(strings={"missing","1","2","4"})
    void v3RequiresItsExactWriterAcknowledgementWithoutDowngrade(String ack) throws Exception {
        v3("BUSINESS");var choices=client.choicesV3ForStart(8);writerVersion=ack;
        int before=requests.get();assertThatThrownBy(()->client.choicesV3ForStart(8)).isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);
        assertThat(requests.get()).isEqualTo(before+1);assertThat(lastStartPath).contains("/v3/");
        assertThatThrownBy(()->client.selectForStart(choices,7)).isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);
    }
    @ParameterizedTest @ValueSource(strings={"owner","fractional-owner","duplicate-location","missing-id","extra-root","location-bytes","preset-bytes","missing-groups","missing-resources"})
    void v3RejectsForeignMalformedAndSubstitutedResourceSnapshots(String mutation) throws Exception {
        v3("BUSINESS");var menu=client.choicesV3ForStart(8);var tree=(ObjectNode)selected.path("locationTree");
        var row=(ObjectNode)tree.withArray("locations").get(0);
        switch(mutation) {
            case "owner"->tree.put("businessId",8);case "fractional-owner"->tree.put("businessId",7.1);
            case "duplicate-location"->tree.withArray("locations").add(row.deepCopy());case "missing-id"->row.remove("id");
            case "extra-root"->tree.put("global","secret");case "location-bytes"->row.put("name","Substituted");
            case "preset-bytes"->((ObjectNode)selected.path("presetDefinitions").path("groups").path("department")).set("name",mapper.valueToTree(List.of("Other business")));
            case "missing-groups"->((ObjectNode)selected.path("presetDefinitions")).remove("groups");default->selected.set("locationTree",mapper.createObjectNode());
        }
        assertThatThrownBy(()->client.selectForStart(menu,7)).isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);
    }
    @Test void v3MenuWithoutTheCommittedResourceDigestFailsBeforeSelection() throws Exception {
        v3("BUSINESS");((ObjectNode)catalog.withArray("choices").get(0)).remove("resourcesSha256");
        assertThatThrownBy(()->client.choicesV3ForStart(8)).isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);
    }

    private void r152Large(String kind,int count) throws Exception {
        v3(kind);writerVersion="4";catalog.put("version",4);selected.put("version",4);
        var business=(ObjectNode)catalog.withArray("choices").get(0);var general=(ObjectNode)catalog.withArray("choices").get(1);
        var all=mapper.createArrayNode();for(int index=0;index<count-2;index++)all.add(business.deepCopy().put("businessId",index+20).put("questionTemplateId","q-extra-"+index));
        all.add(business);all.add(general);catalog.set("choices",all);
    }
    @ParameterizedTest @ValueSource(strings={"BUSINESS","GENERAL"})
    void v4SignedCatalogAndSelectionSupportOneThousandChoicesWithFrozenResourceProof(String kind) throws Exception {
        r152Large(kind,1000);var menu=client.choicesV4ForStart(8);assertThat(menu.choices()).hasSize(1000);assertThat(signatureValid).isTrue();
        assertThat(lastStartPath).endsWith("/v4/choices?forStart=true");
        var result=kind.equals("GENERAL")?client.selectGeneralForStart(menu):client.selectForStart(menu,7);
        assertThat(signatureValid).isTrue();assertThat(result.version()).isEqualTo(4);assertThat(lastStartPath).contains("/v4/selection?");
        var restored=mapper.readValue(mapper.writeValueAsBytes(menu),PanelRuntimeConfigClient.ChoiceCatalog.class);
        SelectedQuestionnaireOptions.validate(result,restored);
        if(kind.equals("GENERAL")){assertThat(result.businessId()).isNull();assertThat(result.locationTree()).isEmpty();}
        else assertThat(result.locationTree().toString()).contains("Only A");
    }
    @ParameterizedTest @ValueSource(strings={"missing","0","1","2","3","5"})
    void v4RequiresExactAckAndNeverRequestsAnOlderProtocol(String ack) throws Exception {
        r152Large("BUSINESS",105);writerVersion=ack;int before=requests.get();
        assertThatThrownBy(()->client.choicesV4ForStart(8)).isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);
        assertThat(requests.get()).isEqualTo(before+1);assertThat(lastStartPath).contains("/v4/");
    }
    @ParameterizedTest @ValueSource(strings={"overflow","missing-resource-digest","old-capacity","foreign-resource"})
    void expandedCapacityCannotWeakenSelectionOrOlderCatalogValidation(String mutation) throws Exception {
        r152Large("BUSINESS",mutation.equals("overflow")?1001:105);
        if(mutation.equals("missing-resource-digest"))((ObjectNode)catalog.withArray("choices").get(0)).remove("resourcesSha256");
        if(mutation.equals("old-capacity")){catalog.put("version",3);writerVersion="3";assertThatThrownBy(()->client.choicesV3ForStart(8)).isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);return;}
        if(!mutation.equals("foreign-resource")){assertThatThrownBy(()->client.choicesV4ForStart(8)).isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);return;}
        var menu=client.choicesV4ForStart(8);((ObjectNode)selected.path("locationTree")).put("businessId",999);
        assertThatThrownBy(()->client.selectForStart(menu,7)).isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);
    }


    @ParameterizedTest @ValueSource(strings={"missing","0","2","01"})
    void v4RequiresTheMediaWriterCapabilityOnCatalogAndRestoredSelection(String ack) throws Exception {
        r152Large("BUSINESS",101);mediaVersion=ack;int before=requests.get();
        assertThatThrownBy(()->client.choicesV4ForStart(8)).isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);
        assertThat(requests.get()).isEqualTo(before+1);assertThat(lastStartPath).contains("/v4/choices");
        mediaVersion="1";var catalog=client.choicesV4ForStart(8);mediaVersion=ack;before=requests.get();
        assertThatThrownBy(()->client.selectForStart(catalog,7L)).isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);
        assertThat(requests.get()).isEqualTo(before+1);assertThat(lastStartPath).contains("/v4/selection");
    }
    @ParameterizedTest @ValueSource(strings={"missing","0","2","01"})
    void vkV4RequiresItsOwnCapabilityForNewAndRestoredSelections(String ack) throws Exception {
        r152Large("BUSINESS",101);vkMediaVersion=ack;
        assertThatThrownBy(()->client.choicesV4ForStart(8,true)).isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);
        var menu=client.choicesV4ForStart(8); // Telegram's capability remains compatible with R153.
        assertThatThrownBy(()->client.selectForStart(menu,7,true)).isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);
        vkMediaVersion="1";assertThat(client.selectForStart(menu,7,true).businessId()).isEqualTo(7L);
    }
    @ParameterizedTest @ValueSource(strings={"missing","0","2","01"})
    void vkV4GeneralSelectionAlsoRequiresItsOwnCapability(String ack) throws Exception {
        r152Large("GENERAL",101);var general=client.choicesV4ForStart(8,true);vkMediaVersion=ack;
        assertThatThrownBy(()->client.selectGeneralForStart(general,true)).isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);
        vkMediaVersion="1";assertThat(client.selectGeneralForStart(general,true).businessId()).isNull();
    }
}
