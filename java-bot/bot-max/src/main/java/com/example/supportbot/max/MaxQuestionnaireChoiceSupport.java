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
        return text != null && (text.startsWith("/business") || text.startsWith("/general") || text.contains(":general]") || BUTTON.matcher(text.strip()).matches());
    }
    static Optional<com.example.supportbot.service.PanelRuntimeConfigClient.Choice> resolveChoice(ChoiceCatalog catalog,String nonce,String text) {
        validate(catalog,nonce);
        if(generalInput(nonce,text)) return catalog.choices().stream().filter(c->catalog.version()==2 && c.general()).findFirst();
        return resolve(catalog,nonce,text).flatMap(id->catalog.choices().stream().filter(c->java.util.Objects.equals(c.businessId(),id)).findFirst());
    }
    private static boolean generalInput(String nonce,String text) {
        return text!=null && (text.strip().equals("/general "+nonce) || text.strip().endsWith(" ["+nonce+":general]"));
    }
    static Optional<Long> resolve(ChoiceCatalog catalog,String nonce,String text) {
        validate(catalog,nonce);
        if(text==null) return Optional.empty();
        var matcher=CODE.matcher(text.strip());
        if(!matcher.matches()) matcher=BUTTON.matcher(text.strip());
        if(!matcher.matches() || !nonce.equals(matcher.group(1))) return Optional.empty();
        try {
            long businessId=Long.parseLong(matcher.group(2));
            return catalog.choices().stream().filter(choice->"BUSINESS".equals(choice.scopeKind()) && java.util.Objects.equals(choice.businessId(),businessId)).map(choice->choice.businessId()).findFirst();
        } catch(NumberFormatException ex) {return Optional.empty();}
    }
    static void validate(ChoiceCatalog catalog,String nonce) {
        if(catalog==null || nonce==null || !nonce.matches("[a-f0-9]{12}")) throw new IllegalStateException("Invalid saved menu");
        try { com.example.supportbot.service.PanelRuntimeConfigClient.validateCatalog(catalog.channelId(),catalog); }
        catch(IllegalArgumentException | com.example.supportbot.service.PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException ex) {
            throw new IllegalStateException("Invalid saved menu",ex);
        }
    }

    static String buttonText(ChoiceCatalog catalog,String nonce,int index) {
        validate(catalog,nonce);
        var choice=catalog.choices().get(index);
        String suffix=" ["+nonce+":"+(choice.general()?"general":choice.businessId())+"]";
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
        return Map.of("text","Выберите бизнес или «Без бизнеса» до начала опроса. Нажмите кнопку или отправьте её текст. Для нового выбора — /start, отмена — /cancel.",
            "attachments",List.of(Map.of("type","inline_keyboard","payload",Map.of("buttons",List.copyOf(rows)))));
    }
}
