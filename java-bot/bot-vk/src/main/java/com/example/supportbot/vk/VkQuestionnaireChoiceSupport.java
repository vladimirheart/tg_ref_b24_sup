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

    record Action(boolean page, long value) { }
    private VkQuestionnaireChoiceSupport() { }

    static String eventKey(Message message) {
        if (message == null || message.getPeerId() == null || message.getPeerId() <= 0
                || message.getConversationMessageId() == null || message.getConversationMessageId() <= 0)
            throw new IllegalStateException("VK questionnaire requires a stable conversation message ID");
        return "vk-message:" + message.getPeerId() + ":" + message.getConversationMessageId();
    }

    static boolean isChoiceCommand(String text, String payload) {
        return (text != null && text.startsWith("/business"))
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
        var matcher = COMMAND.matcher(command.strip());
        if (!matcher.matches() || !nonce.equals(matcher.group(1))) return Optional.empty();
        try {
            long id = Long.parseLong(matcher.group(2));
            return catalog.choices().stream().anyMatch(choice -> choice.businessId() == id)
                    ? Optional.of(new Action(false, id)) : Optional.empty();
        } catch (NumberFormatException ex) { return Optional.empty(); }
    }

    static void validate(ChoiceCatalog catalog, String nonce) {
        if (catalog == null || catalog.version() != 1 || catalog.channelId() <= 0 || catalog.catalogSha256() == null
                || !catalog.catalogSha256().matches("[a-f0-9]{64}") || catalog.choices() == null
                || catalog.choices().isEmpty() || catalog.choices().size() > 100
                || nonce == null || !nonce.matches("[a-f0-9]{12}"))
            throw new IllegalStateException("Invalid saved VK questionnaire menu");
        var ids = new HashSet<Long>();
        var templates = new HashSet<String>();
        for (var choice : catalog.choices()) {
            if (choice == null || !"BUSINESS".equals(choice.scopeKind()) || choice.businessId() <= 0
                    || !ids.add(choice.businessId()) || choice.code() == null || choice.code().isBlank()
                    || choice.displayName() == null || choice.displayName().isBlank()
                    || choice.questionTemplateId() == null || choice.questionTemplateId().isBlank()
                    || !templates.add(choice.questionTemplateId()) || choice.templateSha256() == null
                    || !choice.templateSha256().matches("[a-f0-9]{64}"))
                throw new IllegalStateException("Invalid saved VK questionnaire choice");
        }
    }

    static int pageCount(ChoiceCatalog catalog) { return (catalog.choices().size() + PAGE_SIZE - 1) / PAGE_SIZE; }

    static String payload(String nonce, String kind, long value) {
        return GSON.toJson(Map.of(PAYLOAD_KEY, nonce, "kind", kind, "value", Long.toString(value)));
    }

    static String menuText(ChoiceCatalog catalog, String nonce, int page) {
        validate(catalog, nonce);
        if (page < 0 || page >= pageCount(catalog)) throw new IllegalStateException("Invalid questionnaire page");
        var text = new StringBuilder("Выберите бизнес до начала опроса. Страница ")
                .append(page + 1).append('/').append(pageCount(catalog)).append(".\n");
        for (int i = page * PAGE_SIZE; i < Math.min((page + 1) * PAGE_SIZE, catalog.choices().size()); i++) {
            var choice = catalog.choices().get(i);
            text.append('#').append(choice.businessId()).append(" — ")
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
                String suffix = " #" + choice.businessId();
                row.add(button(shorten(choice.displayName(), 40 - suffix.length()) + suffix, nonce, "business", choice.businessId()));
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
