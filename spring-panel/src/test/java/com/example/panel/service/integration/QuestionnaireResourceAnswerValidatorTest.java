package com.example.panel.service.integration;

import static org.assertj.core.api.Assertions.*;
import com.example.panel.service.QuestionnaireFlowContract;
import com.example.panel.service.SharedGeneralQuestionnaireService.Selected;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QuestionnaireResourceAnswerValidatorTest {
    private List<Map<String,Object>> flow() {
        return List.of(
            Map.of("id","last","type","custom","text","Describe","order",30,"binding_key","problem"),
            Map.of("id","branch","type","select","text","Choose","order",10,"options",List.of(Map.of("id","yes","label","Yes"),Map.of("id","no","label","No")),
                "routes",List.of(Map.of("value_id","yes","next_question_id","last"))),
            Map.of("id","optional","type","text","text","Details","order",20,"required",false));
    }
    private ConversationTicketCreatedEvent.TicketAttributePayload answer(String id,String key,String text,String type,String valueId,String label,String value) {
        return new ConversationTicketCreatedEvent.TicketAttributePayload(id,key,text,type,valueId,label,value,false);
    }
    private ConversationTicketCreatedEvent.TicketAttributePayload branch(String valueId,String label) { return answer("branch","branch","Choose","select",valueId,label,label); }
    private ConversationTicketCreatedEvent.TicketAttributePayload last() { return answer("last","problem","Describe","custom",null,null,"Help"); }
    private ConversationTicketCreatedEvent event(List<ConversationTicketCreatedEvent.TicketAttributePayload> answers) {
        return new ConversationTicketCreatedEvent("e","ticket.created.initial_contact","telegram",55L,"T",1L,"client","client","Client",null,null,null,null,"Help",
            OffsetDateTime.parse("2026-10-21T08:00:00Z"),answers,List.of(),7L,"BUSINESS","USER_SELECTION","q","a".repeat(64),"b".repeat(64));
    }
    private void validate(List<Map<String,Object>> flow,List<ConversationTicketCreatedEvent.TicketAttributePayload> answers,boolean general) {
        var selected=new Selected(3,55,general?"GENERAL":"BUSINESS","USER_SELECTION",general?null:7L,"q","a".repeat(64),"b".repeat(64),
            Map.of("question_templates",List.of(Map.of("id","q","question_flow",flow))),Map.of(),Map.of());
        QuestionnaireResourceAnswerValidator.requireValid(selected,event(answers));
    }
    @ParameterizedTest @ValueSource(strings={"business","general"})
    void sortedBranchMaySkipRequiredQuestionsOutsideItsPathAndOptionalQuestionsMayBeOmitted(String scope) {
        var required=new ArrayList<>(flow());required.set(2,new HashMap<>(required.get(2)));required.get(2).put("required",true);
        validate(required,List.of(last(),branch("yes","Yes")),scope.equals("general"));
        validate(flow(),List.of(branch("no","No"),last()),scope.equals("general"));
        validate(flow(),List.of(branch("no","No"),answer("optional","optional","Details","text",null,null,"More"),last()),scope.equals("general"));
    }
    @ParameterizedTest @ValueSource(strings={"missing-first","missing-last","required","off-route","id-label","unknown-option","free-value-id","free-label","key","type","question-text","dashboard","blank","duplicate","unknown"})
    void completePathAndCanonicalAttributeMetadataAreRequired(String mutation) {
        var configured=new ArrayList<>(flow());var answers=new ArrayList<>(List.of(branch("yes","Yes"),last()));
        switch(mutation) {
            case "missing-first" -> answers.remove(0);
            case "missing-last" -> answers.remove(1);
            case "required" -> {configured.set(2,new HashMap<>(configured.get(2)));configured.get(2).put("required",true);answers.set(0,branch("no","No"));}
            case "off-route" -> answers.add(answer("optional","optional","Details","text",null,null,"Stale branch"));
            case "id-label" -> answers.set(0,branch("yes","No"));
            case "unknown-option" -> answers.set(0,branch("foreign","Yes"));
            case "free-value-id" -> answers.set(1,answer("last","problem","Describe","custom","fake",null,"Help"));
            case "free-label" -> answers.set(1,answer("last","problem","Describe","custom",null,"Fake","Help"));
            case "key" -> answers.set(1,answer("last","other","Describe","custom",null,null,"Help"));
            case "type" -> answers.set(1,answer("last","problem","Describe","select",null,null,"Help"));
            case "question-text" -> answers.set(1,answer("last","problem","Fake","custom",null,null,"Help"));
            case "dashboard" -> answers.set(1,new ConversationTicketCreatedEvent.TicketAttributePayload("last","problem","Describe","custom",null,null,"Help",true));
            case "blank" -> answers.set(1,answer("last","problem","Describe","custom",null,null," "));
            case "duplicate" -> answers.add(last());
            case "unknown" -> answers.add(answer("unknown","unknown","Unknown","text",null,null,"Help"));
        }
        assertThatThrownBy(()->validate(configured,answers,false)).isInstanceOf(IllegalStateException.class);
    }
    @ParameterizedTest @ValueSource(strings={"duplicate-key","space-key","blank-key","duplicate-id","back-route","unknown-target","duplicate-route","unknown-route-value","custom-route","empty-options","duplicate-option-id","duplicate-label","fraction-order","required-string","dashboard-string","choice-problem"})
    void malformedTemplatesAreUnavailableBeforeV3Starts(String mutation) {
        var configured=new ArrayList<Map<String,Object>>();flow().forEach(q->configured.add(new HashMap<>(q)));
        var first=configured.get(1);var last=configured.get(0);
        switch(mutation) {
            case "duplicate-key" -> first.put("binding_key","problem");
            case "space-key" -> first.put("binding_key"," key ");
            case "blank-key" -> first.put("binding_key","");
            case "duplicate-id" -> last.put("id","branch");
            case "back-route" -> first.put("routes",List.of(Map.of("value_id","yes","next_question_id","branch")));
            case "unknown-target" -> first.put("routes",List.of(Map.of("value_id","yes","next_question_id","absent")));
            case "duplicate-route" -> first.put("routes",List.of(Map.of("value_id","yes","next_question_id","last"),Map.of("value_id","yes","next_question_id","optional")));
            case "unknown-route-value" -> first.put("routes",List.of(Map.of("value_id","foreign","next_question_id","last")));
            case "custom-route" -> last.put("routes",List.of(Map.of("value_id","yes","next_question_id","last")));
            case "empty-options" -> first.put("options",List.of());
            case "duplicate-option-id" -> first.put("options",List.of(Map.of("id","x","label","One"),Map.of("id","x","label","Two")));
            case "duplicate-label" -> first.put("options",List.of(Map.of("id","x","label","One"),Map.of("id","y","label","One")));
            case "fraction-order" -> last.put("order",0.5);
            case "required-string" -> last.put("required","false");
            case "dashboard-string" -> last.put("include_in_dashboard","true");
            case "choice-problem" -> {last.remove("binding_key");first.put("binding_key","problem");}
        }
        assertThat(QuestionnaireFlowContract.supports(Map.of("question_flow",configured))).isFalse();
    }
    @Test void absentAndEqualOrderPreserveConfiguredOrder() {
        var configured=List.<Map<String,Object>>of(Map.of("id","b"),Map.of("id","a"));
        assertThat(QuestionnaireFlowContract.questions(Map.of("question_flow",configured))).extracting(q->q.get("id")).containsExactly("b","a");
    }
}
