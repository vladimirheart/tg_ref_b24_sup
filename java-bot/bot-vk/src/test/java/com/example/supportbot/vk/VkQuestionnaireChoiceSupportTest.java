package com.example.supportbot.vk;

import static org.assertj.core.api.Assertions.*;
import com.example.supportbot.service.PanelRuntimeConfigClient.*;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.vk.api.sdk.objects.messages.Keyboard;
import com.vk.api.sdk.objects.messages.Message;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VkQuestionnaireChoiceSupportTest {
    private final String nonce = "123456abcdef";
    private ChoiceCatalog catalog(int size) {
        return new ChoiceCatalog(1,55,"c".repeat(64),IntStream.rangeClosed(1,size).mapToObj(i ->
                new Choice("BUSINESS",i,"business-"+i,"Бизнес "+i+"😀".repeat(40),"q-"+i,"a".repeat(64))).toList());
    }
    @ParameterizedTest @ValueSource(ints={1,8,9,100})
    void everyChoiceIsReachableByNativeSdkKeyboardWithoutChangingIdentity(int size) {
        var menu=catalog(size);var gson=new Gson();var seen=new HashSet<Long>();
        for(int page=0;page<VkQuestionnaireChoiceSupport.pageCount(menu);page++) {
            var body=VkQuestionnaireChoiceSupport.keyboard(menu,nonce,page);
            var tree=JsonParser.parseString(gson.toJson(body));
            var keyboard=body;
            assertThat(VkQuestionnaireChoiceSupport.menuText(menu,nonce,page).length()).isLessThan(2200);
            assertThat(keyboard.getInline()).isTrue();assertThat(keyboard.getOneTime()).isFalse();
            assertThat(keyboard.getButtons()).hasSizeLessThanOrEqualTo(5);
            assertThat(keyboard.getButtons().stream().mapToInt(List::size).sum()).isLessThanOrEqualTo(10);
            for(var row:tree.getAsJsonObject().getAsJsonArray("buttons")) {
                assertThat(row.getAsJsonArray().size()).isLessThanOrEqualTo(2);
                for(var button:row.getAsJsonArray()) {
                    var action=button.getAsJsonObject().getAsJsonObject("action");
                    String label=action.get("label").getAsString(),payload=action.get("payload").getAsString();
                    assertThat(label.length()).isLessThanOrEqualTo(40);
                    assertThat(Character.isHighSurrogate(label.charAt(label.length()-1))).isFalse();
                    assertThat(payload.length()).isLessThan(255);
                    var resolved=VkQuestionnaireChoiceSupport.resolve(menu,nonce,"untrusted label",payload).orElseThrow();
                    if(!resolved.page()) {
                        seen.add(resolved.value());
                        assertThat(label).endsWith(" #"+resolved.value());
                    }
                }
            }
        }
        assertThat(seen).hasSize(size);
    }
    @ParameterizedTest @ValueSource(strings={"old-nonce","foreign-owner","overflow","bad-json","negative-page","foreign-page","label","number"})
    void untrustedOrDelayedInputCannotChoose(String kind) {
        String payload=switch(kind) {
            case "old-nonce" -> VkQuestionnaireChoiceSupport.payload("ffffffffffff","business",1);
            case "foreign-owner" -> VkQuestionnaireChoiceSupport.payload(nonce,"business",999);
            case "overflow" -> "{\"iguana_questionnaire\":\""+nonce+"\",\"kind\":\"business\",\"value\":\"9223372036854775808\"}";
            case "bad-json" -> "{iguana_questionnaire";
            case "negative-page" -> VkQuestionnaireChoiceSupport.payload(nonce,"page",-1);
            case "foreign-page" -> VkQuestionnaireChoiceSupport.payload(nonce,"page",99);
            default -> null;
        };
        assertThat(VkQuestionnaireChoiceSupport.resolve(catalog(2),nonce,kind.equals("number")?"1":"Бизнес 1",payload)).isEmpty();
    }
    @Test void manualCodeStillRequiresTheSavedGenerationAndMembership() {
        assertThat(VkQuestionnaireChoiceSupport.resolve(catalog(2),nonce,"/business "+nonce+" 1",null)).contains(new VkQuestionnaireChoiceSupport.Action(false,1));
        assertThat(VkQuestionnaireChoiceSupport.resolve(catalog(2),nonce,"/business ffffffffffff 1",null)).isEmpty();
        assertThat(VkQuestionnaireChoiceSupport.isChoiceCommand("anything","{iguana_questionnaire")).isTrue();
    }
    @Test void providerIdentityIsConversationScopedNotCallbackEnvelopeOrText() {
        var first=new Message().setPeerId(1001L).setConversationMessageId(42).setId(0).setText("One");
        assertThat(VkQuestionnaireChoiceSupport.eventKey(first)).isEqualTo("vk-message:1001:42");
        first.setText("different").setId(999);
        assertThat(VkQuestionnaireChoiceSupport.eventKey(first)).isEqualTo("vk-message:1001:42");
        assertThatThrownBy(()->VkQuestionnaireChoiceSupport.eventKey(first.setConversationMessageId(0))).isInstanceOf(IllegalStateException.class);
    }
    @Test void messagesWithoutTextCanStillCarryAChoicePayload() {
        assertThat(VkQuestionnaireChoiceSupport.isChoiceCommand(null,null)).isFalse();
        assertThat(VkQuestionnaireChoiceSupport.isChoiceCommand(null,"{}")).isFalse();
        String payload=VkQuestionnaireChoiceSupport.payload(nonce,"business",1);
        assertThat(VkQuestionnaireChoiceSupport.isChoiceCommand(null,payload)).isTrue();
        assertThat(VkQuestionnaireChoiceSupport.resolve(catalog(2),nonce,null,payload))
            .contains(new VkQuestionnaireChoiceSupport.Action(false,1));
    }
    @ParameterizedTest @ValueSource(strings={"empty","oversize","version","hash","duplicate","general","template"})
    void invalidSavedMenusFailClosed(String kind) {
        var choices=new ArrayList<>(catalog(2).choices());
        switch(kind) {
            case "empty" -> choices.clear(); case "oversize" -> choices=new ArrayList<>(catalog(101).choices());
            case "duplicate" -> choices.set(1,choices.get(0));
            case "general" -> choices.set(0,new Choice("GENERAL",1,"a","A","q-a","a".repeat(64)));
            case "template" -> choices.set(1,new Choice("BUSINESS",2,"b","B",choices.get(0).questionTemplateId(),"b".repeat(64)));
        }
        var broken=new ChoiceCatalog(kind.equals("version")?4:1,55,kind.equals("hash")?"bad":"c".repeat(64),choices);
        assertThatThrownBy(()->VkQuestionnaireChoiceSupport.validate(broken,nonce)).isInstanceOf(IllegalStateException.class);
    }
}
