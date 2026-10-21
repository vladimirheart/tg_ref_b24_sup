package com.example.supportbot.vk;

import com.example.supportbot.service.PanelRuntimeConfigClient.ChoiceCatalog;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.vk.api.sdk.objects.messages.Message;
import com.vk.api.sdk.objects.messages.Keyboard;
import com.vk.api.sdk.objects.messages.KeyboardButton;
import com.vk.api.sdk.objects.messages.KeyboardButtonActionText;
import com.vk.api.sdk.objects.messages.KeyboardButtonActionTextType;
import com.vk.api.sdk.objects.messages.KeyboardButtonColor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Menu identity is independent of a visible label and of Callback/Long Poll envelopes. */
final class VkQuestionnaireChoiceSupport {
    private static final Gson GSON = new Gson();
    private static final Pattern COMMAND = Pattern.compile("^/business ([a-f0-9]{12}) ([1-9][0-9]{0,18})$");
    private static final String PAYLOAD_KEY = "iguana_questionnaire";
    private static final int PAGE_SIZE = 8;

    record Action(boolean page,long value,boolean general) {
        Action(boolean page,long value) {this(page,value,false);}
    }
    private VkQuestionnaireChoiceSupport() { }

    static String eventKey(Message message) {
        if (message == null || message.getPeerId() == null || message.getPeerId() <= 0
                || message.getConversationMessageId() == null || message.getConversationMessageId() <= 0)
            throw new IllegalStateException("VK questionnaire requires a stable conversation message ID");
        return "vk-message:" + message.getPeerId() + ":" + message.getConversationMessageId();
    }

    static boolean isChoiceCommand(String text, String payload) {
        return (text != null && (text.startsWith("/business") || text.startsWith("/general")))
                || (payload != null && payload.contains(PAYLOAD_KEY));
    }

    static boolean predatesMenu(String startEventKey, String eventKey) {
        if (startEventKey == null || !startEventKey.matches("vk-message:[1-9][0-9]*:[1-9][0-9]*")
                || eventKey == null || !eventKey.matches("vk-message:[1-9][0-9]*:[1-9][0-9]*"))
            throw new IllegalStateException("Invalid VK questionnaire generation identity");
        String peer = startEventKey.substring(0, startEventKey.lastIndexOf(':') + 1);
        if (!eventKey.startsWith(peer)) throw new IllegalStateException("VK questionnaire message has another peer");
        return Long.parseLong(eventKey.substring(peer.length())) < Long.parseLong(startEventKey.substring(peer.length()));
    }

    static Optional<Action> resolve(ChoiceCatalog catalog, String nonce, String text, String payload) {
        validate(catalog, nonce);
        String command = text;
        if (payload != null && payload.contains(PAYLOAD_KEY)) {
            try {
                var object = JsonParser.parseString(payload).getAsJsonObject();
                if (object.size() != 3 || !nonce.equals(object.get(PAYLOAD_KEY).getAsString())) return Optional.empty();
                String kind = object.get("kind").getAsString();
                if("general".equals(kind))
                    return object.get("value").getAsString().equals("general") && catalog.version()>=2 && catalog.choices().stream().anyMatch(c->c.general())
                        ? Optional.of(new Action(false,0,true)) : Optional.empty();
                if (!"business".equals(kind) && !"page".equals(kind)) return Optional.empty();
                boolean page = "page".equals(kind);
                String value = object.get("value").getAsString();
                if (!value.matches("[0-9]{1,19}")) return Optional.empty();
                long id = Long.parseLong(value);
                if (page) return id < pageCount(catalog) ? Optional.of(new Action(true, id)) : Optional.empty();
                command = "/business " + nonce + " " + id;
            } catch (RuntimeException ex) { return Optional.empty(); }
        }
        if (command == null) return Optional.empty();
        if(command.strip().equals("/general "+nonce)) return catalog.version()>=2 && catalog.choices().stream().anyMatch(c->c.general())
            ? Optional.of(new Action(false,0,true)) : Optional.empty();
        var matcher = COMMAND.matcher(command.strip());
        if (!matcher.matches() || !nonce.equals(matcher.group(1))) return Optional.empty();
        try {
            long id = Long.parseLong(matcher.group(2));
            return catalog.choices().stream().anyMatch(choice -> "BUSINESS".equals(choice.scopeKind()) && java.util.Objects.equals(choice.businessId(),id))
                    ? Optional.of(new Action(false, id)) : Optional.empty();
        } catch (NumberFormatException ex) { return Optional.empty(); }
    }

    static void validate(ChoiceCatalog catalog,String nonce) {
        if(catalog==null || nonce==null || !nonce.matches("[a-f0-9]{12}")) throw new IllegalStateException("Invalid saved menu");
        try { com.example.supportbot.service.PanelRuntimeConfigClient.validateCatalog(catalog.channelId(),catalog); }
        catch(IllegalArgumentException | com.example.supportbot.service.PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException ex) {
            throw new IllegalStateException("Invalid saved menu",ex);
        }
    }

    static int pageCount(ChoiceCatalog catalog) { return (catalog.choices().size() + PAGE_SIZE - 1) / PAGE_SIZE; }

    static String payload(String nonce, String kind, long value) {
        return GSON.toJson(Map.of(PAYLOAD_KEY, nonce, "kind", kind, "value", Long.toString(value)));
    }

    static String menuText(ChoiceCatalog catalog, String nonce, int page) {
        validate(catalog, nonce);
        if (page < 0 || page >= pageCount(catalog)) throw new IllegalStateException("Invalid questionnaire page");
        var text = new StringBuilder("Выберите бизнес или «Без бизнеса» до начала опроса. Страница ")
                .append(page + 1).append('/').append(pageCount(catalog)).append(".\n");
        for (int i = page * PAGE_SIZE; i < Math.min((page + 1) * PAGE_SIZE, catalog.choices().size()); i++) {
            var choice = catalog.choices().get(i);
            text.append(choice.general()?"Без бизнеса":"#"+choice.businessId()).append(" — ")
                    .append(shorten(choice.displayName() + " (" + choice.code() + ")", 180)).append('\n');
        }
        return text.append("Нажмите кнопку или отправьте /business ").append(nonce)
                .append(" ID. Новый выбор — /start, отмена — /cancel.").toString();
    }

    static Keyboard keyboard(ChoiceCatalog catalog, String nonce, int page) {
        validate(catalog, nonce);
        if (page < 0 || page >= pageCount(catalog)) throw new IllegalStateException("Invalid questionnaire page");
        List<List<KeyboardButton>> rows = new ArrayList<>();
        int end = Math.min((page + 1) * PAGE_SIZE, catalog.choices().size());
        for (int i = page * PAGE_SIZE; i < end; i += 2) {
            List<KeyboardButton> row = new ArrayList<>();
            for (int index = i; index < Math.min(i + 2, end); index++) {
                var choice = catalog.choices().get(index);
                if(choice.general()) {
                    var action=new KeyboardButtonActionText().setType(KeyboardButtonActionTextType.TEXT).setLabel("Без бизнеса")
                        .setPayload(GSON.toJson(Map.of(PAYLOAD_KEY,nonce,"kind","general","value","general")));
                    row.add(new KeyboardButton().setColor(KeyboardButtonColor.PRIMARY).setAction(action));
                } else {
                    String suffix = " #" + choice.businessId();
                    row.add(button(shorten(choice.displayName(), 40 - suffix.length()) + suffix, nonce, "business", choice.businessId()));
                }
            }
            rows.add(List.copyOf(row));
        }
        List<KeyboardButton> navigation = new ArrayList<>();
        if (page > 0) navigation.add(button("Назад", nonce, "page", page - 1));
        if (page + 1 < pageCount(catalog)) navigation.add(button("Далее", nonce, "page", page + 1));
        if (!navigation.isEmpty()) rows.add(List.copyOf(navigation));
        return new Keyboard().setOneTime(false).setInline(true).setButtons(List.copyOf(rows));
    }

    private static KeyboardButton button(String label, String nonce, String kind, long value) {
        return new KeyboardButton().setColor(KeyboardButtonColor.PRIMARY).setAction(new KeyboardButtonActionText()
                .setType(KeyboardButtonActionTextType.TEXT).setLabel(shorten(label, 40)).setPayload(payload(nonce, kind, value)));
    }

    private static String shorten(String label, int limit) {
        label = label.replaceAll("[\\p{Cntrl}]", " ");
        int end = Math.min(limit, label.length());
        if (end > 0 && Character.isHighSurrogate(label.charAt(end - 1))) end--;
        return label.substring(0, end);
    }
}
