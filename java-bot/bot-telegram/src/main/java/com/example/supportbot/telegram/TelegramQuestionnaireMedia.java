package com.example.supportbot.telegram;

import com.example.supportbot.service.*;
import java.util.*;
import org.telegram.telegrambots.meta.api.objects.Message;

/** Describes one Telegram media item without fetching it or trusting a client-supplied path. */
final class TelegramQuestionnaireMedia {
    record Item(String type,String fileId,String extension,String name,String fallback) { }
    private TelegramQuestionnaireMedia() { }
    static Item describe(Message m) {
        if(m.hasPhoto()) {
            var photo=m.getPhoto().stream().filter(Objects::nonNull)
                .max(Comparator.comparingLong(p->p.getFileSize()==null?0:p.getFileSize().longValue())).orElseThrow();
            return item("photo",photo.getFileId(),"jpg",null,photo.getFileSize(),"Клиент приложил фото");
        }
        if(m.hasAnimation()) {var v=m.getAnimation();return item("animation",v.getFileId(),extension(v.getFileName(),"mp4"),v.getFileName(),v.getFileSize(),"Клиент приложил анимацию");}
        if(m.hasDocument()) {var v=m.getDocument();return item("document",v.getFileId(),extension(v.getFileName(),"bin"),v.getFileName(),v.getFileSize(),"Клиент приложил документ");}
        if(m.hasVideo()) {var v=m.getVideo();return item("video",v.getFileId(),"mp4",v.getFileName(),v.getFileSize(),"Клиент приложил видео");}
        if(m.hasVoice()) {var v=m.getVoice();return item("voice",v.getFileId(),"ogg",null,v.getFileSize(),"Клиент отправил голосовое сообщение");}
        if(m.hasAudio()) {var v=m.getAudio();return item("audio",v.getFileId(),extension(v.getFileName(),"mp3"),v.getFileName(),v.getFileSize(),"Клиент приложил аудио");}
        if(m.getSticker()!=null) {var v=m.getSticker();return item("sticker",v.getFileId(),Boolean.TRUE.equals(v.getIsAnimated())?"tgs":Boolean.TRUE.equals(v.getIsVideo())?"webm":"webp",null,v.getFileSize(),"Клиент отправил стикер");}
        if(m.getVideoNote()!=null) {var v=m.getVideoNote();return item("video_note",v.getFileId(),"mp4",null,v.getFileSize(),"Клиент отправил видеосообщение");}
        throw new IllegalArgumentException("Unsupported Telegram media");
    }
    private static Item item(String type,String fileId,String ext,String name,Number size,String fallback) {
        if(fileId==null || fileId.isBlank() || fileId.length()>512 || (size!=null && (size.longValue()<0 || size.longValue()>QuestionnaireMedia.MAX_BYTES)))
            throw new IllegalArgumentException("Invalid or oversized Telegram media");
        if(name!=null) {
            name=name.replace('\\','/');name=name.substring(name.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]","_").strip();
            if(name.length()>255) name=name.substring(0,255);
            if(name.isEmpty()) name=null;
        }
        return new Item(type,fileId,ext,name,fallback);
    }
    private static String extension(String name,String fallback) {
        if(name==null || !name.contains(".")) return fallback;
        String ext=name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
        return ext.matches("[a-z0-9]{1,10}")?ext:fallback;
    }
    static boolean validHistory(TelegramQuestionnaireHandler.State state,ConversationHistoryEntry entry) {
        if(entry==null || !Objects.equals(entry.userId(),state.userId) || entry.providerMessageId()==null
                || !entry.providerMessageId().matches("[1-9][0-9]{0,18}") || entry.occurredAt()==null) return false;
        if("text".equals(entry.messageType())) return entry.attachmentPath()==null && entry.attachmentName()==null;
        if(state.selected==null || state.selected.version()!=4 || !QuestionnaireMedia.TYPES.contains(Objects.toString(entry.messageType(),""))
                || (entry.attachmentName()!=null && (entry.attachmentName().length()>255 || entry.attachmentName().chars().anyMatch(c->c<32 || c==127 || c=='/' || c=='\\')))) return false;
        try {return QuestionnaireMedia.matches(entry.attachmentPath(),state.channelId,state.selected.businessId(),state.userId,state.catalog.catalogSha256(),Long.parseLong(entry.providerMessageId()));}
        catch(IllegalArgumentException ex) {return false;}
    }
}
