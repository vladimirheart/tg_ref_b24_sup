package com.example.supportbot.service;

import com.example.supportbot.entity.Channel;
import java.util.Locale;

/** Human-facing support-chat text: internal ticket IDs are never a display fallback. */
public final class SupportNotificationText {
    private SupportNotificationText() { }

    public static String reference(String requestNumber) {
        String number = requestNumber == null ? "" : requestNumber.trim();
        return number.matches("\\d{8}-\\d{3,}") ? "№" + number : "(номер ожидается)";
    }

    public static String newAppealTitle(String requestNumber) {
        return "Новая заявка " + reference(requestNumber);
    }

    public static String fromChannel(Channel channel, String platform, String text) {
        String provider = switch (platform == null ? "" : platform.toLowerCase(Locale.ROOT)) {
            case "telegram" -> "Telegram";
            case "vk" -> "ВКонтакте";
            case "max" -> "MAX";
            default -> "Бот";
        };
        String name = channel == null || channel.getChannelName() == null ? "" : channel.getChannelName().replaceAll("\\s+", " ").trim();
        return "💬 Диалоги · " + provider + (name.isEmpty() ? "" : "\nКанал: " + name)
                + "\n──────────\n" + text;
    }
}
