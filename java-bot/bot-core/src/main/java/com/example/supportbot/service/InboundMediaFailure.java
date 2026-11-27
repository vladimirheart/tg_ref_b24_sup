package com.example.supportbot.service;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;

/** Human description of a receive/download failure. Raw SDK exceptions are never exposed. */
public record InboundMediaFailure(String code, String message) {
    public static InboundMediaFailure describe(Throwable failure) {
        StringBuilder words = new StringBuilder();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean boundedMedia = false;
        for (Throwable item = failure; item != null && seen.size() < 12 && seen.add(item); item = item.getCause()) {
            boundedMedia |= item instanceof AttachmentService.MediaSizeException;
            words.append(' ').append(item.getClass().getSimpleName()).append(' ').append(item.getMessage());
        }
        String detail = words.toString().toLowerCase(Locale.ROOT);
        if (boundedMedia) return new InboundMediaFailure("INBOUND_MEDIA_SIZE", "Вложение анкеты должно содержать от 1 байта до 10 МиБ. Отправьте другой файл или текст.");
        if (detail.contains("too big") || detail.contains("too large") || detail.contains("413"))
            return new InboundMediaFailure("INBOUND_FILE_TOO_LARGE", "Мессенджер отклонил файл из-за его размера. Отправьте файл меньшего размера или текст.");
        if (detail.contains("timeout") || detail.contains("timed out") || detail.contains("deadline"))
            return new InboundMediaFailure("INBOUND_MEDIA_TIMEOUT", "Не удалось дождаться получения вложения. Проверьте соединение; файл пока не подтверждён как принятый.");
        if (detail.contains("unauthorized") || detail.contains("forbidden") || detail.contains("401") || detail.contains("403"))
            return new InboundMediaFailure("INBOUND_PROVIDER_ACCESS", "Мессенджер не разрешил получить вложение. Сообщите оператору о проблеме доступа бота.");
        if (detail.contains("not found") || detail.contains("404"))
            return new InboundMediaFailure("INBOUND_FILE_UNAVAILABLE", "Исходный файл недоступен. Отправьте его заново напрямую или продолжите текстом.");
        return new InboundMediaFailure("INBOUND_MEDIA_UNAVAILABLE", "Не удалось получить или сохранить вложение. Сообщите оператору код ошибки или продолжите текстом.");
    }
}
