package com.example.supportbot.service;

import com.example.supportbot.service.PanelRuntimeConfigClient.SelectedQuestionnaire;
import com.example.supportbot.settings.dto.QuestionFlowItemDto;
import java.util.*;

/** Resolves only the frozen, owner-labelled resources of an explicit questionnaire selection. */
public final class SelectedQuestionnaireOptions {
    private SelectedQuestionnaireOptions() {}
    public static boolean preset(QuestionFlowItemDto q) {return q!=null && (q.getPreset()!=null || "preset".equalsIgnoreCase(q.getType()));}

    public static void validate(SelectedQuestionnaire selected,PanelRuntimeConfigClient.ChoiceCatalog catalog) {
        validate(selected);
        if(catalog==null || catalog.version()!=selected.version() || catalog.channelId()!=selected.channelId()
                || !Objects.equals(catalog.catalogSha256(),selected.catalogSha256()))throw invalid();
        var choice=catalog.choices().stream().filter(c->Objects.equals(c.scopeKind(),selected.scopeKind()) && Objects.equals(c.businessId(),selected.businessId())
            && Objects.equals(c.questionTemplateId(),selected.questionTemplateId()) && Objects.equals(c.templateSha256(),selected.templateSha256())).findFirst().orElseThrow(SelectedQuestionnaireOptions::invalid);
        if(selected.version()>=3 && !Objects.equals(choice.resourcesSha256(),resourceSha256(selected.locationTree(),selected.presetDefinitions())))throw invalid();
    }
    public static String resourceSha256(Map<String,Object> tree,Map<String,Object> presets) {
        try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
            .digest(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(canonical(List.of(tree,presets)))));}
        catch(Exception ex){throw invalid();}
    }
    private static Object canonical(Object value) {
        if(value instanceof Map<?,?> map){var sorted=new TreeMap<String,Object>();map.forEach((k,v)->sorted.put(k.toString(),canonical(v)));return sorted;}
        if(value instanceof List<?> list)return list.stream().map(SelectedQuestionnaireOptions::canonical).toList();return value;
    }
    public static void validate(SelectedQuestionnaire selected) {
        QuestionnaireScope.fromSelection(selected);
        if(selected.locationTree()==null || selected.presetDefinitions()==null) throw invalid();
        if(selected.version()<3 || "GENERAL".equals(selected.scopeKind())) {
            if(!selected.locationTree().isEmpty() || !selected.presetDefinitions().isEmpty()) throw invalid();
            for(var q:questions(selected)) if(q.get("preset")!=null || "preset".equalsIgnoreCase(Objects.toString(q.get("type"),""))) throw invalid();
            return;
        }
        var tree=selected.locationTree();var definitions=selected.presetDefinitions();
        if(!tree.keySet().equals(Set.of("businessId","locations")) || !definitions.keySet().equals(Set.of("businessId","groups"))
                || id(tree.get("businessId"))!=selected.businessId() || id(definitions.get("businessId"))!=selected.businessId()
                || !(tree.get("locations") instanceof List<?> locations) || locations.size()>1000
                || !(definitions.get("groups") instanceof Map<?,?> groups) || groups.size()>20) throw invalid();
        var ids=new HashSet<Long>();
        for(Object item:locations) {
            if(!(item instanceof Map<?,?> row) || !row.keySet().equals(Set.of("id","name","locationType","city")) || !ids.add(id(row.get("id")))) throw invalid();
            text(row.get("name"),200,false);text(row.get("locationType"),80,true);text(row.get("city"),120,true);
        }
        int total=0;
        for(var group:groups.entrySet()) {
            key(group.getKey());if("locations".equals(group.getKey()) || !(group.getValue() instanceof Map<?,?> fields) || fields.isEmpty() || fields.size()>20)throw invalid();
            for(var field:fields.entrySet()) {
                key(field.getKey());if(!(field.getValue() instanceof List<?> values) || values.isEmpty() || values.size()>100)throw invalid();
                var unique=new HashSet<String>();
                for(Object value:values){if(++total>1000 || !unique.add(text(value,200,false)))throw invalid();}
            }
        }
        var dimensions=new HashSet<String>();
        for(var q:questions(selected)) {
            if(q.get("preset")==null && !"preset".equalsIgnoreCase(Objects.toString(q.get("type"),"")))continue;
            if(!"preset".equalsIgnoreCase(Objects.toString(q.get("type"),"")) || !(q.get("preset") instanceof Map<?,?> ref)
                    || !ref.keySet().equals(Set.of("group","field")))throw invalid();
            String group=key(ref.get("group")),field=key(ref.get("field"));
            if("locations".equals(group)) {
                if(!Set.of("location_type","city","location_name").contains(field) || !dimensions.add(field))throw invalid();
                String column=column(field);
                if(locations.stream().noneMatch(item->!((String)((Map<?,?>)item).get(column)).isBlank()))throw invalid();
            } else if(!(groups.get(group) instanceof Map<?,?> fields) || !(fields.get(field) instanceof List<?> values) || values.isEmpty())throw invalid();
        }
    }

    public static List<String> options(SelectedQuestionnaire selected,QuestionFlowItemDto q,Map<String,String> answers) {
        if(!preset(q)) {
            if(q==null || !"select".equalsIgnoreCase(q.getType()) || q.getOptions()==null)return List.of();
            return exclude(q,q.getOptions().stream().filter(Objects::nonNull).map(o->o.getLabel()).filter(Objects::nonNull).toList());
        }
        validate(selected);
        if(selected.version()<3 || !"BUSINESS".equals(selected.scopeKind()) || q.getPreset()==null)throw invalid();
        String group=q.getPreset().group(),field=q.getPreset().field();List<String> values;
        if("locations".equals(group)) {
            var locations=(List<?>)selected.locationTree().get("locations");
            String type=dimension(selected,answers,"location_type"),city=dimension(selected,answers,"city"),name=dimension(selected,answers,"location_name");
            String column=column(field);
            values=locations.stream().map(item->(Map<?,?>)item)
                .filter(row->"location_type".equals(field) || type==null || type.equals(row.get("locationType")))
                .filter(row->"city".equals(field) || city==null || city.equals(row.get("city")))
                .filter(row->"location_name".equals(field) || name==null || name.equals(row.get("name")))
                .map(row->(String)row.get(column)).filter(value->!value.isBlank()).distinct().sorted().toList();
        } else {
            var groups=(Map<?,?>)selected.presetDefinitions().get("groups");
            if(!(groups.get(group) instanceof Map<?,?> fields) || !(fields.get(field) instanceof List<?> list))throw invalid();
            values=list.stream().map(value->(String)value).toList();
        }
        return exclude(q,values);
    }
    private static List<String> exclude(QuestionFlowItemDto q,List<String> options) {
        return q.getExcludedOptions()==null?options:options.stream().filter(value->!q.getExcludedOptions().contains(value)).toList();
    }
    private static String dimension(SelectedQuestionnaire selected,Map<String,String> answers,String field) {
        for(var q:questions(selected))if(q.get("preset") instanceof Map<?,?> ref && "locations".equals(ref.get("group")) && field.equals(ref.get("field"))) {
            String key=q.get("binding_key") instanceof String b && !b.isBlank()?b:Objects.toString(q.get("id"),"");
            String value=answers.get(key);return value==null || value.isBlank()?null:value;
        }
        return null;
    }
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> questions(SelectedQuestionnaire selected) {
        if(selected.botSettings()==null || !(selected.botSettings().get("question_templates") instanceof List<?> templates) || templates.size()!=1
                || !(templates.get(0) instanceof Map<?,?> template) || !(template.get("question_flow") instanceof List<?> flow) || flow.isEmpty()
                || flow.stream().anyMatch(q->!(q instanceof Map<?,?>)))throw invalid();
        return (List<Map<String,Object>>)(List<?>)flow;
    }
    private static String column(String field){return switch(field){case "location_type"->"locationType";case "city"->"city";case "location_name"->"name";default->throw invalid();};}
    private static long id(Object value) {
        if(!(value instanceof Number) || !value.toString().matches("[1-9][0-9]{0,18}"))throw invalid();
        try{return Long.parseLong(value.toString());}catch(NumberFormatException ex){throw invalid();}
    }
    private static String key(Object value){if(!(value instanceof String s) || !s.matches("[a-z][a-z0-9_]{0,39}"))throw invalid();return s;}
    private static String text(Object value,int limit,boolean empty){
        if(!(value instanceof String s) || (!empty && s.isBlank()) || !s.equals(s.strip()) || s.length()>limit || s.chars().anyMatch(Character::isISOControl))throw invalid();return s;
    }
    private static IllegalStateException invalid(){return new IllegalStateException("Invalid selected questionnaire resources");}
}
