package com.example.supportbot.max;

import static org.assertj.core.api.Assertions.*;
import com.example.supportbot.service.PanelRuntimeConfigClient.Choice;
import com.example.supportbot.service.PanelRuntimeConfigClient.ChoiceCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MaxQuestionnaireChoiceSupportTest {
    @ParameterizedTest @ValueSource(ints={1,4,100})
    void keyboardUsesSavedCodesWithinProviderLimitsAndUnicodeLabelsStayWhole(int count) throws Exception {
        var choices=new ArrayList<Choice>();
        for(int id=1;id<=count;id++) choices.add(new Choice("BUSINESS",id,"b"+id,"😀".repeat(100),"q-"+id,"a".repeat(64)));
        var catalog=new ChoiceCatalog(1,55,"c".repeat(64),choices);
        var body=new ObjectMapper().valueToTree(MaxQuestionnaireChoiceSupport.messageBody(catalog,"123456abcdef"));
        assertThat(body.at("/attachments/0/type").asText()).isEqualTo("inline_keyboard");
        var rows=body.at("/attachments/0/payload/buttons");
        assertThat(rows.size()).isLessThanOrEqualTo(30);
        int buttons=0;
        for(var row:rows) {
            assertThat(row.size()).isLessThanOrEqualTo(4);
            for(var button:row) {
                String text=button.path("text").asText();
                assertThat(button.path("type").asText()).isEqualTo("message");
                assertThat(text.length()).isLessThanOrEqualTo(128);
                assertThat(Character.isHighSurrogate(text.charAt(text.length()-1))).isFalse();
                assertThat(new String(text.getBytes(java.nio.charset.StandardCharsets.UTF_8),java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(text);
                assertThat(MaxQuestionnaireChoiceSupport.resolve(catalog,"123456abcdef",text)).contains((long)++buttons);
            }
        }
        assertThat(buttons).isEqualTo(count);
    }
    @ParameterizedTest @ValueSource(strings={"nonce","duplicate","scope","template","hash","empty","limit"})
    void malformedStoredMenuCannotRenderOrResolve(String corruption) {
        var choice=new Choice("BUSINESS",7,"a","A","q-a","a".repeat(64));
        List<Choice> choices=switch(corruption) {
            case "duplicate"->List.of(choice,choice);
            case "scope"->List.of(new Choice("GENERAL",7,"a","A","q-a","a".repeat(64)));
            case "template"->List.of(new Choice("BUSINESS",7,"a","A"," ","a".repeat(64)));
            case "hash"->List.of(new Choice("BUSINESS",7,"a","A","q-a","bad"));
            case "empty"->List.of();case "limit"->java.util.Collections.nCopies(101,choice);default->List.of(choice);
        };
        var catalog=new ChoiceCatalog(1,55,"c".repeat(64),choices);
        String nonce=corruption.equals("nonce")?"bad":"123456abcdef";
        assertThatThrownBy(()->MaxQuestionnaireChoiceSupport.messageBody(catalog,nonce)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->MaxQuestionnaireChoiceSupport.resolve(catalog,nonce,"/business 123456abcdef 7")).isInstanceOf(IllegalStateException.class);
    }
    @ParameterizedTest @ValueSource(ints={101,1000})
    void expandedPagesReachEveryChoiceAndTheFinalGeneralWithoutOversizedKeyboards(int count) throws Exception {
        var choices=new ArrayList<com.example.supportbot.service.PanelRuntimeConfigClient.Choice>();
        for(long id=1;id<count;id++)choices.add(new com.example.supportbot.service.PanelRuntimeConfigClient.Choice("BUSINESS",id,"b-"+id,"Business "+id,"q-"+id,"a".repeat(64),"b".repeat(64)));
        choices.add(new com.example.supportbot.service.PanelRuntimeConfigClient.Choice("GENERAL",(Long)null,"GENERAL","Без бизнеса","q-general","c".repeat(64),"d".repeat(64)));
        var menu=new ChoiceCatalog(4,55,"c".repeat(64),choices);String nonce="123456abcdef";var seen=new java.util.HashSet<Long>();int pages=(count+7)/8;
        for(int page=0;page<pages;page++) {
            var tree=new ObjectMapper().valueToTree(MaxQuestionnaireChoiceSupport.messageBody(menu,nonce,page)).at("/attachments/0/payload/buttons");assertThat(tree.size()).isLessThanOrEqualTo(5);
            for(var row:tree)for(var button:row) {
                String text=button.path("text").asText();
                var target=MaxQuestionnaireChoiceSupport.resolvePage(menu,nonce,text);
                if(target.isPresent()){assertThat(target.get()).isBetween(0,pages-1);continue;}
                var choice=MaxQuestionnaireChoiceSupport.resolveChoice(menu,nonce,text).orElseThrow();if(choice.general())seen.add(0L);else seen.add(choice.businessId());
            }
        }
        assertThat(seen).hasSize(count).contains(0L,(long)count-1);
    }

}
