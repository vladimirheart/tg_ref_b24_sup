package com.example.supportbot.max;

import com.example.supportbot.service.PanelRuntimeConfigClient.ChoiceCatalog;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Pure menu protocol: a delayed button belongs to one saved session generation. */
final class MaxQuestionnaireChoiceSupport {
    private static final Pattern CODE=Pattern.compile("^/business ([a-f0-9]{12}) ([1-9][0-9]{0,18})(?: .*)?$");
    private static final Pattern BUTTON=Pattern.compile("^.+ \\[([a-f0-9]{12}):([1-9][0-9]{0,18})\\]$");
    private MaxQuestionnaireChoiceSupport() { }

    static boolean isChoiceCommand(String text) {
        return text != null && (text.startsWith("/business") || BUTTON.matcher(text.strip()).matches());
    }
    static Optional<Long> resolve(ChoiceCatalog catalog,String nonce,String text) {
        validate(catalog,nonce);
        if(text==null) return Optional.empty();
        var matcher=CODE.matcher(text.strip());
        if(!matcher.matches()) matcher=BUTTON.matcher(text.strip());
        if(!matcher.matches() || !nonce.equals(matcher.group(1))) return Optional.empty();
        try {
            long businessId=Long.parseLong(matcher.group(2));
            return catalog.choices().stream().filter(choice->choice.businessId()==businessId).map(choice->choice.businessId()).findFirst();
        } catch(NumberFormatException ex) {return Optional.empty();}
    }
    static void validate(ChoiceCatalog catalog,String nonce) {
        if(catalog==null || catalog.version()!=1 || catalog.channelId()<=0 || catalog.catalogSha256()==null
                || !catalog.catalogSha256().matches("[a-f0-9]{64}") || catalog.choices()==null
                || catalog.choices().isEmpty() || catalog.choices().size()>100 || nonce==null || !nonce.matches("[a-f0-9]{12}"))
            throw new IllegalStateException("Invalid saved questionnaire menu");
        var ids=new HashSet<Long>();var templates=new HashSet<String>();
        for(var choice:catalog.choices()) {
            if(choice==null || !"BUSINESS".equals(choice.scopeKind()) || choice.businessId()<=0 || !ids.add(choice.businessId())
                    || choice.code()==null || choice.code().isBlank()
                    || choice.displayName()==null || choice.displayName().isBlank() || choice.questionTemplateId()==null
                    || choice.questionTemplateId().isBlank() || !templates.add(choice.questionTemplateId())
                    || choice.templateSha256()==null || !choice.templateSha256().matches("[a-f0-9]{64}"))
                throw new IllegalStateException("Invalid saved questionnaire choice");
        }
    }
    static String buttonText(ChoiceCatalog catalog,String nonce,int index) {
        validate(catalog,nonce);
        var choice=catalog.choices().get(index);
        String suffix=" ["+nonce+":"+choice.businessId()+"]";
        String label=(choice.displayName()+" ("+choice.code()+")").replaceAll("[\\p{Cntrl}]"," ");
        int end=Math.min(128-suffix.length(),label.length());
        if(end>0 && Character.isHighSurrogate(label.charAt(end-1))) end--;
        return label.substring(0,end)+suffix;
    }
    static Map<String,Object> messageBody(ChoiceCatalog catalog,String nonce) {
        validate(catalog,nonce);
        List<List<Map<String,Object>>> rows=new ArrayList<>();
        for(int index=0;index<catalog.choices().size();index+=4) {
            List<Map<String,Object>> row=new ArrayList<>();
            for(int item=index;item<Math.min(index+4,catalog.choices().size());item++)
                row.add(Map.of("type","message","text",buttonText(catalog,nonce,item)));
            rows.add(List.copyOf(row));
        }
        return Map.of("text","Выберите бизнес до начала опроса. Нажмите кнопку или отправьте её текст. Для нового выбора — /start, отмена — /cancel.",
            "attachments",List.of(Map.of("type","inline_keyboard","payload",Map.of("buttons",List.copyOf(rows)))));
    }
}
