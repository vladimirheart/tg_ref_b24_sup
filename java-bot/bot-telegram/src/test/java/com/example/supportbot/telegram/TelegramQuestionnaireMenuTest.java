package com.example.supportbot.telegram;

import static org.assertj.core.api.Assertions.*;
import com.example.supportbot.service.PanelRuntimeConfigClient.*;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TelegramQuestionnaireMenuTest {
    private final String nonce="123456abcdef";
    private ChoiceCatalog catalog(int count) {
        return new ChoiceCatalog(1,55,"c".repeat(64),IntStream.rangeClosed(1,count).mapToObj(id->
                new Choice("BUSINESS",id,"b-"+id,"Бизнес "+id+"😀".repeat(100),"q-"+id,"a".repeat(64))).toList());
    }
    @ParameterizedTest @ValueSource(ints={1,8,9,100})
    void everyNativeReplyButtonResolvesToTheSameSavedBusiness(int count) {
        var catalog=catalog(count);var seen=new HashSet<Long>();
        for(int page=0;page<TelegramQuestionnaireMenu.pages(catalog);page++) {
            var keyboard=TelegramQuestionnaireMenu.keyboard(catalog,nonce,page);
            var serialized=new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(keyboard);
            assertThat(serialized.path("keyboard").size()).isEqualTo(keyboard.getKeyboard().size());
            assertThat(serialized.path("one_time_keyboard").asBoolean()).isTrue();
            for(var row:serialized.path("keyboard")) for(var button:row)
                assertThat(TelegramQuestionnaireMenu.resolve(catalog,nonce,button.path("text").asText())).isPresent();
            assertThat(keyboard.getKeyboard()).hasSizeLessThanOrEqualTo(5);
            assertThat(TelegramQuestionnaireMenu.text(catalog,nonce,page).length()).isLessThan(2200);
            for(var row:keyboard.getKeyboard()) for(var button:row) {
                String text=button.getText();assertThat(text.length()).isLessThanOrEqualTo(96);
                var resolved=TelegramQuestionnaireMenu.resolve(catalog,nonce,text).orElseThrow();
                if(!resolved.page()) {seen.add(resolved.value());assertThat(text).endsWith(":"+resolved.value()+"]");}
            }
        }
        assertThat(seen).hasSize(count);
    }
    @ParameterizedTest @ValueSource(strings={"Business 1","1","/business ffffffffffff 1","/business 123456abcdef 999",
            "/business 123456abcdef 9223372036854775808","Далее [ffffffffffff:page:1]","Далее [123456abcdef:page:99]"})
    void arbitraryOrOldTextCannotSelect(String text) {assertThat(TelegramQuestionnaireMenu.resolve(catalog(2),nonce,text)).isEmpty();}
    @Test void validManualCodeDoesNotTrustDisplayLabel() {
        assertThat(TelegramQuestionnaireMenu.resolve(catalog(2),nonce,"/business "+nonce+" 1")).contains(new TelegramQuestionnaireMenu.Action(false,1));
        assertThat(TelegramQuestionnaireMenu.resolve(catalog(2),nonce,"Forged label ["+nonce+":2]")).contains(new TelegramQuestionnaireMenu.Action(false,2));
    }
    @ParameterizedTest @ValueSource(strings={"empty","too-many","version","hash","duplicate","general"})
    void corruptCatalogsFailClosed(String kind) {
        var choices=new ArrayList<>(catalog(2).choices());
        switch(kind) {
            case "empty"->choices.clear();case "too-many"->choices=new ArrayList<>(catalog(101).choices());
            case "duplicate"->choices.set(1,choices.get(0));
            case "general"->choices.set(0,new Choice("GENERAL",1,"a","A","q-a","a".repeat(64)));
        }
        var menu=new ChoiceCatalog(kind.equals("version")?3:1,55,kind.equals("hash")?"bad":"c".repeat(64),choices);
        assertThatThrownBy(()->TelegramQuestionnaireMenu.validate(menu,nonce)).isInstanceOf(IllegalStateException.class);
    }
}
