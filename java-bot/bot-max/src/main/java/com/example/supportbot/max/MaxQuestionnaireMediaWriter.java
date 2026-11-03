package com.example.supportbot.max;

import com.example.supportbot.service.AttachmentService;
import com.example.supportbot.service.QuestionnaireMedia;
import java.io.*;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Byte/storage primitive only. Owner must come from frozen selection or canonical admission, never current chat settings. */
@Component
public final class MaxQuestionnaireMediaWriter {
    private final MaxApiClient api;
    private final AttachmentService storage;
    public MaxQuestionnaireMediaWriter(MaxApiClient api,AttachmentService storage) {
        this.api=Objects.requireNonNull(api);this.storage=Objects.requireNonNull(storage);
    }
    public record Owner(long channelId,Long businessId,long userId,String catalogSha256,long eventId) {
        public Owner {QuestionnaireMedia.maxPrefix(channelId,businessId,userId,catalogSha256,eventId,1);}
    }
    public record StoredMedia(String storageKey,String name,String type,int size,String sha256,long providerMessageId,int slot) {}

    public StoredMedia store(Owner owner,int slot,String type,String url,String originalName) throws IOException,InterruptedException {
        Objects.requireNonNull(owner,"Frozen media owner required");
        String prefix=QuestionnaireMedia.maxPrefix(owner.channelId(),owner.businessId(),owner.userId(),owner.catalogSha256(),owner.eventId(),slot);
        if(type==null || !QuestionnaireMedia.TYPES.contains(type))throw new IllegalArgumentException("Unsupported MAX scoped media type");
        String name=safeName(originalName);
        var downloaded=api.downloadQuestionnaireAttachment(url);
        if(downloaded==null)throw new IOException("MAX scoped media bytes missing");
        byte[] bytes=downloaded.bytes();
        if(bytes.length<1 || bytes.length>QuestionnaireMedia.MAX_BYTES)throw new AttachmentService.MediaSizeException();
        String extension=MaxAttachmentMetadataSupport.resolveAttachmentExtension(name,downloaded.contentType(),type);
        String sha=QuestionnaireMedia.sha256(bytes),key=prefix+sha+"."+extension;
        try(var input=new ByteArrayInputStream(bytes)) {
            var stored=storage.storeMaxQuestionnaire(owner.channelId(),owner.businessId(),owner.userId(),owner.catalogSha256(),owner.eventId(),slot,extension,input);
            if(stored==null || !key.equals(stored.storageKey()))throw new IOException("MAX scoped storage proof mismatch");
        }
        return new StoredMedia(key,name==null?"вложение."+extension:name,type,bytes.length,sha,owner.eventId(),slot);
    }
    private static String safeName(String raw) {
        if(raw==null || raw.contains("://"))return null;
        String value=raw.replace('\\','/');value=value.substring(value.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]","").strip();
        return value.isBlank()?null:value.substring(0,Math.min(value.length(),255));
    }
}
