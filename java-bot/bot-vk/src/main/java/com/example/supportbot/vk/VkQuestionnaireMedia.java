package com.example.supportbot.vk;

import com.example.supportbot.service.QuestionnaireMedia;
import com.vk.api.sdk.objects.messages.Message;
import com.vk.api.sdk.objects.photos.PhotoSizes;
import java.net.URI;
import java.time.*;
import java.util.*;

/** Describe the whole batch before any download or answer mutation. Provider URLs never enter persisted history. */
final class VkQuestionnaireMedia {
    static final Set<String> TYPES=Set.of("photo","document","audio_message");
    private static final Set<String> CDN_DOMAINS=Set.of("userapi.com","vkuserphoto.ru","vkuserphoto.net","vkuseraudio.net","vkuser.net","vk.com","vk.ru");
    record Item(int slot,String type,URI url,String extension,String name) { }
    private VkQuestionnaireMedia() { }

    static List<Item> describe(Message message) {
        VkQuestionnaireChoiceSupport.eventKey(message);
        occurredAt(message);
        var attachments=message.getAttachments();
        if(attachments==null || attachments.isEmpty() || attachments.size()>QuestionnaireMedia.MAX_FILES)
            throw new IllegalArgumentException("В сообщении допустимо от 1 до 10 вложений.");
        var result=new ArrayList<Item>();
        for(var attachment:attachments) {
            if(attachment==null || attachment.getType()==null) throw unsupported();
            int slot=result.size()+1;
            switch(attachment.getType()) {
                case PHOTO -> {
                    var photo=attachment.getPhoto();
                    var size=photo==null || photo.getSizes()==null?null:photo.getSizes().stream().filter(Objects::nonNull)
                        .max(Comparator.comparingLong(VkQuestionnaireMedia::area)).orElse(null);
                    if(size==null || size.getUrl()==null) throw unsupported();
                    result.add(new Item(slot,"photo",url(size.getUrl().toString()),"jpg","photo.jpg"));
                }
                case DOC -> {
                    var doc=attachment.getDoc();
                    if(doc==null || doc.getUrl()==null) throw unsupported();
                    if(doc.getSize()!=null && doc.getSize()>QuestionnaireMedia.MAX_BYTES)
                        throw new IllegalArgumentException("Размер файла не должен превышать 10 МиБ.");
                    String extension=extension(doc.getExt(),"bin");
                    result.add(new Item(slot,"document",url(doc.getUrl().toString()),extension,name(doc.getTitle(),"document."+extension)));
                }
                case AUDIO_MESSAGE -> {
                    var audio=attachment.getAudioMessage();
                    if(audio==null || audio.getLinkOgg()==null) throw unsupported();
                    result.add(new Item(slot,"audio_message",url(audio.getLinkOgg().toString()),"ogg","voice.ogg"));
                }
                default -> throw unsupported();
            }
        }
        return List.copyOf(result);
    }

    static OffsetDateTime occurredAt(Message message) {
        if(message.getDate()==null || message.getDate()<=0) throw new IllegalArgumentException("Вложение не содержит времени сообщения.");
        return Instant.ofEpochSecond(message.getDate()).atOffset(ZoneOffset.UTC);
    }
    private static long area(PhotoSizes size) {
        return (long)Math.max(0,Objects.requireNonNullElse(size.getWidth(),0))*Math.max(0,Objects.requireNonNullElse(size.getHeight(),0));
    }
    static URI url(String value) {
        try {
            URI uri=URI.create(value);String host=uri.getHost()==null?"":uri.getHost().toLowerCase(Locale.ROOT);
            if(!"https".equals(uri.getScheme()) || uri.getUserInfo()!=null || uri.getFragment()!=null
                    || (uri.getPort()!=-1 && uri.getPort()!=443) || CDN_DOMAINS.stream().noneMatch(domain->host.equals(domain) || host.endsWith("."+domain)))
                throw unsupported();
            return uri;
        } catch(IllegalArgumentException ex) {throw unsupported();}
    }
    private static String extension(String value,String fallback) {
        String normalized=value==null?"":value.toLowerCase(Locale.ROOT);
        return normalized.matches("[a-z0-9]{1,10}")?normalized:fallback;
    }
    private static String name(String value,String fallback) {
        if(value==null || value.isBlank()) return fallback;
        String safe=value.replace('\\','/');safe=safe.substring(safe.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]","").strip();
        if(safe.isBlank()) return fallback;
        return safe.substring(0,Math.min(255,safe.length()));
    }
    private static IllegalArgumentException unsupported() {
        return new IllegalArgumentException("Отправьте фото, документ или голосовое сообщение с доступным файлом VK.");
    }
}
