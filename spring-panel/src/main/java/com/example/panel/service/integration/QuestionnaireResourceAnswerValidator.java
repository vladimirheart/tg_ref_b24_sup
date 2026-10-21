package com.example.panel.service.integration;

import com.example.panel.service.SharedGeneralQuestionnaireService.Selected;
import java.util.*;

/** Validates transport attributes against the current v3 snapshot before the first root write. */
final class QuestionnaireResourceAnswerValidator {
    private QuestionnaireResourceAnswerValidator() {}
    @SuppressWarnings("unchecked") static void requireValid(Selected selected,ConversationTicketCreatedEvent event) {
        var attributes=event.attributes();
        var templates=(List<?>)selected.botSettings().get("question_templates");
        var template=(Map<?,?>)templates.get(0);
        var questions=(List<Map<String,Object>>)template.get("question_flow");
        Map<String,Map<String,Object>> byId=new HashMap<>();
        questions.forEach(q->byId.put((String)q.get("id"),q));
        Map<String,ConversationTicketCreatedEvent.TicketAttributePayload> answers=new HashMap<>();
        for(var attribute:attributes==null?List.<ConversationTicketCreatedEvent.TicketAttributePayload>of():attributes) {
            if(attribute==null || !byId.containsKey(attribute.questionId()) || answers.putIfAbsent(attribute.questionId(),attribute)!=null)throw invalid();
        }
        for(var entry:answers.entrySet()) {
            var question=byId.get(entry.getKey());var answer=entry.getValue();
            if(!(question.get("preset") instanceof Map<?,?> ref))continue;
            String key=question.get("binding_key") instanceof String b && !b.isBlank()?b:(String)question.get("id");
            if(!key.equals(answer.attributeKey()) || !"preset".equalsIgnoreCase(answer.inputType())
                    || answer.valueText()==null || answer.valueText().isBlank() || !Objects.equals(answer.valueText(),answer.valueLabel()))throw invalid();
            String group=(String)ref.get("group"),field=(String)ref.get("field");List<String> options;
            if("locations".equals(group)) {
                String type=dimension(questions,answers,"location_type"),city=dimension(questions,answers,"city"),name=dimension(questions,answers,"location_name");
                String column=switch(field){case "location_type"->"locationType";case "city"->"city";case "location_name"->"name";default->throw invalid();};
                options=((List<?>)selected.locationTree().get("locations")).stream().map(item->(Map<?,?>)item)
                    .filter(row->"location_type".equals(field) || type==null || type.equals(row.get("locationType")))
                    .filter(row->"city".equals(field) || city==null || city.equals(row.get("city")))
                    .filter(row->"location_name".equals(field) || name==null || name.equals(row.get("name")))
                    .map(row->(String)row.get(column)).toList();
            } else {
                var groups=(Map<?,?>)selected.presetDefinitions().get("groups");
                options=(List<String>)((Map<?,?>)groups.get(group)).get(field);
            }
            if(!options.contains(answer.valueText()) || (question.get("excluded_options") instanceof List<?> excluded && excluded.contains(answer.valueText())))throw invalid();
        }
        for(var question:questions)if(question.get("preset") instanceof Map<?,?> ref && "locations".equals(ref.get("group"))) {
            String projection=switch((String)ref.get("field")){case "location_type"->event.locationType();case "city"->event.city();case "location_name"->event.locationName();default->throw invalid();};
            if(projection!=null && !projection.isBlank()) {
                var answer=answers.get(question.get("id"));
                if(answer==null || !projection.equals(answer.valueText()))throw invalid();
            }
        }
    }
    private static String dimension(List<Map<String,Object>> questions,Map<String,ConversationTicketCreatedEvent.TicketAttributePayload> answers,String field){
        for(var q:questions)if(q.get("preset") instanceof Map<?,?> ref && "locations".equals(ref.get("group")) && field.equals(ref.get("field"))) {
            var answer=answers.get(q.get("id"));return answer==null?null:answer.valueText();
        }
        return null;
    }
    private static IllegalStateException invalid(){return new IllegalStateException("Questionnaire answer does not match the selected business resources");}
}
