package com.example.supportbot.telegram;

import com.example.supportbot.service.PanelRuntimeConfigClient.ChoiceCatalog;
import java.util.*;
import java.util.regex.Pattern;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

/** Visible option numbers address the current saved page; old signed buttons remain valid only for their generation. */
final class TelegramQuestionnaireMenu {
    private static final Pattern CODE=Pattern.compile("^/business ([a-f0-9]{12}) ([1-9][0-9]{0,18})$");
    private static final Pattern BUTTON=Pattern.compile("^.+ \\[([a-f0-9]{12}):([1-9][0-9]{0,18})\\]$");
    private static final Pattern PAGE=Pattern.compile("^(?:Назад|Далее) \\[([a-f0-9]{12}):page:([0-9]{1,3})\\]$");
    record Action(boolean page,long value,boolean general) {
        Action(boolean page,long value) {this(page,value,false);}
    }
    private TelegramQuestionnaireMenu() { }

    static void validate(ChoiceCatalog catalog,String nonce) {
        if(catalog==null || nonce==null || !nonce.matches("[a-f0-9]{12}")) throw new IllegalStateException("Invalid saved menu");
        try { com.example.supportbot.service.PanelRuntimeConfigClient.validateCatalog(catalog.channelId(),catalog); }
        catch(IllegalArgumentException | com.example.supportbot.service.PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException ex) {
            throw new IllegalStateException("Invalid saved menu",ex);
        }
    }

    static boolean reserved(String text) {
        return text!=null && (text.startsWith("/business") || text.startsWith("/general") || text.contains(":general]") || BUTTON.matcher(text).matches() || PAGE.matcher(text).matches());
    }
    static Optional<Action> resolve(ChoiceCatalog catalog,String nonce,String text) {
        validate(catalog,nonce);
        if(text==null) return Optional.empty();
        if(text.strip().equals("/general "+nonce) || text.strip().endsWith(" ["+nonce+":general]"))
            return catalog.version()>=2 && catalog.choices().stream().anyMatch(c->c.general()) ? Optional.of(new Action(false,0,true)) : Optional.empty();
        var page=PAGE.matcher(text.strip());
        if(page.matches()) {
            int number=Integer.parseInt(page.group(2));
            return nonce.equals(page.group(1)) && number<pages(catalog) ? Optional.of(new Action(true,number)) : Optional.empty();
        }
        var code=CODE.matcher(text.strip());if(!code.matches()) code=BUTTON.matcher(text.strip());
        if(!code.matches() || !nonce.equals(code.group(1))) return Optional.empty();
        try {
            long id=Long.parseLong(code.group(2));
            return catalog.choices().stream().anyMatch(choice->"BUSINESS".equals(choice.scopeKind()) && java.util.Objects.equals(choice.businessId(),id))?Optional.of(new Action(false,id)):Optional.empty();
        } catch(NumberFormatException ex) {return Optional.empty();}
    }
    static Optional<Action> resolve(ChoiceCatalog catalog,String nonce,int currentPage,String text) {
        validatePage(catalog,nonce,currentPage);
        var legacy=resolve(catalog,nonce,text);if(legacy.isPresent() || text==null)return legacy;
        String value=text.strip();
        if("Далее".equals(value) && currentPage+1<pages(catalog))return Optional.of(new Action(true,currentPage+1));
        if("Назад".equals(value) && currentPage>0)return Optional.of(new Action(true,currentPage-1));
        int first=currentPage*8,last=Math.min(first+8,catalog.choices().size());
        for(int index=first;index<last;index++) {
            if(!value.equals(Integer.toString(index+1)) && !value.equals(visibleButton(catalog,index)))continue;
            var choice=catalog.choices().get(index);
            return Optional.of(new Action(false,choice.general()?0:choice.businessId(),choice.general()));
        }
        return Optional.empty();
    }
    static int pages(ChoiceCatalog catalog) {return (catalog.choices().size()+7)/8;}
    static String button(ChoiceCatalog catalog,String nonce,int index) {
        validate(catalog,nonce);var choice=catalog.choices().get(index);
        String suffix=" ["+nonce+":"+(choice.general()?"general":choice.businessId())+"]";
        return shorten(choice.displayName()+" ("+choice.code()+")",96-suffix.length())+suffix;
    }
    static String pageButton(String nonce,int page,boolean next) {
        return (next?"Далее":"Назад")+" ["+nonce+":page:"+page+"]";
    }
    private static String visibleButton(ChoiceCatalog catalog,int index) {
        return (index+1)+". "+shorten(catalog.choices().get(index).displayName(),90);
    }
    static ReplyKeyboardMarkup keyboard(ChoiceCatalog catalog,String nonce,int page) {
        validatePage(catalog,nonce,page);List<KeyboardRow> rows=new ArrayList<>();
        for(int i=page*8;i<Math.min(page*8+8,catalog.choices().size());i+=2) {
            var row=new KeyboardRow();row.add(visibleButton(catalog,i));
            if(i+1<Math.min(page*8+8,catalog.choices().size())) row.add(visibleButton(catalog,i+1));
            rows.add(row);
        }
        var navigation=new KeyboardRow();
        if(page>0) navigation.add("Назад");
        if(page+1<pages(catalog)) navigation.add("Далее");
        if(!navigation.isEmpty()) rows.add(navigation);
        return ReplyKeyboardMarkup.builder().keyboard(rows).resizeKeyboard(true).oneTimeKeyboard(true).build();
    }
    static String text(ChoiceCatalog catalog,String nonce,int page) {
        validatePage(catalog,nonce,page);
        var text=new StringBuilder(catalog.choices().stream().anyMatch(c->c.general())?"Выберите бизнес или «Без бизнеса».":"Выберите бизнес.");
        if(pages(catalog)>1)text.append(" Страница ").append(page+1).append('/').append(pages(catalog)).append('.');
        text.append('\n');
        for(int i=page*8;i<Math.min(page*8+8,catalog.choices().size());i++) {
            text.append(visibleButton(catalog,i)).append('\n');
        }
        return text.append("Нажмите кнопку или отправьте номер варианта. /start — начать заново, /cancel — отменить заполнение анкеты.").toString();
    }
    private static void validatePage(ChoiceCatalog catalog,String nonce,int page) {
        validate(catalog,nonce);if(page<0 || page>=pages(catalog)) throw new IllegalStateException("Invalid saved questionnaire page");
    }
    private static String shorten(String label,int limit) {
        label=label.replaceAll("[\\p{Cntrl}]"," ");int end=Math.min(limit,label.length());
        if(end>0 && Character.isHighSurrogate(label.charAt(end-1))) end--;
        return label.substring(0,end);
    }
}
