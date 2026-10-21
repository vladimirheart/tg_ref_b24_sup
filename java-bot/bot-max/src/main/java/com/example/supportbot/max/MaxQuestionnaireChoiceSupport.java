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
    private static final Pattern PAGE=Pattern.compile("^(?:Назад|Далее) \\[([a-f0-9]{12}):page:([0-9]{1,3})\\]$");
    private MaxQuestionnaireChoiceSupport() { }

    static boolean isChoiceCommand(String text) {
        return text != null && (text.startsWith("/business") || text.startsWith("/general") || text.contains(":general]") || BUTTON.matcher(text.strip()).matches() || PAGE.matcher(text.strip()).matches());
    }
    static Optional<com.example.supportbot.service.PanelRuntimeConfigClient.Choice> resolveChoice(ChoiceCatalog catalog,String nonce,String text) {
        validate(catalog,nonce);
        if(generalInput(nonce,text)) return catalog.choices().stream().filter(c->catalog.version()>=2 && c.general()).findFirst();
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
    static int pages(ChoiceCatalog catalog) {return catalog.version()==4?(catalog.choices().size()+7)/8:1;}
    static String pageButton(String nonce,int page,boolean next) {return (next?"Далее":"Назад")+" ["+nonce+":page:"+page+"]";}
    static Optional<Integer> resolvePage(ChoiceCatalog catalog,String nonce,String text) {
        validate(catalog,nonce);
        if(catalog.version()!=4 || text==null) return Optional.empty();
        var matcher=PAGE.matcher(text.strip());
        if(!matcher.matches() || !nonce.equals(matcher.group(1))) return Optional.empty();
        int page=Integer.parseInt(matcher.group(2));return page<pages(catalog)?Optional.of(page):Optional.empty();
    }
    static Map<String,Object> messageBody(ChoiceCatalog catalog,String nonce) {
        return messageBody(catalog,nonce,0);
    }
    static Map<String,Object> messageBody(ChoiceCatalog catalog,String nonce,int page) {
        validate(catalog,nonce);
        if(page<0 || page>=pages(catalog)) throw new IllegalStateException("Invalid saved questionnaire page");
        int start=catalog.version()==4?page*8:0,end=catalog.version()==4?Math.min(start+8,catalog.choices().size()):catalog.choices().size();
        int columns=catalog.version()==4?2:4;
        List<List<Map<String,Object>>> rows=new ArrayList<>();
        for(int index=start;index<end;index+=columns) {
            List<Map<String,Object>> row=new ArrayList<>();
            for(int item=index;item<Math.min(index+columns,end);item++)
                row.add(Map.of("type","message","text",buttonText(catalog,nonce,item)));
            rows.add(List.copyOf(row));
        }
        if(catalog.version()==4) {
            List<Map<String,Object>> navigation=new ArrayList<>();
            if(page>0) navigation.add(Map.of("type","message","text",pageButton(nonce,page-1,false)));
            if(page+1<pages(catalog)) navigation.add(Map.of("type","message","text",pageButton(nonce,page+1,true)));
            if(!navigation.isEmpty()) rows.add(List.copyOf(navigation));
        }
        return Map.of("text","Выберите бизнес или «Без бизнеса» до начала опроса. "+(catalog.version()==4?"Страница "+(page+1)+"/"+pages(catalog)+". ":"")+"Нажмите кнопку или отправьте её текст. Для нового выбора — /start, отмена — /cancel.",
            "attachments",List.of(Map.of("type","inline_keyboard","payload",Map.of("buttons",List.copyOf(rows)))));
    }
}
