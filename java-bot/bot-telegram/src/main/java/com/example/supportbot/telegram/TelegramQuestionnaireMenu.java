package com.example.supportbot.telegram;

import com.example.supportbot.service.PanelRuntimeConfigClient.ChoiceCatalog;
import java.util.*;
import java.util.regex.Pattern;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

/** Reply buttons are text messages; their generation token must survive delayed delivery. */
final class TelegramQuestionnaireMenu {
    private static final Pattern CODE=Pattern.compile("^/business ([a-f0-9]{12}) ([1-9][0-9]{0,18})$");
    private static final Pattern BUTTON=Pattern.compile("^.+ \\[([a-f0-9]{12}):([1-9][0-9]{0,18})\\]$");
    private static final Pattern PAGE=Pattern.compile("^(?:Назад|Далее) \\[([a-f0-9]{12}):page:([0-9]{1,2})\\]$");
    record Action(boolean page,long value) { }
    private TelegramQuestionnaireMenu() { }

    static void validate(ChoiceCatalog catalog,String nonce) {
        if(catalog==null || catalog.version()!=1 || catalog.channelId()<=0 || catalog.catalogSha256()==null
                || !catalog.catalogSha256().matches("[a-f0-9]{64}") || catalog.choices()==null
                || catalog.choices().isEmpty() || catalog.choices().size()>100 || nonce==null || !nonce.matches("[a-f0-9]{12}"))
            throw new IllegalStateException("Invalid saved Telegram questionnaire menu");
        var ids=new HashSet<Long>();var templates=new HashSet<String>();
        for(var choice:catalog.choices()) {
            if(choice==null || !"BUSINESS".equals(choice.scopeKind()) || choice.businessId()<=0 || !ids.add(choice.businessId())
                    || choice.code()==null || choice.code().isBlank() || choice.displayName()==null || choice.displayName().isBlank()
                    || choice.questionTemplateId()==null || choice.questionTemplateId().isBlank() || !templates.add(choice.questionTemplateId())
                    || choice.templateSha256()==null || !choice.templateSha256().matches("[a-f0-9]{64}"))
                throw new IllegalStateException("Invalid saved Telegram questionnaire choice");
        }
    }
    static boolean reserved(String text) {
        return text!=null && (text.startsWith("/business") || BUTTON.matcher(text).matches() || PAGE.matcher(text).matches());
    }
    static Optional<Action> resolve(ChoiceCatalog catalog,String nonce,String text) {
        validate(catalog,nonce);
        if(text==null) return Optional.empty();
        var page=PAGE.matcher(text.strip());
        if(page.matches()) {
            int number=Integer.parseInt(page.group(2));
            return nonce.equals(page.group(1)) && number<pages(catalog) ? Optional.of(new Action(true,number)) : Optional.empty();
        }
        var code=CODE.matcher(text.strip());if(!code.matches()) code=BUTTON.matcher(text.strip());
        if(!code.matches() || !nonce.equals(code.group(1))) return Optional.empty();
        try {
            long id=Long.parseLong(code.group(2));
            return catalog.choices().stream().anyMatch(choice->choice.businessId()==id)?Optional.of(new Action(false,id)):Optional.empty();
        } catch(NumberFormatException ex) {return Optional.empty();}
    }
    static int pages(ChoiceCatalog catalog) {return (catalog.choices().size()+7)/8;}
    static String button(ChoiceCatalog catalog,String nonce,int index) {
        validate(catalog,nonce);var choice=catalog.choices().get(index);
        String suffix=" ["+nonce+":"+choice.businessId()+"]";
        return shorten(choice.displayName()+" ("+choice.code()+")",96-suffix.length())+suffix;
    }
    static String pageButton(String nonce,int page,boolean next) {
        return (next?"Далее":"Назад")+" ["+nonce+":page:"+page+"]";
    }
    static ReplyKeyboardMarkup keyboard(ChoiceCatalog catalog,String nonce,int page) {
        validatePage(catalog,nonce,page);List<KeyboardRow> rows=new ArrayList<>();
        for(int i=page*8;i<Math.min(page*8+8,catalog.choices().size());i+=2) {
            var row=new KeyboardRow();row.add(button(catalog,nonce,i));
            if(i+1<Math.min(page*8+8,catalog.choices().size())) row.add(button(catalog,nonce,i+1));
            rows.add(row);
        }
        var navigation=new KeyboardRow();
        if(page>0) navigation.add(pageButton(nonce,page-1,false));
        if(page+1<pages(catalog)) navigation.add(pageButton(nonce,page+1,true));
        if(!navigation.isEmpty()) rows.add(navigation);
        return ReplyKeyboardMarkup.builder().keyboard(rows).resizeKeyboard(true).oneTimeKeyboard(true).build();
    }
    static String text(ChoiceCatalog catalog,String nonce,int page) {
        validatePage(catalog,nonce,page);
        var text=new StringBuilder("Выберите бизнес до первого вопроса. Страница ").append(page+1).append('/').append(pages(catalog)).append(".\n");
        for(int i=page*8;i<Math.min(page*8+8,catalog.choices().size());i++) {
            var choice=catalog.choices().get(i);
            text.append('#').append(choice.businessId()).append(" — ").append(shorten(choice.displayName()+" ("+choice.code()+")",180)).append('\n');
        }
        return text.append("Нажмите кнопку или отправьте /business ").append(nonce).append(" ID. Новый выбор — /start, отмена — /cancel.").toString();
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
