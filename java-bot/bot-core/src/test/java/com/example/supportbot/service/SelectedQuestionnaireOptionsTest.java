package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.*;
import com.example.supportbot.service.PanelRuntimeConfigClient.*;
import com.example.supportbot.settings.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SelectedQuestionnaireOptionsTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private SelectedQuestionnaire selected() {
        var flow=List.of(
            Map.of("id","kind","type","preset","preset",Map.of("group","locations","field","location_type")),
            Map.of("id","where","binding_key","chosen_city","type","preset","preset",Map.of("group","locations","field","city")),
            Map.of("id","place","type","preset","preset",Map.of("group","locations","field","location_name")),
            Map.of("id","department","type","preset","preset",Map.of("group","department","field","name")));
        var tree=Map.<String,Object>of("businessId",512L,"locations",List.of(
            Map.of("id",91L,"name","North store","locationType","Store","city","North"),
            Map.of("id",92L,"name","South store","locationType","Store","city","South"),
            Map.of("id",93L,"name","North office","locationType","Office","city","North")));
        var presets=Map.<String,Object>of("businessId",512L,"groups",Map.of("department",Map.of("name",List.of("Kitchen","Delivery"))));
        return new SelectedQuestionnaire(3,55,"BUSINESS","USER_SELECTION",512L,"q-a","a".repeat(64),"c".repeat(64),
            Map.of("active_template_id","q-a","question_templates",List.of(Map.of("id","q-a","question_flow",flow))),tree,presets);
    }
    @SuppressWarnings("unchecked") private QuestionFlowItemDto question(SelectedQuestionnaire selected,int index) {
        var templates=(List<Map<String,Object>>)selected.botSettings().get("question_templates");
        return mapper.convertValue(((List<?>)templates.get(0).get("question_flow")).get(index),QuestionFlowItemDto.class);
    }
    @Test void canonicalLocationOptionsDependOnlyOnTheSavedOwnerAndChosenDimensions() throws Exception {
        var selected=selected();SelectedQuestionnaireOptions.validate(selected);
        assertThat(SelectedQuestionnaireOptions.options(selected,question(selected,0),Map.of())).containsExactly("Office","Store");
        assertThat(SelectedQuestionnaireOptions.options(selected,question(selected,1),Map.of("kind","Store"))).containsExactly("North","South");
        assertThat(SelectedQuestionnaireOptions.options(selected,question(selected,2),Map.of("kind","Store","chosen_city","North"))).containsExactly("North store");
        assertThat(SelectedQuestionnaireOptions.options(selected,question(selected,2),Map.of("kind","Office","chosen_city","South"))).isEmpty();
        assertThat(SelectedQuestionnaireOptions.options(selected,question(selected,3),Map.of())).containsExactly("Kitchen","Delivery");
        var restored=mapper.readValue(mapper.writeValueAsBytes(selected),SelectedQuestionnaire.class);
        assertThat(SelectedQuestionnaireOptions.options(restored,question(restored,2),Map.of("kind","Store","chosen_city","North"))).containsExactly("North store");
    }
    @Test void excludedOptionsApplyWithoutConsultingAnyGlobalFallback() {
        var selected=selected();var question=question(selected,3);question.setExcludedOptions(List.of("Kitchen","Delivery"));
        assertThat(SelectedQuestionnaireOptions.options(selected,question,Map.of())).isEmpty();
    }
    @Test void locationDimensionsCanBeAskedInAnyOrderWithoutContradictoryOptions() {
        var selected=selected();
        assertThat(SelectedQuestionnaireOptions.options(selected,question(selected,1),Map.of("place","North office"))).containsExactly("North");
        assertThat(SelectedQuestionnaireOptions.options(selected,question(selected,0),Map.of("place","North office"))).containsExactly("Office");
        assertThat(SelectedQuestionnaireOptions.options(selected,question(selected,0),Map.of("chosen_city","South"))).containsExactly("Store");
    }
    @ParameterizedTest @ValueSource(strings={"foreign-owner","zero-owner","general","old-version","missing-group","wrong-digest"})
    void malformedOrForeignSavedResourcesCannotBeUsed(String mutation) {
        var original=selected();var tree=new LinkedHashMap<>(original.locationTree());
        int version=3;String kind="BUSINESS";Long owner=512L;var presets=original.presetDefinitions();
        switch(mutation){
            case "foreign-owner"->tree.put("businessId",513L);case "zero-owner"->tree.put("businessId",0L);
            case "general"->{kind="GENERAL";owner=null;}case "old-version"->version=2;
            case "missing-group"->presets=Map.of("businessId",512L,"groups",Map.of());
        }
        var broken=new SelectedQuestionnaire(version,55,kind,"USER_SELECTION",owner,"q-a","a".repeat(64),"c".repeat(64),original.botSettings(),tree,presets);
        var catalog=new ChoiceCatalog(version,55,"c".repeat(64),List.of(new Choice(kind,owner,"a","A","q-a","a".repeat(64),"f".repeat(64))));
        assertThatThrownBy(()->SelectedQuestionnaireOptions.validate(broken,catalog)).isInstanceOf(IllegalStateException.class);
    }

    @Test void resourceDigestHasAStableCrossModuleVector() {
        var tree=Map.<String,Object>of("businessId",7L,"locations",List.of(Map.of("id",91L,"name","Only A","locationType","Store","city","North")));
        var groups=Map.<String,Object>of("businessId",7L,"groups",Map.of("department",Map.of("name",List.of("Kitchen"))));
        assertThat(SelectedQuestionnaireOptions.resourceSha256(tree,groups)).isEqualTo("0d234a0b5df12624860061454ecf3f3b82734a3387ab864a7339653210745735");
    }

}
